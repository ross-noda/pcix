package com.example.pix

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.pix.ui.priorityColor
import com.example.pix.ui.theme.*
import org.junit.*
import org.junit.Assert.*

class PriorityThemeTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun lowPriorityStaysBlueWithPinkAccent() {
        var actual = Color.Unspecified
        rule.setContent { PixTheme(accent = 0xFFC70070.toInt()) { actual = priorityColor(1) } }
        rule.runOnIdle { assertEquals(AccentBlue, actual) }
    }
}
