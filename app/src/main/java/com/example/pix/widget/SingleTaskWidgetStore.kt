package com.example.pix.widget

import android.content.Context
import com.example.pix.PixApplication
import com.example.pix.cloud.AccountSessionState

internal data class SingleTaskBinding(val taskId: String, val owner: String)
internal class SingleTaskWidgetStore(context: Context) {
    private val prefs = context.getSharedPreferences("single_task_widgets", Context.MODE_PRIVATE)
    fun read(id: Int): SingleTaskBinding? {
        val task = prefs.getString("$id.task", null) ?: return null
        val owner = prefs.getString("$id.owner", null) ?: return null
        return SingleTaskBinding(task, owner)
    }
    fun write(id: Int, binding: SingleTaskBinding) {
        check(prefs.edit().putString("$id.task", binding.taskId).putString("$id.owner", binding.owner).commit())
    }
    fun delete(id: Int) { prefs.edit().remove("$id.task").remove("$id.owner").apply() }
}
internal fun singleTaskAccountMatches(app: PixApplication, owner: String): Boolean =
    app.accounts.owner().orEmpty() == owner && (!app.cloud.configured ||
        (app.session.state.value as? AccountSessionState.Ready)?.user?.id == owner)
