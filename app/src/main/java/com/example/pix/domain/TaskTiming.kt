package com.example.pix.domain

import com.example.pix.data.TaskEntity
import java.time.LocalDate
import java.time.LocalDateTime

/** Calendar-local duration: end is exclusive; all-day end dates are displayed inclusively. */
object TaskTiming {
    const val MAX_MINUTES = 365 * 1440

    fun validate(task: TaskEntity) {
        task.durationMinutes?.let {
            require(task.dueDay != null && it in 1..MAX_MINUTES)
            require(task.minuteOfDay != null || it % 1440 == 0)
        }
    }

    fun start(task: TaskEntity): LocalDateTime? =
        task.dueDay?.let {
            LocalDate.ofEpochDay(it).atStartOfDay().plusMinutes((task.minuteOfDay ?: 0).toLong())
        }

    fun end(task: TaskEntity): LocalDateTime? =
        task.durationMinutes?.let { start(task)?.plusMinutes(it.toLong()) }

    fun lastDay(task: TaskEntity): Long? =
        end(task)?.minusMinutes(1)?.toLocalDate()?.toEpochDay() ?: task.dueDay

    fun allDay(task: TaskEntity, enabled: Boolean): TaskEntity =
        task.copy(
            minuteOfDay = if (enabled) null else 540,
            durationMinutes =
                if (enabled) task.durationMinutes?.let { ((it + 1439) / 1440) * 1440 }
                else task.durationMinutes,
        )
}
