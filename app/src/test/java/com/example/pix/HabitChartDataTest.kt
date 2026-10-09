package com.example.pix

import com.example.pix.data.*
import com.example.pix.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class HabitChartDataTest {
    private val today = LocalDate.of(2026, 10, 4)
    private fun log(date: String, count: Int = 1, status: String? = null) = HabitLogEntity(date,"h",LocalDate.parse(date).toEpochDay(),count,sourceStatus=status)
    @Test fun calendarWeeksExcludeFutureAndSkippedAndIncludeZeroPeriods() {
        val logs = listOf(log("2026-09-28",2),log("2026-10-04",3),log("2026-10-05",99), log("2026-10-03",5).copy(skipped=true))
        val bars = HabitChartData.bars(logs,today,HabitChartData.Period.WEEK)
        assertEquals(LocalDate.of(2026,9,28),bars.last().start)
        assertEquals(5L,bars.last().total)
        assertEquals(1.0,HabitChartData.mean(bars),0.001)
        assertEquals(today,bars.last().end)
    }
    @Test fun monthAndYearBoundariesAndLeapDays() {
        val date = LocalDate.of(2024,3,1)
        val logs = listOf(log("2024-02-29",4),log("2024-03-01",2))
        val months = HabitChartData.bars(logs,date,HabitChartData.Period.MONTH)
        assertEquals(4L,months[3].total); assertEquals(2L,months[4].total)
        assertEquals(LocalDate.of(2024,2,29),months[3].end)
        val years = HabitChartData.bars(logs,date,HabitChartData.Period.YEAR)
        assertEquals(6L,years.last().total)
        for (max in listOf(0.0,1.0,7.0,250.0,999999999.0)) assertTrue(HabitChartData.tickStep(max)*4 >= max)
    }
    @Test fun importedConsecutiveCompletionsCountButUnknownGapsDoNotJoin() {
        val logs = listOf(log("2026-09-25",status="Completed"),log("2026-09-26",status="Completed"),
            log("2026-09-28",status="Completed"),log("2026-09-29",status="Completed"),log("2026-09-30",status="Completed"))
        val rule = HabitRuleEntity(habitId="h",effectiveDay=LocalDate.of(2026,9,1).toEpochDay(),enabled=false)
        for (rules in listOf(emptyList(),listOf(rule))) {
            val stats = HabitRules.stats(rules,logs,today.toEpochDay())
            assertEquals(3,stats.best); assertEquals(0,stats.current)
            assertEquals(listOf(3,2),stats.series.map { it.length })
        }
    }
}
