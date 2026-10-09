package com.example.pix.domain

import com.example.pix.data.HabitLogEntity
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.time.DayOfWeek
import kotlin.math.*

object HabitChartData {
    enum class Period { DAY, WEEK, MONTH, YEAR }
    data class Bar(val start: LocalDate, val end: LocalDate, val total: Long)
    fun bars(logs: List<HabitLogEntity>, today: LocalDate, period: Period, count: Int = 5): List<Bar> {
        fun anchor(date: LocalDate) = when (period) {
            Period.DAY -> date
            Period.WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            Period.MONTH -> date.withDayOfMonth(1)
            Period.YEAR -> date.withDayOfYear(1)
        }
        fun shift(date: LocalDate, n: Long) = when (period) {
            Period.DAY -> date.plusDays(n)
            Period.WEEK -> date.plusWeeks(n)
            Period.MONTH -> date.plusMonths(n)
            Period.YEAR -> date.plusYears(n)
        }
        val current = anchor(today)
        val grouped = logs.filter { !it.skipped && it.day <= today.toEpochDay() }
            .groupBy { anchor(LocalDate.ofEpochDay(it.day)) }
        return (count - 1 downTo 0).map { offset ->
            val start = shift(current, -offset.toLong())
            Bar(start, minOf(today, shift(start, 1).minusDays(1)), grouped[start].orEmpty().sumOf { it.count.toLong() })
        }
    }
    fun mean(bars: List<Bar>) = if (bars.isEmpty()) 0.0 else bars.sumOf { it.total }.toDouble() / bars.size
    fun tickStep(max: Double): Double {
        val raw = max.coerceAtLeast(4.0) / 4
        val power = 10.0.pow(floor(log10(raw)))
        return listOf(1.0, 2.0, 5.0, 10.0).first { it * power >= raw } * power
    }
}
