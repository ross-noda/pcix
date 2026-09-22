package com.example.pix.widget

import android.content.Context
import java.time.LocalDate

class CalendarWidgetConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(appWidgetId: Int): CalendarWidgetConfig {
        val prefix = keyPrefix(appWidgetId)
        val filter =
            runCatching {
                    CalendarWidgetFilterType.valueOf(
                        prefs.getString(prefix + FILTER, CalendarWidgetFilterType.ALL.name)
                            ?: CalendarWidgetFilterType.ALL.name
                    )
                }
                .getOrDefault(CalendarWidgetFilterType.ALL)
        return CalendarWidgetConfig(
            filterType = filter,
            selectedTagIds = prefs.getStringSet(prefix + TAGS, emptySet()).orEmpty().toSet(),
            allTags = prefs.getBoolean(prefix + ALL_TAGS, true),
            selectedListIds = prefs.getStringSet(prefix + LISTS, emptySet()).orEmpty().toSet(),
            allLists = prefs.getBoolean(prefix + ALL_LISTS, true),
            selectedEpochDay = prefs.getLong(prefix + DAY, LocalDate.now().toEpochDay()),
        )
    }

    fun write(appWidgetId: Int, config: CalendarWidgetConfig) {
        val prefix = keyPrefix(appWidgetId)
        prefs.edit()
            .putString(prefix + FILTER, config.filterType.name)
            .putStringSet(prefix + TAGS, config.selectedTagIds)
            .putBoolean(prefix + ALL_TAGS, config.allTags)
            .putStringSet(prefix + LISTS, config.selectedListIds)
            .putBoolean(prefix + ALL_LISTS, config.allLists)
            .putLong(prefix + DAY, config.selectedEpochDay)
            .commit()
    }

    fun delete(appWidgetId: Int) {
        val prefix = keyPrefix(appWidgetId)
        prefs.edit()
            .remove(prefix + FILTER)
            .remove(prefix + TAGS)
            .remove(prefix + ALL_TAGS)
            .remove(prefix + LISTS)
            .remove(prefix + ALL_LISTS)
            .remove(prefix + DAY)
            .commit()
    }

    private fun keyPrefix(id: Int) = "calendar_widget_${id}_"

    private companion object {
        const val PREFS = "calendar_week_widget_config"
        const val FILTER = "filter"
        const val TAGS = "tags"
        const val ALL_TAGS = "all_tags"
        const val LISTS = "lists"
        const val ALL_LISTS = "all_lists"
        const val DAY = "selected_epoch_day"
    }
}
