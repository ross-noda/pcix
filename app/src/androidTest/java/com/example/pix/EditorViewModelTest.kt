package com.example.pix

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.TaskEntity
import com.example.pix.ui.TasksViewModel
import java.time.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class EditorViewModelTest {
    @Test fun backFromChildRestoresParentAndPersistsBothDrafts() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PixApplication
        val parent = TaskEntity(title = "Parent history")
        val child = TaskEntity(title = "Child history", parentTaskId = parent.id)
        val store = ViewModelStore()
        lateinit var model: TasksViewModel
        app.repository.create(parent)
        app.repository.create(child)
        try {
            val detail = app.repository.details(parent.id)!!
            instrumentation.runOnMainSync {
                model = TasksViewModel(app, SavedStateHandle())
                store.put("history", model)
                model.open(detail)
                model.edit(model.draft.value!!.let { it.copy(task = it.task.copy(title = "Parent edited")) })
                model.openTask(child.id)
            }
            withTimeout(5000) { while (model.draft.value?.task?.id != child.id) delay(30) }
            instrumentation.runOnMainSync {
                model.edit(model.draft.value!!.let { it.copy(task = it.task.copy(title = "Child edited")) })
                model.close()
            }
            withTimeout(5000) { while (model.draft.value?.task?.id != parent.id) delay(30) }
            assertEquals("Parent edited", model.draft.value!!.task.title)
            assertEquals("Child edited", app.repository.details(child.id)!!.task.title)
            instrumentation.runOnMainSync { model.close() }
            assertNull(model.draft.value)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            app.repository.delete(parent.id)
        }
    }

    @Test
    fun rapidEditsFlushWithoutRevertingCompletionAndUntouchedDraftDoesNotRevertSnooze() =
        runBlocking {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val app = instrumentation.targetContext.applicationContext as PixApplication
            val task =
                TaskEntity(
                    title = "Original",
                    dueDay = LocalDate.now().toEpochDay(),
                    minuteOfDay = 600,
                )
            val notes = "## Live Markdown\n\n" + "- [ ] Keep every character\n".repeat(120)
            val store = ViewModelStore()
            lateinit var model: TasksViewModel
            app.repository.create(task)
            try {
                val detail = app.repository.details(task.id)!!
                instrumentation.runOnMainSync {
                    model = TasksViewModel(app, SavedStateHandle())
                    store.put("test", model)
                    model.open(detail)
                    model.edit(
                        model.draft.value!!.let { it.copy(task = it.task.copy(title = "Changed")) }
                    )
                    model.edit(
                        model.draft.value!!.let { it.copy(task = it.task.copy(notes = notes)) }
                    )
                    model.complete(task, true)
                    model.close()
                }
                withTimeout(5000) {
                    while (app.repository.details(task.id)?.task?.notes != notes) delay(30)
                }
                val updated = app.repository.details(task.id)!!.task
                assertEquals("Changed", updated.title)
                assertEquals(notes, updated.notes)
                assertTrue(updated.isCompleted)
                app.repository.complete(task.id, false)
                val fresh = app.repository.details(task.id)!!
                instrumentation.runOnMainSync { model.open(fresh) }
                app.repository.snooze(task.id)
                val postponed = app.repository.details(task.id)!!.task
                instrumentation.runOnMainSync {
                    model.flush()
                    model.close()
                }
                delay(600)
                assertEquals(
                    postponed.minuteOfDay,
                    app.repository.details(task.id)!!.task.minuteOfDay,
                )
                assertEquals(postponed.dueDay, app.repository.details(task.id)!!.task.dueDay)
            } finally {
                instrumentation.runOnMainSync { store.clear() }
                app.repository.delete(task.id)
            }
        }
}
