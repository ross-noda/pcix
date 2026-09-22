package com.example.pix.domain

import com.example.pix.data.TaskEntity
import java.time.LocalDate

/** Preferences affect automatic classification, never the task's date or priority. */
data class MatrixConfig(
    val layout: Int = 0,
    val columnSplit: Float = .5f,
    val rowSplit: Float = .5f,
    val cornerRadius: Float = 20f,
    val urgentDays: Int = 0,
    val importantPriority: Int = 3,
)

object MatrixRules {
    fun quadrant(task: TaskEntity, today: LocalDate, config: MatrixConfig): Int {
        val urgent =
            task.matrixUrgent
                ?: (TaskTiming.lastDay(task)?.let {
                    it <= today.plusDays(config.urgentDays.toLong()).toEpochDay()
                } ?: false)
        val important = task.matrixImportant ?: (task.priority >= config.importantPriority)
        return when {
            urgent && important -> 0
            important -> 1
            urgent -> 2
            else -> 3
        }
    }
}
