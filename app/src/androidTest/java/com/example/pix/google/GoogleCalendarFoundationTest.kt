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
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GoogleCalendarFoundationTest {
    private lateinit var context: Context
    private lateinit var db: PixDatabase

    @Before
    fun setUp() {
        context = InstrumentationRegistry.getInstrumentation().targetContext
        context.getSharedPreferences("pcix.google", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, PixDatabase::class.java).build()
    }

    @After
    fun tearDown() {
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
        val auth = FakeAuthorizationGateway(GoogleAuthorizationResult.Authorized("fresh-token"))
        val repository = repository(auth)

        assertEquals(GoogleCalendarRepository.SyncOutcome.Synced, repository.synchronize())
        assertFalse(repository.needsReconnect.value)
        assertEquals(listOf(false), auth.interactiveFlags)
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
    val revokedEmails = mutableListOf<String>()

    override suspend fun authorize(
        accountEmail: String?,
        interactive: Boolean,
    ): GoogleAuthorizationResult {
        interactiveFlags += interactive
        return next
    }

    override fun complete(data: android.content.Intent): GoogleAuthorizationResult.Authorized? = null

    override suspend fun revokeCalendarAccess(accountEmail: String) {
        revokedEmails += accountEmail
    }
}
