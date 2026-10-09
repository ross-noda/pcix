package com.example.pix

import com.example.pix.domain.*
import com.example.pix.data.*
import com.example.pix.widget.MonthWidgetConfig
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MonthStripsTest {
    private fun entry(id: String, start: Long, end: Long, google: Boolean = false) = MonthStrip(id, id, start, end, 123, google)
    @Test fun spanningTaskIsSingleStripPerWeekAndClippedAtMonthEdges() {
        val task = entry("task", 98, 111)
        val first = MonthStrips.lanes(listOf(task), 98, 100, 110).flatten().single()
        assertEquals(2, first.column); assertEquals(5, first.span)
        val next = MonthStrips.lanes(listOf(task), 105, 100, 110).flatten().single()
        assertEquals(0, next.column); assertEquals(6, next.span)
        assertTrue(MonthStrips.lanes(listOf(task), 112, 100, 110).isEmpty())
    }
    @Test fun overlapsGetSeparateLanesAndNonOverlappingItemsShareOne() {
        val a = entry("long", 100, 102)
        val b = entry("overlap", 101, 103, true)
        val c = entry("later", 104, 104)
        val lanes = MonthStrips.lanes(listOf(c, b, a), 100, 100, 106)
        assertEquals(2, lanes.size)
        assertEquals(listOf("long", "later"), lanes[0].map { it.entry.key })
        assertEquals(listOf(0,1,1,1,0,0,0), MonthStrips.hiddenCounts(lanes, 1))
        assertTrue(lanes[1].single().entry.google)
    }
    @Test fun randomizedPackingNeverDuplicatesLosesOrOverlapsSegments() {
        val random = Random(12)
        repeat(80) {
            val entries = List(25) { index ->
                val start = random.nextLong(88, 114)
                entry(index.toString(), start, start + random.nextInt(1, 12))
            }
            val lanes = MonthStrips.lanes(entries, 100, 102, 105)
            val packed = lanes.flatten()
            assertEquals(entries.filter { it.start <= 105 && it.end >= 102 }.map { it.key }.toSet(), packed.map { it.entry.key }.toSet())
            assertEquals(packed.size, packed.map { it.entry.key }.toSet().size)
            for (lane in lanes) {
                lane.zipWithNext().forEach { (a,b) -> assertTrue(a.column + a.span <= b.column) }
                lane.forEach { segment ->
                    assertEquals(maxOf(segment.entry.start, 102), 100L + segment.column)
                    assertEquals(minOf(segment.entry.end, 105), 100L + segment.column + segment.span - 1)
                }
            }
        }
    }
    @Test fun widgetTaskFiltersUseAnyWithinCategoriesAndAllAcrossCategories() {
        val detail = TaskWithDetails(TaskEntity(title = "Task", listId = "b", priority = 5), null,
            ListEntity(id = "b", name = "List"), listOf(TagEntity(id = "tag", name = "Tag", normalizedName = "tag")), emptyList())
        val config = MonthWidgetConfig(listIds = setOf("a", "b"), tagIds = setOf("tag", "other"), priorities = setOf(3,5))
        assertTrue(config.accepts(detail))
        assertFalse(config.copy(showTasks = false).accepts(detail))
        assertFalse(config.copy(listIds = setOf("missing")).accepts(detail))
        assertFalse(config.copy(tagIds = setOf("missing")).accepts(detail))
        assertFalse(config.copy(priorities = setOf(0)).accepts(detail))
        assertTrue(MonthWidgetConfig().accepts(detail))
    }
}
