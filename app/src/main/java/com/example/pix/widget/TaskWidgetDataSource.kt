package com.example.pix.widget

import android.content.Context
import com.example.pix.PixApplication
import com.example.pix.R
import com.example.pix.data.ListEntity
import com.example.pix.data.TagEntity
import com.example.pix.data.TaskFilter
import com.example.pix.data.TaskWithDetails
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

class TaskWidgetDataSource(private val context: Context) {
    private val app = context.applicationContext as PixApplication
    private val repository = app.repository

    suspend fun load(appWidgetId: Int): WidgetContent {
        val store = TaskWidgetConfigStore(context)
        val stored = store.read(appWidgetId)
        val lists = repository.lists.first().map { it.list }
        val tags = repository.tags.first().map { it.tag }
        val config = validate(stored, lists, tags)
        if (config != stored) store.write(appWidgetId, config)

        val now = ZonedDateTime.now()
        val filter = config.toTaskFilter()
        val tasks = repository.observe(filter, now).first()
        val neutralColor =
            context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
                .getInt("accent", NEUTRAL)
        val groups = group(tasks, config.groupBy, lists, tags, now, neutralColor)
        return WidgetContent(
            config = config,
            filterLabel = filterLabel(config, lists, tags),
            groups = groups,
            emptyLabel = emptyLabel(config.filterType),
        )
    }

    private fun validate(
        config: TaskWidgetConfig,
        lists: List<ListEntity>,
        tags: List<TagEntity>,
    ): TaskWidgetConfig =
        when (config.filterType) {
            WidgetFilterType.LIST ->
                if (lists.any { it.id == config.filterEntityId }) config
                else config.copy(filterType = WidgetFilterType.ALL, filterEntityId = null)
            WidgetFilterType.TAG ->
                if (tags.any { it.id == config.filterEntityId }) config
                else config.copy(filterType = WidgetFilterType.ALL, filterEntityId = null)
            else -> config.copy(filterEntityId = null)
        }

    private fun TaskWidgetConfig.toTaskFilter(): TaskFilter =
        when (filterType) {
            WidgetFilterType.ALL -> TaskFilter(mode = "ALL")
            WidgetFilterType.TODAY -> TaskFilter(mode = "TODAY")
            WidgetFilterType.TOMORROW -> TaskFilter(mode = "TOMORROW")
            WidgetFilterType.NEXT_7_DAYS -> TaskFilter(mode = "WEEK")
            WidgetFilterType.LIST -> TaskFilter(mode = "ALL", listId = filterEntityId)
            WidgetFilterType.TAG -> TaskFilter(mode = "ALL", tagId = filterEntityId)
        }

    private fun filterLabel(
        config: TaskWidgetConfig,
        lists: List<ListEntity>,
        tags: List<TagEntity>,
    ): String =
        when (config.filterType) {
            WidgetFilterType.ALL -> context.getString(R.string.all)
            WidgetFilterType.TODAY -> context.getString(R.string.today)
            WidgetFilterType.TOMORROW -> context.getString(R.string.tomorrow)
            WidgetFilterType.NEXT_7_DAYS -> context.getString(R.string.week)
            WidgetFilterType.LIST ->
                lists.firstOrNull { it.id == config.filterEntityId }?.name
                    ?: context.getString(R.string.all)
            WidgetFilterType.TAG ->
                tags.firstOrNull { it.id == config.filterEntityId }?.let { "#${it.name}" }
                    ?: context.getString(R.string.all)
        }

    private fun emptyLabel(filter: WidgetFilterType): String =
        when (filter) {
            WidgetFilterType.TODAY -> context.getString(R.string.widget_empty_today)
            WidgetFilterType.TOMORROW -> context.getString(R.string.widget_empty_tomorrow)
            WidgetFilterType.NEXT_7_DAYS -> context.getString(R.string.widget_empty_week)
            else -> context.getString(R.string.widget_empty_generic)
        }

