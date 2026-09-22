package com.example.pix

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PersonalizationUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun label(id: Int) = rule.activity.getString(id)

    private fun shot(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pix-$name.png")
            .use { java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
    }

    @Test
    fun blankQuickAddExpandsAndSavesWithoutLosingTitle() {
        val title = "Progetto creativo"
        rule.onNodeWithContentDescription(label(R.string.add_task)).performClick()
        rule.onNodeWithContentDescription(label(R.string.expand_editor)).performClick()
        rule.onNodeWithTag("detail-title").performTextInput(title)
        rule.onNodeWithTag("detail-notes").performTextInput("Idee e immagini del progetto")
        rule.onNodeWithText(label(R.string.done)).performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(title).assertIsDisplayed()
    }

    @Test
    fun longSubtaskWrapsAndImageAppearsInsideEditor() {
        val repo = (rule.activity.application as PixApplication).repository
        val title = "Note illustrate"
        val task = TaskEntity(title = title)
        val sub =
            SubtaskEntity(
                taskId = task.id,
                title =
                    "Una sotto-attività lunga con tutti i dettagli da leggere per intero senza dover scorrere orizzontalmente il testo mentre si consulta la propria lista delle cose da fare",
            )
        val file =
            java.io.File(rule.activity.filesDir, "task-images/ui-test.image").apply {
                parentFile!!.mkdirs()
            }
        val bitmap =
            android.graphics.Bitmap.createBitmap(480, 260, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(android.graphics.Color.rgb(230, 200, 214))
        val paint =
            android.graphics.Paint().apply { color = android.graphics.Color.rgb(190, 20, 100) }
        canvas.drawCircle(240f, 130f, 90f, paint)
        file.outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        runBlocking {
            repo.create(task)
            repo.saveSubtask(sub)
            repo.addImage(TaskImage(taskId = task.id, fileName = file.name))
        }
        rule.waitUntil(5000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(title).performClick()
        rule.waitUntil(5000) {
            rule
                .onAllNodesWithContentDescription(label(R.string.task_image))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        rule.onNodeWithContentDescription(label(R.string.task_image)).assertIsDisplayed()
        rule.onNodeWithTag("subtask-${sub.id}").performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        rule.onNodeWithTag("subtask-${sub.id}").performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) {
            it(layouts)
        }
        assertTrue(layouts.any { it.lineCount > 1 })
        shot("images-subtasks")
    }

    @Test
    fun colorAndStartupPreferencesPersist() {
        rule.onNodeWithTag("navigation-settings").performClick()
        rule.onNodeWithText(label(R.string.main_color)).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.color_preview)).assertIsDisplayed()
        shot("color-picker")
        rule.onNodeWithText(label(R.string.save)).performClick()
        rule.onNodeWithText(label(R.string.startup_view)).performScrollTo().performClick()
        rule.onAllNodesWithText(label(R.string.tomorrow)).onLast().performClick()
        assertEquals(
            "TOMORROW",
            rule.activity.getSharedPreferences("appearance", 0).getString("startupView", null),
        )
        rule.activity
            .getSharedPreferences("appearance", 0)
            .edit()
            .putString("startupView", "ALL")
            .commit()
    }
}
