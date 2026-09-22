package com.example.pix

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.*
import org.junit.Assert.*

class TypographySettingsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    private fun label(id: Int) = rule.activity.getString(id)

    @After
    fun reset() {
        rule.activity
            .getSharedPreferences("appearance", 0)
            .edit()
            .putInt("fontStyle", 0)
            .putInt("textSize", 1)
            .commit()
    }

    private fun height(): Int {
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(label(R.string.font_preview)).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) {
            it(results)
        }
        return results.single().size.height
    }

    private fun shot(name: String) {
        rule.waitForIdle()
        InstrumentationRegistry.getInstrumentation()
            .uiAutomation
            .executeShellCommand("screencap -p /sdcard/Download/pix-$name.png")
            .use { java.io.FileInputStream(it.fileDescriptor).use { s -> s.readBytes() } }
    }

    @Test
    fun sizeAndFamilyChangeImmediatelyAndSurviveRecreation() {
        rule.onNodeWithTag("navigation-settings").performClick()
        rule.onNodeWithText(label(R.string.text_size)).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.font_small)).performClick()
        val small = height()
        rule.onNodeWithText(label(R.string.font_large)).performClick()
        assertTrue(height() > small)
        shot("font-size")
        rule.onNodeWithText(label(R.string.done)).performClick()
        rule.onNodeWithText(label(R.string.font_family)).performScrollTo().performClick()
        rule.onNodeWithText(label(R.string.font_serif)).performClick()
        val results = mutableListOf<TextLayoutResult>()
        rule.onNodeWithText(label(R.string.font_preview)).performSemanticsAction(
            SemanticsActions.GetTextLayoutResult
        ) {
            it(results)
        }
        assertEquals(FontFamily.Serif, results.single().layoutInput.style.fontFamily)
        shot("font-family")
        rule.onNodeWithText(label(R.string.font_mono)).performClick()
        rule.onNodeWithText(label(R.string.done)).performClick()
        rule.activityRule.scenario.recreate()
        rule.onNodeWithTag("navigation-settings").performClick()
        rule.onNodeWithText(label(R.string.font_family)).performScrollTo()
        rule.onNodeWithText(label(R.string.font_mono)).assertIsDisplayed()
        assertEquals(2, rule.activity.getSharedPreferences("appearance", 0).getInt("textSize", -1))
        rule.onNodeWithText(label(R.string.backup_export)).performScrollTo().assertIsDisplayed()
        shot("backup-settings")
    }
}
