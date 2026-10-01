package com.example.pix.google

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.util.Log
import androidx.room.withTransaction
import com.example.pix.cloud.CloudConfig
import com.example.pix.cloud.CloudHttp
import com.example.pix.data.GoogleCalendarAccountEntity
import com.example.pix.data.GoogleCalendarEntity
import com.example.pix.data.GoogleSyncStateEntity
import com.example.pix.data.PixDatabase
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.json.JSONObject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class GoogleCalendarRepository internal constructor(
    private val context: Context,
    private val db: PixDatabase,
    private val http: CloudHttp,
    @Suppress("UNUSED_PARAMETER") config: CloudConfig,
    private val authorization: GoogleAuthorizationGateway =
        PlayServicesGoogleAuthorizationGateway(context),
    private val api: GoogleCalendarApi = GoogleCalendarApi(http.client),
) {
    private val mutex = Mutex()
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dao = db.googleDao()
    private val _activeGoogleAccountId = MutableStateFlow(prefs.getString(KEY_ACCOUNT_ID, null))
    val calendars =
        _activeGoogleAccountId.flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList()) else dao.observeCalendars(accountId)
        }
    private val _needsReconnect = MutableStateFlow(prefs.getBoolean(KEY_RECONNECT, false))
    val needsReconnect: StateFlow<Boolean> = _needsReconnect

    enum class Issue { Cancelled, Callback, Permission, Configuration, Network, Other }
    data class ConnectionState(val busy: Boolean = false, val email: String? = null, val issue: Issue? = null, val resolution: IntentSender? = null)
    private val _connection = MutableStateFlow(ConnectionState(email = if (connected()) prefs.getString(KEY_ACCOUNT_EMAIL, null) else null))
    val connection: StateFlow<ConnectionState> = _connection

    fun beginSelection() { _connection.value = _connection.value.copy(busy = true, issue = null, resolution = null) }
    fun resolutionLaunched() { _connection.value = _connection.value.copy(resolution = null) }
    fun connectionCancelled(missingResult: Boolean = false) {
        _connection.value = _connection.value.copy(busy = false, resolution = null, issue = if (missingResult) Issue.Callback else Issue.Cancelled)
        Log.i(TAG, if (missingResult) "calendar callback missing result" else "calendar connection cancelled")
    }
    fun reportFailure(error: Exception, phase: String) {
        val issue = when(error) {
            is CalendarPermissionException -> Issue.Permission
            is com.google.android.gms.common.api.ApiException -> when(error.statusCode) {
                10 -> Issue.Configuration
                7, 8, 14, 15 -> Issue.Network
                16 -> Issue.Cancelled
                else -> Issue.Permission
            }
            is GoogleCalendarApi.HttpError -> when {
                error.requiresAuthorization -> Issue.Permission
                error.retryable -> Issue.Network
                error.code == 403 -> Issue.Configuration
                else -> Issue.Other
            }
            is java.io.IOException -> Issue.Network
            else -> Issue.Other
        }
        val detail = when(error) {
            is GoogleCalendarApi.HttpError -> "http=${error.code} reason=${error.reason}"
            is com.google.android.gms.common.api.ApiException -> "oauth=${error.statusCode}"
            else -> "type=${error.javaClass.simpleName}"
        }
        Log.w(TAG, "phase=$phase $detail category=$issue")
        _connection.value = _connection.value.copy(busy = false, issue = issue, resolution = null)
    }

    val email: String?
        get() = prefs.getString(KEY_ACCOUNT_EMAIL, null)

    init {
        // Previous builds persisted bearer tokens with a made-up 45-minute expiry. Remove those
        // values unconditionally; Google Play services is now the only token cache.
        if (prefs.contains(LEGACY_ACCESS) || prefs.contains(LEGACY_ACCESS_EXPIRES)) {
            prefs.edit().remove(LEGACY_ACCESS).remove(LEGACY_ACCESS_EXPIRES).apply()
        }
        // v7 Google cache had no trustworthy account owner. Never infer it from calendar IDs.
        if (prefs.getBoolean(KEY_CONNECTED, false) && prefs.getString(KEY_ACCOUNT_ID, null) == null) {
            prefs
                .edit()
                .putBoolean(KEY_RECONNECT, true)
                .remove(KEY_ACCOUNT_EMAIL)
                .apply()
            _needsReconnect.value = true
        }
    }

    fun connected() = prefs.getBoolean(KEY_CONNECTED, false)

    fun events(start: Long, end: Long) =
        _activeGoogleAccountId.flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList()) else dao.observeEvents(accountId, start, end)
        }

    suspend fun authorize(@Suppress("UNUSED_PARAMETER") activity: Activity? = null, accountEmail: String? = null): AuthOutcome = mutex.withLock {
        authorizationOutcome {
            when (val result = authorization.authorize(accountEmail ?: activeAccount()?.email, interactive = true)) {
                is GoogleAuthorizationResult.Authorized -> finishAuthorization(result.accessToken)
                is GoogleAuthorizationResult.Resolution -> AuthOutcome.Resolution(result.sender)
                GoogleAuthorizationResult.Unavailable -> AuthOutcome.Failed
            }
        }
    }

    suspend fun completeAuthorization(
        @Suppress("UNUSED_PARAMETER") activity: Activity? = null,
        data: android.content.Intent,
    ): AuthOutcome = mutex.withLock {
        authorizationOutcome {
            val result = authorization.complete(data) ?: return@authorizationOutcome AuthOutcome.Failed
            finishAuthorization(result.accessToken)
        }
    }

    private suspend fun authorizationOutcome(block: suspend () -> AuthOutcome): AuthOutcome {
        beginSelection()
        return try {
            block().also { outcome ->
                when (outcome) {
                    is AuthOutcome.Resolution -> _connection.value = _connection.value.copy(resolution = outcome.sender)
                    AuthOutcome.Ready -> _connection.value = ConnectionState(email = email)
                    AuthOutcome.Failed -> {
                        Log.w(TAG, "phase=grant category=Permission result=unavailable")
                        _connection.value = _connection.value.copy(busy = false, issue = Issue.Permission)
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            _connection.value = _connection.value.copy(busy = false)
            throw cancelled
        } catch (error: Exception) {
            reportFailure(error, "connect")
            AuthOutcome.Failed
        }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = mutex.withLock {
        val account = activeAccount() ?: return@withLock
        dao.setEnabled(account.id, id, enabled)
    }

    suspend fun synchronize(@Suppress("UNUSED_PARAMETER") activity: Activity? = null): SyncOutcome = mutex.withLock {
        if (!connected()) return@withLock SyncOutcome.NotConnected
        val account = activeAccount() ?: return@withLock markReconnect()
        try {
            val token = when (val result = authorization.authorize(account.email, interactive = false)) {
                is GoogleAuthorizationResult.Authorized -> result.accessToken
                else -> return@withLock markReconnect()
            }
            refreshCalendarList(token, account)
            for (calendar in dao.calendars(account.id).filter { it.enabled }) {
                syncCalendar(token, account, calendar)
            }
            markReady(account)
            SyncOutcome.Synced
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            reportFailure(error, "sync")
            if (connection.value.issue == Issue.Permission) markReconnect() else throw error
        }
    }

    suspend fun disconnect(@Suppress("UNUSED_PARAMETER") activity: Activity?) = mutex.withLock {
        val account = activeAccount()
        // Disconnect is serialized with downloads; a late response cannot repopulate the cache.
        if (account != null) {
            try { authorization.revokeCalendarAccess(account.email) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (error: Exception) { reportFailure(error, "revoke-local-disconnect") }
        }
        db.withTransaction { dao.clearAccounts() }
        prefs.edit().clear().commit()
        _activeGoogleAccountId.value = null
        _needsReconnect.value = false
        _connection.value = ConnectionState()
    }

    private suspend fun finishAuthorization(token: String): AuthOutcome {
        // Resolve identity from this grant, never from the P©ix login or a previous Calendar grant.
        val identity = api.identity(token)
        val account = GoogleCalendarAccountEntity(identity.getString("sub"), identity.getString("email"))
        require(account.id.isNotBlank() && account.email.isNotBlank())
        val existing = dao.calendars(account.id).associateBy { it.id }
        val calendars = api.calendars(token).filterNot { it.optBoolean("deleted") }.map { item ->
            val id = item.getString("id")
            GoogleCalendarEntity(account.id, id,
                item.optString("summaryOverride").ifBlank { item.optString("summary").ifBlank { id } },
                colorArgb = GoogleColors.argb(item.optString("backgroundColor").ifBlank { null }),
                timeZone = item.optString("timeZone").takeIf { it.isNotBlank() },
                enabled = existing[id]?.enabled ?: true,
                accessRole = item.optString("accessRole").takeIf { it.isNotBlank() })
        }
        val zone = ZoneId.systemDefault()
        val downloaded = calendars.filter { it.enabled }.map { calendar ->
            val result = api.events(token, calendar.id, null)
            val events = result.items.map { item ->
                GoogleEventParser.parse(account.id, calendar.id, calendar.colorArgb, item, zone)
                    ?: error("Invalid Google event")
            }
            Triple(calendar, result, events)
        }
        // A failed identity/list/event request leaves the previous offline account/cache intact.
        db.withTransaction {
            if (activeAccount()?.id != account.id) dao.clearAccounts()
            dao.saveAccount(account)
            for (calendar in calendars) dao.saveCalendar(calendar)
            for (id in existing.keys - calendars.map { it.id }.toSet()) dao.deleteCalendar(account.id, id)
            for ((calendar, result, events) in downloaded) {
                dao.clearEvents(account.id, calendar.id)
                events.filterNot { it.cancelled }.forEach { dao.saveEvent(it) }
                dao.saveSyncState(GoogleSyncStateEntity(account.id, calendar.id, result.nextSyncToken, System.currentTimeMillis()))
            }
        }
        prefs.edit().also { editor -> downloaded.forEach { (calendar, _, _) ->
            editor.putString("zone:${account.id}:${calendar.id}", zone.id)
        } }.commit()
        markReady(account)
        return AuthOutcome.Ready
    }

    private suspend fun activeAccount(): GoogleCalendarAccountEntity? {
        val id = prefs.getString(KEY_ACCOUNT_ID, null) ?: return null
        return dao.account(id)
    }

    private fun markReady(account: GoogleCalendarAccountEntity) {
        prefs
            .edit()
            .putBoolean(KEY_CONNECTED, true)
            .putBoolean(KEY_RECONNECT, false)
            .putString(KEY_ACCOUNT_ID, account.id)
            .putString(KEY_ACCOUNT_EMAIL, account.email)
            .commit()
        _activeGoogleAccountId.value = account.id
        _needsReconnect.value = false
        _connection.value = ConnectionState(email = account.email)
    }

    private fun markReconnect(): SyncOutcome {
        prefs.edit().putBoolean(KEY_RECONNECT, true).apply()
        _needsReconnect.value = true
        _connection.value = _connection.value.copy(busy = false, issue = Issue.Permission)
        Log.w(TAG, "calendar authorization must be renewed")
        return SyncOutcome.NeedsReconnect
    }

    private suspend fun refreshCalendarList(token: String, account: GoogleCalendarAccountEntity) {
        val items = api.calendars(token)
        db.withTransaction {
            val existing = dao.calendars(account.id).associateBy { it.id }
            val retained = mutableSetOf<String>()
            for (item in items) {
                val id = item.getString("id")
                if (item.optBoolean("deleted")) continue
                retained += id
                val previous = existing[id]
                val calendar = GoogleCalendarEntity(
                    accountId = account.id, id = id,
                    summary = item.optString("summaryOverride").ifBlank { item.optString("summary").ifBlank { id } },
                    colorArgb = GoogleColors.argb(item.optString("backgroundColor").ifBlank { null }),
                    timeZone = item.optString("timeZone").takeIf { it.isNotBlank() },
                    enabled = previous?.enabled ?: true,
                    accessRole = item.optString("accessRole").takeIf { it.isNotBlank() },
                )
                dao.saveCalendar(calendar)
                dao.updateEventColor(account.id, id, calendar.colorArgb)
            }
            for (id in existing.keys - retained) dao.deleteCalendar(account.id, id)
        }
    }

    private suspend fun syncCalendar(token: String, account: GoogleCalendarAccountEntity, calendar: GoogleCalendarEntity) {
        val zone = ZoneId.systemDefault()
        val zoneKey = "zone:${account.id}:${calendar.id}"
        val previousToken = dao.syncState(account.id, calendar.id)?.syncToken
        val syncToken = if (prefs.getString(zoneKey, null) == zone.id) previousToken else null
        val result = api.events(token, calendar.id, syncToken)
        val events = result.items.map { item ->
            GoogleEventParser.parse(account.id, calendar.id, calendar.colorArgb, item, zone)
                ?: error("Invalid Google event")
        }
        db.withTransaction {
            if (result.full) dao.clearEvents(account.id, calendar.id)
            for (event in events) {
                if (event.cancelled) {
                    dao.deleteEvent(account.id, calendar.id, event.eventId)
                    // A cancelled recurrence master may contain only its id.
                    if (event.recurringEventId == null) dao.deleteInstances(account.id, calendar.id, event.eventId)
                } else dao.saveEvent(event)
            }
            dao.saveSyncState(GoogleSyncStateEntity(account.id, calendar.id, result.nextSyncToken, System.currentTimeMillis()))
        }
        prefs.edit().putString(zoneKey, zone.id).commit()
    }

    sealed class AuthOutcome {
        data object Ready : AuthOutcome()

        data class Resolution(val sender: IntentSender) : AuthOutcome()

        data object Failed : AuthOutcome()
    }

    enum class SyncOutcome {
        Synced,
        NotConnected,
        NeedsReconnect,
    }

    private companion object {
        const val TAG = "PcixGoogle"
        const val PREFS = "pcix.google"
        const val KEY_CONNECTED = "connected"
        const val KEY_RECONNECT = "reconnect"
        const val KEY_ACCOUNT_ID = "accountId"
        const val KEY_ACCOUNT_EMAIL = "accountEmail"
        const val LEGACY_ACCESS = "access"
        const val LEGACY_ACCESS_EXPIRES = "accessExpires"
    }
}
