package com.example.pix.domain

import java.time.LocalDate
import java.time.YearMonth

object CalendarRules {
    /** Monday-first grid, complete weeks, no timezone or millisecond arithmetic. */
    fun days(month: YearMonth): List<LocalDate> {
        val first = month.atDay(1)
        val start = first.minusDays((first.dayOfWeek.value - 1).toLong())
        val count = ((first.dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7) * 7
        return List(count) { start.plusDays(it.toLong()) }
    }

    fun week(date: LocalDate): List<LocalDate> {
        val monday = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return List(7) { monday.plusDays(it.toLong()) }
    }

    fun moveSelection(date: LocalDate, months: Long): LocalDate = date.plusMonths(months)
}
