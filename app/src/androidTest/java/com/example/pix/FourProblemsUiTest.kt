package com.example.pix

import android.content.res.Configuration
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.pix.data.*
import com.example.pix.domain.MatrixConfig
import com.example.pix.ui.*
import com.example.pix.ui.theme.PixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FourProblemsUiTest {
    @get:Rule val rule = createComposeRule()
    @Test fun matrixTitlesRemainCompleteAcrossWidthsThemesAndTextScaling() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val italian = Configuration(context.resources.configuration).apply { setLocale(Locale.ITALIAN) }
        var width by mutableIntStateOf(360)
        var height by mutableIntStateOf(640)
        var fontScale by mutableFloatStateOf(1f)
        var textSize by mutableIntStateOf(2)
        var mode by mutableIntStateOf(1)
        val tasks = (0..3).map { q ->
            TaskWithDetails(TaskEntity(id = "matrix-$q", title = "Task molto lunga con dettagli da leggere senza cambiare il layout",
                matrixUrgent = q % 2 == 0, matrixImportant = q < 2, dueDay = LocalDate.now().toEpochDay()),
                null, ListEntity(id = INBOX_ID, name = "Inbox"), emptyList(), emptyList())
        }
        rule.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalContext provides context.createConfigurationContext(italian),
                LocalConfiguration provides italian, LocalDensity provides Density(density, fontScale)) {
                PixTheme(mode = mode, textSize = textSize) {
                    Box(Modifier.statusBarsPadding()) {
                    Box(Modifier.requiredSize(width.dp, height.dp)) {
                        MatrixScreen(TaskContent(loading = false, tasks = tasks), LocalDate.now(), MatrixConfig(), {}, {}, {}, {})
                    }
                    }
                }
            }
        }
        for ((w,h,scale) in listOf(Triple(320,640,1f), Triple(360,640,1f), Triple(600,800,1f), Triple(840,360,1f), Triple(360,640,1.3f))) {
            for (theme in listOf(1,2)) {
                rule.runOnIdle { width = w; height = h; fontScale = scale; mode = theme; textSize = if(scale > 1f) 3 else 2 }
                rule.waitForIdle()
                rule.onNodeWithTag("matrix-hide-children").assertDoesNotExist()
                rule.onNodeWithTag("quadrant-add-0").assertDoesNotExist()
                val first = rule.onNodeWithTag("quadrant-0").fetchSemanticsNode().boundsInRoot
                val second = rule.onNodeWithTag("quadrant-1").fetchSemanticsNode().boundsInRoot
                assertEquals(first.top, second.top, 1f)
                assertTrue(first.right <= second.left)
                val third = rule.onNodeWithTag("quadrant-2").fetchSemanticsNode().boundsInRoot
                assertTrue(third.top >= first.bottom)
                repeat(4) { q ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    rule.onNodeWithTag("quadrant-title-$q").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                    assertTrue("width=$w font=$scale quadrant=$q", layouts.single().lineCount >= 1)
                    assertEquals(1, layouts.single().lineCount)
                }

            }
        }
    }

    @Test fun plainDescriptionNeverRendersMarkdownAndSupportsRepeatedEditing() {
        var source by mutableStateOf("# Title\n**Bold** and *italic*")
        var edits = 0
        rule.setContent { PixTheme(mode = 1) { MarkdownDescription("plain", source, { source = it; edits++ }) } }
        val field = rule.onNodeWithTag("detail-notes")
        field.assertTextContains(source)
        field.performTextInputSelection(TextRange(2, 7))
        rule.runOnIdle { assertEquals(0, edits) }
        field.performTextInput("Changed")
        rule.runOnIdle { assertTrue(source.startsWith("# Changed\n")) }
        field.performTextReplacement("Text 🌻\nSecond line")
        rule.onNodeWithTag("description-mode").performClick()
        rule.onNodeWithTag("description-item-1").performTextReplacement("Edited second line")
        rule.onNodeWithTag("description-check-1").performClick().assertIsOn()
        rule.onNodeWithTag("description-item-1").performTextInput(" again")
        rule.runOnIdle { assertTrue(source.contains("- [x]")); assertTrue(source.contains("again")) }
    }
}
