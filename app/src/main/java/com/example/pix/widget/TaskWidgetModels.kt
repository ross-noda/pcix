package com.example.pix.widget

data class WidgetTask(
    val id: String,
    val title: String,
    val dueLabel: String,
    val overdue: Boolean,
    val priority: Int,
)

data class WidgetGroup(
    val id: String,
    val title: String,
    val colorArgb: Int,
    val tasks: List<WidgetTask>,
)

data class WidgetContent(
    val config: TaskWidgetConfig,
    val filterLabel: String,
    val groups: List<WidgetGroup>,
    val emptyLabel: String,
)
