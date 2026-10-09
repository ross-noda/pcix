package com.example.pix.data

import com.example.pix.cloud.tracked
import com.example.pix.domain.TaskRules
import java.time.ZonedDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class TaskFilter(
    val mode: String = "TODAY",
    val listId: String? = null,
    val tagId: String? = null,
    val search: String = "",
    val showCompleted: Boolean = false,
    val manual: Boolean = false,
    val rootsOnly: Boolean = false,
)

class TaskRepository(
    private val db: PixDatabase,
    private val onTasksChanged: () -> Unit = {},
    private val onMutated: () -> Unit = {},
    private val accountMutex: Mutex = Mutex(),
    private val canMutate: () -> Boolean = { true },
) {
    private val dao = db.dao()
    private val recurrence = RecurrenceStore(dao)
    val lists = dao.observeLists()
    val tags = dao.observeTags()

    private suspend fun <T> mutate(block: suspend () -> T): T {
        if (db.inTransaction()) return block()
        val result = accountMutex.withLock {
            check(canMutate()) { "Account is not ready for local mutations" }
            db.tracked { block() }
        }
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
            filter.rootsOnly,
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

    private suspend fun validate(task: TaskEntity) {
        TaskHierarchy.validate(dao, task)
        require(TaskRules.validTitle(task.title))
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

    suspend fun completeWidgetChild(parentId: String, childId: String, completed: Boolean, guard: () -> Boolean) = mutate {
        check(guard()) { "Stale widget account or configuration" }
        val parent = dao.task(parentId) ?: return@mutate
        val child = dao.task(childId) ?: return@mutate
        if (parent.isSkipped || parent.isTemplate || child.parentTaskId != parentId) return@mutate
        complete(childId, completed)
    }

    /** Widget reads share the account transition lock, preventing cross-account snapshots. */
    suspend fun widgetDetails(id: String, guard: () -> Boolean): TaskWithDetails? =
        accountMutex.withLock { if (guard()) dao.details(id) else null }

    /** Compare the rendered notes before editing so stale taps cannot change another row. */
    suspend fun setChecklistItem(id: String, notesRevision: String, index: Int, checked: Boolean, guard: () -> Boolean = { true }) = mutate {
        check(guard()) { "Stale widget account or configuration" }
        val detail = dao.details(id) ?: return@mutate
        if (detail.task.isTemplate || detail.task.isSkipped || com.example.pix.domain.DescriptionText.revision(detail.task.notes) != notesRevision) return@mutate
        val rows = com.example.pix.domain.DescriptionText.items(detail.task.notes).toMutableList()
        val row = rows.getOrNull(index) ?: return@mutate
        if (row.checked == null || row.checked == checked) return@mutate
        rows[index] = row.copy(checked = checked)
        edit(detail.task.copy(notes = com.example.pix.domain.DescriptionText.encode(rows)), detail.tags.map { it.id }.toSet())
    }

    suspend fun delete(id: String, scope: RecurrenceScope = RecurrenceScope.ONLY_THIS) {
        mutate {
            val task = dao.task(id) ?: return@mutate
            dao.detachChildren(id, System.currentTimeMillis())
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
            recurrence.refreshTemplate(image.taskId, scope)
        }

    suspend fun removeImage(image: TaskImage, scope: RecurrenceScope = RecurrenceScope.ONLY_THIS) =
        mutate {
            dao.deleteImage(image.id)
            recurrence.refreshTemplate(image.taskId, scope)
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

    fun parentCandidates(id: String, search: String) = dao.parentCandidates(id, TaskRules.searchPattern(search).ifEmpty { "%" })

    suspend fun linkParent(id: String, parent: String?) = mutate {
        val task = requireNotNull(dao.task(id))
        require(!task.isTemplate && !task.isSkipped)
        TaskHierarchy.validate(dao, task.copy(parentTaskId = parent))
        dao.setParent(id, parent, System.currentTimeMillis())
    }

    suspend fun createChild(parentId: String, title: String): TaskEntity = mutate {
        val parent = requireNotNull(dao.task(parentId))
        val child = TaskEntity(title = title, parentTaskId = parentId, listId = parent.listId,
            sortOrder = (dao.children(parentId).minOfOrNull { it.sortOrder } ?: 0L) - 1)
        create(child)
        child
    }

    suspend fun reorderChild(id: String, targetId: String, parentId: String) = mutate {
        val rows = dao.children(parentId)
        val completed = rows.find { it.id == id }?.isCompleted ?: return@mutate
        val ids = rows.filter { it.isCompleted == completed }.map { it.id }
        if (id !in ids || targetId !in ids) return@mutate
        val now = System.currentTimeMillis()
        com.example.pix.domain.OrderRules.move(ids, id, targetId).forEachIndexed { order, key -> dao.setTaskOrder(key, order.toLong(), now) }
    }

    suspend fun moveChild(id: String, parentId: String, direction: Int) = mutate {
        val rows = dao.children(parentId)
        val completed = rows.find { it.id == id }?.isCompleted ?: return@mutate
        val ids = rows.filter { it.isCompleted == completed }.map { it.id }
        val index = ids.indexOf(id)
        if (index + direction in ids.indices) reorderChild(id, ids[index + direction], parentId)
    }
}
