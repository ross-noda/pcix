package com.example.pix

import com.example.pix.data.*
import com.example.pix.domain.HabitRules
import com.example.pix.cloud.HabitCodec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class HabitRulesTest {
    private val monday = LocalDate.of(2026, 9, 28).toEpochDay()
    private fun rule() = HabitRuleEntity(habitId = "h", effectiveDay = monday)
    private fun log(day: Long, count: Int = 1, skipped: Boolean = false) = HabitLogEntity("$day", "h", day, count, skipped)
    @Test fun scheduleHonorsWeekdaysBoundsAndInterval() {
        val daily = rule()
        assertTrue((monday..monday+6).all { HabitRules.scheduled(daily, it) })
        assertFalse(HabitRules.scheduled(daily, monday-1))
        val selected = daily.copy(weekdays = 1 or 4 or 16, endDay = monday+4)
        assertTrue(HabitRules.scheduled(selected, monday+2))
        assertFalse(HabitRules.scheduled(selected, monday+1))
        assertFalse(HabitRules.scheduled(selected, monday+7))
        val interval = daily.copy(intervalDays = 2)
        assertTrue(HabitRules.scheduled(interval, monday+4))
        assertFalse(HabitRules.scheduled(interval, monday+3))
        assertTrue(HabitRules.scheduled(daily.copy(intervalDays = 14), monday+14))
    }
    @Test fun archivedPeriodsAreNotMissedDays() {
        val original = rule()
        val pause = original.copy(id = "pause", effectiveDay = monday+1, enabled = false)
        val resume = original.copy(id = "resume", effectiveDay = monday+4)
        val stats = HabitRules.stats(listOf(original,pause,resume), listOf(log(monday),log(monday+4)), monday+4)
        assertEquals(0, stats.failed); assertEquals(100, stats.consistency)
    }
    @Test fun booleanDoubleTapIsUndo() {
        val r = rule()
        val done = HabitRules.changedCount(r, 0, 1)
        assertTrue(HabitRules.complete(r, log(monday, done)))
        val undo = HabitRules.changedCount(r, done, 1)
        assertEquals(0, undo)
        assertFalse(HabitRules.complete(r, log(monday, undo)))
    }
    @Test fun quantityTargetDecrementAndLowerBound() {
        val r = rule().copy(quantity = true, target = 3)
        var count = 0
        repeat(3) { count = HabitRules.changedCount(r, count, 1); assertEquals(it+1, count); assertEquals(count == 3, HabitRules.complete(r, log(monday, count))) }
        assertEquals(2, HabitRules.changedCount(r, count, -1))
        assertEquals(0, HabitRules.changedCount(r, 0, -1))
        assertEquals(1000000, HabitRules.changedCount(r, 1000000, Int.MAX_VALUE))
    }
    @Test fun unscheduledDaysDoNotBreakSeries() {
        val r = rule().copy(weekdays = 1 or 4 or 16)
        val stats = HabitRules.stats(listOf(r), listOf(log(monday),log(monday+2),log(monday+4)), monday+4)
        assertEquals(3, stats.current); assertEquals(3, stats.best); assertEquals(100, stats.consistency)
    }
    @Test fun skipsBreakSeriesAndFutureNeverCounts() {
        val r = rule().copy(weekdays = 1 or 4 or 16)
        val stats = HabitRules.stats(listOf(r), listOf(log(monday),log(monday+2, skipped = true),log(monday+4),log(monday+7)), monday+4)
        assertEquals(1, stats.current); assertEquals(1, stats.skipped); assertEquals(66, stats.consistency); assertEquals(2L, stats.total)
    }
    @Test fun revisionsPreserveOldScheduleAndTarget() {
        val old = rule().copy(weekdays = 1 or 4 or 16)
        val new = old.copy(id = "new", effectiveDay = monday+7, quantity = true, target = 5, weekdays = 127)
        assertEquals(old, HabitRules.at(listOf(old,new), monday+2))
        assertEquals(new, HabitRules.at(listOf(old,new), monday+8))
    }
    @Test fun missingTodayDoesNotBreakCurrentStreakOrCountAsFailed() {
        val stats = HabitRules.stats(listOf(rule()), listOf(log(monday)), monday+1)
        assertEquals(1, stats.current); assertEquals(0, stats.failed); assertEquals(50, stats.consistency)
    }
    @Test fun completedChecksMoveLastWhileCountersKeepTheirOrderAndUndoRestoresPosition() {
        val habits = listOf("done1", "counter", "pending", "done2").map { HabitEntity(id = it, name = it) }
        val rules = habits.associate { it.id to rule().copy(habitId = it.id, quantity = it.id == "counter") }
        val logs = listOf("done1", "counter", "done2").associateWith { log(monday).copy(habitId = it) }
        assertEquals(listOf("counter", "pending", "done1", "done2"), HabitRules.homeOrder(habits, rules, logs).map { it.id })
        assertFalse(HabitRules.completedCheck(rules["counter"], logs["counter"]))
        assertTrue(HabitRules.completedCheck(rules["done1"], logs["done1"]))
        assertEquals(listOf("done1", "counter", "pending", "done2"), HabitRules.homeOrder(habits, rules, logs - "done1").map { it.id })
        assertEquals(habits, HabitRules.homeOrder(habits, rules, emptyMap()))
    }
    @Test fun manualOrderHandlesBothDirectionsTiesAndStaleIds() {
        val original = listOf("a", "b", "c").map { HabitEntity(id = it, name = it, sortOrder = 0, createdAt = 1) }
        val down = HabitRules.manualOrder(original.reversed(), "a", "c")
        assertEquals(listOf("b", "c", "a"), down.map { it.id })
        assertEquals(listOf(0L, 1L, 2L), down.map { it.sortOrder })
        assertEquals(listOf("a", "b", "c"), HabitRules.manualOrder(down, "a", "b").map { it.id })
        assertEquals(down, HabitRules.manualOrder(down, "deleted", "a"))
        val archived = original.map { it.copy(active = false) }
        assertEquals(archived, HabitRules.manualOrder(archived, "a", "c"))
    }
    @Test fun codecPreservesEveryField() {
        val h = HabitEntity(name = "Water", groupId = "g", reminderMinute = 480, notes = "notes")
        assertEquals(h, HabitCodec.parseHabit(HabitCodec.habit(h)))
        val r = rule().copy(quantity = true, target = 8, endDay = monday+90)
        assertEquals(r, HabitCodec.parseHabitRule(HabitCodec.habitRule(r)))
        val l = log(monday,4)
        assertEquals(l, HabitCodec.parseHabitLog(HabitCodec.habitLog(l)))
        val g = HabitGroupEntity(name = "Health")
        assertEquals(g, HabitCodec.parseHabitGroup(HabitCodec.habitGroup(g)))
    }
}
