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

class PlanningRepositoryTest {
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
    fun tomorrowIsCalendarDayAcrossDstWithFiltersAndHiddenTemplates() = runBlocking {
        val now = ZonedDateTime.of(2026, 3, 28, 23, 30, 0, 0, ZoneId.of("Europe/Rome"))
        val tomorrow = now.toLocalDate().plusDays(1).toEpochDay()
        val tag = repo.saveTag("Match", 1)
        val task = TaskEntity(title = "Tomorrow", dueDay = tomorrow)
        repo.create(task, setOf(tag))
        repo.create(TaskEntity(title = "Today", dueDay = tomorrow - 1))
        repo.create(TaskEntity(title = "Later", dueDay = tomorrow + 1))
        repo.create(TaskEntity(title = "Undated"))
        repo.editRecurring(
            task,
            setOf(tag),
            RecurrenceRule(Frequency.DAILY).encode(),
            RecurrenceScope.THIS_AND_FUTURE,
        )
        assertEquals(
            listOf(task.id),
            repo.observe(TaskFilter(mode = "TOMORROW", tagId = tag), now).first().map { it.task.id },
        )
        repo.complete(task.id, true)
        assertTrue(repo.observe(TaskFilter(mode = "TOMORROW"), now).first().isEmpty())
        assertEquals(
            1,
            repo.observe(TaskFilter(mode = "TOMORROW", showCompleted = true), now).first().size,
        )
    }

    @Test
    fun matrixOverridesPersistAndFollowRecurrenceScope() = runBlocking {
        val date = LocalDate.of(2026, 9, 17).toEpochDay()
        val task =
            TaskEntity(
                title = "Plant",
                dueDay = date,
                priority = 1,
                matrixUrgent = true,
                matrixImportant = false,
            )
        repo.create(task)
        val rule = RecurrenceRule(Frequency.DAILY).encode()
        val recurring = repo.editRecurring(task, emptySet(), rule, RecurrenceScope.THIS_AND_FUTURE)
        repo.editRecurring(
            recurring.copy(matrixImportant = true),
            emptySet(),
            rule,
            RecurrenceScope.ONLY_THIS,
        )
        assertEquals(true, repo.details(task.id)!!.task.matrixImportant)
        assertEquals(1, repo.details(task.id)!!.task.priority)
        assertEquals(date, repo.details(task.id)!!.task.dueDay)
        repo.complete(task.id, true)
        val next = repo.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first().single().task
        assertEquals(false, next.matrixImportant)
        assertEquals(true, next.matrixUrgent)
        val saved =
            repo.editRecurring(
                next.copy(matrixImportant = true),
                emptySet(),
                rule,
                RecurrenceScope.THIS_AND_FUTURE,
            )
        repo.complete(saved.id, true)
        assertEquals(
            true,
            repo
                .observe(TaskFilter(mode = "ALL"), ZonedDateTime.now())
                .first()
                .single()
                .task
                .matrixImportant,
        )
    }
}
