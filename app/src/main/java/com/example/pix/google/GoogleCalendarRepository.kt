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
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import org.json.JSONArray
import org.json.JSONObject

class GoogleCalendarRepository internal constructor(
    private val context: Context,
    private val db: PixDatabase,
    private val http: CloudHttp,
    @Suppress("UNUSED_PARAMETER") config: CloudConfig,
    private val authorization: GoogleAuthorizationGateway =
        PlayServicesGoogleAuthorizationGateway(context),
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val dao = db.googleDao()
    private val _activeGoogleAccountId = MutableStateFlow(prefs.getString(KEY_ACCOUNT_ID, null))
    val calendars =
        _activeGoogleAccountId.flatMapLatest { accountId ->
            if (accountId == null) flowOf(emptyList()) else dao.observeCalendars(accountId)
        }
    private val _needsReconnect = MutableStateFlow(prefs.getBoolean(KEY_RECONNECT, false))
    val needsReconnect: StateFlow<Boolean> = _needsReconnect

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

    suspend fun authorize(@Suppress("UNUSED_PARAMETER") activity: Activity): AuthOutcome =
        runCatching {
                val account = activeAccount()
                when (val result = authorization.authorize(account?.email, interactive = true)) {
                    is GoogleAuthorizationResult.Authorized ->
                        finishAuthorization(result.accessToken, account)
                    is GoogleAuthorizationResult.Resolution -> AuthOutcome.Resolution(result.sender)
                    GoogleAuthorizationResult.Unavailable -> AuthOutcome.Failed
                }
            }
            .getOrElse {
                Log.w(TAG, "calendar authorization failed", it)
                AuthOutcome.Failed
            }

    suspend fun completeAuthorization(
        @Suppress("UNUSED_PARAMETER") activity: Activity,
        data: android.content.Intent,
    ) {
        runCatching {
                val result = authorization.complete(data) ?: return@runCatching
                finishAuthorization(result.accessToken, activeAccount())
            }
            .onFailure { Log.w(TAG, "calendar authorization completion failed", it) }
    }

    suspend fun setEnabled(id: String, enabled: Boolean) {
        val account = activeAccount() ?: return
        dao.setEnabled(account.id, id, enabled)
    }

    suspend fun synchronize(@Suppress("UNUSED_PARAMETER") activity: Activity? = null): SyncOutcome {
        if (!connected()) return SyncOutcome.NotConnected
        val account = activeAccount() ?: return markReconnect()
        val token =
            when (val result = authorization.authorize(account.email, interactive = false)) {
                is GoogleAuthorizationResult.Authorized -> result.accessToken
                is GoogleAuthorizationResult.Resolution,
                GoogleAuthorizationResult.Unavailable -> return markReconnect()
            }
        markReady(account)
        val enabled = dao.calendars(account.id).filter { it.enabled }
        for (calendar in enabled) {
            if (!syncCalendar(token, account, calendar)) return SyncOutcome.NeedsReconnect
        }
        return SyncOutcome.Synced
    }

    suspend fun disconnect(@Suppress("UNUSED_PARAMETER") activity: Activity?) {
        val account = activeAccount()
        if (account != null) {
            runCatching { authorization.revokeCalendarAccess(account.email) }
                .onFailure { Log.w(TAG, "calendar authorization revocation failed", it) }
        }
        prefs.edit().clear().apply()
        _activeGoogleAccountId.value = null
        _needsReconnect.value = false
        db.withTransaction {
            // Account row owns calendars, events and sync-state through CASCADE.
            dao.clearAccounts()
        }
    }

    private suspend fun finishAuthorization(
        token: String,
        existingAccount: GoogleCalendarAccountEntity?,
    ): AuthOutcome {
        val account = existingAccount ?: fetchGoogleAccount(token) ?: return AuthOutcome.Failed
        if (existingAccount == null) {
            db.withTransaction {
                dao.clearAccounts()
                dao.saveAccount(account)
            }
        }
        markReady(account)
        if (!refreshCalendarList(token, account)) return AuthOutcome.Failed
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
            .apply()
        _activeGoogleAccountId.value = account.id
        _needsReconnect.value = false
    }

    private fun markReconnect(): SyncOutcome {
        prefs.edit().putBoolean(KEY_RECONNECT, true).apply()
        _needsReconnect.value = true
        Log.w(TAG, "calendar authorization must be renewed")
        return SyncOutcome.NeedsReconnect
    }

    private fun fetchGoogleAccount(token: String): GoogleCalendarAccountEntity? {
        val response =
            http.client
                .newCall(
                    okhttp3.Request.Builder()
                        .url("https://openidconnect.googleapis.com/v1/userinfo")
                        .header("Authorization", "Bearer $token")
                        .build()
                )
                .execute()
        val code = response.code
        val body = response.body?.string().orEmpty()
        response.close()
        if (code !in 200..299) return null
        val json = JSONObject(body)
        val id = json.optString("sub").takeIf { it.isNotBlank() } ?: return null
        val accountEmail = json.optString("email").takeIf { it.isNotBlank() } ?: return null
        return GoogleCalendarAccountEntity(id = id, email = accountEmail)
    }

    private suspend fun refreshCalendarList(
        token: String,
        account: GoogleCalendarAccountEntity,
    ): Boolean {
        val response =
            http.client
                .newCall(
                    okhttp3.Request.Builder()
                        .url("https://www.googleapis.com/calendar/v3/users/me/calendarList")
                        .header("Authorization", "Bearer $token")
                        .build()
                )
                .execute()
        val code = response.code
        val body = response.body?.string().orEmpty()
        response.close()
        if (code == 401 || code == 403) {
            markReconnect()
            return false
        }
        if (code !in 200..299) throw IllegalStateException("calendarList $code")
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        val existing = dao.calendars(account.id).associateBy { it.id }
        db.withTransaction {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.getString("id")
                val previous = existing[id]
                dao.saveCalendar(
                    GoogleCalendarEntity(
                        accountId = account.id,
                        id = id,
                        summary = item.optString("summary").ifBlank { id },
                        colorArgb =
                            GoogleColors.argb(
                                item.optString("backgroundColor").ifBlank { null }
                            ),
                        timeZone = item.optString("timeZone").takeIf { it.isNotBlank() },
                        enabled = previous?.enabled ?: true,
                        accessRole = item.optString("accessRole").takeIf { it.isNotBlank() },
                    )
                )
            }
        }
        return true
    }

    private suspend fun syncCalendar(
        token: String,
        account: GoogleCalendarAccountEntity,
        calendar: GoogleCalendarEntity,
    ): Boolean {
        var pageToken: String? = null
        var syncToken = dao.syncState(account.id, calendar.id)?.syncToken
        while (true) {
            val url =
                StringBuilder("https://www.googleapis.com/calendar/v3/calendars/")
                    .append(URLEncoder.encode(calendar.id, "UTF-8"))
                    .append("/events?singleEvents=true&showDeleted=true")
            if (pageToken != null) {
                url.append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
            } else if (syncToken != null) {
                url.append("&syncToken=").append(URLEncoder.encode(syncToken, "UTF-8"))
            } else {
                val min = Instant.now().minus(365, ChronoUnit.DAYS).toString()
                url.append("&timeMin=").append(URLEncoder.encode(min, "UTF-8"))
            }
            val response =
                http.client
                    .newCall(
                        okhttp3.Request.Builder()
                            .url(url.toString())
                            .header("Authorization", "Bearer $token")
                            .build()
                    )
                    .execute()
            val code = response.code
            val body = response.body?.string().orEmpty()
            response.close()
            if (code == 410 && syncToken != null) {
                dao.saveSyncState(GoogleSyncStateEntity(account.id, calendar.id, null, 0))
                dao.clearEvents(account.id, calendar.id)
                syncToken = null
                pageToken = null
                continue
            }
            if (code == 401 || code == 403) {
                markReconnect()
                return false
            }
            if (code !in 200..299) throw IllegalStateException("events $code")
            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: JSONArray()
            val zone = ZoneId.of(calendar.timeZone ?: ZoneId.systemDefault().id)
            db.withTransaction {
                for (i in 0 until items.length()) {
                    val parsed =
                        GoogleEventParser.parse(
                            account.id,
                            calendar.id,
                            calendar.colorArgb,
                            items.getJSONObject(i),
                            zone,
                        ) ?: continue
                    if (parsed.cancelled) {
                        dao.deleteEvent(account.id, calendar.id, parsed.eventId)
                    } else {
                        dao.saveEvent(parsed)
                    }
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            val nextSync = json.optString("nextSyncToken").takeIf { it.isNotBlank() }
            if (nextSync != null) {
                dao.saveSyncState(
                    GoogleSyncStateEntity(
                        accountId = account.id,
                        calendarId = calendar.id,
                        syncToken = nextSync,
                        lastSyncAt = System.currentTimeMillis(),
                    )
                )
            }
            if (pageToken == null) break
        }
        return true
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
