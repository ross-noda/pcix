package com.example.pix.google

import android.app.Activity
import android.content.Context
import android.content.IntentSender
import android.util.Log
import androidx.room.withTransaction
import com.example.pix.cloud.CloudConfig
import com.example.pix.cloud.CloudHttp
import com.example.pix.data.GoogleCalendarEntity
import com.example.pix.data.GoogleSyncStateEntity
import com.example.pix.data.PixDatabase
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

class GoogleCalendarRepository(
    private val context: Context,
    private val db: PixDatabase,
    private val http: CloudHttp,
    private val config: CloudConfig,
) {
    private val prefs = context.getSharedPreferences("pcix.google", Context.MODE_PRIVATE)
    private val dao = db.googleDao()
    val calendars = dao.observeCalendars()
    private val _needsReconnect = MutableStateFlow(prefs.getBoolean("reconnect", false))
    val needsReconnect: StateFlow<Boolean> = _needsReconnect
    val email: String?
        get() = prefs.getString("email", null)

    fun connected() = prefs.getBoolean("connected", false)

    fun events(start: Long, end: Long) = dao.observeEvents(start, end)

    fun authorizationIntentSender(activity: Activity): IntentSender? = null

    suspend fun authorize(activity: Activity): AuthOutcome {
        val request =
            AuthorizationRequest.builder()
                .setRequestedScopes(
                    listOf(
                        Scope("https://www.googleapis.com/auth/calendar.calendarlist.readonly"),
                        Scope("https://www.googleapis.com/auth/calendar.events.readonly"),
                    )
                )
                .build()
        val result = Identity.getAuthorizationClient(activity).authorize(request).await()
        if (result.hasResolution()) {
            return AuthOutcome.Resolution(result.pendingIntent!!.intentSender)
        }
        val token = result.accessToken ?: return AuthOutcome.Failed
        prefs
            .edit()
            .putBoolean("connected", true)
            .putBoolean("reconnect", false)
            .putString("email", result.grantedScopes?.let { email } ?: email)
            .apply()
        _needsReconnect.value = false
        storeToken(token)
        refreshCalendarList()
        return AuthOutcome.Ready
    }

    suspend fun completeAuthorization(activity: Activity, data: android.content.Intent) {
        val result = Identity.getAuthorizationClient(activity).getAuthorizationResultFromIntent(data)
        val token = result.accessToken ?: return
        prefs.edit().putBoolean("connected", true).putBoolean("reconnect", false).apply()
        _needsReconnect.value = false
        storeToken(token)
        refreshCalendarList()
    }

    suspend fun accessToken(activity: Activity?): String? {
        val cached = prefs.getString("access", null)
        val expiry = prefs.getLong("accessExpires", 0)
        if (cached != null && System.currentTimeMillis() < expiry - 30_000) return cached
        if (activity == null) return cached
        return when (val outcome = authorize(activity)) {
            AuthOutcome.Ready -> prefs.getString("access", null)
            else -> null
        }
    }

    private fun storeToken(token: String) {
        prefs.edit().putString("access", token).putLong("accessExpires", System.currentTimeMillis() + 45 * 60_000).apply()
    }

    suspend fun refreshCalendarList(activity: Activity? = null) {
        val token = accessToken(activity) ?: return markReconnect()
        val response =
            http.client
                .newCall(
                    okhttp3.Request.Builder()
                        .url("https://www.googleapis.com/calendar/v3/users/me/calendarList")
                        .header("Authorization", "Bearer $token")
                        .build()
                )
                .execute()
        if (response.code == 401 || response.code == 403) {
            response.close()
            return markReconnect()
        }
        val body = response.body?.string().orEmpty()
        response.close()
        if (!response.isSuccessful) throw IllegalStateException("calendarList")
        val items = JSONObject(body).optJSONArray("items") ?: JSONArray()
        val existing = dao.calendars().associateBy { it.id }
        db.withTransaction {
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val id = item.getString("id")
                val previous = existing[id]
                dao.saveCalendar(
                    GoogleCalendarEntity(
                        id = id,
                        summary = item.optString("summary").ifBlank { id },
                        colorArgb = GoogleColors.argb(item.optString("backgroundColor").ifBlank { null }),
                        timeZone = item.optString("timeZone").takeIf { it.isNotBlank() },
                        enabled = previous?.enabled ?: true,
                        accessRole = item.optString("accessRole"),
                    )
                )
            }
        }
        prefs.edit().putString("email", inferEmail(items)).apply()
    }

    suspend fun setEnabled(id: String, enabled: Boolean) = dao.setEnabled(id, enabled)

    suspend fun synchronize(activity: Activity? = null) {
        if (!connected()) return
        val token = accessToken(activity) ?: return markReconnect()
        val enabled = dao.calendars().filter { it.enabled }
        enabled.forEach { calendar -> syncCalendar(token, calendar) }
    }

    private suspend fun syncCalendar(token: String, calendar: GoogleCalendarEntity) {
        var state = dao.syncState(calendar.id)
        var pageToken: String? = null
        var syncToken = state?.syncToken
        var first = syncToken == null
        while (true) {
            val url = StringBuilder("https://www.googleapis.com/calendar/v3/calendars/")
                .append(URLEncoder.encode(calendar.id, "UTF-8"))
                .append("/events?singleEvents=true&showDeleted=true")
            if (pageToken != null) url.append("&pageToken=").append(URLEncoder.encode(pageToken, "UTF-8"))
            else if (syncToken != null) url.append("&syncToken=").append(URLEncoder.encode(syncToken, "UTF-8"))
            else {
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
                dao.saveSyncState(GoogleSyncStateEntity(calendar.id, null, 0))
                dao.clearEvents(calendar.id)
                syncToken = null
                pageToken = null
                continue
            }
            if (code == 401 || code == 403) return markReconnect()
            if (code !in 200..299) throw IllegalStateException("events $code")
            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: JSONArray()
            val zone = ZoneId.of(calendar.timeZone ?: ZoneId.systemDefault().id)
            db.withTransaction {
                for (i in 0 until items.length()) {
                    val parsed =
                        GoogleEventParser.parse(calendar.id, calendar.colorArgb, items.getJSONObject(i), zone)
                            ?: continue
                    if (parsed.cancelled) dao.deleteEvent(parsed.id) else dao.saveEvent(parsed)
                }
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            val nextSync = json.optString("nextSyncToken").takeIf { it.isNotBlank() }
            if (nextSync != null) {
                dao.saveSyncState(
                    GoogleSyncStateEntity(calendar.id, nextSync, System.currentTimeMillis())
                )
            }
            if (pageToken == null) break
            if (first) first = false
        }
    }

    suspend fun disconnect(activity: Activity?) {
        runCatching {
            val token = prefs.getString("access", null)
            if (token != null) {
                Identity.getAuthorizationClient(activity ?: return@runCatching)
                    .clearToken(
                        com.google.android.gms.auth.api.identity.ClearTokenRequest.builder()
                            .setToken(token)
                            .build()
                    )
                    .await()
            }
        }
        prefs.edit().clear().apply()
        _needsReconnect.value = false
        db.withTransaction {
            dao.clearAllEvents()
            dao.clearCalendars()
            dao.clearSyncState()
        }
    }

    private fun markReconnect() {
        prefs.edit().putBoolean("reconnect", true).remove("access").apply()
        _needsReconnect.value = true
        Log.w("PcixGoogle", "calendar authorization must be renewed")
    }

    private fun inferEmail(items: JSONArray): String? {
        for (i in 0 until items.length()) {
            val id = items.getJSONObject(i).optString("id")
            if ('@' in id) return id
        }
        return email
    }

    sealed class AuthOutcome {
        data object Ready : AuthOutcome()

        data class Resolution(val sender: IntentSender) : AuthOutcome()

        data object Failed : AuthOutcome()
    }
}
