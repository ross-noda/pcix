package com.example.pix.google

import java.io.IOException
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

/** Read-only transport. All pages are validated before the repository changes its offline cache. */
internal class GoogleCalendarApi(
    private val client: OkHttpClient,
    private val baseUrl: String = "https://www.googleapis.com/calendar/v3/",
    private val identityUrl: String = "https://openidconnect.googleapis.com/v1/userinfo",
) {
    class HttpError(val code: Int, val reason: String?) : IOException("Google Calendar HTTP $code") {
        val retryable: Boolean get() = code == 408 || code == 429 || code >= 500 ||
            (code == 403 && reason in setOf("rateLimitExceeded", "userRateLimitExceeded", "quotaExceeded", "dailyLimitExceeded"))
        val requiresAuthorization: Boolean
            get() = code == 401 || (code == 403 && reason in setOf("insufficientPermissions", "authError", "forbidden"))
    }
    data class Events(val items: List<JSONObject>, val nextSyncToken: String, val full: Boolean)

    private suspend fun get(url: String, token: String): JSONObject = withContext(Dispatchers.IO) {
        client.newCall(Request.Builder().url(url).header("Authorization", "Bearer $token").build())
            .execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    val reason = runCatching {
                        JSONObject(body).optJSONObject("error")?.optJSONArray("errors")
                            ?.optJSONObject(0)?.optString("reason")?.takeIf { it.matches(Regex("[a-zA-Z_]{1,64}")) }
                    }.getOrNull()
                    throw HttpError(response.code, reason)
                }
                JSONObject(body)
            }
    }

    suspend fun identity(token: String): JSONObject = get(identityUrl, token)

    suspend fun calendars(token: String): List<JSONObject> {
        val items = mutableListOf<JSONObject>()
        val seen = mutableSetOf<String>()
        var page: String? = null
        do {
            val url = (baseUrl + "users/me/calendarList").toHttpUrl().newBuilder()
                .addQueryParameter("showHidden", "true")
            page?.let { url.addQueryParameter("pageToken", it) }
            val json = get(url.build().toString(), token)
            json.optJSONArray("items")?.let { a ->
                for (i in 0 until a.length()) items += a.getJSONObject(i)
            }
            page = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            check(page == null || seen.add(page)) { "Repeated CalendarList page token" }
        } while (page != null)
        return items
    }

    suspend fun events(token: String, calendarId: String, syncToken: String?): Events {
        return try {
            eventPages(token, calendarId, syncToken)
        } catch (error: HttpError) {
            if (error.code != 410 || syncToken == null) throw error
            // Exactly one full retry. Old Room cache survives if this download also fails.
            eventPages(token, calendarId, null)
        }
    }

    private suspend fun eventPages(token: String, calendarId: String, syncToken: String?): Events {
        val items = mutableListOf<JSONObject>()
        val seen = mutableSetOf<String>()
        var page: String? = null
        val min = Instant.now().minus(365, ChronoUnit.DAYS).toString()
        do {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegment("calendars").addPathSegment(calendarId).addPathSegment("events")
                .addQueryParameter("singleEvents", "true").addQueryParameter("showDeleted", "true")
                .addQueryParameter("maxResults", "2500")
            if (syncToken != null) url.addQueryParameter("syncToken", syncToken)
            else url.addQueryParameter("timeMin", min)
            page?.let { url.addQueryParameter("pageToken", it) }
            val json = get(url.build().toString(), token)
            json.optJSONArray("items")?.let { a ->
                for (i in 0 until a.length()) items += a.getJSONObject(i)
            }
            page = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            check(page == null || seen.add(page)) { "Repeated Events page token" }
            if (page == null) {
                val next = json.getString("nextSyncToken")
                check(next.isNotBlank()) { "Missing final sync token" }
                return Events(items, next, syncToken == null)
            }
        } while (true)
    }
}
