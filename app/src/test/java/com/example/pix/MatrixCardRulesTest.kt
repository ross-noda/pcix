package com.example.pix

import com.example.pix.data.*
import com.example.pix.domain.*
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class MatrixCardRulesTest {
    private val today = LocalDate.of(2026, 12, 31)
    private fun detail(task: TaskEntity = TaskEntity(title = "Task", listId = "a"), tags: List<String> = emptyList()) =
        TaskWithDetails(task, null, ListEntity(id = task.listId, name = "List"), tags.map { TagEntity(id = it, name = it, normalizedName = it) }, emptyList())
    private fun config(card: MatrixCard) = MatrixConfig(cards = List(4) { card })
    @Test fun sharedAppAndWidgetProjectionSortsAndRemovesCompletionFromEveryCard() {
        val c = config(MatrixCard(custom = true)).copy(hideChildren = false)
        val early = TaskEntity(id = "early", title = "Early", dueDay = today.toEpochDay(), priority = 1)
        val high = early.copy(id = "high", title = "High", priority = 5)
        val child = TaskEntity(id = "child", title = "Child", parentTaskId = "parent")
        val rows = listOf(detail(child), detail(early), detail(high))
        val groups = MatrixRules.groups(rows, today, c)
        repeat(4) { assertEquals(listOf("high", "early", "child"), groups[it]!!.map { it.task.id }) }
        val completed = rows.map { if (it.task.id == "high") it.copy(task = it.task.copy(isCompleted = true)) else it }
        val after = MatrixRules.groups(completed, today, c.copy(hideChildren = true))
        repeat(4) { assertEquals(listOf("early"), after[it]!!.map { it.task.id }) }
    }

    @Test fun customFiltersReplaceQuadrantAndCanOverlap() {
        val task = detail(TaskEntity(title = "High", priority = 5))
        assertFalse(MatrixRules.matches(task, 3, today, MatrixConfig()))
        val custom = config(MatrixCard(custom = true))
        repeat(4) { assertTrue(MatrixRules.matches(task, it, today, custom)) }
        assertTrue(MatrixRules.matches(detail(), 0, today, custom)) // undated included by All
    }
    @Test fun categoriesAreAndSelectionsAreOr() {
        val card = MatrixCard(custom = true, listIds = setOf("a", "b"), tagIds = setOf("x", "y"), priorities = setOf(1, 5))
        val task = TaskEntity(title = "Task", listId = "b", priority = 5)
        assertTrue(MatrixRules.matches(detail(task, listOf("y")), 0, today, config(card)))
        assertFalse(MatrixRules.matches(detail(task.copy(listId = "c"), listOf("y")), 0, today, config(card)))
        assertFalse(MatrixRules.matches(detail(task), 0, today, config(card)))
        assertFalse(MatrixRules.matches(detail(task.copy(priority = 3), listOf("y")), 0, today, config(card)))
    }
    @Test fun weeksAreMondayFirstAndMonthsCrossYearsAndLeapDays() {
        fun range(date: MatrixDate, now: LocalDate = today) = MatrixRules.dateRange(MatrixCard(date = date), now)!!
        assertEquals(LocalDate.of(2026,12,28).toEpochDay()..LocalDate.of(2027,1,3).toEpochDay(), range(MatrixDate.THIS_WEEK))
        assertEquals(LocalDate.of(2027,1,4).toEpochDay()..LocalDate.of(2027,1,10).toEpochDay(), range(MatrixDate.NEXT_WEEK))
        assertEquals(LocalDate.of(2027,1,1).toEpochDay()..LocalDate.of(2027,1,31).toEpochDay(), range(MatrixDate.NEXT_MONTH))
        assertEquals(LocalDate.of(2028,2,29).toEpochDay(), range(MatrixDate.THIS_MONTH, LocalDate.of(2028,2,10)).last)
        assertEquals(today.toEpochDay()..today.toEpochDay(), range(MatrixDate.TODAY))
        assertEquals(today.plusDays(1).toEpochDay()..today.plusDays(1).toEpochDay(), range(MatrixDate.TOMORROW))
    }
    @Test fun durationOverlapMatchesInclusiveDatesButNotExclusiveEnd() {
        val task = detail(TaskEntity(title = "Spanning", dueDay = today.minusDays(1).toEpochDay(), durationMinutes = 2 * 1440))
        assertTrue(MatrixRules.matches(task, 0, today, config(MatrixCard(custom = true, date = MatrixDate.TODAY))))
        assertFalse(MatrixRules.matches(task, 0, today, config(MatrixCard(custom = true, date = MatrixDate.TOMORROW))))
        assertFalse(MatrixRules.matches(detail(), 0, today, config(MatrixCard(custom = true, date = MatrixDate.TODAY))))
    }
    @Test fun invalidRangesDoNotSilentlyShowAllTasks() {
        val card = MatrixCard(custom = true, date = MatrixDate.RANGE, fromDay = 9, toDay = 3)
        assertNull(MatrixRules.dateRange(card, today))
        assertNull(MatrixRules.dateRange(card.copy(toDay = null), today))
        assertFalse(MatrixRules.matches(detail(), 0, today, config(card)))
        assertEquals(3L..9L, MatrixRules.dateRange(card.copy(fromDay = 3, toDay = 9), today))
    }
    @Test fun orderingNormalizesDuplicatesAndDoesNotChangeCardIdentity() {
        val c = config(MatrixCard(title = "Work", custom = true)).copy(cardOrder = listOf(3, 3, 7, 0))
        assertEquals(listOf(3, 0, 1, 2), MatrixRules.orderedIds(c))
        assertEquals("Work", MatrixRules.card(c, 3).title)
    }
    @Test fun hiddenChildrenAndNonActionableTasksStayExcludedWithCustomFilters() {
        val c = config(MatrixCard(custom = true)).copy(hideChildren = true)
        val task = TaskEntity(title = "Task")
        listOf(task.copy(parentTaskId = "parent"), task.copy(isCompleted = true), task.copy(isTemplate = true), task.copy(isSkipped = true)).forEach {
            assertFalse(MatrixRules.matches(detail(it), 0, today, c))
        }
        assertTrue(MatrixRules.matches(detail(task.copy(parentTaskId = "parent")), 0, today, c.copy(hideChildren = false)))
    }
}
