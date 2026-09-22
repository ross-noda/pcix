package com.example.pix.widget

data class CalendarWidgetDay(
    val epochDay: Long,
    val weekdayLabel: String,
    val dayNumber: Int,
    val selected: Boolean,
    val today: Boolean,
    val markerColorsArgb: List<Int>,
)

data class CalendarWidgetTask(
    val id: String,
    val title: String,
    val timeLabel: String,
    val priority: Int,
    val listColorArgb: Int,
    val isCompleted: Boolean,
)

data class CalendarWidgetContent(
    val config: CalendarWidgetConfig,
    val monthLabel: String,
    val days: List<CalendarWidgetDay>,
    val tasks: List<CalendarWidgetTask>,
)
