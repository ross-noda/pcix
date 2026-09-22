package com.example.pix

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pix.cloud.OutboxRecorder
import com.example.pix.data.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OutboxAndLegacyTest {
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
    fun createEnqueueIsIdempotentAndAcknowledged() = runBlocking {
        val task = TaskEntity(title = "Studiare Statistica")
        repository.create(task)
        val pending = db.syncDao().pending()
        assertTrue(pending.any { it.entityType == "tasks" && it.entityId == task.id && it.operation == "UPSERT" })
        repository.edit(task.copy(title = "Studiare Statistica 2"), emptySet())
        val upserts = db.syncDao().pending().filter { it.entityId == task.id && it.operation == "UPSERT" }
        assertEquals(1, upserts.size)
        db.syncDao().remove(upserts.single().id)
        assertTrue(db.syncDao().pending().none { it.entityId == task.id && it.operation == "UPSERT" })
    }

    @Test
    fun deleteProducesTombstoneNotResurrection() = runBlocking {
        val task = TaskEntity(title = "Temp")
        repository.create(task)
        repository.delete(task.id)
        val ops = db.syncDao().pending().filter { it.entityId == task.id }
        assertTrue(ops.any { it.operation == "DELETE" })
        assertTrue(ops.none { it.operation == "UPSERT" })
        assertNull(db.dao().task(task.id))
    }

    @Test
    fun legacyEnqueuePreservesUuidAndRelations() = runBlocking {
        val list = ListEntity(name = "Uni")
        repository.saveList(list)
        val tag = repository.saveTag("Stat", 1)
        val task = TaskEntity(id = "keep-uuid", title = "Exam", listId = list.id)
        repository.create(task, setOf(tag))
        repository.saveSubtask(SubtaskEntity(taskId = task.id, title = "Chapter 1"))
        db.syncDao().clear()
        OutboxRecorder(db).enqueueAll()
        val types = db.syncDao().pending().map { it.entityType + ":" + it.entityId }.toSet()
        assertTrue(types.any { it.startsWith("tasks:keep-uuid") })
        assertTrue(types.any { it.startsWith("lists:${list.id}") })
        assertTrue(types.any { it.contains(tag) })
        assertEquals("Exam", db.dao().task("keep-uuid")!!.title)
        assertEquals(list.id, db.dao().task("keep-uuid")!!.listId)
        assertEquals(tag, db.dao().tagIds("keep-uuid").single())
    }
}
