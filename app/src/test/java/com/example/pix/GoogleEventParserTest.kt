package com.example.pix

import com.example.pix.google.GoogleEventParser
import java.time.LocalDate
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GoogleEventParserTest {
    private val zone = ZoneId.of("Europe/Rome")

    @Test
    fun parsesAllDayExclusiveEnd() {
        val event = parse(
            JSONObject(
                """{"id":"a","summary":"Holiday","start":{"date":"2026-09-22"},"end":{"date":"2026-09-24"},"status":"confirmed"}"""
            )
        )!!
        assertEquals("account", event.accountId)
        assertEquals("cal", event.calendarId)
        assertEquals("a", event.eventId)
        assertTrue(event.allDay)
        assertEquals(LocalDate.parse("2026-09-22").toEpochDay(), event.startDay)
        assertEquals(LocalDate.parse("2026-09-24").toEpochDay(), event.endDay)
        assertFalse(event.cancelled)
    }

    @Test
    fun parsesTimedSameDay() {
        val event = parse(
            JSONObject(
                """{"id":"b","summary":"Lesson","start":{"dateTime":"2026-09-22T10:00:00+02:00"},"end":{"dateTime":"2026-09-22T11:30:00+02:00"},"status":"confirmed"}"""
            )
        )!!
        assertFalse(event.allDay)
        assertEquals(10 * 60, event.startMinute)
        assertEquals(11 * 60 + 30, event.endMinute)
        assertEquals(event.startDay + 1, event.endDay)
    }

    @Test
    fun parsesMultiDayAndCancelled() {
        val overnight = parse(
            JSONObject(
                """{"id":"c","summary":"Night","start":{"dateTime":"2026-09-22T22:00:00+02:00"},"end":{"dateTime":"2026-09-23T02:00:00+02:00"}}"""
            )
        )!!
        assertEquals(2, overnight.endDay - overnight.startDay)
        val cancelled = parse(
            JSONObject(
                """{"id":"d","status":"cancelled","start":{"date":"2026-09-22"},"end":{"date":"2026-09-23"}}"""
            )
        )!!
        assertTrue(cancelled.cancelled)
    }

    @Test
    fun recurringInstanceKeepsImmutableOriginalStartIdentity() {
        val moved = parse(
            JSONObject(
                """{
                  "id":"series_20260922T080000Z",
                  "recurringEventId":"series",
                  "originalStartTime":{"dateTime":"2026-09-22T10:00:00+02:00"},
                  "start":{"dateTime":"2026-09-22T15:00:00+02:00"},
                  "end":{"dateTime":"2026-09-22T16:00:00+02:00"}
                }"""
            )
        )!!
        assertEquals("series", moved.recurringEventId)
        assertEquals(LocalDate.parse("2026-09-22").toEpochDay(), moved.originalStartDay)
        assertEquals(10 * 60, moved.originalStartMinute)
        assertEquals(15 * 60, moved.startMinute)
    }

    private fun parse(json: JSONObject) =
        GoogleEventParser.parse("account", "cal", 1, json, zone)
}
