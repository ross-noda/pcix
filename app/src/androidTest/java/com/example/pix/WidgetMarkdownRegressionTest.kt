package com.example.pix

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.domain.Markdown
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.time.ZonedDateTime

class WidgetMarkdownRegressionTest {
    @Test fun widgetsQueryOnlyParentsAndMarkdownPersistsBothDirections() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), PixDatabase::class.java).build()
        try {
            val repo = TaskRepository(db)
            val now = ZonedDateTime.now()
            val day = now.toLocalDate().toEpochDay()
            val parent = TaskEntity(title = "Parent", dueDay = day, notes = "- [ ] milk")
            repo.create(parent)
            val child = TaskEntity(title = "Child", parentTaskId = parent.id, dueDay = day)
            repo.create(child)
            for (mode in listOf("ALL", "TODAY", "WEEK")) {
                val rows = repo.observe(TaskFilter(mode = mode, rootsOnly = true), now).first()
                assertEquals(listOf(parent.id), rows.map { it.task.id })
            }
            assertEquals(listOf(parent.id), db.dao().observeWidgetRange(day, day + 42).first().map { it.task.id })
            assertEquals(2, repo.observe(TaskFilter(mode = "ALL"), now).first().size)
            val offset = parent.notes.indexOf('[') + 1
            repo.edit(parent.copy(notes = Markdown.toggle(parent.notes, offset)), emptySet())
            val reopened = TaskRepository(db).details(parent.id)!!.task
            assertEquals("- [x] milk", reopened.notes)
            repo.edit(reopened.copy(notes = Markdown.toggle(reopened.notes, offset)), emptySet())
            assertEquals("- [ ] milk", repo.details(parent.id)!!.task.notes)
            assertTrue(db.syncDao().pendingFor("tasks", parent.id).single().payload.contains("- [ ] milk"))
        } finally { db.close() }
    }
}
