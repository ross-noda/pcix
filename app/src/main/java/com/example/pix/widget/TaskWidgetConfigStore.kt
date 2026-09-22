package com.example.pix.widget

import android.content.Context

class TaskWidgetConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun read(appWidgetId: Int): TaskWidgetConfig {
        val prefix = keyPrefix(appWidgetId)
        val filter =
            runCatching {
                    WidgetFilterType.valueOf(
                        prefs.getString(prefix + FILTER, WidgetFilterType.ALL.name)
                            ?: WidgetFilterType.ALL.name
                    )
                }
                .getOrDefault(WidgetFilterType.ALL)
        val group =
            runCatching {
                    WidgetGroupBy.valueOf(
                        prefs.getString(prefix + GROUP, WidgetGroupBy.LIST.name)
                            ?: WidgetGroupBy.LIST.name
                    )
                }
                .getOrDefault(WidgetGroupBy.LIST)
        return TaskWidgetConfig(
            filterType = filter,
            filterEntityId = prefs.getString(prefix + ENTITY, null),
            groupBy = group,
        )
    }

    fun write(appWidgetId: Int, config: TaskWidgetConfig) {
        val prefix = keyPrefix(appWidgetId)
        prefs.edit()
            .putString(prefix + FILTER, config.filterType.name)
            .putString(prefix + GROUP, config.groupBy.name)
            .apply {
                if (config.filterEntityId == null) remove(prefix + ENTITY)
                else putString(prefix + ENTITY, config.filterEntityId)
            }
            .apply()
    }

    fun delete(appWidgetId: Int) {
        val prefix = keyPrefix(appWidgetId)
        prefs.edit()
            .remove(prefix + FILTER)
            .remove(prefix + ENTITY)
            .remove(prefix + GROUP)
            .apply()
    }

    private fun keyPrefix(id: Int) = "widget_${id}_"

    private companion object {
        const val PREFS = "task_widget_config"
        const val FILTER = "filter"
        const val ENTITY = "entity"
        const val GROUP = "group"
    }
}