    private fun group(
        tasks: List<TaskWithDetails>,
        groupBy: WidgetGroupBy,
        lists: List<ListEntity>,
        tags: List<TagEntity>,
        now: ZonedDateTime,
        neutralColor: Int,
    ): List<WidgetGroup> =
        when (groupBy) {
            WidgetGroupBy.LIST -> groupByList(tasks, lists, now)
            WidgetGroupBy.DATE -> groupByDate(tasks, now, neutralColor)
            WidgetGroupBy.TAG -> groupByTag(tasks, tags, neutralColor, now)
            WidgetGroupBy.CREATED_AT -> groupByCreatedAt(tasks, now, neutralColor)
            WidgetGroupBy.PRIORITY -> groupByPriority(tasks, now)
            WidgetGroupBy.NONE ->
                if (tasks.isEmpty()) emptyList()
                else
                    listOf(
                        WidgetGroup(
                            id = "none",
                            title = "",
                            colorArgb = neutralColor,
                            tasks = tasks.map { it.toWidgetTask(now) },
                        )
                    )
        }

    private fun groupByList(
        tasks: List<TaskWithDetails>,
        lists: List<ListEntity>,
        now: ZonedDateTime,
    ): List<WidgetGroup> {
        val byId = tasks.groupBy { it.list.id }
        return lists.mapNotNull { list ->
            val rows = byId[list.id].orEmpty()
            if (rows.isEmpty()) null
            else
                WidgetGroup(
                    id = "list:${list.id}",
                    title = "${list.icon} ${list.name}",
                    colorArgb = palette(list.color),
                    tasks = rows.map { it.toWidgetTask(now) },
                )
        }
    }

    private fun groupByDate(tasks: List<TaskWithDetails>, now: ZonedDateTime, neutralColor: Int): List<WidgetGroup> {
        val today = now.toLocalDate()
        val dated = tasks.filter { it.task.dueDay != null }.groupBy { it.task.dueDay!! }.toSortedMap()
        val groups =
            dated.map { (day, rows) ->
                WidgetGroup(
                    id = "date:$day",
                    title = dateGroupLabel(LocalDate.ofEpochDay(day), today),
                    colorArgb = neutralColor,
                    tasks = rows.map { it.toWidgetTask(now) },
                )
            }.toMutableList()
        tasks.filter { it.task.dueDay == null }.takeIf { it.isNotEmpty() }?.let { rows ->
            groups +=
                WidgetGroup(
                    id = "date:none",
                    title = context.getString(R.string.widget_without_date),
                    colorArgb = neutralColor,
                    tasks = rows.map { it.toWidgetTask(now) },
                )
        }
        return groups
    }

    private fun groupByTag(
        tasks: List<TaskWithDetails>,
        tags: List<TagEntity>,
        neutralColor: Int,
        now: ZonedDateTime,
    ): List<WidgetGroup> {
        val result = mutableListOf<WidgetGroup>()
        tags.forEach { tag ->
            val rows = tasks.filter { detail -> detail.tags.any { it.id == tag.id } }
            if (rows.isNotEmpty()) {
                result +=
                    WidgetGroup(
                        id = "tag:${tag.id}",
                        title = "#${tag.name}",
                        colorArgb = palette(tag.color),
                        tasks = rows.map { it.toWidgetTask(now) },
                    )
            }
        }
        tasks.filter { it.tags.isEmpty() }.takeIf { it.isNotEmpty() }?.let { rows ->
            result +=
                WidgetGroup(
                    id = "tag:none",
                    title = context.getString(R.string.widget_without_tag),
                    colorArgb = neutralColor,
                    tasks = rows.map { it.toWidgetTask(now) },
                )
        }
        return result
    }

    private fun groupByPriority(tasks: List<TaskWithDetails>, now: ZonedDateTime): List<WidgetGroup> {
        val labels =
            listOf(
                5 to R.string.priority_high,
                3 to R.string.priority_medium,
                1 to R.string.priority_low,
                0 to R.string.priority_none,
            )
        return labels.mapNotNull { (priority, label) ->
            val rows = tasks.filter { it.task.priority == priority }
            if (rows.isEmpty()) null
            else
                WidgetGroup(
                    id = "priority:$priority",
                    title = context.getString(label),
                    colorArgb = priorityColor(priority),
                    tasks = rows.map { it.toWidgetTask(now) },
                )
        }
    }

