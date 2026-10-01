package com.example.pix.google

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GoogleCalendarApiTest {
    private val server = MockWebServer()
    private lateinit var api: GoogleCalendarApi
    @Before fun setup() {
        server.start()
        api = GoogleCalendarApi(OkHttpClient(), server.url("/").toString())
    }
    @After fun cleanup() { server.shutdown() }
    private fun response(body: String, code: Int = 200) {
        server.enqueue(MockResponse().setResponseCode(code).setBody(body))
    }

    @Test fun calendarListTraversesAllPagesIncludingHiddenCalendars() = runBlocking {
        response("""{"items":[{"id":"a"}],"nextPageToken":"page 2"}""")
        response("""{"items":[{"id":"b"}]}""")
        assertEquals(listOf("a", "b"), api.calendars("token").map { it.getString("id") })
        assertEquals("true", server.takeRequest().requestUrl!!.queryParameter("showHidden"))
        assertEquals("page 2", server.takeRequest().requestUrl!!.queryParameter("pageToken"))
    }

    @Test fun incrementalPagesKeepOriginalSyncTokenAndEncodeCalendarIdentity() = runBlocking {
        response("""{"items":[],"nextPageToken":"p2"}""")
        response("""{"items":[{"id":"cancelled","status":"cancelled"}],"nextSyncToken":"next"}""")
        val result = api.events("token", "name/a@example.com", "original")
        assertFalse(result.full)
        assertEquals("next", result.nextSyncToken)
        repeat(2) {
            val request = server.takeRequest()
            assertEquals("original", request.requestUrl!!.queryParameter("syncToken"))
            assertNull(request.requestUrl!!.queryParameter("timeMin"))
            assertEquals("name/a@example.com", request.requestUrl!!.pathSegments[1])
        }
    }

    @Test fun fullPagesKeepSameTimeBoundary() = runBlocking {
        response("""{"nextPageToken":"p2"}""")
        response("""{"nextSyncToken":"next"}""")
        assertTrue(api.events("t", "c", null).full)
        assertEquals(server.takeRequest().requestUrl!!.queryParameter("timeMin"),
            server.takeRequest().requestUrl!!.queryParameter("timeMin"))
    }

    @Test fun expiredSyncTokenRestartsOnlyAffectedCalendar() = runBlocking {
        response("{}", 410)
        response("""{"items":[{"id":"new"}],"nextSyncToken":"fresh"}""")
        val result = api.events("t", "c", "expired")
        assertTrue(result.full)
        assertEquals("fresh", result.nextSyncToken)
        assertEquals("expired", server.takeRequest().requestUrl!!.queryParameter("syncToken"))
        assertNull(server.takeRequest().requestUrl!!.queryParameter("syncToken"))
    }

    @Test fun missingFinalTokenAndBrokenSecondPageFailWithoutPartialResult() {
        response("""{"items":[]}""")
        assertThrows(Exception::class.java) { runBlocking { api.events("t", "c", null) } }
        response("""{"items":[{"id":"first"}],"nextPageToken":"p2"}""")
        response("{}", 503)
        assertThrows(GoogleCalendarApi.HttpError::class.java) { runBlocking { api.events("t", "c", "s") } }
    }

    @Test fun quotaErrorsDoNotRequestInteractiveReconnect() = runBlocking {
        response("""{"error":{"errors":[{"reason":"rateLimitExceeded"}]}}""", 403)
        try { api.calendars("t"); fail("expected error") }
        catch (e: GoogleCalendarApi.HttpError) { assertFalse(e.requiresAuthorization) }
        response("{}", 401)
        try { api.calendars("t"); fail("expected error") }
        catch (e: GoogleCalendarApi.HttpError) { assertTrue(e.requiresAuthorization) }
    }
}
