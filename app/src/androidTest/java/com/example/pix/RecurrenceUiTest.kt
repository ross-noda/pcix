package com.example.pix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.TaskFilter
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class RecurrenceUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun shot(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pix-$name.png")
            .use { java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
    }

    @Test
    fun createDailyTaskAndCompleteGeneratesNextOccurrence() {
        val title = "Annaffiare le piante"
        fun label(id: Int) = rule.activity.getString(id)
        rule.onNodeWithContentDescription(label(R.string.add_task)).performClick()
        rule.onNodeWithTag("quick-add-title").performTextInput(title)
        shot("quick")
        rule.onNodeWithTag("quick-add-title").performImeAction()
        rule.waitUntil(5000) {
            runBlocking {
                (rule.activity.application as PixApplication)
                    .repository
                    .observe(TaskFilter(mode = "ALL"), ZonedDateTime.now())
                    .first()
                    .any { it.task.title == title }
            }
        }
        rule.onNodeWithTag("task-list").performScrollToNode(hasText(title))
        rule.onNodeWithText(title).performClick()
        rule.onNodeWithContentDescription(label(R.string.recurrence)).performClick()
        rule.onNodeWithText(label(R.string.daily)).performClick()
        rule.waitUntil(5000) {
            rule.onAllNodesWithText(label(R.string.daily)).fetchSemanticsNodes().isNotEmpty()
        }
        shot("editor")
        rule.onNodeWithText(label(R.string.done)).performClick()
        rule.waitUntil(5000) {
            rule.onAllNodesWithTag("detail-title").fetchSemanticsNodes().isEmpty()
        }
        rule.onNodeWithTag("task-list").performScrollToNode(hasText(title))
        rule
            .onNodeWithContentDescription(
                rule.activity.getString(R.string.completed_description, title)
            )
            .performClick()
        rule.waitUntil(5000) {
            runBlocking {
                (rule.activity.application as PixApplication)
                    .repository
                    .observe(TaskFilter(mode = "ALL"), ZonedDateTime.now())
                    .first()
                    .any { it.task.title == title }
            }
        }
        rule.onNodeWithTag("task-list").performScrollToNode(hasText(title))
        rule.onNodeWithText(title).assertIsDisplayed()
        shot("home")
        rule.onNodeWithContentDescription(label(R.string.pix_navigation_menu)).performClick()
        shot("drawer")
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithTag("navigation-settings").performClick()
        shot("settings")
        rule.onNodeWithText(label(R.string.appearance)).performClick()
        rule.onNodeWithText(label(R.string.light)).performClick()
        shot("settings-light")
        rule.onNodeWithText(label(R.string.appearance)).performClick()
        rule.onNodeWithText(label(R.string.dark)).performClick()
    }
}
