package com.example.pix.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import com.example.pix.PixApplication

val TaskIdKey = ActionParameters.Key<String>("task_id")
val TaskCompletedKey = ActionParameters.Key<Boolean>("task_completed")

class CompleteTaskAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val taskId = parameters[TaskIdKey] ?: return
        val completed = parameters[TaskCompletedKey] ?: true
        val app = context.applicationContext as PixApplication
        runCatching { app.repository.complete(taskId, completed) }
        updateAllPixWidgets(context)
    }
}
