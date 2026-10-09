package com.example.pix.domain

import com.example.pix.data.TaskEntity
import com.example.pix.data.TaskWithDetails
import java.time.LocalDate

/** Preferences affect automatic classification, never the task's date or priority. */
data class MatrixConfig(
    val cards: List<MatrixCard> = List(4) { MatrixCard() },
    val cardOrder: List<Int> = listOf(0, 1, 2, 3),
    val layout: Int = 0,
    val hideChildren: Boolean = false,
    val columnSplit: Float = .5f,
    val rowSplit: Float = .5f,
    val cornerRadius: Float = 20f,
    val urgentDays: Int = 0,
    val importantPriority: Int = 3,
)

enum class MatrixDate { ALL, TODAY, TOMORROW, THIS_WEEK, NEXT_WEEK, THIS_MONTH, NEXT_MONTH, RANGE }

data class MatrixCard(
    val title: String = "",
    val custom: Boolean = false,
    val listIds: Set<String> = emptySet(),
    val tagIds: Set<String> = emptySet(),
    val date: MatrixDate = MatrixDate.ALL,
    val fromDay: Long? = null,
    val toDay: Long? = null,
    val priorities: Set<Int> = emptySet(),
)

object MatrixRules {
    fun groups(tasks: List<TaskWithDetails>, today: LocalDate, config: MatrixConfig): Map<Int, List<TaskWithDetails>> {
        val sorted = tasks.sortedWith(compareBy<TaskWithDetails> { TaskTiming.lastDay(it.task) ?: Long.MAX_VALUE }
            .thenByDescending { it.task.priority }.thenBy { it.task.title }.thenBy { it.task.id })
        return (0..3).associateWith { id -> sorted.filter { matches(it, id, today, config) } }
    }

    fun orderedIds(config: MatrixConfig): List<Int> =
        (config.cardOrder.filter { it in 0..3 }.distinct() + (0..3)).distinct()

    fun card(config: MatrixConfig, id: Int): MatrixCard = config.cards.getOrNull(id) ?: MatrixCard()

    /** OR within a selection; AND across categories. Custom cards can overlap. */
    fun matches(detail: TaskWithDetails, id: Int, today: LocalDate, config: MatrixConfig): Boolean {
        val task = detail.task
        if (task.isCompleted || task.isTemplate || task.isSkipped || (config.hideChildren && task.parentTaskId != null)) return false
        val filter = card(config, id)
        if (!filter.custom) return quadrant(task, today, config) == id
        if (filter.listIds.isNotEmpty() && task.listId !in filter.listIds) return false
        if (filter.tagIds.isNotEmpty() && detail.tags.none { it.id in filter.tagIds }) return false
        if (filter.priorities.isNotEmpty() && task.priority !in filter.priorities) return false
        if (filter.date == MatrixDate.ALL) return true
        val range = dateRange(filter, today) ?: return false
        val start = task.dueDay ?: return false
        val end = TaskTiming.lastDay(task) ?: start
        return start <= range.last && end >= range.first
    }

    fun dateRange(card: MatrixCard, today: LocalDate): LongRange? {
        val monday = today.minusDays((today.dayOfWeek.value - 1).toLong())
        val month = today.withDayOfMonth(1)
        val dates = when (card.date) {
            MatrixDate.ALL -> return null
            MatrixDate.TODAY -> today to today
            MatrixDate.TOMORROW -> today.plusDays(1) to today.plusDays(1)
            MatrixDate.THIS_WEEK -> monday to monday.plusDays(6)
            MatrixDate.NEXT_WEEK -> monday.plusDays(7) to monday.plusDays(13)
            MatrixDate.THIS_MONTH -> month to month.plusMonths(1).minusDays(1)
            MatrixDate.NEXT_MONTH -> month.plusMonths(1) to month.plusMonths(2).minusDays(1)
            MatrixDate.RANGE -> {
                val from = card.fromDay ?: return null
                val to = card.toDay ?: return null
                return if (from <= to) from..to else null
            }
        }
        return dates.first.toEpochDay()..dates.second.toEpochDay()
    }

    fun quadrant(task: TaskEntity, today: LocalDate, config: MatrixConfig): Int {
        val urgent =
            task.matrixUrgent
                ?: (task.priority >= 5 || (TaskTiming.lastDay(task)?.let {
                    it <= today.plusDays(config.urgentDays.toLong()).toEpochDay()
                } ?: false))
        val important = task.matrixImportant ?: (task.priority >= config.importantPriority)
        return when {
            urgent && important -> 0
            important -> 1
            urgent -> 2
            else -> 3
        }
    }
}
