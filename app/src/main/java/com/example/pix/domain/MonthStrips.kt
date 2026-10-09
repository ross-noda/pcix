package com.example.pix.domain

/** Inclusive dates, independent from Android rendering. */
data class MonthStrip(val key: String, val title: String, val start: Long, val end: Long,
    val color: Int, val google: Boolean = false, val taskId: String? = null)
data class WeekStrip(val entry: MonthStrip, val column: Int, val span: Int)

object MonthStrips {
    /** Each item occupies one contiguous lane within a week, split only at week/month boundaries. */
    fun lanes(entries: List<MonthStrip>, weekStart: Long, monthStart: Long, monthEnd: Long): List<List<WeekStrip>> {
        val left = maxOf(weekStart, monthStart)
        val right = minOf(weekStart + 6, monthEnd)
        if (left > right) return emptyList()
        val segments = entries.filter { it.start <= right && it.end >= left && it.end >= it.start }
            .map { WeekStrip(it, (maxOf(it.start, left) - weekStart).toInt(), (minOf(it.end, right) - maxOf(it.start, left) + 1).toInt()) }
            .sortedWith(compareByDescending<WeekStrip> { it.span }.thenBy { it.column }.thenBy { it.entry.google }.thenBy { it.entry.key })
        val lanes = mutableListOf<MutableList<WeekStrip>>()
        for (segment in segments) {
            val lane = lanes.firstOrNull { row -> row.none { it.column < segment.column + segment.span && segment.column < it.column + it.span } }
                ?: mutableListOf<WeekStrip>().also { lanes += it }
            lane += segment
        }
        return lanes.map { it.sortedBy { segment -> segment.column } }
    }
    fun hiddenCounts(lanes: List<List<WeekStrip>>, visibleLanes: Int): List<Int> = (0..6).map { day ->
        lanes.drop(visibleLanes).sumOf { row -> row.count { day in it.column until it.column + it.span } }
    }
}
