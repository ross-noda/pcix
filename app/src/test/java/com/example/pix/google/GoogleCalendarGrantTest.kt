package com.example.pix.google

import org.junit.Assert.*
import org.junit.Test

class GoogleCalendarGrantTest {
    private val scopes = listOf("https://www.googleapis.com/auth/calendar.calendarlist.readonly",
        "https://www.googleapis.com/auth/calendar.events.readonly")
    @Test fun tokenWithoutBothCalendarPermissionsCannotConnect() {
        assertThrows(CalendarPermissionException::class.java) { validateCalendarGrant("token", scopes.take(1)) }
        assertThrows(CalendarPermissionException::class.java) { validateCalendarGrant("token", emptyList()) }
        assertThrows(CalendarPermissionException::class.java) { validateCalendarGrant(null, scopes) }
        assertThrows(CalendarPermissionException::class.java) { validateCalendarGrant(" ", scopes) }
    }
    @Test fun fullGrantAcceptedAndQuotaRemainsRetryable() {
        assertEquals("token", validateCalendarGrant("token", scopes).accessToken)
        assertTrue(GoogleCalendarApi.HttpError(403, "rateLimitExceeded").retryable)
        assertFalse(GoogleCalendarApi.HttpError(403, "accessNotConfigured").retryable)
        assertFalse(GoogleCalendarApi.HttpError(403, "accessNotConfigured").requiresAuthorization)
    }
}
