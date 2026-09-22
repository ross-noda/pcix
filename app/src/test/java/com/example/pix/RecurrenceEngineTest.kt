package com.example.pix

import com.example.pix.data.TaskEntity
import com.example.pix.domain.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class RecurrenceEngineTest {
    private fun next(anchor: String, after: String, rule: RecurrenceRule) =
        RecurrenceEngine.next(LocalDate.parse(anchor), LocalDate.parse(after), rule)

    @Test
    fun monthlyClampsWithoutLosingOriginalDay() {
        val r = RecurrenceRule(Frequency.MONTHLY)
        assertEquals(LocalDate.parse("2026-02-28"), next("2026-01-31", "2026-01-31", r))
        assertEquals(LocalDate.parse("2026-03-31"), next("2026-01-31", "2026-02-28", r))
        assertEquals(LocalDate.parse("2028-02-29"), next("2028-01-31", "2028-01-31", r))
    }

    @Test
    fun yearlyLeapDayReturnsAfterLeapYearCycle() {
        val r = RecurrenceRule(Frequency.YEARLY)
        assertEquals(LocalDate.parse("2025-02-28"), next("2024-02-29", "2024-02-29", r))
        assertEquals(LocalDate.parse("2028-02-29"), next("2024-02-29", "2027-02-28", r))
    }

    @Test
    fun weekdaysSkipWeekendsAndCrossYear() {
        val r = RecurrenceRule(Frequency.WEEKLY, weekdays = setOf(1, 2, 3, 4, 5))
        assertEquals(LocalDate.parse("2027-01-04"), next("2026-12-28", "2027-01-01", r))
    }

    @Test
    fun multiDayEveryOtherWeekHonorsMondayAnchor() {
        val r = RecurrenceRule(Frequency.WEEKLY, 2, setOf(1, 3, 5))
        assertEquals(LocalDate.parse("2026-09-16"), next("2026-09-14", "2026-09-14", r))
        assertEquals(LocalDate.parse("2026-09-28"), next("2026-09-14", "2026-09-18", r))
    }

    @Test
    fun customMonthlyAndDailyIntervals() {
        assertEquals(
            LocalDate.parse("2027-01-31"),
            next("2026-10-31", "2026-10-31", RecurrenceRule(Frequency.MONTHLY, 3)),
        )
        assertEquals(
            LocalDate.parse("2027-01-02"),
            next("2026-12-30", "2026-12-30", RecurrenceRule(Frequency.DAILY, 3)),
        )
    }

    @Test
    fun encodedRuleRoundTrips() {
        for (f in Frequency.entries) for (i in listOf(1, 2, 99)) {
            val r = RecurrenceRule(f, i, if (f == Frequency.WEEKLY) setOf(1, 3, 7) else emptySet())
            assertEquals(r, RecurrenceRule.parse(r.encode()))
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun zeroIntervalRejected() {
        RecurrenceRule(Frequency.DAILY, 0)
    }

    @Test
    fun dailyLocalTimeStaysAcrossDstAndTimezone() {
        val next = next("2026-03-28", "2026-03-28", RecurrenceRule(Frequency.DAILY))
        val t = TaskEntity(title = "DST", dueDay = next.toEpochDay(), minuteOfDay = 150)
        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(),
            ReminderRules.trigger(t, ZoneId.of("Europe/Rome")),
        )
        assertEquals(
            Instant.parse("2026-03-29T02:30:00Z").toEpochMilli(),
            ReminderRules.trigger(t, ZoneOffset.UTC),
        )
    }

    @Test
    fun thousandsOfOccurrencesAlwaysAdvanceAndStayAnchored() {
        for (f in Frequency.entries) {
            val anchor = LocalDate.of(2024, 1, 31)
            var after = anchor
            val rule = RecurrenceRule(f, 2)
            repeat(1000) {
                val candidate = RecurrenceEngine.next(anchor, after, rule)
                assertTrue(candidate.isAfter(after))
                after = candidate
            }
        }
    }
}
