package com.example.pix

import androidx.glance.GlanceId
import androidx.glance.action.actionParametersOf
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.TaskEntity
import com.example.pix.widget.CompleteTaskAction
import com.example.pix.widget.TaskIdKey
import com.example.pix.widget.TaskCompletedKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MatrixWidgetActionTest {
    @Test fun checkboxCompletesOnlyItsTaskAndRepeatedTapCannotReopenIt() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<PixApplication>()
        val first = TaskEntity(title = "Widget checkbox regression")
        val second = TaskEntity(title = "Other widget task")
        app.repository.create(first)
        app.repository.create(second)
        try {
            val action = CompleteTaskAction()
            val parameters = actionParametersOf(TaskIdKey to first.id, TaskCompletedKey to true)
            val id = object : GlanceId {}
            action.onAction(app, id, parameters)
            action.onAction(app, id, parameters)
            assertTrue(app.repository.details(first.id)!!.task.isCompleted)
            assertFalse(app.repository.details(second.id)!!.task.isCompleted)
        } finally {
            app.repository.delete(first.id)
            app.repository.delete(second.id)
        }
    }
}
