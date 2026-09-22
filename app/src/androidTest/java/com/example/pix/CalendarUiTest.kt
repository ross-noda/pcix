package com.example.pix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class CalendarUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun selectedDayPrepopulatesQuickAddAndSurvivesMonthNavigation() {
        val day = LocalDate.now().withDayOfMonth(15)
        val title = "Calendar ${System.nanoTime()}"
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.calendar)).performClick()
        rule.onNodeWithTag("calendar-month").performClick()
        rule.onNodeWithTag("day-${day.toEpochDay()}").performClick()
        rule.onNodeWithContentDescription(rule.activity.getString(R.string.add_task)).performClick()
        rule.onNodeWithTag("quick-add-title").performTextInput(title)
        rule.onNodeWithTag("quick-add-title").performImeAction()
        rule.waitUntil(5000) { rule.onAllNodesWithTag("quick-add-title").fetchSemanticsNodes().isEmpty() }
        rule.waitUntil(5000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        rule.onNodeWithTag("next-month").performScrollTo().performClick()
        rule.onNodeWithText(title).assertDoesNotExist()
        rule.onNodeWithTag("previous-month").performScrollTo().performClick()
        rule.waitUntil(5000) { rule.onAllNodesWithText(title).fetchSemanticsNodes().isNotEmpty() }
        rule.onNodeWithText(title).performScrollTo().assertIsDisplayed()
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pix-calendar.png")
            .use { descriptor ->
                java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() }
            }
    }
}
