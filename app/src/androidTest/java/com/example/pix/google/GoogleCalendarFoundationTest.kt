package com.example.pix.google

import android.content.Context
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.cloud.CloudConfig
import com.example.pix.cloud.CloudHttp
import com.example.pix.data.GoogleCalendarAccountEntity
import com.example.pix.data.GoogleCalendarEntity
import com.example.pix.data.GoogleEventEntity
import com.example.pix.data.INBOX_ID
import com.example.pix.data.ListEntity
import com.example.pix.data.PixDatabase
import com.example.pix.data.TaskEntity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.MockResponse
import java.time.ZoneId
import com.example.pix.data.GoogleSyncStateEntity
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GoogleCalendarFoundationTest {
    private lateinit var context: Context
    private lateinit var db: PixDatabase
    private val server = MockWebServer()

    @Before
    fun setUp() {
        server.start()
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("pcix.google", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        server.shutdown()
        db.close()
        context.getSharedPreferences("pcix.google", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun legacyPersistedAccessTokenAndInventedExpiryArePurged() {
        val prefs = context.getSharedPreferences("pcix.google", Context.MODE_PRIVATE)
        prefs.edit().putString("access", "legacy-token").putLong("accessExpires", 123L).commit()

        repository(FakeAuthorizationGateway())

        assertFalse(prefs.contains("access"))
        assertFalse(prefs.contains("accessExpires"))
    }

    @Test
    fun cacheHasExplicitGoogleAccountAndSameEventIdCanExistInTwoCalendars() = runBlocking {
        val dao = db.googleDao()
        val account = GoogleCalendarAccountEntity("google-sub-1", "calendar@example.com")
        dao.saveAccount(account)
        dao.saveCalendar(GoogleCalendarEntity(account.id, "calendar-a", "A"))
        dao.saveCalendar(GoogleCalendarEntity(account.id, "calendar-b", "B"))

        dao.saveEvent(event(account.id, "calendar-a", "same-id", "First"))
        dao.saveEvent(event(account.id, "calendar-b", "same-id", "Second"))

        assertEquals(account, dao.account())
        assertEquals("First", dao.events(account.id, "calendar-a").single().title)
        assertEquals("Second", dao.events(account.id, "calendar-b").single().title)
    }

    @Test fun vanishedCalendarDoesNotBlockAnotherCalendar() = runBlocking {
        val account = seedConnectedAccount()
        val dao = db.googleDao()
        dao.saveCalendar(GoogleCalendarEntity(account.id, "a", "A"))
        dao.saveCalendar(GoogleCalendarEntity(account.id, "b", "B"))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"a","summary":"A"},{"id":"b","summary":"B"}]}"""))
        server.enqueue(MockResponse().setResponseCode(404))
        server.enqueue(MockResponse().setBody("""{"items":[],"nextSyncToken":"b-token"}"""))
        val repo = repository(FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("t")))
        assertEquals(GoogleCalendarRepository.SyncOutcome.Synced, repo.synchronize())
        assertEquals(listOf("b"), dao.calendars(account.id).map { it.id })
        assertEquals("b-token", dao.syncState(account.id, "b")!!.syncToken)
    }

    @Test fun localColorsPersistAcrossRefreshAndDoNotWriteGoogle() = runBlocking {
        val account = seedConnectedAccount()
        val dao = db.googleDao()
        dao.saveCalendar(GoogleCalendarEntity(account.id, "a", "A"))
        dao.saveCalendar(GoogleCalendarEntity(account.id, "b", "B"))
        dao.saveEvent(event(account.id, "a", "same", "A"))
        dao.saveEvent(event(account.id, "b", "same", "B"))
        val repo = repository(FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("t")))
        repo.setColor("a", 123)
        repo.setColor("b", 456)
        assertEquals(0, server.requestCount)
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"a","backgroundColor":"#112233"},{"id":"b"}]}"""))
        repeat(2) { server.enqueue(MockResponse().setBody("""{"items":[{"id":"same","start":{"date":"2026-09-10"},"end":{"date":"2026-09-11"}}],"nextSyncToken":"s"}""")) }
        assertEquals(GoogleCalendarRepository.SyncOutcome.Synced, repo.synchronize())
        assertEquals(123, dao.events(account.id, "a").single().colorArgb)
        assertEquals(456, dao.events(account.id, "b").single().colorArgb)
        repeat(3) { assertEquals("GET", server.takeRequest().method) }
        val reopened = repository(FakeAuthorizationGateway())
        assertEquals(123, reopened.calendars.first().single { it.id == "a" }.localColorArgb)
        repo.setColor("a", null)
        assertEquals(0xff112233.toInt(), dao.events(account.id, "a").single().colorArgb)
        assertEquals(456, dao.events(account.id, "b").single().colorArgb)
    }

    @Test
    fun disconnectRevokesCalendarAuthorizationClearsOnlyGoogleCacheAndLeavesPcixData() = runBlocking {
        val account = seedConnectedAccount()
        val dao = db.googleDao()
        dao.saveCalendar(GoogleCalendarEntity(account.id, "calendar-a", "A"))
        dao.saveEvent(event(account.id, "calendar-a", "event-a", "Google event"))
        db.dao().insertList(ListEntity(id = INBOX_ID, name = "Inbox"))
        db.dao().insertTask(TaskEntity(id = "pcix-task", title = "Keep me", listId = INBOX_ID))

        val auth = FakeAuthorizationGateway()
        val repository = repository(auth)
        repository.disconnect(null)

        assertEquals(listOf(account.email), auth.revokedEmails)
        assertNull(dao.account())
        assertTrue(dao.calendars(account.id).isEmpty())
        assertNotNull(db.dao().task("pcix-task"))
        assertFalse(repository.connected())
    }

    @Test
    fun backgroundSyncWithoutValidAuthorizationMarksReconnectWithoutInteractiveFlow() = runBlocking {
        seedConnectedAccount()
        val auth = FakeAuthorizationGateway(GoogleAuthorizationResult.Unavailable)
        val repository = repository(auth)

        assertEquals(
            GoogleCalendarRepository.SyncOutcome.NeedsReconnect,
            repository.synchronize(),
        )
        assertTrue(repository.needsReconnect.value)
        assertEquals(listOf(false), auth.interactiveFlags)
    }

    @Test
    fun reconnectBecomesReadyWhenSilentAuthorizationIsAvailableAgain() = runBlocking {
        seedConnectedAccount(reconnect = true)
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        val auth = FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("fresh-token"))
        val repository = repository(auth)

        assertEquals(GoogleCalendarRepository.SyncOutcome.Synced, repository.synchronize())
        assertFalse(repository.needsReconnect.value)
        assertEquals(listOf(false), auth.interactiveFlags)
    }

    @Test
    fun calendarPaginationReconcilesRemovalRenameColorAndPreservesDisabled() = runBlocking {
        val account = seedConnectedAccount()
        val dao = db.googleDao()
        dao.saveCalendar(GoogleCalendarEntity(account.id, "keep", "Old", enabled = false))
        dao.saveCalendar(GoogleCalendarEntity(account.id, "gone", "Gone"))
        dao.saveEvent(event(account.id, "gone", "e", "Removed"))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"keep","summary":"New","backgroundColor":"#112233"}],"nextPageToken":"two"}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"added","summary":"Added"}]}"""))
        server.enqueue(MockResponse().setBody("""{"items":[],"nextSyncToken":"s"}"""))
        assertEquals(GoogleCalendarRepository.SyncOutcome.Synced,
            repository(FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("t"))).synchronize())
        assertEquals(setOf("keep", "added"), dao.calendars(account.id).map { it.id }.toSet())
        val kept = dao.calendars(account.id).single { it.id == "keep" }
        assertFalse(kept.enabled)
        assertEquals("New", kept.summary)
        assertEquals(0xff112233.toInt(), kept.colorArgb)
        assertTrue(dao.events(account.id, "gone").isEmpty())
        assertEquals(3, server.requestCount)
    }

    @Test
    fun failedFullResyncPreservesOfflineCacheAndOldTokenThenRetryReplacesAtomically() = runBlocking {
        val account = seedConnectedAccount()
        val dao = db.googleDao()
        dao.saveCalendar(GoogleCalendarEntity(account.id, "c", "Calendar"))
        dao.saveEvent(event(account.id, "c", "old", "Offline"))
        dao.saveSyncState(GoogleSyncStateEntity(account.id, "c", "expired"))
        context.getSharedPreferences("pcix.google", Context.MODE_PRIVATE).edit()
            .putString("zone:${account.id}:c", ZoneId.systemDefault().id).commit()
        val repo = repository(FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("t")))
        fun calendars() { server.enqueue(MockResponse().setBody("""{"items":[{"id":"c"}]}""")) }
        calendars()
        server.enqueue(MockResponse().setResponseCode(410))
        server.enqueue(MockResponse().setResponseCode(503))
        try { repo.synchronize(); fail("Expected unavailable network") } catch (_: GoogleCalendarApi.HttpError) { }
        assertEquals("Offline", repo.events(0, 3).first().single().title)
        assertEquals("expired", dao.syncState(account.id, "c")!!.syncToken)
        calendars()
        server.enqueue(MockResponse().setResponseCode(410))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"new","start":{"date":"2026-09-10"},"end":{"date":"2026-09-12"}}],"nextPageToken":"p2"}"""))
        server.enqueue(MockResponse().setBody("""{"items":[],"nextSyncToken":"fresh"}"""))
        repo.synchronize()
        assertEquals("new", dao.events(account.id, "c").single().eventId)
        assertEquals("fresh", dao.syncState(account.id, "c")!!.syncToken)
    }

    @Test
    fun disabledCalendarKeepsCacheButHidesEventsAndDoesNotFetchThem() = runBlocking {
        val account = seedConnectedAccount()
        val dao = db.googleDao()
        dao.saveCalendar(GoogleCalendarEntity(account.id, "c", "Calendar", enabled = false))
        dao.saveEvent(event(account.id, "c", "old", "Offline"))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"c"}]}"""))
        val repo = repository(FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("t")))
        repo.synchronize()
        assertTrue(repo.events(0, 3).first().isEmpty())
        assertEquals(1, dao.events(account.id, "c").size)
        assertEquals(1, server.requestCount)
        repo.setEnabled("c", true)
        assertEquals(1, repo.events(0, 3).first().size)
    }

    @Test fun selectedCalendarAccountUsesItsOwnIdentityAndLoadsEventsBeforeReady() = runBlocking {
        val auth = FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("test-token"))
        val repo = repository(auth)
        server.enqueue(MockResponse().setBody("""{"sub":"account-b","email":"b@example.com"}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"c","summary":"B calendar"}]}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"e","summary":"Meeting","start":{"date":"2026-09-26"},"end":{"date":"2026-09-27"}}],"nextSyncToken":"s"}"""))
        assertEquals(GoogleCalendarRepository.AuthOutcome.Ready, repo.authorize(accountEmail = "b@example.com"))
        assertEquals(listOf("b@example.com"), auth.authorizedEmails)
        assertEquals("b@example.com", repo.connection.value.email)
        assertFalse(repo.connection.value.busy)
        assertEquals("Meeting", db.googleDao().events("account-b", "c").single().title)
        assertEquals("b@example.com", repository(auth).connection.value.email)
        assertEquals(3, server.requestCount)
    }

    @Test fun callbackCompletesGrantAndMissingOrCancelledResultsAreVisible() = runBlocking {
        val auth = FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("t"))
        val repo = repository(auth)
        repo.beginSelection()
        assertTrue(repo.connection.value.busy)
        repo.connectionCancelled()
        assertEquals(GoogleCalendarRepository.Issue.Cancelled, repo.connection.value.issue)
        repo.connectionCancelled(missingResult = true)
        assertEquals(GoogleCalendarRepository.Issue.Callback, repo.connection.value.issue)
        server.enqueue(MockResponse().setBody("""{"sub":"b","email":"b@example.com"}"""))
        server.enqueue(MockResponse().setBody("""{"items":[]}"""))
        assertEquals(GoogleCalendarRepository.AuthOutcome.Ready, repo.completeAuthorization(data = android.content.Intent()))
        assertTrue(repo.connected())
    }

    @Test fun failedSwitchKeepsOldAccountAndDoesNotPublishNewConnection() = runBlocking {
        val old = seedConnectedAccount()
        db.googleDao().saveCalendar(GoogleCalendarEntity(old.id, "old", "Offline"))
        db.googleDao().saveEvent(event(old.id, "old", "e", "Keep"))
        val repo = repository(FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("t")))
        server.enqueue(MockResponse().setBody("""{"sub":"b","email":"b@example.com"}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"c"}]}"""))
        server.enqueue(MockResponse().setResponseCode(403).setBody("""{"error":{"errors":[{"reason":"accessNotConfigured"}]}}"""))
        assertEquals(GoogleCalendarRepository.AuthOutcome.Failed, repo.authorize(accountEmail = "b@example.com"))
        assertEquals(GoogleCalendarRepository.Issue.Configuration, repo.connection.value.issue)
        assertEquals(old.email, repo.email)
        assertEquals("Keep", db.googleDao().events(old.id, "old").single().title)
        assertNull(db.googleDao().account("b"))
    }

    @Test fun oauthConfigurationFailureIsActionableAndNeverConnected() {
        val repo = repository(FakeAuthorizationGateway())
        repo.reportFailure(com.google.android.gms.common.api.ApiException(com.google.android.gms.common.api.Status(10)), "test")
        assertEquals(GoogleCalendarRepository.Issue.Configuration, repo.connection.value.issue)
        assertFalse(repo.connected())
        assertFalse(repo.connection.value.busy)
    }

    @Test fun addingSecondAccountPreservesFirstAndScopesColorsVisibilityAndRemoval() = runBlocking {
        val first = seedConnectedAccount()
        val dao = db.googleDao()
        dao.saveCalendar(GoogleCalendarEntity(first.id, "shared", "First"))
        dao.saveEvent(event(first.id, "shared", "same", "First event"))
        val auth = FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("token"))
        val repo = repository(auth)
        server.enqueue(MockResponse().setBody("""{"sub":"second","email":"second@example.com"}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"shared","summary":"Second"}]}"""))
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"same","summary":"Second event","start":{"date":"2026-10-02"},"end":{"date":"2026-10-03"}}],"nextSyncToken":"s"}"""))
        assertEquals(GoogleCalendarRepository.AuthOutcome.Ready, repo.authorize(accountEmail = "second@example.com"))
        assertEquals(2, repo.accounts.first().size)
        assertEquals(2, repo.events(0, 99999).first().size)
        repo.setColor("shared", 123, first.id)
        repo.setColor("shared", 456, "second")
        assertEquals(123, dao.events(first.id, "shared").single().colorArgb)
        assertEquals(456, dao.events("second", "shared").single().colorArgb)
        repo.setEnabled("shared", false, first.id)
        assertEquals("second", repo.events(0, 99999).first().single().accountId)
        repo.disconnectAccount(first.id)
        assertEquals(listOf(first.email), auth.revokedEmails)
        assertEquals("second", repo.accounts.first().single().id)
        assertEquals("Second event", repo.events(0, 99999).first().single().title)
        assertTrue(repo.connected())
        assertEquals(3, server.requestCount)
    }

    @Test fun expiredFirstAccountDoesNotPreventSecondAccountSync() = runBlocking {
        val first = seedConnectedAccount()
        db.googleDao().saveAccount(GoogleCalendarAccountEntity("second", "second@example.com"))
        val emails = mutableListOf<String?>()
        val auth = object : GoogleAuthorizationGateway {
            override suspend fun authorize(accountEmail: String?, interactive: Boolean): GoogleAuthorizationResult {
                assertFalse(interactive)
                emails += accountEmail
                return if (accountEmail == first.email) GoogleAuthorizationResult.Unavailable else GoogleAuthorizationResult.Authorized("token")
            }
            override fun complete(data: android.content.Intent): GoogleAuthorizationResult.Authorized? = null
            override suspend fun revokeCalendarAccess(accountEmail: String) {}
        }
        server.enqueue(MockResponse().setBody("""{"items":[{"id":"c"}]}"""))
        server.enqueue(MockResponse().setBody("""{"items":[],"nextSyncToken":"second-token"}"""))
        val repo = repository(auth)
        assertEquals(GoogleCalendarRepository.SyncOutcome.NeedsReconnect, repo.synchronize())
        assertEquals(listOf(first.email, "second@example.com"), emails)
        assertEquals(setOf(first.id), repo.reconnectAccounts.value)
        assertTrue(repo.needsReconnect.value)
        assertEquals("second-token", db.googleDao().syncState("second", "c")!!.syncToken)
    }

    private suspend fun seedConnectedAccount(reconnect: Boolean = false): GoogleCalendarAccountEntity {
        val account = GoogleCalendarAccountEntity("google-sub-1", "calendar@example.com")
        db.googleDao().saveAccount(account)
        context
            .getSharedPreferences("pcix.google", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("connected", true)
            .putBoolean("reconnect", reconnect)
            .putString("accountId", account.id)
            .putString("accountEmail", account.email)
            .commit()
        return account
    }

    private fun repository(auth: GoogleAuthorizationGateway) =
        GoogleCalendarRepository(
            context = context,
            db = db,
            http = CloudHttp(CloudConfig("", "", "")),
            config = CloudConfig("", "", ""),
            authorization = auth,
            api = GoogleCalendarApi(OkHttpClient(), server.url("/").toString(), server.url("/identity").toString()),
        )

    private fun event(accountId: String, calendarId: String, eventId: String, title: String) =
        GoogleEventEntity(
            accountId = accountId,
            calendarId = calendarId,
            eventId = eventId,
            title = title,
            startDay = 1,
            endDay = 2,
        )
}

private class FakeAuthorizationGateway(
    private var next: GoogleAuthorizationResult = GoogleAuthorizationResult.Unavailable,
) : GoogleAuthorizationGateway {
    val interactiveFlags = mutableListOf<Boolean>()
    val authorizedEmails = mutableListOf<String?>()
    val revokedEmails = mutableListOf<String>()

    override suspend fun authorize(
        accountEmail: String?,
        interactive: Boolean,
    ): GoogleAuthorizationResult {
        interactiveFlags += interactive
        authorizedEmails += accountEmail
        return next
    }

    override fun complete(data: android.content.Intent): GoogleAuthorizationResult.Authorized? = next as? GoogleAuthorizationResult.Authorized

    override suspend fun revokeCalendarAccess(accountEmail: String) {
        revokedEmails += accountEmail
    }
}
