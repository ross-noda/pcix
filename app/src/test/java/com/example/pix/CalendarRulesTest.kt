package com.example.pix

import com.example.pix.domain.CalendarRules
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class CalendarRulesTest {
    @Test
    fun gridsCoverLeapFebruaryAndSixWeekMonths() {
        for (year in 2024..2030) for (month in 1..12) {
            val ym = YearMonth.of(year, month)
            val days = CalendarRules.days(ym)
            assertEquals(DayOfWeek.MONDAY, days.first().dayOfWeek)
            assertEquals(DayOfWeek.SUNDAY, days.last().dayOfWeek)
            assertEquals(ym.lengthOfMonth(), days.count { YearMonth.from(it) == ym })
            assertEquals(days.size, days.distinct().size)
            assertTrue(days.size in listOf(28, 35, 42))
        }
        assertTrue(CalendarRules.days(YearMonth.of(2028, 2)).contains(LocalDate.of(2028, 2, 29)))
    }

    @Test
    fun monthNavigationClampsAtEndAndCrossesYears() {
        assertEquals(
            LocalDate.of(2028, 2, 29),
            CalendarRules.moveSelection(LocalDate.of(2028, 1, 31), 1),
        )
        assertEquals(
            LocalDate.of(2027, 1, 17),
            CalendarRules.moveSelection(LocalDate.of(2026, 12, 17), 1),
        )
    }
}
