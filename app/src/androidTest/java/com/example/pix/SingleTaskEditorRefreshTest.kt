package com.example.pix

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.TaskEntity
import com.example.pix.domain.DescriptionText
import com.example.pix.ui.TasksViewModel
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class SingleTaskEditorRefreshTest {
    @Test fun persistedWidgetChecklistChangeReachesAnAlreadyOpenEditor() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PixApplication
        val task = TaskEntity(title = "Widget editor refresh", notes = "- [ ] First")
        val store = ViewModelStore()
        lateinit var model: TasksViewModel
        app.repository.create(task)
        try {
            val detail = app.repository.details(task.id)!!
            instrumentation.runOnMainSync {
                model = TasksViewModel(app, SavedStateHandle())
                store.put("widget", model)
                model.open(detail)
            }
            app.repository.setChecklistItem(task.id, DescriptionText.revision(task.notes), 0, true)
            withTimeout(5000) { while (model.draft.value?.task?.notes != "- [x] First") delay(25) }
            assertEquals("- [x] First", model.draft.value!!.task.notes)
        } finally {
            instrumentation.runOnMainSync { store.clear() }
            app.repository.delete(task.id)
        }
    }
}
