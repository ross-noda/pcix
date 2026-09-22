package com.example.pix

import com.example.pix.data.TaskEntity
import com.example.pix.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MatrixRulesTest {
    private val today = LocalDate.of(2026, 12, 31)
    private val config = MatrixConfig()

    @Test
    fun fourQuadrantsUseIndependentDimensions() {
        for (urgent in listOf(false, true)) for (important in listOf(false, true)) {
            val task =
                TaskEntity(
                    title = "Test",
                    dueDay = if (urgent) today.toEpochDay() else null,
                    priority = if (important) 3 else 0,
                )
            val expected =
                if (important) {
                    if (urgent) 0 else 1
                } else {
                    if (urgent) 2 else 3
                }
            assertEquals(expected, MatrixRules.quadrant(task, today, config))
        }
    }

    @Test
    fun overdueAndInclusiveHorizonRespectLocalDatesAcrossYear() {
        for (offset in listOf(-100, 0, 1, 7)) assertEquals(
            2,
            MatrixRules.quadrant(
                TaskEntity(title = "Task", dueDay = today.plusDays(offset.toLong()).toEpochDay()),
                today,
                config.copy(urgentDays = 7),
            ),
        )
        assertEquals(
            3,
            MatrixRules.quadrant(
                TaskEntity(title = "Task", dueDay = today.plusDays(8).toEpochDay()),
                today,
                config.copy(urgentDays = 7),
            ),
        )
    }

    @Test
    fun overridesDoNotNeedDateOrPriorityAndCanDisableBoth() {
        assertEquals(
            0,
            MatrixRules.quadrant(
                TaskEntity(title = "Task", matrixUrgent = true, matrixImportant = true),
                today,
                config,
            ),
        )
        assertEquals(
            3,
            MatrixRules.quadrant(
                TaskEntity(
                    title = "Task",
                    dueDay = today.toEpochDay(),
                    priority = 5,
                    matrixUrgent = false,
                    matrixImportant = false,
                ),
                today,
                config,
            ),
        )
    }

    @Test
    fun minimumPriorityAndMidnightReclassifyAutomatically() {
        val task = TaskEntity(title = "Task", dueDay = today.plusDays(1).toEpochDay(), priority = 3)
        assertEquals(1, MatrixRules.quadrant(task, today, config))
        assertEquals(0, MatrixRules.quadrant(task, today.plusDays(1), config))
        assertEquals(
            2,
            MatrixRules.quadrant(task, today.plusDays(1), config.copy(importantPriority = 5)),
        )
    }

    @Test
    fun weekIsMondayFirstAndCrossesYearAndLeapDay() {
        val week = CalendarRules.week(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), week.first())
        assertEquals(LocalDate.of(2027, 1, 3), week.last())
        assertEquals(7, week.toSet().size)
        assertTrue(CalendarRules.week(LocalDate.of(2028, 3, 1)).contains(LocalDate.of(2028, 2, 29)))
    }
}
