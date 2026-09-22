package com.example.pix

import com.example.pix.google.GoogleEventParser
import java.time.ZoneId
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class GoogleEventParserTest {
    private val zone = ZoneId.of("Europe/Rome")

    @Test
    fun parsesAllDayExclusiveEnd() {
        val event =
            GoogleEventParser.parse(
                "cal",
                1,
                JSONObject(
                    """{"id":"a","summary":"Holiday","start":{"date":"2026-09-22"},"end":{"date":"2026-09-24"},"status":"confirmed"}"""
                ),
                zone,
            )!!
        assertTrue(event.allDay)
        assertEquals(java.time.LocalDate.parse("2026-09-22").toEpochDay(), event.startDay)
        assertEquals(java.time.LocalDate.parse("2026-09-24").toEpochDay(), event.endDay)
        assertFalse(event.cancelled)
    }

    @Test
    fun parsesTimedSameDay() {
        val event =
            GoogleEventParser.parse(
                "cal",
                1,
                JSONObject(
                    """{"id":"b","summary":"Lesson","start":{"dateTime":"2026-09-22T10:00:00+02:00"},"end":{"dateTime":"2026-09-22T11:30:00+02:00"},"status":"confirmed"}"""
                ),
                zone,
            )!!
        assertFalse(event.allDay)
        assertEquals(10 * 60, event.startMinute)
        assertEquals(11 * 60 + 30, event.endMinute)
        assertEquals(event.startDay + 1, event.endDay)
    }

    @Test
    fun parsesMultiDayAndCancelled() {
        val overnight =
            GoogleEventParser.parse(
                "cal",
                1,
                JSONObject(
                    """{"id":"c","summary":"Night","start":{"dateTime":"2026-09-22T22:00:00+02:00"},"end":{"dateTime":"2026-09-23T02:00:00+02:00"}}"""
                ),
                zone,
            )!!
        assertEquals(2, overnight.endDay - overnight.startDay)
        val cancelled =
            GoogleEventParser.parse(
                "cal",
                1,
                JSONObject("""{"id":"d","status":"cancelled","start":{"date":"2026-09-22"},"end":{"date":"2026-09-23"}}"""),
                zone,
            )!!
        assertTrue(cancelled.cancelled)
    }
}
