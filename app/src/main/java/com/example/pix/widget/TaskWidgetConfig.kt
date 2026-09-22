package com.example.pix.widget

enum class WidgetFilterType {
    ALL,
    TODAY,
    TOMORROW,
    NEXT_7_DAYS,
    LIST,
    TAG,
}

enum class WidgetGroupBy {
    LIST,
    DATE,
    TAG,
    CREATED_AT,
    PRIORITY,
    NONE,
}

data class TaskWidgetConfig(
    val filterType: WidgetFilterType = WidgetFilterType.ALL,
    val filterEntityId: String? = null,
    val groupBy: WidgetGroupBy = WidgetGroupBy.LIST,
)

enum class WidgetConfigMode {
    FULL,
    FILTER,
    GROUPING,
}
