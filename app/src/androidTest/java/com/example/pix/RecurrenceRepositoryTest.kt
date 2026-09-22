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

class RecurrenceRepositoryTest {
    private lateinit var db: PixDatabase
    private lateinit var repo: TaskRepository
    private val rule = RecurrenceRule(Frequency.DAILY).encode()
    private val date = LocalDate.of(2026, 9, 17).toEpochDay()

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

    private suspend fun rows() =
        repo.observe(TaskFilter(mode = "ALL", showCompleted = true), ZonedDateTime.now()).first()

    private suspend fun recurring(): TaskEntity {
        val t = TaskEntity(title = "Original", dueDay = date, minuteOfDay = 600)
        repo.create(t)
        return repo.editRecurring(t, emptySet(), rule, RecurrenceScope.THIS_AND_FUTURE)
    }

    @Test
    fun completionPreservesHistoryAndGeneratesExactlyOneSuccessor() = runBlocking {
        val t = recurring()
        repo.complete(t.id, true)
        repo.complete(t.id, true)
        val rows = rows()
        assertEquals(2, rows.size)
        assertEquals(1, rows.count { it.task.isCompleted })
        assertEquals(date + 1, rows.first { !it.task.isCompleted }.task.dueDay)
        repo.complete(t.id, false)
        repo.complete(t.id, true)
        assertEquals(2, rows().size)
        assertEquals(1, repo.lists.first().single().activeCount)
    }

    @Test
    fun onlyThisDoesNotChangeTemplateAndRetainsOriginalDate() = runBlocking {
        val t = recurring()
        repo.editRecurring(
            t.copy(title = "Exception", dueDay = date + 5),
            emptySet(),
            rule,
            RecurrenceScope.ONLY_THIS,
        )
        repo.complete(t.id, true)
        val next = rows().single { !it.task.isCompleted }.task
        assertEquals("Original", next.title)
        assertEquals(date + 1, next.dueDay)
        assertEquals(date, db.dao().task(t.id)!!.originalDay)
    }

    @Test
    fun futureSplitPreservesHistoryAndChangesFollowingTasks() = runBlocking {
        val first = recurring()
        repo.complete(first.id, true)
        val second = rows().single { !it.task.isCompleted }.task
        repo.editRecurring(
            second.copy(title = "New series"),
            emptySet(),
            rule,
            RecurrenceScope.THIS_AND_FUTURE,
        )
        repo.complete(second.id, true)
        val all = rows()
        assertEquals(3, all.size)
        assertEquals("Original", all.single { it.task.id == first.id }.task.title)
        assertEquals("New series", all.single { !it.task.isCompleted }.task.title)
        assertNotEquals(first.seriesId, all.single { !it.task.isCompleted }.task.seriesId)
    }

    @Test
    fun skipSingleGeneratesNextButDeleteFutureStopsSeries() = runBlocking {
        val first = recurring()
        repo.delete(first.id)
        val second = rows().single().task
        assertEquals(date + 1, second.dueDay)
        assertTrue(db.dao().task(first.id)!!.isSkipped)
        repo.delete(second.id, RecurrenceScope.THIS_AND_FUTURE)
        assertTrue(rows().isEmpty())
        assertTrue(repo.day(date).first().isEmpty())
        assertTrue(repo.calendar(date, date + 7).first().isEmpty())
    }

    @Test
    fun disablingOnlyThisContinuesSeriesAndDuplicateIsIndependent() = runBlocking {
        val t = recurring()
        repo.duplicate(t.id)
        val copy = rows().single { it.task.id != t.id }.task
        assertNotEquals(t.seriesId, copy.seriesId)
        repo.editRecurring(t, emptySet(), null, RecurrenceScope.ONLY_THIS)
        assertNull(db.dao().task(t.id)!!.seriesId)
        assertEquals(3, rows().size)
    }

    @Test
    fun tagsAndSubtasksAreCopiedAndExcludedFromCountsForTemplate() = runBlocking {
        val t = TaskEntity(title = "Repeat", dueDay = date)
        val tag = repo.saveTag("Test", 1)
        repo.create(t, setOf(tag))
        repo.saveSubtask(SubtaskEntity(taskId = t.id, title = "Child", isCompleted = true))
        repo.editRecurring(t, setOf(tag), rule, RecurrenceScope.THIS_AND_FUTURE)
        assertEquals(1, repo.tags.first().single().activeCount)
        repo.complete(t.id, true)
        val next = rows().single { !it.task.isCompleted }
        assertEquals(tag, next.tags.single().id)
        assertFalse(next.subtasks.single().isCompleted)
    }

    @Test
    fun reminderCompletionUsesSameRecurrencePath() = runBlocking {
        val t = recurring()
        repo.complete(t.id, true)
        assertEquals(1, db.dao().timedTasks().size)
        assertEquals(date + 1, db.dao().timedTasks().single().dueDay)
    }

    @Test
    fun futureNotesEditPreservesMonthEndAnchor() = runBlocking {
        val monthly = RecurrenceRule(Frequency.MONTHLY).encode()
        val task = TaskEntity(title = "Month end", dueDay = LocalDate.of(2026, 1, 31).toEpochDay())
        repo.create(task)
        repo.editRecurring(task, emptySet(), monthly, RecurrenceScope.THIS_AND_FUTURE)
        repo.complete(task.id, true)
        val feb = rows().single { !it.task.isCompleted }.task
        repo.editRecurring(
            feb.copy(notes = "Changed"),
            emptySet(),
            monthly,
            RecurrenceScope.THIS_AND_FUTURE,
        )
        repo.complete(feb.id, true)
        assertEquals(
            LocalDate.of(2026, 3, 31).toEpochDay(),
            rows().single { !it.task.isCompleted }.task.dueDay,
        )
    }

    @Test
    fun editingHistoryDoesNotReopenPreviouslyEndedSegment() = runBlocking {
        val first = recurring()
        repo.complete(first.id, true)
        val second = rows().single { !it.task.isCompleted }.task
        repo.editRecurring(
            second.copy(title = "New segment"),
            emptySet(),
            rule,
            RecurrenceScope.THIS_AND_FUTURE,
        )
        repo.editRecurring(
            db.dao().task(first.id)!!.copy(notes = "History"),
            emptySet(),
            rule,
            RecurrenceScope.THIS_AND_FUTURE,
        )
        assertEquals(1, rows().count { !it.task.isCompleted })
        assertEquals("New segment", rows().single { !it.task.isCompleted }.task.title)
    }
}
