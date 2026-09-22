package com.example.pix.widget

import android.content.Context
import com.example.pix.PixApplication
import com.example.pix.data.INBOX_ID
import com.example.pix.data.TaskFilter
import com.example.pix.data.TaskWithDetails
import com.example.pix.domain.CalendarRules
import com.example.pix.domain.TaskTiming
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.flow.first

class CalendarWidgetDataSource(private val context: Context) {
    companion object {
        private const val MAX_DAY_MARKERS = 3

        private data class CachedWeek(
            val weekStartEpochDay: Long,
            val rows: List<TaskWithDetails>,
        )

        private val weekCache = ConcurrentHashMap<Int, CachedWeek>()

        fun invalidateAll() {
            weekCache.clear()
        }

        fun invalidate(appWidgetId: Int) {
            weekCache.remove(appWidgetId)
        }
    }

    private val repository = (context.applicationContext as PixApplication).repository

    /**
     * Loads exactly one Room snapshot for the visible week. This method intentionally completes
     * before Glance composition starts, so CalendarWeekWidget.compose() can produce a complete
     * RemoteViews in a single pass for user interactions.
     */
    suspend fun load(appWidgetId: Int): CalendarWidgetContent {
        val config = CalendarWidgetConfigStore(context).read(appWidgetId)
        val selectedDate = LocalDate.ofEpochDay(config.selectedEpochDay)
        val weekStart = CalendarRules.week(selectedDate).first()
        val cached = weekCache[appWidgetId]
        val weekRows =
            if (cached?.weekStartEpochDay == weekStart.toEpochDay()) {
                cached.rows
            } else {
                repository
                    .observe(
                        TaskFilter(mode = "WEEK", showCompleted = true),
                        weekStart.atStartOfDay(ZoneId.systemDefault()),
                    )
                    .first()
                    .also { rows ->
                        weekCache[appWidgetId] = CachedWeek(weekStart.toEpochDay(), rows)
                    }
            }
        return content(config, weekRows)
    }

    /** Pure rendering transform from the already loaded weekly Room snapshot. */
    fun content(
        config: CalendarWidgetConfig,
        weekRows: List<TaskWithDetails>,
    ): CalendarWidgetContent {
        val selectedDate = LocalDate.ofEpochDay(config.selectedEpochDay)
        val today = LocalDate.now()
        val locale = currentLocale()
        val week = CalendarRules.week(selectedDate)
        val filteredWeek = filter(weekRows, config)
        val selectedRows =
            filteredWeek
                .filter { it.occupies(config.selectedEpochDay) }
                .sortedWith(dayComparator)

        return CalendarWidgetContent(
            config = config,
            monthLabel = monthLabel(week.first(), week.last(), locale),
            days =
                week.map { day ->
                    val epochDay = day.toEpochDay()
                    CalendarWidgetDay(
                        epochDay = epochDay,
                        weekdayLabel =
                            day.dayOfWeek.getDisplayName(TextStyle.SHORT, locale).capitalized(locale),
                        dayNumber = day.dayOfMonth,
                        selected = day == selectedDate,
                        today = day == today,
                        markerColorsArgb =
                            filteredWeek
                                .asSequence()
                                .filter { !it.task.isCompleted && it.occupies(epochDay) }
                                .take(MAX_DAY_MARKERS)
                                .map { TaskWidgetDataSource.palette(it.list.color) }
                                .toList(),
                    )
                },
            tasks = selectedRows.map { it.toWidgetTask(config.selectedEpochDay, locale) },
        )
    }

    private fun filter(
        rows: List<TaskWithDetails>,
        config: CalendarWidgetConfig,
    ): List<TaskWithDetails> =
        when (config.filterType) {
            CalendarWidgetFilterType.ALL -> rows
            CalendarWidgetFilterType.INBOX -> rows.filter { it.list.id == INBOX_ID }
            CalendarWidgetFilterType.TAG ->
                if (config.allTags) rows.filter { it.tags.isNotEmpty() }
                else rows.filter { detail -> detail.tags.any { it.id in config.selectedTagIds } }
            CalendarWidgetFilterType.LISTS ->
                if (config.allLists) rows
                else rows.filter { it.list.id in config.selectedListIds }
        }

    private fun TaskWithDetails.occupies(day: Long): Boolean {
        val start = task.dueDay ?: return false
        val end = TaskTiming.lastDay(task) ?: start
        return day in start..end
    }

    private val dayComparator =
        compareBy<TaskWithDetails> { if (it.task.isCompleted) 1 else 0 }
            .thenBy { if (it.task.minuteOfDay == null) 1 else 0 }
            .thenBy { it.task.minuteOfDay ?: Int.MAX_VALUE }
            .thenByDescending { it.task.priority }
            .thenBy { it.task.createdAt }
            .thenBy { it.task.id }

    private fun TaskWithDetails.toWidgetTask(selectedDay: Long, locale: Locale): CalendarWidgetTask =
        CalendarWidgetTask(
            id = task.id,
            title = task.title,
            timeLabel =
                if (task.dueDay == selectedDay && task.minuteOfDay != null) {
                    LocalTime.of(task.minuteOfDay / 60, task.minuteOfDay % 60)
                        .format(DateTimeFormatter.ofPattern("HH:mm", locale))
                } else {
                    ""
                },
            priority = task.priority,
            listColorArgb = TaskWidgetDataSource.palette(list.color),
            isCompleted = task.isCompleted,
        )

    private fun monthLabel(first: LocalDate, last: LocalDate, locale: Locale): String {
        val formatter = DateTimeFormatter.ofPattern("LLLL", locale)
        val firstMonth = first.format(formatter).capitalized(locale)
        val lastMonth = last.format(formatter).capitalized(locale)
        return if (first.year == last.year && first.month == last.month) firstMonth
        else if (firstMonth == lastMonth) firstMonth
        else "$firstMonth · $lastMonth"
    }

    private fun currentLocale(): Locale =
        context.resources.configuration.locales.get(0) ?: Locale.getDefault()

    private fun String.capitalized(locale: Locale): String =
        replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
}
