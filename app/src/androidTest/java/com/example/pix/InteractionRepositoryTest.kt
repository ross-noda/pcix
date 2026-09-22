package com.example.pix

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.domain.*
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class InteractionRepositoryTest {
    private lateinit var db: PixDatabase
    private lateinit var repo: TaskRepository

    @Before
    fun setup() = runBlocking {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PixDatabase::class.java,
                )
                .build()
        repo = TaskRepository(db)
        repo.initialize()
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun reorderPreservesHiddenRowsAndRejectsOtherGroups() = runBlocking {
        val day = LocalDate.now().toEpochDay()
        val tasks =
            (0..2).map {
                TaskEntity(
                    title = "Task $it",
                    dueDay = day,
                    sortOrder = it.toLong(),
                    minuteOfDay = it * 60,
                )
            }
        tasks.forEach { repo.create(it) }
        val tomorrow = TaskEntity(title = "Tomorrow", dueDay = day + 1)
        repo.create(tomorrow)
        repo.reorderTask(tasks[0].id, tasks[2].id)
        fun filter(manual: Boolean) = TaskFilter(mode = "TODAY", manual = manual)
        assertEquals(
            listOf(tasks[1].id, tasks[2].id, tasks[0].id),
            repo.observe(filter(true), ZonedDateTime.now()).first().map { it.task.id },
        )
        assertEquals(
            tasks.map { it.id },
            repo.observe(filter(false), ZonedDateTime.now()).first().map { it.task.id },
        )
        repo.reorderTask(tasks[0].id, tomorrow.id)
        repo.edit(tasks[0].copy(notes = "Latest note"), emptySet())
        assertEquals(2L, repo.details(tasks[0].id)!!.task.sortOrder)
        assertEquals("Latest note", repo.details(tasks[0].id)!!.task.notes)
        repo.complete(tasks[0].id, true)
        repo.reorderTask(tasks[0].id, tasks[1].id)
        assertEquals(2L, repo.details(tasks[0].id)!!.task.sortOrder)
    }

    @Test
    fun reorderKeepsOtherListSlotsInMixedHome() = runBlocking {
        val list = ListEntity(name = "Other")
        repo.saveList(list)
        val a = TaskEntity(title = "A", sortOrder = 100)
        val other = TaskEntity(title = "Other", listId = list.id, sortOrder = 200)
        val b = TaskEntity(title = "B", sortOrder = 300)
        listOf(a, other, b).forEach { repo.create(it) }
        repo.reorderTask(b.id, a.id)
        val rows =
            repo.observe(TaskFilter(mode = "ALL", manual = true), ZonedDateTime.now()).first()
        assertEquals(listOf(b.id, other.id, a.id), rows.map { it.task.id })
    }

    @Test
    fun listOrderKeepsInboxFixed() = runBlocking {
        val a = ListEntity(name = "A", sortOrder = 1)
        val b = ListEntity(name = "B", sortOrder = 2)
        repo.saveList(a)
        repo.saveList(b)
        repo.reorderList(b.id, a.id)
        assertEquals(listOf(INBOX_ID, b.id, a.id), repo.lists.first().map { it.list.id })
        repo.reorderList(a.id, INBOX_ID)
        assertEquals(listOf(INBOX_ID, b.id, a.id), repo.lists.first().map { it.list.id })
        repo.saveList(a.copy(sortOrder = 0))
        repo.saveList(b.copy(sortOrder = 0))
        val tied = repo.lists.first().drop(1).map { it.list.id }
        repo.reorderList(tied.last(), tied.first())
        assertEquals(listOf(INBOX_ID) + tied.reversed(), repo.lists.first().map { it.list.id })
    }

    @Test
    fun subtaskOrderCopiesToFutureWithoutCompletingParent() = runBlocking {
        val task = TaskEntity(title = "Series", dueDay = LocalDate.now().toEpochDay())
        repo.create(task)
        val a = SubtaskEntity(taskId = task.id, title = "A", sortOrder = 0)
        val b = SubtaskEntity(taskId = task.id, title = "B", sortOrder = 1)
        repo.saveSubtask(a)
        repo.saveSubtask(b)
        repo.reorderSubtask(a.id, b.id, task.id, RecurrenceScope.ONLY_THIS)
        repo.editRecurring(
            task,
            emptySet(),
            RecurrenceRule(Frequency.DAILY).encode(),
            RecurrenceScope.THIS_AND_FUTURE,
        )
        var schedules = 0
        repo = TaskRepository(db) { schedules++ }
        repo.reorderSubtask(b.id, a.id, task.id, RecurrenceScope.THIS_AND_FUTURE)
        assertEquals(1, schedules)
        assertFalse(repo.details(task.id)!!.task.isCompleted)
        repo.complete(task.id, true)
        val next = repo.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first().single()
        assertEquals(listOf("B", "A"), next.subtasks.sortedBy { it.sortOrder }.map { it.title })
    }

    @Test
    fun scopedMoveAndPostponePreserveContentAndSeriesAnchor() = runBlocking {
        val day = LocalDate.now().toEpochDay()
        val list = ListEntity(name = "Work")
        repo.saveList(list)
        val task =
            TaskEntity(title = "Series", notes = "Keep notes", dueDay = day, minuteOfDay = 600)
        val tag = repo.saveTag("Keep tag", 1)
        repo.create(task, setOf(tag))
        repo.addImage(TaskImage(taskId = task.id, fileName = "keep.image"))
        repo.editRecurring(
            task,
            setOf(tag),
            RecurrenceRule(Frequency.DAILY).encode(),
            RecurrenceScope.THIS_AND_FUTURE,
        )
        repo.moveTask(task.id, list.id, RecurrenceScope.ONLY_THIS)
        repo.postponeTask(task.id, day + 5, 700, RecurrenceScope.ONLY_THIS)
        repo.complete(task.id, true)
        val next = repo.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first().single()
        assertEquals(day + 1, next.task.dueDay)
        assertEquals(INBOX_ID, next.task.listId)
        repo.moveTask(next.task.id, list.id, RecurrenceScope.THIS_AND_FUTURE)
        repo.postponeTask(next.task.id, day + 8, null, RecurrenceScope.THIS_AND_FUTURE)
        repo.complete(next.task.id, true)
        val future = repo.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first().single()
        assertEquals(day + 9, future.task.dueDay)
        assertEquals(list.id, future.task.listId)
        assertNull(future.task.minuteOfDay)
        assertEquals("Keep notes", future.task.notes)
        assertEquals(tag, future.tags.single().id)
        assertEquals("keep.image", future.images.single().fileName)
    }
}
