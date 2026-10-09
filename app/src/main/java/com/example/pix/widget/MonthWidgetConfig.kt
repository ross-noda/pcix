package com.example.pix.widget

import android.content.Context
import com.example.pix.data.TaskWithDetails
import org.json.JSONArray

data class MonthWidgetConfig(
    val showTasks: Boolean = true, val showGoogle: Boolean = true,
    val listIds: Set<String> = emptySet(), val tagIds: Set<String> = emptySet(),
    val priorities: Set<Int> = emptySet(), val calendarIds: Set<String> = emptySet(),
) {
    fun accepts(task: TaskWithDetails) = showTasks &&
        (listIds.isEmpty() || task.task.listId in listIds) &&
        (tagIds.isEmpty() || task.tags.any { it.id in tagIds }) &&
        (priorities.isEmpty() || task.task.priority in priorities)
}
fun monthCalendarKey(account: String, calendar: String): String = JSONArray(listOf(account, calendar)).toString()
class MonthWidgetConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("widget.month.filters", 0)
    fun read(id: Int) = MonthWidgetConfig(
        prefs.getBoolean("$id.tasks", true), prefs.getBoolean("$id.google", true),
        prefs.getStringSet("$id.lists", emptySet())!!.toSet(), prefs.getStringSet("$id.tags", emptySet())!!.toSet(),
        prefs.getStringSet("$id.priorities", emptySet())!!.mapNotNull { it.toIntOrNull() }.toSet(),
        prefs.getStringSet("$id.calendars", emptySet())!!.toSet())
    fun write(id: Int, c: MonthWidgetConfig) { prefs.edit().putBoolean("$id.tasks", c.showTasks).putBoolean("$id.google", c.showGoogle)
        .putStringSet("$id.lists", c.listIds).putStringSet("$id.tags", c.tagIds)
        .putStringSet("$id.priorities", c.priorities.map { it.toString() }.toSet()).putStringSet("$id.calendars", c.calendarIds).commit() }
    fun remove(id: Int) { prefs.edit().apply { prefs.all.keys.filter { it.startsWith("$id.") }.forEach { remove(it) } }.apply() }
}
