package com.example.pix.domain

import com.example.pix.data.*
import java.time.LocalDate

object HabitRules {
    fun validate(rule: HabitRuleEntity) {
        require(rule.startDay in -719162L..2932896L && rule.effectiveDay in -719162L..2932896L)
        require(rule.endDay == null || rule.endDay in rule.startDay..2932896L)
        require(rule.target in 1..100000 && rule.step in 1..100000)
        require(rule.quantity || (rule.target == 1 && rule.step == 1))
        require(rule.weekdays in 1..127 && rule.intervalDays in 1..3650)
    }
    fun at(rules: List<HabitRuleEntity>, day: Long) = rules.filter { it.effectiveDay <= day }.maxByOrNull { it.effectiveDay }
    fun scheduled(rule: HabitRuleEntity, day: Long): Boolean = rule.enabled && day >= rule.startDay &&
        (rule.endDay == null || day <= rule.endDay) && (day - rule.startDay) % rule.intervalDays == 0L &&
        rule.weekdays and (1 shl (LocalDate.ofEpochDay(day).dayOfWeek.value - 1)) != 0
    fun complete(rule: HabitRuleEntity, log: HabitLogEntity?) = log != null && !log.skipped && when (log.sourceStatus) {
        null -> log.count >= rule.target
        "Completed" -> true
        else -> false
    }
    fun completedCheck(rule: HabitRuleEntity?, log: HabitLogEntity?): Boolean =
        rule != null && !rule.quantity && complete(rule, log)

    /** Stable partition: counters remain among active rows, even above their target. */
    fun homeOrder(habits: List<HabitEntity>, rules: Map<String, HabitRuleEntity?>, logs: Map<String, HabitLogEntity>): List<HabitEntity> =
        habits.sortedBy { completedCheck(rules[it.id], logs[it.id]) }

    fun manualOrder(habits: List<HabitEntity>, source: String, target: String): List<HabitEntity> {
        val ordered = habits.sortedWith(compareBy({ it.sortOrder }, { it.createdAt }, { it.id })).toMutableList()
        val from = ordered.indexOfFirst { it.id == source && it.active }
        val to = ordered.indexOfFirst { it.id == target && it.active }
        if (from < 0 || to < 0 || from == to) return ordered
        ordered.add(to, ordered.removeAt(from))
        return ordered.mapIndexed { index, habit -> habit.copy(sortOrder = index.toLong()) }
    }

    fun changedCount(rule: HabitRuleEntity, count: Int, delta: Int) =
        if (rule.quantity) (count.toLong() + delta).coerceIn(0, 1000000).toInt() else if (count > 0) 0 else 1
    data class Series(val start: Long, val end: Long, val length: Int)
    data class Stats(val completed: Int, val failed: Int, val skipped: Int, val total: Long,
        val current: Int, val best: Int, val consistency: Int, val series: List<Series>)
    /** Skips break a streak and remain in the consistency denominator. Today is not failed yet. */
    fun stats(rules: List<HabitRuleEntity>, logs: List<HabitLogEntity>, today: Long, from: Long = minOf(rules.minOfOrNull { it.startDay } ?: today, logs.minOfOrNull { it.day } ?: today)): Stats {
        val indexed = logs.associateBy { it.day }
        var done = 0; var failed = 0; var skipped = 0; var expected = 0; var streak = 0
        var first = 0L; var last = 0L
        val series = mutableListOf<Series>()
        fun finish() { if (streak > 0) series.add(Series(first, last, streak)); streak = 0 }
        for (day in from..today) {
            val rule = at(rules, day)
            val log = indexed[day]
            // Imported states are authoritative; blank states and missing imported days are unknown.
            if (log?.sourceStatus == "") { finish(); continue }
            val imported = log?.sourceStatus != null
            if (!imported && (rule == null || !scheduled(rule, day))) {
                // Without a known schedule only consecutive recorded calendar days form a series.
                if (rule == null || !rule.enabled) finish()
                continue
            }
            expected++
            if (log?.sourceStatus == "Completed" || (rule != null && complete(rule, log))) { done++; if (streak == 0) first = day; last = day; streak++ }
            else if (log?.skipped == true) { skipped++; finish() }
            else if (log?.sourceStatus == "Failed" || day < today) { failed++; finish() }
        }
        val current = streak
        finish()
        return Stats(done, failed, skipped, logs.filter { it.day in from..today && !it.skipped }.sumOf { it.count.toLong() },
            current, series.maxOfOrNull { it.length } ?: 0, if (expected == 0) 0 else done * 100 / expected, series.sortedByDescending { it.length }.take(6))
    }
}
