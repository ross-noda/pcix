package com.example.pix.widget

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Test

class WidgetThemeTest {
    @Test fun backgroundModesOnlyChangeOpacity() {
        assertEquals(Color.Black, widgetBackdrop(0))
        assertEquals(Color.Black.copy(alpha = .55f), widgetBackdrop(1))
        assertEquals(0f, widgetBackdrop(2).alpha, .001f)
    }

    @Test fun unknownModeFallsBackToOpaqueBackground() {
        assertEquals(Color.Black, widgetBackdrop(-1))
        assertEquals(Color.Black, widgetBackdrop(3))
    }
}
