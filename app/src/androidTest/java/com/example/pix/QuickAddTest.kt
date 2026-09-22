package com.example.pix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.pix.data.TaskFilter
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test

class QuickAddTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun quickAddPersistsAndCompletionCanBeUndone() {
        val name = "Quick add ${System.currentTimeMillis()}"
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.add_task)).performClick()
        rule.onNodeWithTag("quick-add-title").performTextInput(name)
        rule.onNodeWithTag("quick-add-title").performImeAction()
        rule.waitUntil(5000) {
            runBlocking {
                (rule.activity.application as PixApplication)
                    .repository
                    .observe(TaskFilter(mode = "ALL"), ZonedDateTime.now())
                    .first()
                    .any { it.task.title == name }
            }
        }
        rule.onNodeWithTag("task-list").performScrollToNode(hasText(name))
        rule.onNodeWithText(name).assertIsDisplayed()
        rule
            .onNodeWithContentDescription(
                rule.activity.getString(R.string.completed_description, name)
            )
            .performClick()
        rule.onNodeWithText(rule.activity.getString(R.string.undo)).performClick()
        rule.waitUntil(5000) {
            runBlocking {
                (rule.activity.application as PixApplication)
                    .repository
                    .observe(TaskFilter(mode = "ALL"), ZonedDateTime.now())
                    .first()
                    .any { it.task.title == name }
            }
        }
        rule.onNodeWithTag("task-list").performScrollToNode(hasText(name))
        rule.onNodeWithText(name).assertIsDisplayed()
    }
}
