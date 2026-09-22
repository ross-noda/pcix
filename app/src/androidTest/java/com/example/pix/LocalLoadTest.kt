package com.example.pix

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.domain.TaskTiming
import java.time.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Functional load coverage; this is not a device UI/startup benchmark. */
class LocalLoadTest {
    @Test
    fun fiveHundredTasksKeepFiltersAndReorderingConsistent() = runBlocking {
        val db =
            Room.inMemoryDatabaseBuilder(
                    ApplicationProvider.getApplicationContext(),
                    PixDatabase::class.java,
                )
                .build()
        try {
            val repo = TaskRepository(db)
            repo.initialize()
            val today = LocalDate.now()
            val base = today.toEpochDay()
            val tasks =
                (0 until 500).map { i ->
                    TaskEntity(
                        title = "Load $i",
                        dueDay = base + i % 7,
                        minuteOfDay = if (i % 3 == 0) 540 else null,
                        durationMinutes = if (i % 4 == 0) 2880 else null,
                        sortOrder = i.toLong(),
                    )
                }
            db.withTransaction { tasks.forEach { repo.create(it) } }
            val start = System.nanoTime()
            val all =
                repo.observe(TaskFilter(mode = "ALL", manual = true), ZonedDateTime.now()).first()
            assertEquals(500, all.size)
            for (offset in 0L..8L) {
                val expected =
                    tasks
                        .filter {
                            it.dueDay!! <= base + offset &&
                                TaskTiming.lastDay(it)!! >= base + offset
                        }
                        .map { it.id }
                        .toSet()
                assertEquals(expected, repo.day(base + offset).first().map { it.task.id }.toSet())
            }
            val group = all.filter { it.task.dueDay == base }.map { it.task.id }
            repo.reorderTask(group.last(), group.first())
            assertEquals(
                group.last(),
                repo
                    .observe(TaskFilter(mode = "TODAY", manual = true), ZonedDateTime.now())
                    .first()
                    .first()
                    .task
                    .id,
            )
            tasks.take(50).forEach { repo.complete(it.id, true) }
            assertEquals(
                450,
                repo.observe(TaskFilter(mode = "ALL"), ZonedDateTime.now()).first().size,
            )
            println("500-task functional checks: ${(System.nanoTime()-start)/1_000_000} ms")
        } finally {
            db.close()
        }
    }
}
