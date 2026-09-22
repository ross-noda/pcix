package com.example.pix

import com.example.pix.domain.TaskRules
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class TaskRulesTest {
    @Test
    fun unicodeTagsShareIdentity() {
        assertEquals(
            TaskRules.normalizedTag("  UNIVERSITÀ "),
            TaskRules.normalizedTag("università"),
        )
        assertEquals(TaskRules.normalizedTag("CAFÉ"), TaskRules.normalizedTag("cafe\u0301"))
    }

    @Test
    fun searchEscapesWildcards() {
        assertEquals("%100\\%\\_\\\\%", TaskRules.searchPattern("100%_\\"))
        assertEquals("", TaskRules.searchPattern("  "))
    }

    @Test
    fun titleBounds() {
        assertFalse(TaskRules.validTitle("   "))
        assertTrue(TaskRules.validTitle("x".repeat(200)))
        assertFalse(TaskRules.validTitle("x".repeat(201)))
    }

    @Test
    fun allDayIsNotOverdueUntilFollowingDay() {
        val now = ZonedDateTime.of(2026, 9, 17, 23, 59, 0, 0, ZoneId.of("Europe/Rome"))
        val day = now.toLocalDate().toEpochDay()
        assertFalse(TaskRules.isOverdue(day, null, now))
        assertTrue(TaskRules.isOverdue(day, null, now.plusMinutes(1)))
        assertFalse(TaskRules.isOverdue(null, null, now))
    }

    @Test
    fun timeDeadlineUsesLocalMinute() {
        val now = ZonedDateTime.parse("2026-09-17T10:30:00+02:00[Europe/Rome]")
        assertTrue(TaskRules.isOverdue(now.toLocalDate().toEpochDay(), 629, now))
        assertFalse(TaskRules.isOverdue(now.toLocalDate().toEpochDay(), 630, now))
    }

    @Test
    fun sevenDaysAcrossLeapDayAndYear() {
        for (today in listOf(LocalDate.of(2028, 2, 26), LocalDate.of(2026, 12, 29))) {
            assertTrue(TaskRules.inNextSevenDays(today.plusDays(6).toEpochDay(), today))
            assertFalse(TaskRules.inNextSevenDays(today.plusDays(7).toEpochDay(), today))
            assertFalse(TaskRules.inNextSevenDays(today.minusDays(1).toEpochDay(), today))
        }
    }

    @Test
    fun daylightSavingDoesNotShortenCalendarDay() {
        val now = ZonedDateTime.of(2026, 3, 29, 23, 30, 0, 0, ZoneId.of("Europe/Rome"))
        assertFalse(TaskRules.isOverdue(now.toLocalDate().toEpochDay(), null, now))
        assertTrue(TaskRules.isOverdue(now.toLocalDate().toEpochDay(), null, now.plusHours(1)))
    }
}
