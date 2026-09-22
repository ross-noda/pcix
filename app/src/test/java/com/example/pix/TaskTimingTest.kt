package com.example.pix

import com.example.pix.data.TaskEntity
import com.example.pix.domain.TaskTiming
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class TaskTimingTest {
    private val day = LocalDate.of(2028, 2, 28).toEpochDay()

    @Test
    fun midnightEndDoesNotOccupyFollowingDay() {
        val task =
            TaskEntity(title = "Night", dueDay = day, minuteOfDay = 23 * 60, durationMinutes = 60)
        assertEquals(day, TaskTiming.lastDay(task))
        assertEquals(day + 1, TaskTiming.lastDay(task.copy(durationMinutes = 90)))
    }

    @Test
    fun allDayRangeIncludesLeapDay() {
        val task = TaskEntity(title = "Trip", dueDay = day, durationMinutes = 3 * 1440)
        assertEquals(LocalDate.of(2028, 3, 1).toEpochDay(), TaskTiming.lastDay(task))
        assertEquals(LocalDate.of(2028, 3, 2), TaskTiming.end(task)!!.toLocalDate())
    }

    @Test
    fun validationRejectsInvalidRanges() {
        val base = TaskEntity(title = "Time", dueDay = day, minuteOfDay = 600)
        listOf(
                base.copy(durationMinutes = 0),
                base.copy(durationMinutes = -1),
                base.copy(durationMinutes = TaskTiming.MAX_MINUTES + 1),
                base.copy(dueDay = null, durationMinutes = 60),
                base.copy(minuteOfDay = null, durationMinutes = 60),
            )
            .forEach {
                assertThrows(IllegalArgumentException::class.java) { TaskTiming.validate(it) }
            }
    }

    @Test
    fun allDayConversionRoundsUpAndLocalDurationSurvivesClockChanges() {
        val task =
            TaskEntity(
                title = "Time",
                dueDay = LocalDate.of(2026, 3, 29).toEpochDay(),
                minuteOfDay = 60,
                durationMinutes = 180,
            )
        assertEquals(4, TaskTiming.end(task)!!.hour)
        assertEquals(1440, TaskTiming.allDay(task, true).durationMinutes)
        assertNull(TaskTiming.allDay(task, true).minuteOfDay)
    }
}
