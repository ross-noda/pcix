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
                        model.draft.value!!.let { it.copy(task = it.task.copy(notes = "Note")) }
                    )
                    model.complete(task, true)
                    model.close()
                }
                withTimeout(5000) {
                    while (app.repository.details(task.id)?.task?.notes != "Note") delay(30)
                }
                val updated = app.repository.details(task.id)!!.task
                assertEquals("Changed", updated.title)
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
