package com.example.pix

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.example.pix.domain.*
import com.example.pix.ui.*
import com.example.pix.ui.theme.PixTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class MatrixCardEditorTest {
    @get:Rule val rule = createComposeRule()
    @Test fun renameSaveAndReorderKeepIdentityAndAutomaticMode() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var saved = MatrixConfig()
        rule.setContent { PixTheme { MatrixCardEditor(MatrixConfig(), emptyList(), emptyList(), { saved = it }, {}) } }
        rule.onNodeWithTag("matrix-edit-card-0").performClick()
        rule.onNodeWithTag("matrix-card-title").performTextReplacement("FALLO SUBITO")
        rule.onNodeWithTag("matrix-card-save").performClick()
        rule.runOnIdle { assertEquals("FALLO SUBITO", saved.cards[0].title); assertFalse(saved.cards[0].custom) }
        rule.onNodeWithTag("reorder-0").performClick()
        rule.onNodeWithText(context.getString(R.string.move_down)).performClick()
        rule.runOnIdle { assertEquals(listOf(1,0,2,3), saved.cardOrder); assertEquals("FALLO SUBITO", saved.cards[0].title) }
        rule.onNodeWithTag("matrix-edit-card-0").performClick()
        rule.onNodeWithTag("matrix-card-custom").performClick()
        rule.onNodeWithTag("matrix-card-save").performClick()
        rule.runOnIdle { assertTrue(saved.cards[0].custom) }
    }
}
