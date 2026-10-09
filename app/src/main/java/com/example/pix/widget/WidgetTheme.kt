package com.example.pix.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.glance.unit.ColorProvider

internal data class WidgetPalette(
    val surface: Color,
    val primaryText: Color,
    val secondaryText: Color,
    val divider: Color,
    val error: Color,
    val accent: Color,
    val onAccent: Color,
    val textScale: Float,
    val backdrop: Color = surface,
) {
    companion object {
        fun forContext(context: Context): WidgetPalette {
            val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
            val textScale = listOf(.88f, 1f, 1.15f)[prefs.getInt("textSize", 1).coerceIn(0, 2)]
            val accent = Color(prefs.getInt("accent", TaskWidgetDataSource.NEUTRAL))
            val onAccent = if (accent.luminance() > .52f) Color(0xFF181A20) else Color.White
            return WidgetPalette(
                    surface = Color(0xFF1D1D1F),
                    primaryText = Color(0xFFEDEDF0),
                    secondaryText = Color(0xFF98989F),
                    divider = Color(0xFF34343A),
                    error = Color(0xFFFF6D80),
                    accent = accent,
                    onAccent = onAccent,
                    textScale = textScale,
                )

        }
    }
}

internal fun fixedColorProvider(color: Color): ColorProvider =
    androidx.glance.color.ColorProvider(day = color, night = color)

internal fun widgetBackdrop(mode: Int): Color = Color.Black.copy(
    alpha = when (mode) { 1 -> .55f; 2 -> 0f; else -> 1f }
)
