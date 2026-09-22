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

class DurationRepositoryTest {
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
    fun rangeAppearsOnEveryOccupiedDayAndCalendarMark() = runBlocking {
        val today = LocalDate.of(2028, 2, 29)
        val day = today.toEpochDay()
        val t = TaskEntity(title = "Trip", dueDay = day - 1, durationMinutes = 3 * 1440)
        repo.create(t)
        assertEquals(t.id, repo.day(day).first().single().task.id)
        assertEquals(
            t.id,
            repo
                .observe(
                    TaskFilter(mode = "TOMORROW"),
                    today.atStartOfDay(ZoneId.of("Europe/Rome")),
                )
                .first()
                .single()
                .task
                .id,
        )
        assertEquals(
            listOf(day - 1, day, day + 1),
            repo.calendar(day - 2, day + 3).first().map { it.dueDay },
        )
        assertTrue(repo.day(day + 2).first().isEmpty())
    }

    @Test
    fun timedEndAndOverdueUseActualRangeBoundary() = runBlocking {
        val today = LocalDate.now()
        val day = today.toEpochDay()
        val zone = ZoneId.systemDefault()
        val t =
            TaskEntity(
                title = "Night",
                dueDay = day - 1,
                minuteOfDay = 23 * 60,
                durationMinutes = 90,
            )
        repo.create(t)
        assertEquals(t.id, repo.day(day).first().single().task.id)
        assertTrue(
            repo
                .observe(TaskFilter(mode = "OVERDUE"), today.atTime(0, 15).atZone(zone))
                .first()
                .isEmpty()
        )
        assertEquals(
            t.id,
            repo
                .observe(TaskFilter(mode = "OVERDUE"), today.atTime(0, 31).atZone(zone))
                .first()
                .single()
                .task
                .id,
        )
        repo.edit(t.copy(durationMinutes = 60), emptySet())
        assertTrue(repo.day(day).first().isEmpty())
    }

    @Test
    fun durationSurvivesRecurrenceDuplicateAndPostpone() = runBlocking {
        val day = LocalDate.now().toEpochDay()
        val t = TaskEntity(title = "Routine", dueDay = day, minuteOfDay = 600, durationMinutes = 90)
        repo.create(t)
        repo.editRecurring(
            t,
            emptySet(),
            RecurrenceRule(Frequency.DAILY).encode(),
            RecurrenceScope.THIS_AND_FUTURE,
        )
        repo.complete(t.id, true)
        val next = repo.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first().single()
        assertEquals(90, next.task.durationMinutes)
        repo.duplicate(next.task.id)
        assertTrue(
            repo.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first().all {
                it.task.durationMinutes == 90
            }
        )
        repo.postponeTask(next.task.id, day + 2, null, RecurrenceScope.ONLY_THIS)
        assertEquals(1440, repo.details(next.task.id)!!.task.durationMinutes)
    }

    @Test
    fun newestSubtaskIsFirstAndCompletedItemsRemainLast() = runBlocking {
        val t = TaskEntity(title = "Checklist")
        repo.create(t)
        val a = SubtaskEntity(taskId = t.id, title = "A")
        val b = SubtaskEntity(taskId = t.id, title = "B")
        repo.saveSubtask(a)
        repo.saveSubtask(b)
        repo.saveSubtask(b.copy(isCompleted = true))
        val c = SubtaskEntity(taskId = t.id, title = "New")
        repo.saveSubtask(c)
        assertEquals(listOf(c.id, a.id, b.id), db.dao().subtasks(t.id).map { it.id })
        repo.reorderSubtask(c.id, b.id, t.id, RecurrenceScope.ONLY_THIS)
        assertEquals(listOf(c.id, a.id, b.id), db.dao().subtasks(t.id).map { it.id })
        repo.saveSubtask(b.copy(isCompleted = false))
        assertTrue(db.dao().subtasks(t.id).none { it.isCompleted })
        assertFalse(repo.details(t.id)!!.task.isCompleted)
    }
}
