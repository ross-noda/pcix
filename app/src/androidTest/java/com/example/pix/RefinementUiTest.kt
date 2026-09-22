package com.example.pix

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextDecoration
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.*
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

class RefinementUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    private val repo
        get() = (rule.activity.application as PixApplication).repository

    private fun label(id: Int) = rule.activity.getString(id)

    private fun shot(name: String) {
        rule.mainClock.advanceTimeBy(500)
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pix-$name.png")
            .use { java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
    }

    private fun open(task: TaskEntity) {
        runBlocking { repo.create(task) }
        rule.onNodeWithTag("task-list").performScrollToNode(hasText(task.title))
        rule.onNodeWithText(task.title).performClick()
    }

    @Test
    fun editorHasConsistentToolsAndPersistsDuration() {
        val task =
            TaskEntity(
                title = "Intervallo di lavoro",
                dueDay = LocalDate.now().plusDays(10).toEpochDay(),
                minuteOfDay = 9 * 60,
                priority = 5,
            )
        open(task)
        rule.onNodeWithTag("editor-tool-REPEAT").assertIsDisplayed()
        rule.onNodeWithTag("editor-tool-IMAGE").assertIsDisplayed()
        rule.onNodeWithTag("editor-tool-LISTS").assertIsDisplayed()
        shot("refined-editor")
        rule.onNodeWithTag("edit-date-time").performClick()
        rule.onNodeWithTag("duration-enabled").performScrollTo().performClick()
        rule
            .onNodeWithText(rule.activity.getString(R.string.duration_minutes, 90))
            .performScrollTo()
            .performClick()
        shot("duration")
        rule.onNodeWithTag("editor-panel-done").performScrollTo().performClick()
        rule.waitUntil(5000) {
            rule.onAllNodesWithTag("editor-panel-done").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("editor-done").performClick()
        rule.waitUntil(5000) { runBlocking { repo.details(task.id)!!.task.durationMinutes == 90 } }
    }

    @Test
    fun completedSubtasksAreStruckThroughAndNewSubtaskIsFirst() {
        val task = TaskEntity(title = "Checklist ordinata")
        open(task)
        val active = SubtaskEntity(taskId = task.id, title = "Da preparare")
        val done = SubtaskEntity(taskId = task.id, title = "Già completata", isCompleted = true)
        runBlocking {
            repo.saveSubtask(active)
            repo.saveSubtask(done)
        }
        rule.onNodeWithTag("new-subtask").performScrollTo().performTextInput("Nuova in cima")
        rule.onNodeWithTag("new-subtask").performImeAction()
        rule.waitUntil(5000) { runBlocking { repo.details(task.id)!!.subtasks.size == 3 } }
        val new = runBlocking {
            repo.details(task.id)!!.subtasks.single { it.title == "Nuova in cima" }
        }
        val first = rule.onNodeWithTag("subtask-${new.id}").fetchSemanticsNode().boundsInRoot.top
        val second =
            rule.onNodeWithTag("subtask-${active.id}").fetchSemanticsNode().boundsInRoot.top
        val last = rule.onNodeWithTag("subtask-${done.id}")
        assertTrue(first < second)
        assertTrue(second < last.fetchSemanticsNode().boundsInRoot.top)
        last.performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        last.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(
            layouts.any { it.layoutInput.style.textDecoration == TextDecoration.LineThrough }
        )
        shot("completed-subtasks")
    }
}
