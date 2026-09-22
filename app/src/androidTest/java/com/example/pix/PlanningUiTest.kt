package com.example.pix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import com.example.pix.data.*
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PlanningUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun label(id: Int) = rule.activity.getString(id)

    private val repository
        get() = (rule.activity.application as PixApplication).repository

    private fun shot(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pix-$name.png")
            .use { java.io.FileInputStream(it.fileDescriptor).use { stream -> stream.readBytes() } }
    }

    @Test
    fun tomorrowQuickAddPrepopulatesDate() {
        val title = "Tomorrow UI ${System.nanoTime()}"
        rule.onAllNodesWithText(label(R.string.tomorrow)).onFirst().performClick()
        rule.onNodeWithContentDescription(label(R.string.add_task)).performClick()
        rule.onNodeWithTag("quick-add-title").performTextInput(title)
        rule.onNodeWithTag("quick-add-title").performImeAction()
        rule.waitUntil(5000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(title).assertIsDisplayed()
        shot("tomorrow")
        rule.onNodeWithText(title).performClick()
        rule.onNodeWithTag("detail-title").assertTextContains(title)
        val tasks = runBlocking { repository.day(LocalDate.now().plusDays(1).toEpochDay()).first() }
        assertTrue(tasks.any { it.task.title == title })
    }

    @Test
    fun matrixSupportsContextualAddRulesAndSavedLayout() {
        val titles =
            listOf(
                "Preparare esame",
                "Pianificare progetto",
                "Inviare documento",
                "Riordinare appunti",
            )
        runBlocking {
            titles.forEachIndexed { q, title ->
                repository.create(
                    TaskEntity(
                        title = title,
                        matrixUrgent = q == 0 || q == 2,
                        matrixImportant = q == 0 || q == 1,
                        dueDay = LocalDate.now().plusDays(q.toLong()).toEpochDay(),
                    )
                )
            }
        }
        rule.onNodeWithTag("navigation-matrix").performClick()
        rule.waitUntil(5000) {
            rule.onAllNodesWithText(titles[0]).fetchSemanticsNodes().isNotEmpty()
        }
        titles.forEach { rule.onNodeWithText(it).assertIsDisplayed() }
        shot("matrix")
        rule.onNodeWithTag("quadrant-add-1").performClick()
        rule.onNodeWithTag("quick-add-title").performTextInput("Studiare capitolo")
        rule.onNodeWithTag("quick-add-title").performImeAction()
        rule.waitUntil(5000) {
            rule.onAllNodesWithText("Studiare capitolo").fetchSemanticsNodes().isNotEmpty()
        }
        rule
            .onNode(hasText("Studiare capitolo") and hasAnyAncestor(hasTestTag("quadrant-1")))
            .assertIsDisplayed()
        rule.onNodeWithContentDescription(label(R.string.matrix_options)).performClick()
        rule.onNodeWithText(label(R.string.matrix_rows)).performClick()
        rule.onNodeWithText(label(R.string.save)).performScrollTo().performClick()
        assertEquals(1, rule.activity.getSharedPreferences("matrix", 0).getInt("layout", -1))
        shot("matrix-rows")
        rule.onNodeWithContentDescription(label(R.string.matrix_options)).performClick()
        rule.onNodeWithText(label(R.string.matrix_grid)).performClick()
        rule.onNodeWithText(label(R.string.save)).performScrollTo().performClick()
    }

    @Test
    fun weeklyHourCreatesTimedTaskAndWeekNavigationPreservesSelection() {
        rule.onNodeWithTag("navigation-calendar").performClick()
        rule.onNodeWithTag("calendar-week").performClick()
        rule.onNodeWithTag("week-agenda").performScrollToNode(hasTestTag("hour-9"))
        rule.onNodeWithTag("hour-9").performClick()
        val name = "Riunione alle nove"
        rule.onNodeWithTag("quick-add-title").performTextInput(name)
        rule.onNodeWithTag("quick-add-title").performImeAction()
        rule.waitUntil(5000) { runBlocking { repository.day(LocalDate.now().toEpochDay()).first().any{it.task.title==name} } }
        rule.onNodeWithTag("week-agenda").performScrollToNode(hasText(name))
        rule.onNodeWithText(name).assertIsDisplayed()
        shot("week")
        val rows = runBlocking { repository.day(LocalDate.now().toEpochDay()).first() }
        assertEquals(540, rows.single { it.task.title == name }.task.minuteOfDay)
        rule.onNodeWithTag("next-week").performClick()
        rule.onNodeWithText(name).assertDoesNotExist()
        rule.onNodeWithTag("previous-week").performClick()
        rule.waitUntil(5000) { runBlocking { repository.day(LocalDate.now().toEpochDay()).first().any{it.task.title==name} } }
        rule.onNodeWithTag("week-agenda").performScrollToNode(hasText(name))
        rule.onNodeWithTag("calendar-month").performClick()
    }
}
