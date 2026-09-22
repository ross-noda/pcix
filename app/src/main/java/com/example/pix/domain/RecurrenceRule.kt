package com.example.pix.domain

import java.time.*
import java.time.temporal.ChronoUnit

enum class Frequency {
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY,
}

data class RecurrenceRule(
    val frequency: Frequency,
    val interval: Int = 1,
    val weekdays: Set<Int> = emptySet(),
) {
    init {
        require(interval in 1..99)
        require(weekdays.all { it in 1..7 })
    }

    fun encode(): String =
        "FREQ=${frequency.name};INTERVAL=$interval" +
            if (weekdays.isEmpty()) ""
            else ";BYDAY=${weekdays.sorted().joinToString(",") { codes[it-1] }}"

    companion object {
        private val codes = listOf("MO", "TU", "WE", "TH", "FR", "SA", "SU")

        fun parse(value: String): RecurrenceRule {
            val parts =
                value.split(';').associate {
                    val pair = it.split('=', limit = 2)
                    require(pair.size == 2)
                    pair[0] to pair[1]
                }
            val days =
                parts["BYDAY"]
                    ?.split(',')
                    ?.map {
                        require(it in codes)
                        codes.indexOf(it) + 1
                    }
                    ?.toSet()
                    .orEmpty()
            return RecurrenceRule(
                Frequency.valueOf(requireNotNull(parts["FREQ"])),
                parts["INTERVAL"]?.toInt() ?: 1,
                days,
            )
        }
    }
}

object RecurrenceEngine {
    fun next(anchor: LocalDate, after: LocalDate, rule: RecurrenceRule): LocalDate {
        require(!after.isBefore(anchor))
        return when (rule.frequency) {
            Frequency.DAILY ->
                anchor.plusDays(
                    (ChronoUnit.DAYS.between(anchor, after) / rule.interval + 1) * rule.interval
                )
            Frequency.WEEKLY -> {
                val start = anchor.minusDays((anchor.dayOfWeek.value - 1).toLong())
                val days = rule.weekdays.ifEmpty { setOf(anchor.dayOfWeek.value) }
                var candidate = after.plusDays(1)
                while (
                    ChronoUnit.WEEKS.between(start, candidate) % rule.interval != 0L ||
                        candidate.dayOfWeek.value !in days
                ) candidate = candidate.plusDays(1)
                candidate
            }
            Frequency.MONTHLY -> {
                var index =
                    ChronoUnit.MONTHS.between(YearMonth.from(anchor), YearMonth.from(after)) /
                        rule.interval
                var candidate = anchor.plusMonths(index * rule.interval)
                while (!candidate.isAfter(after)) {
                    index++
                    candidate = anchor.plusMonths(index * rule.interval)
                }
                candidate
            }
            Frequency.YEARLY -> {
                var index = (after.year - anchor.year) / rule.interval
                var candidate = anchor.plusYears(index.toLong() * rule.interval)
                while (!candidate.isAfter(after)) {
                    index++
                    candidate = anchor.plusYears(index.toLong() * rule.interval)
                }
                candidate
            }
        }
    }
}