    private fun groupByCreatedAt(tasks: List<TaskWithDetails>, now: ZonedDateTime, neutralColor: Int): List<WidgetGroup> {
        val today = now.toLocalDate()
        val buckets = linkedMapOf<String, MutableList<TaskWithDetails>>()
        tasks.forEach { detail ->
            val created =
                Instant.ofEpochMilli(detail.task.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
            val key =
                when {
                    created == today -> "today"
                    created == today.minusDays(1) -> "yesterday"
                    !created.isBefore(today.minusDays(6)) -> "week"
                    created.year == today.year && created.month == today.month -> "month"
                    else -> "older"
                }
            buckets.getOrPut(key) { mutableListOf() } += detail
        }
        val labels =
            listOf(
                "today" to R.string.today,
                "yesterday" to R.string.widget_yesterday,
                "week" to R.string.widget_created_last_7_days,
                "month" to R.string.widget_created_this_month,
                "older" to R.string.widget_created_older,
            )
        return labels.mapNotNull { (key, label) ->
            val rows = buckets[key].orEmpty()
            if (rows.isEmpty()) null
            else
                WidgetGroup(
                    id = "created:$key",
                    title = context.getString(label),
                    colorArgb = neutralColor,
                    tasks = rows.map { it.toWidgetTask(now) },
                )
        }
    }

    private fun TaskWithDetails.toWidgetTask(now: ZonedDateTime): WidgetTask =
        WidgetTask(
            id = task.id,
            title = task.title,
            dueLabel = task.dueDay?.let(::formatDueDate).orEmpty(),
            overdue = isOverdue(this, now),
            priority = task.priority,
        )

    private fun formatDueDate(epochDay: Long): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val now = LocalDate.now()
        val locale = currentLocale()
        val pattern = if (date.year == now.year) "d MMM" else "d MMM yyyy"
        val raw = date.format(DateTimeFormatter.ofPattern(pattern, locale))
        return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    private fun dateGroupLabel(date: LocalDate, today: LocalDate): String =
        when (date) {
            today -> context.getString(R.string.today)
            today.plusDays(1) -> context.getString(R.string.tomorrow)
            else -> date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(currentLocale()))
        }

    private fun isOverdue(detail: TaskWithDetails, now: ZonedDateTime): Boolean {
        val day = detail.task.dueDay ?: return false
        val currentDay = now.toLocalDate().toEpochDay()
        val minute = detail.task.minuteOfDay
        return if (minute == null) {
            val occupiedDays = maxOf(1, (detail.task.durationMinutes ?: 1440) / 1440)
            day + occupiedDays - 1 < currentDay
        } else {
            val endMinute = day * 1440 + minute + (detail.task.durationMinutes ?: 0)
            endMinute < currentDay * 1440 + now.hour * 60 + now.minute
        }
    }

    private fun currentLocale(): Locale =
        context.resources.configuration.locales.get(0) ?: Locale.getDefault()

    companion object {
        const val NEUTRAL: Int = 0xFF5275FF.toInt()
        private val PALETTE =
            intArrayOf(
                0xFFE8A93A.toInt(),
                0xFFE15B4F.toInt(),
                0xFF6E9B7B.toInt(),
                0xFF5B8BB0.toInt(),
                0xFF8A6BA8.toInt(),
                0xFFC4A335.toInt(),
                0xFF5FAE9E.toInt(),
                0xFFC77B92.toInt(),
                0xFF7C93A0.toInt(),
                0xFFB0794F.toInt(),
                0xFF7A828A.toInt(),
                0xFF8FAE4A.toInt(),
            )

        fun palette(index: Int): Int = PALETTE[index.mod(PALETTE.size)]

        fun priorityColor(priority: Int): Int =
            when (priority) {
                5 -> PALETTE[1]
                3 -> PALETTE[0]
                1 -> 0xFF5275FF.toInt()
                else -> 0xFF98989F.toInt()
            }
    }
}
