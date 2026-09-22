package com.example.pix.data

import com.example.pix.domain.RecurrenceEngine
import com.example.pix.domain.RecurrenceRule
import java.time.LocalDate

/** All operations are called inside the repository's Room transaction. */
internal class RecurrenceStore(private val dao: PixDao) {
    suspend fun copyRelations(from: String, to: String) {
        dao.images(from).forEach { dao.insertImage(it.copy(id = newId(), taskId = to)) }
        dao.tagIds(from).forEach { dao.attachTag(TaskTagCrossRef(to, it)) }
        dao.subtasks(from).forEach {
            dao.saveSubtask(it.copy(id = newId(), taskId = to, isCompleted = false))
        }
    }

    suspend fun start(
        task: TaskEntity,
        rule: String,
        anchor: Long? = null,
        original: Long? = null,
        endBefore: Long? = null,
    ): RecurringSeriesEntity {
        RecurrenceRule.parse(rule)
        val day = original ?: requireNotNull(task.dueDay)
        val template =
            task.copy(
                id = newId(),
                seriesId = null,
                originalDay = null,
                isTemplate = true,
                isSkipped = false,
                isCompleted = false,
                completedAt = null,
            )
        dao.insertTask(template)
        copyRelations(task.id, template.id)
        val series =
            RecurringSeriesEntity(
                rule = rule,
                anchorDay = anchor ?: day,
                templateTaskId = template.id,
                endBefore = endBefore,
            )
        dao.insertSeries(series)
        dao.attachSeries(task.id, series.id, day)
        if (task.isCompleted) advance(task.copy(seriesId = series.id, originalDay = day))
        return series
    }

    suspend fun advance(task: TaskEntity) {
        val series = task.seriesId?.let { dao.series(it) } ?: return
        val original = task.originalDay ?: return
        val next =
            RecurrenceEngine.next(
                    LocalDate.ofEpochDay(series.anchorDay),
                    LocalDate.ofEpochDay(original),
                    RecurrenceRule.parse(series.rule),
                )
                .toEpochDay()
        if (series.endBefore != null && next >= series.endBefore) return
        if (dao.occurrence(series.id, next) != null) return
        val template = requireNotNull(dao.task(series.templateTaskId))
        val now = System.currentTimeMillis()
        val occurrence =
            template.copy(
                id = newId(),
                isTemplate = false,
                seriesId = series.id,
                originalDay = next,
                dueDay = next,
                createdAt = now,
                updatedAt = now,
                sortOrder = now,
            )
        dao.insertTask(occurrence)
        copyRelations(template.id, occurrence.id)
    }

    suspend fun cut(task: TaskEntity) {
        val series = task.seriesId?.let { dao.series(it) } ?: return
        val day = requireNotNull(task.originalDay)
        dao.updateSeries(
            series.copy(
                endBefore = minOf(series.endBefore ?: Long.MAX_VALUE, day),
                updatedAt = System.currentTimeMillis(),
            )
        )
        dao.deleteFuture(series.id, day, task.id)
    }

    private suspend fun discardEmptySeries(series: RecurringSeriesEntity) {
        if (dao.seriesOccurrenceCount(series.id) == 0) dao.deleteTask(series.templateTaskId)
    }

    suspend fun configure(old: TaskEntity, rule: String?, scope: RecurrenceScope) {
        val current = requireNotNull(dao.task(old.id))
        val series = old.seriesId?.let { dao.series(it) }
        if (series == null) {
            if (rule != null) start(current, rule)
            return
        }
        if (scope == RecurrenceScope.ONLY_THIS) {
            require(rule == series.rule || rule == null) { "Changing a rule requires series scope" }
            if (rule == null) {
                advance(old)
                dao.attachSeries(old.id, null, null)
                discardEmptySeries(series)
            }
        } else {
            cut(old)
            dao.attachSeries(old.id, null, null)
            discardEmptySeries(series)
            if (rule != null) {
                val preserve = rule == series.rule && current.dueDay == old.dueDay
                start(
                    current.copy(seriesId = null, originalDay = null),
                    rule,
                    if (preserve) series.anchorDay else null,
                    if (preserve) old.originalDay else null,
                    series.endBefore,
                )
            }
        }
    }

    suspend fun refreshSubtaskTemplate(taskId: String, scope: RecurrenceScope) {
        if (scope != RecurrenceScope.THIS_AND_FUTURE) return
        val task = dao.task(taskId) ?: return
        val series = task.seriesId?.let { dao.series(it) } ?: return
        cut(task)
        dao.attachSeries(task.id, null, null)
        discardEmptySeries(series)
        start(
            task.copy(seriesId = null, originalDay = null),
            series.rule,
            series.anchorDay,
            task.originalDay,
            series.endBefore,
        )
    }
}
