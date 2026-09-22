package com.example.pix.domain

import com.example.pix.data.TaskEntity
import java.time.*

object ReminderRules {
    fun trigger(task: TaskEntity, zone: ZoneId): Long? {
        if (task.isCompleted || task.isTemplate || task.isSkipped) return null
        val day = task.dueDay ?: return null
        val minute = task.minuteOfDay ?: return null
        return LocalDate.ofEpochDay(day)
            .atTime(minute / 60, minute % 60)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
    }

    fun snoozed(now: Instant, zone: ZoneId): Pair<Long, Int> {
        val next =
            now.plusSeconds(3659).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).atZone(zone)
        return next.toLocalDate().toEpochDay() to (next.hour * 60 + next.minute)
    }
}
