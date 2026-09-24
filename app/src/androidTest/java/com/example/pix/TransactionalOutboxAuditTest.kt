package com.example.pix

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pix.cloud.OutboxRecorder
import com.example.pix.cloud.tracked
import com.example.pix.data.*
import com.example.pix.domain.Frequency
import com.example.pix.domain.RecurrenceRule
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TransactionalOutboxAuditTest {
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
        db.syncDao().clear()
    }

    @After fun close() = db.close()

    private suspend fun pending(type: String, id: String) = db.syncDao().pendingFor(type, id)

    private suspend fun single(type: String, id: String) = pending(type, id).single()

    private suspend fun assertUpsert(type: String, id: String): JSONObject {
        val row = single(type, id)
        assertEquals(OutboxRecorder.UPSERT, row.operation)
        return JSONObject(row.payload)
    }

    private suspend fun assertDelete(type: String, id: String) {
        val row = single(type, id)
        assertEquals(OutboxRecorder.DELETE, row.operation)
        assertEquals("{}", row.payload)
    }

    @Test
    fun taskCrudCompletionUndoSnoozeDurationAndMatrixAreQueued() = runBlocking {
        val day = LocalDate.of(2026, 9, 23).toEpochDay()
        val task = TaskEntity(title = "Atomic", dueDay = day, minuteOfDay = 600)

        repo.create(task)
        assertEquals("Atomic", assertUpsert("tasks", task.id).getString("title"))

        db.syncDao().clear()
        repo.edit(
            task.copy(
                title = "Edited",
                durationMinutes = 90,
                matrixUrgent = true,
                matrixImportant = false,
            ),
            emptySet(),
        )
        val edited = assertUpsert("tasks", task.id)
        assertEquals("Edited", edited.getString("title"))
        assertEquals(90, edited.getInt("duration_minutes"))
        assertTrue(edited.getBoolean("matrix_urgent"))
        assertFalse(edited.getBoolean("matrix_important"))

        db.syncDao().clear()
        repo.complete(task.id, true)
        assertTrue(assertUpsert("tasks", task.id).getBoolean("is_completed"))

        db.syncDao().clear()
        repo.complete(task.id, false)
        assertFalse(assertUpsert("tasks", task.id).getBoolean("is_completed"))

        db.syncDao().clear()
        repo.snooze(
            task.id,
            Instant.parse("2026-09-23T10:00:00Z"),
            ZoneOffset.UTC,
        )
        val snoozed = assertUpsert("tasks", task.id)
        assertEquals(day, snoozed.getLong("due_day"))
        assertEquals(660, snoozed.getInt("minute_of_day"))

        db.syncDao().clear()
        repo.delete(task.id)
        assertDelete("tasks", task.id)
    }

    @Test
    fun fullPayloadSnapshotCatchesSameVersionMutation() = runBlocking {
        val task = TaskEntity(title = "Before", updatedAt = 1234L, createdAt = 1234L)
        repo.create(task)
        db.syncDao().clear()

        // Deliberately keep the exact same updatedAt. Timestamp-only snapshots used to miss this.
        db.tracked {
            db.dao().editTask(
                id = task.id,
                title = "After",
                notes = "",
                listId = task.listId,
                day = null,
                minute = null,
                priority = 0,
                duration = null,
                urgent = null,
                important = null,
                now = 1234L,
            )
        }

        assertEquals("After", assertUpsert("tasks", task.id).getString("title"))
    }

    @Test
    fun moveReorderListsTagsAndLinksUseTransactionalOutbox() = runBlocking {
        val day = LocalDate.of(2026, 9, 23).toEpochDay()
        val firstList = ListEntity(name = "A", sortOrder = 1)
        val secondList = ListEntity(name = "B", sortOrder = 2)
        repo.saveList(firstList)
        assertEquals("A", assertUpsert("lists", firstList.id).getString("name"))
        repo.saveList(secondList)
        assertEquals("B", assertUpsert("lists", secondList.id).getString("name"))
        val a = TaskEntity(title = "A", listId = firstList.id, dueDay = day, sortOrder = 1)
        val b = TaskEntity(title = "B", listId = firstList.id, dueDay = day, sortOrder = 2)
        repo.create(a)
        repo.create(b)
        db.syncDao().clear()

        repo.reorderTask(b.id, a.id)
        assertTrue(pending("tasks", a.id).isNotEmpty())
        assertTrue(pending("tasks", b.id).isNotEmpty())

        db.syncDao().clear()
        repo.reorderList(secondList.id, firstList.id)
        assertTrue(pending("lists", firstList.id).isNotEmpty())
        assertTrue(pending("lists", secondList.id).isNotEmpty())

        db.syncDao().clear()
        repo.moveTask(a.id, secondList.id, RecurrenceScope.ONLY_THIS)
        assertEquals(secondList.id, assertUpsert("tasks", a.id).getString("list_id"))

        db.syncDao().clear()
        repo.saveList(secondList.copy(name = "Renamed"))
        assertEquals("Renamed", assertUpsert("lists", secondList.id).getString("name"))

        val tagId = repo.saveTag("Cloud", 1)
        assertEquals("Cloud", assertUpsert("tags", tagId).getString("name"))
        db.syncDao().clear()
        repo.edit(db.dao().task(a.id)!!, setOf(tagId))
        assertUpsert("task_tags", OutboxRecorder.linkId(a.id, tagId))

        db.syncDao().clear()
        repo.edit(db.dao().task(a.id)!!, emptySet())
        assertDelete("task_tags", OutboxRecorder.linkId(a.id, tagId))

        db.syncDao().clear()
        repo.saveTag("Cloud 2", 2, tagId)
        assertEquals("Cloud 2", assertUpsert("tags", tagId).getString("name"))

        // Re-link and delete the tag: both tag tombstone and cascaded link tombstone must survive.
        repo.edit(db.dao().task(a.id)!!, setOf(tagId))
        db.syncDao().clear()
        repo.deleteTag(tagId)
        assertDelete("tags", tagId)
        assertDelete("task_tags", OutboxRecorder.linkId(a.id, tagId))

        db.syncDao().clear()
        repo.deleteList(secondList.id)
        assertDelete("lists", secondList.id)
        assertEquals(INBOX_ID, assertUpsert("tasks", a.id).getString("list_id"))
    }

    @Test
    fun subtasksAndBothReorderPathsAreQueued() = runBlocking {
        val task = TaskEntity(title = "Parent")
        repo.create(task)
        val a = SubtaskEntity(taskId = task.id, title = "A", sortOrder = 0)
        val b = SubtaskEntity(taskId = task.id, title = "B", sortOrder = 1)
        repo.saveSubtask(a)
        assertEquals("A", assertUpsert("subtasks", a.id).getString("title"))
        repo.saveSubtask(b)
        assertEquals("B", assertUpsert("subtasks", b.id).getString("title"))
        db.syncDao().clear()

        repo.reorderSubtask(a.id, b.id, task.id, RecurrenceScope.ONLY_THIS)
        assertTrue(pending("subtasks", a.id).isNotEmpty())
        assertTrue(pending("subtasks", b.id).isNotEmpty())

        db.syncDao().clear()
        repo.moveSubtask(a.id, task.id, -1, RecurrenceScope.ONLY_THIS)
        assertTrue(pending("subtasks", a.id).isNotEmpty() || pending("subtasks", b.id).isNotEmpty())

        db.syncDao().clear()
        repo.saveSubtask(a.copy(title = "A2", isCompleted = true))
        val payload = assertUpsert("subtasks", a.id)
        assertEquals("A2", payload.getString("title"))
        assertTrue(payload.getBoolean("is_completed"))

        db.syncDao().clear()
        repo.deleteSubtask(a.id, task.id, RecurrenceScope.ONLY_THIS)
        assertDelete("subtasks", a.id)
    }

    @Test
    fun recurrenceTemplatesOccurrencesSplitScopesAndSeriesDeleteAreQueued() = runBlocking {
        val day = LocalDate.of(2026, 9, 23).toEpochDay()
        val rule = RecurrenceRule(Frequency.DAILY).encode()
        val task = TaskEntity(title = "Series", dueDay = day, minuteOfDay = 600)
        repo.create(task)
        db.syncDao().clear()

        val recurring =
            repo.editRecurring(task, emptySet(), rule, RecurrenceScope.THIS_AND_FUTURE)
        val seriesId = requireNotNull(recurring.seriesId)
        val series = requireNotNull(db.dao().series(seriesId))
        assertUpsert("recurring_series", seriesId)
        assertUpsert("tasks", series.templateTaskId)
        assertTrue(requireNotNull(db.dao().task(series.templateTaskId)).isTemplate)
        assertUpsert("tasks", task.id)

        db.syncDao().clear()
        repo.editRecurring(
            requireNotNull(db.dao().task(task.id)).copy(notes = "Only this"),
            emptySet(),
            rule,
            RecurrenceScope.ONLY_THIS,
        )
        assertEquals("Only this", assertUpsert("tasks", task.id).getString("notes"))

        db.syncDao().clear()
        repo.complete(task.id, true)
        val next =
            repo.observe(TaskFilter(mode = "ALL", showCompleted = true), java.time.ZonedDateTime.now())
                .first()
                .single { !it.task.isCompleted }
                .task
        assertUpsert("tasks", task.id)
        assertUpsert("tasks", next.id)

        db.syncDao().clear()
        repo.editRecurring(
            next.copy(title = "Split"),
            emptySet(),
            rule,
            RecurrenceScope.THIS_AND_FUTURE,
        )
        assertEquals("Split", assertUpsert("tasks", next.id).getString("title"))
        assertTrue(db.syncDao().pending().any { it.entityType == "recurring_series" })

        db.syncDao().clear()
        repo.delete(next.id, RecurrenceScope.THIS_AND_FUTURE)
        val deletedOccurrence = assertUpsert("tasks", next.id)
        assertTrue(deletedOccurrence.getBoolean("is_skipped"))
        assertTrue(db.syncDao().pending().any { it.entityType == "recurring_series" })
    }

    @Test
    fun duplicationAndImageMetadataAreQueued() = runBlocking {
        val task = TaskEntity(title = "Original")
        val tag = repo.saveTag("T", 1)
        repo.create(task, setOf(tag))
        val subtask = SubtaskEntity(taskId = task.id, title = "Child")
        repo.saveSubtask(subtask)
        val image = TaskImage(taskId = task.id, fileName = "metadata.image")
        repo.addImage(image)
        db.syncDao().clear()

        repo.duplicate(task.id)
        val copies =
            repo.observe(TaskFilter(mode = "ALL"), java.time.ZonedDateTime.now()).first()
        val copy = copies.single { it.task.id != task.id }
        assertUpsert("tasks", copy.task.id)
        assertEquals(1, copy.subtasks.size)
        assertTrue(pending("subtasks", copy.subtasks.single().id).isNotEmpty())
        assertEquals(1, copy.images.size)
        assertTrue(pending("task_images", copy.images.single().id).isNotEmpty())
        assertTrue(pending("task_tags", OutboxRecorder.linkId(copy.task.id, tag)).isNotEmpty())

        db.syncDao().clear()
        val extra = TaskImage(taskId = task.id, fileName = "new.image")
        repo.addImage(extra)
        assertEquals("new.image", assertUpsert("task_images", extra.id).getString("file_name"))

        db.syncDao().clear()
        repo.removeImage(extra)
        assertDelete("task_images", extra.id)
    }

    @Test
    fun coalescingRetryAndCrashRollbackPreserveSemantics() = runBlocking {
        val task = TaskEntity(title = "v1")
        repo.create(task)
        repo.edit(task.copy(title = "v2"), emptySet())
        repo.edit(task.copy(title = "v3"), emptySet())
        var row = single("tasks", task.id)
        assertEquals(OutboxRecorder.UPSERT, row.operation)
        assertEquals("v3", JSONObject(row.payload).getString("title"))
        assertEquals(1, pending("tasks", task.id).size)

        val firstOutboxId = row.id
        db.syncDao().attempted(firstOutboxId, 100L)
        db.syncDao().attempted(firstOutboxId, 200L)
        row = single("tasks", task.id)
        assertEquals(2, row.attemptCount)
        assertEquals(200L, row.lastAttemptAt)
        OutboxRecorder(db).enqueueAll()
        row = single("tasks", task.id)
        assertEquals(firstOutboxId, row.id)
        assertEquals(2, row.attemptCount)
        assertEquals(200L, row.lastAttemptAt)

        repo.edit(task.copy(title = "v4"), emptySet())
        row = single("tasks", task.id)
        assertNotEquals(firstOutboxId, row.id)
        assertEquals(0, row.attemptCount)
        assertNull(row.lastAttemptAt)
        assertEquals("v4", JSONObject(row.payload).getString("title"))

        repo.delete(task.id)
        assertDelete("tasks", task.id)
        val tombstoneId = single("tasks", task.id).id
        repo.saveTag("Unrelated", 3)
        assertDelete("tasks", task.id)
        assertEquals(tombstoneId, single("tasks", task.id).id)
        OutboxRecorder(db).enqueueAll()
        assertDelete("tasks", task.id)
        assertEquals(tombstoneId, single("tasks", task.id).id)
        assertEquals(1, pending("tasks", task.id).size)

        db.syncDao().clear()
        val doomed = TaskEntity(title = "Rollback")
        try {
            db.tracked {
                db.dao().insertTask(doomed)
                error("simulated process failure before transaction commit")
            }
            fail("transaction should have failed")
        } catch (_: IllegalStateException) {}
        assertNull(db.dao().task(doomed.id))
        assertTrue(pending("tasks", doomed.id).isEmpty())

        val committed = TaskEntity(title = "Commit")
        db.tracked { db.dao().insertTask(committed) }
        assertNotNull(db.dao().task(committed.id))
        assertUpsert("tasks", committed.id)
    }
}
