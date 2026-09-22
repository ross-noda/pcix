package com.example.pix.widget

import java.time.LocalDate

enum class CalendarWidgetFilterType {
    ALL,
    TAG,
    INBOX,
    LISTS,
}

data class CalendarWidgetConfig(
    val filterType: CalendarWidgetFilterType = CalendarWidgetFilterType.ALL,
    val selectedTagIds: Set<String> = emptySet(),
    val allTags: Boolean = true,
    val selectedListIds: Set<String> = emptySet(),
    val allLists: Boolean = true,
    val selectedEpochDay: Long = LocalDate.now().toEpochDay(),
)
