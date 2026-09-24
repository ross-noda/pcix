package com.example.pix.data

import com.example.pix.cloud.tracked
import com.example.pix.domain.TaskRules
import java.time.ZonedDateTime

data class TaskFilter(
    val mode: String = "TODAY",
    val listId: String? = null,
    val tagId: String? = null,
    val search: String = "",
    val showCompleted: Boolean = false,
    val manual: Boolean = false,
)

class TaskRepository(
    private val db: PixDatabase,
    private val onTasksChanged: () -> Unit = {},
    private val onMutated: () -> Unit = {},
) {
    private val dao = db.dao()
    private val recurrence = RecurrenceStore(dao)
    val lists = dao.observeLists()
    val tags = dao.observeTags()

    private suspend fun <T> mutate(block: suspend () -> T): T {
        val result = if (db.inTransaction()) block() else db.tracked { block() }
        onTasksChanged()
        onMutated()
        return result
    }

    suspend fun initialize() =
        mutate { dao.insertList(ListEntity(id = INBOX_ID, name = "Inbox", color = 8, sortOrder = 0)) }

    fun observe(filter: TaskFilter, now: ZonedDateTime) =
        dao.observeTasks(
            filter.mode,
            now.toLocalDate().toEpochDay(),
            now.toLocalDate().plusDays(7).toEpochDay(),
            now.hour * 60 + now.minute,
            filter.listId,
            filter.tagId,
            TaskRules.searchPattern(filter.search),
            filter.showCompleted,
            filter.manual,
        )

    fun day(day: Long) = dao.observeDay(day)

    fun calendar(start: Long, end: Long) = dao.observeCalendar(start, end)

    suspend fun details(id: String) = dao.details(id)

    fun task(id: String) = dao.observeTask(id)

    suspend fun create(task: TaskEntity, tags: Set<String> = emptySet()) =
        mutate {
            initialize()
            validate(task)
            dao.insertTask(task.copy(title = task.title.trim()))
            tags.forEach { dao.attachTag(TaskTagCrossRef(task.id, it)) }
        }

    suspend fun edit(task: TaskEntity, tags: Set<String>) =
        mutate {
            validate(task)
            dao.editTask(
                task.id,
                task.title.trim(),
                task.notes,
                task.listId,
                task.dueDay,
                task.minuteOfDay,
                task.priority,
                task.durationMinutes,
                task.matrixUrgent,
                task.matrixImportant,
                System.currentTimeMillis(),
            )
            dao.clearTags(task.id)
            tags.forEach { dao.attachTag(TaskTagCrossRef(task.id, it)) }
        }

    private fun validate(task: TaskEntity) {
        require(TaskRules.validTitle(task.title))
        require(task.notes.length <= 2000)
        require(task.minuteOfDay == null || (task.dueDay != null && task.minuteOfDay in 0..1439))
        require(task.priority in listOf(0, 1, 3, 5))
        com.example.pix.domain.TaskTiming.validate(task)
    }

    suspend fun editRecurring(
        task: TaskEntity,
        tags: Set<String>,
        rule: String?,
        scope: RecurrenceScope,
    ): TaskEntity =
        mutate {
            val old = requireNotNull(dao.task(task.id))
            require(!old.isSkipped && !old.isTemplate)
            if (rule != null) require(task.dueDay != null)
            edit(task, tags)
            recurrence.configure(old, rule, scope)
            requireNotNull(dao.task(task.id))
        }

    suspend fun complete(id: String, completed: Boolean) {
        mutate {
            val old = dao.task(id) ?: return@mutate
            if (old.isTemplate || old.isSkipped || old.isCompleted == completed) return@mutate
            val now = System.currentTimeMillis()
            dao.complete(id, completed, if (completed) now else null, now)
            if (completed) recurrence.advance(old)
        }
    }

    suspend fun delete(id: String, scope: RecurrenceScope = RecurrenceScope.ONLY_THIS) {
        mutate {
            val task = dao.task(id) ?: return@mutate
            if (task.seriesId == null) dao.deleteTask(id)
            else {
                if (scope == RecurrenceScope.THIS_AND_FUTURE) recurrence.cut(task)
                else recurrence.advance(task)
                dao.skip(id)
            }
        }
    }

    suspend fun duplicate(id: String) =
        mutate {
            val original = requireNotNull(dao.task(id))
            val copy =
                original.copy(
                    id = newId(),
                    seriesId = null,
                    originalDay = null,
                    isCompleted = false,
                    completedAt = null,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                )
            dao.insertTask(copy)
            dao.images(id).forEach { dao.insertImage(it.copy(id = newId(), taskId = copy.id)) }
            dao.tagIds(id).forEach { dao.attachTag(TaskTagCrossRef(copy.id, it)) }
            dao.subtasks(id).forEach {
                dao.saveSubtask(it.copy(id = newId(), taskId = copy.id, isCompleted = false))
            }
            original.seriesId?.let { dao.series(it) }?.let { recurrence.start(copy, it.rule) }
        }

    suspend fun snooze(
        id: String,
        now: java.time.Instant = java.time.Instant.now(),
        zone: java.time.ZoneId = java.time.ZoneId.systemDefault(),
    ) {
        val (day, minute) = com.example.pix.domain.ReminderRules.snoozed(now, zone)
        mutate { dao.snooze(id, day, minute, now.toEpochMilli()) }
    }

    suspend fun reorderTask(id: String, targetId: String) =
        mutate {
            val task = dao.task(id) ?: return@mutate
            val target = dao.task(targetId) ?: return@mutate
            if (
                task.isCompleted ||
                    target.isCompleted ||
                    task.isTemplate ||
                    target.isTemplate ||
                    task.isSkipped ||
                    target.isSkipped ||
                    task.listId != target.listId ||
                    task.dueDay != target.dueDay
            )
                return@mutate
            val dayRows = dao.taskDayOrder(task.dueDay)
            val group = dayRows.filter { it.listId == task.listId }.map { it.id }
            val moved = com.example.pix.domain.OrderRules.move(group, id, targetId).iterator()
            val ordered = dayRows.map { if (it.listId == task.listId) moved.next() else it.id }
            val now = System.currentTimeMillis()
            ordered.forEachIndexed { index, key -> dao.setTaskOrder(key, index.toLong(), now) }
        }

    suspend fun reorderList(id: String, targetId: String) =
        mutate {
            if (id == INBOX_ID || targetId == INBOX_ID) return@mutate
            val ids = dao.orderedLists().map { it.id }
            com.example.pix.domain.OrderRules.move(ids, id, targetId).forEachIndexed { index, key ->
                dao.setListOrder(key, index.toLong())
            }
        }

    suspend fun reorderSubtask(
        id: String,
        targetId: String,
        taskId: String,
        scope: RecurrenceScope,
    ) =
        mutate {
            val rows = dao.subtasks(taskId)
            val completed = rows.find { it.id == id }?.isCompleted ?: return@mutate
            val ids = rows.filter { it.isCompleted == completed }.map { it.id }
            if (id !in ids || targetId !in ids || id == targetId) return@mutate
            val now = System.currentTimeMillis()
            com.example.pix.domain.OrderRules.move(ids, id, targetId).forEachIndexed { index, key ->
                dao.setSubtaskOrder(key, index.toLong(), now)
            }
            recurrence.refreshSubtaskTemplate(taskId, scope)
        }

    suspend fun moveTask(id: String, listId: String, scope: RecurrenceScope) {
        mutate {
            val old = dao.task(id) ?: return@mutate
            if (old.isTemplate || old.isSkipped || old.listId == listId) return@mutate
            dao.moveTask(id, listId, System.currentTimeMillis())
            old.seriesId?.let { dao.series(it) }?.let { recurrence.configure(old, it.rule, scope) }
        }
    }

    suspend fun postponeTask(id: String, day: Long, minute: Int?, scope: RecurrenceScope) {
        require(minute == null || minute in 0..1439)
        mutate {
            val old = dao.task(id) ?: return@mutate
            if (old.isTemplate || old.isSkipped || old.isCompleted) return@mutate
            dao.rescheduleTask(
                id,
                day,
                minute,
                if (minute == null)
                    com.example.pix.domain.TaskTiming.allDay(old, true).durationMinutes
                else old.durationMinutes,
                System.currentTimeMillis(),
            )
            old.seriesId?.let { dao.series(it) }?.let { recurrence.configure(old, it.rule, scope) }
        }
    }

    suspend fun setListIcon(id: String, icon: String) {
        require(icon.isNotBlank() && icon.length <= 16)
        mutate { dao.setListIcon(id, icon) }
    }

    suspend fun imageReferenced(name: String) = name in dao.referencedImages()

    suspend fun addImage(image: TaskImage, scope: RecurrenceScope = RecurrenceScope.ONLY_THIS) =
        mutate {
            dao.insertImage(image)
            recurrence.refreshSubtaskTemplate(image.taskId, scope)
        }

    suspend fun removeImage(image: TaskImage, scope: RecurrenceScope = RecurrenceScope.ONLY_THIS) =
        mutate {
            dao.deleteImage(image.id)
            recurrence.refreshSubtaskTemplate(image.taskId, scope)
        }

    suspend fun saveList(list: ListEntity) {
        require(list.id != INBOX_ID)
        require(list.name.isNotBlank() && list.name.trim().length <= 50)
        val now = System.currentTimeMillis()
        val row = list.copy(name = list.name.trim(), updatedAt = now)
        mutate {
            dao.insertList(row.copy(createdAt = now))
            dao.updateList(row)
        }
    }

    suspend fun deleteList(id: String) =
        mutate {
            require(id != INBOX_ID)
            initialize()
            dao.moveToInbox(id)
            dao.deleteList(id)
        }

    suspend fun saveTag(name: String, color: Int, id: String? = null): String =
        mutate {
            require(name.isNotBlank() && name.trim().length <= 50)
            val normalized = TaskRules.normalizedTag(name)
            val existing = dao.tagByName(normalized)
            if (existing != null && existing.id != id) {
                require(id == null) { "Duplicate tag" }
                return@mutate existing.id
            }
            val now = System.currentTimeMillis()
            val tag =
                TagEntity(
                    id = id ?: newId(),
                    name = name.trim(),
                    normalizedName = normalized,
                    color = color,
                    createdAt = now,
                    updatedAt = now,
                )
            if (id == null) dao.insertTag(tag) else dao.updateTag(tag)
            tag.id
        }

    suspend fun deleteTag(id: String) = mutate { dao.deleteTag(id) }

    suspend fun saveSubtask(
        subtask: SubtaskEntity,
        scope: RecurrenceScope = RecurrenceScope.ONLY_THIS,
    ) =
        mutate {
            require(TaskRules.validTitle(subtask.title))
            val siblings = dao.subtasks(subtask.taskId)
            val existing = siblings.find { it.id == subtask.id }
            val order =
                existing?.sortOrder
                    ?: ((siblings.filterNot { it.isCompleted }.minOfOrNull { it.sortOrder } ?: 0L) -
                        1)
            dao.saveSubtask(
                subtask.copy(
                    title = subtask.title.trim(),
                    sortOrder = order,
                    updatedAt = System.currentTimeMillis(),
                )
            )
            recurrence.refreshSubtaskTemplate(subtask.taskId, scope)
        }

    suspend fun deleteSubtask(
        id: String,
        taskId: String? = null,
        scope: RecurrenceScope = RecurrenceScope.ONLY_THIS,
    ) =
        mutate {
            dao.deleteSubtask(id)
            if (taskId != null) recurrence.refreshSubtaskTemplate(taskId, scope)
        }

    suspend fun moveSubtask(
        id: String,
        taskId: String,
        direction: Int,
        scope: RecurrenceScope = RecurrenceScope.ONLY_THIS,
    ) =
        mutate {
            val all = dao.subtasks(taskId)
            val completed = all.find { it.id == id }?.isCompleted ?: return@mutate
            val rows = all.filter { it.isCompleted == completed }.toMutableList()
            val index = rows.indexOfFirst { it.id == id }
            val target = index + direction
            if (index >= 0 && target in rows.indices) {
                java.util.Collections.swap(rows, index, target)
                val now = System.currentTimeMillis()
                rows.forEachIndexed { order, row ->
                    dao.saveSubtask(
                        row.copy(sortOrder = order.toLong(), updatedAt = now)
                    )
                }
                recurrence.refreshSubtaskTemplate(taskId, scope)
            }
        }
}
