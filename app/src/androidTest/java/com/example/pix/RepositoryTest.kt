package com.example.pix

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pix.data.*
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RepositoryTest {
    private lateinit var db: PixDatabase
    private lateinit var repository: TaskRepository

    @Before
    fun setup() = runBlocking {
        db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PixDatabase::class.java,
                )
                .build()
        repository = TaskRepository(db)
        repository.initialize()
    }

    @After fun close() = db.close()

    @Test
    fun deleteListMovesCompletedAndActiveTasks() = runBlocking {
        val list = ListEntity(name = "Work")
        repository.saveList(list)
        val first = TaskEntity(title = "Active", listId = list.id)
        val second = TaskEntity(title = "Completed", listId = list.id)
        repository.create(first)
        repository.create(second)
        repository.complete(second.id, true)
        repository.deleteList(list.id)
        assertEquals(INBOX_ID, repository.task(first.id).first()!!.task.listId)
        assertEquals(INBOX_ID, repository.task(second.id).first()!!.task.listId)
        assertTrue(repository.task(second.id).first()!!.task.isCompleted)
    }

    @Test
    fun tagNormalizationAndCascade() = runBlocking {
        val id = repository.saveTag("Università", 0)
        assertEquals(id, repository.saveTag("UNIVERSITÀ", 2))
        val task = TaskEntity(title = "Read")
        repository.create(task, setOf(id))
        assertEquals(1, repository.task(task.id).first()!!.tags.size)
        repository.deleteTag(id)
        assertTrue(repository.task(task.id).first()!!.tags.isEmpty())
        assertNotNull(repository.task(task.id).first())
    }

    @Test
    fun editDoesNotRevertCompletionAndSubtasksAreIndependent() = runBlocking {
        val task = TaskEntity(title = "Parent")
        repository.create(task)
        repository.saveSubtask(SubtaskEntity(taskId = task.id, title = "Child", isCompleted = true))
        assertFalse(repository.task(task.id).first()!!.task.isCompleted)
        repository.complete(task.id, true)
        repository.edit(task.copy(title = "Updated"), emptySet())
        assertTrue(repository.task(task.id).first()!!.task.isCompleted)
        repository.delete(task.id)
        assertTrue(db.dao().subtasks(task.id).isEmpty())
    }

    @Test
    fun roomDateFiltersAndSearchCoverMetadata() = runBlocking {
        val now = ZonedDateTime.now()
        val today = now.toLocalDate().toEpochDay()
        val list = ListEntity(name = "Research")
        repository.saveList(list)
        val tag = repository.saveTag("Reading", 1)
        repository.create(
            TaskEntity(title = "Today", notes = "Details", dueDay = today, listId = list.id),
            setOf(tag),
        )
        repository.create(TaskEntity(title = "Undated"))
        repository.create(TaskEntity(title = "Yesterday", dueDay = today - 1))
        assertEquals(1, repository.observe(TaskFilter(), now).first().size)
        assertEquals(1, repository.observe(TaskFilter(mode = "OVERDUE"), now).first().size)
        for (query in listOf("Today", "Details", "Research", "Reading")) {
            assertEquals(
                1,
                repository.observe(TaskFilter(mode = "ALL", search = query), now).first().size,
            )
        }
    }

    @Test
    fun inboxIsProtectedAndDuplicationCopiesRelations() = runBlocking {
        try {
            repository.deleteList(INBOX_ID)
            fail("Inbox removed")
        } catch (_: IllegalArgumentException) {}
        val task = TaskEntity(title = "Original")
        val tag = repository.saveTag("Tag", 0)
        repository.create(task, setOf(tag))
        repository.saveSubtask(SubtaskEntity(taskId = task.id, title = "Child"))
        repository.duplicate(task.id)
        val copies = repository.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first()
        assertEquals(2, copies.size)
        assertTrue(copies.all { it.tags.size == 1 && it.subtasks.size == 1 })
    }
}
