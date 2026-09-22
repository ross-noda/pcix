package com.example.pix

import com.example.pix.data.TaskEntity
import com.example.pix.domain.ReminderRules
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class ReminderRulesTest {
    private val rome = ZoneId.of("Europe/Rome")

    private fun task(date: String, minute: Int?) =
        TaskEntity(
            title = "Task",
            dueDay = LocalDate.parse(date).toEpochDay(),
            minuteOfDay = minute,
        )

    @Test
    fun onlyIncompleteTimedTasksHaveReminders() {
        assertNull(ReminderRules.trigger(TaskEntity(title = "Undated"), rome))
        assertNull(ReminderRules.trigger(task("2026-09-17", null), rome))
        assertNull(ReminderRules.trigger(task("2026-09-17", 600).copy(isCompleted = true), rome))
    }

    @Test
    fun springGapMovesForwardAndFallOverlapUsesFirstOffset() {
        assertEquals(
            Instant.parse("2026-03-29T01:30:00Z").toEpochMilli(),
            ReminderRules.trigger(task("2026-03-29", 150), rome),
        )
        assertEquals(
            Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(),
            ReminderRules.trigger(task("2026-10-25", 150), rome),
        )
    }

    @Test
    fun timezoneChangeKeepsLocalAppointmentTime() {
        val t = task("2026-09-17", 600)
        val romeTrigger = ReminderRules.trigger(t, rome)!!
        val utcTrigger = ReminderRules.trigger(t, ZoneOffset.UTC)!!
        assertEquals(7200000, utcTrigger - romeTrigger)
    }

    @Test
    fun snoozeCrossesMidnightAndNeverEarlierThanOneHour() {
        val now = Instant.parse("2026-12-31T23:40:35Z")
        val (day, minute) = ReminderRules.snoozed(now, ZoneOffset.UTC)
        val trigger =
            ReminderRules.trigger(
                TaskEntity(title = "Snooze", dueDay = day, minuteOfDay = minute),
                ZoneOffset.UTC,
            )!!
        assertEquals(LocalDate.of(2027, 1, 1).toEpochDay(), day)
        assertTrue(trigger >= now.plusSeconds(3600).toEpochMilli())
        assertTrue(trigger < now.plusSeconds(3660).toEpochMilli())
    }
}
