package com.example.pix.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

val LocalTextScale = androidx.compose.runtime.staticCompositionLocalOf { 1f }

val AccentBlue = Color(0xFF5275FF)
private val Light =
    lightColorScheme(
        primary = Color(0xFF3455DB),
        onPrimary = Color.White,
        primaryContainer = AccentBlue,
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF727782),
        secondaryContainer = Color(0xFFE8EDFF),
        onSecondaryContainer = Color(0xFF3455DB),
        background = Color(0xFFF4F5F8),
        surface = Color.White,
        onBackground = Color(0xFF181A20),
        onSurface = Color(0xFF181A20),
        onSurfaceVariant = Color(0xFF666B76),
        surfaceContainer = Color.White,
        surfaceContainerLow = Color(0xFFF4F5F8),
        surfaceContainerHigh = Color(0xFFEAECF1),
        surfaceContainerHighest = Color(0xFFE1E4EC),
        outline = Color(0xFF838895),
        outlineVariant = Color(0xFFE4E6EC),
        error = Color(0xFFC83C51),
    )
private val Dark =
    darkColorScheme(
        primary = Color(0xFF8199FF),
        onPrimary = Color.White,
        primaryContainer = AccentBlue,
        onPrimaryContainer = Color.White,
        secondary = Color(0xFF9A9AA3),
        secondaryContainer = Color(0xFF222B49),
        onSecondaryContainer = Color(0xFF91A5FF),
        background = Color(0xFF000000),
        surface = Color(0xFF1D1D1F),
        onBackground = Color(0xFFEDEDF0),
        onSurface = Color(0xFFEDEDF0),
        onSurfaceVariant = Color(0xFF98989F),
        surfaceContainer = Color(0xFF1D1D1F),
        surfaceContainerLow = Color(0xFF121214),
        surfaceContainerHigh = Color(0xFF262629),
        surfaceContainerHighest = Color(0xFF303035),
        outline = Color(0xFF787880),
        outlineVariant = Color(0xFF333337),
        error = Color(0xFFFF6D80),
    )

object Space {
    val small = 8.dp
    val screen = 16.dp
    val large = 24.dp
}

@Composable
fun PixTheme(
    mode: Int = 0,
    accent: Int = 0xFF5275FF.toInt(),
    textSize: Int = 1,
    fontStyle: Int = 0,
    content: @Composable () -> Unit,
) {
    val dark = mode == 2 || (mode == 0 && isSystemInDarkTheme())
    val chosen = Color(accent)
    val onChosen = if (chosen.luminance() > .45f) Color.Black else Color.White
    val base = if (dark) Dark else Light
    val foreground = if (dark) lerp(chosen, Color.White, .25f) else lerp(chosen, Color.Black, .22f)
    val scale = listOf(.88f, 1f, 1.15f)[textSize.coerceIn(0, 2)]
    CompositionLocalProvider(LocalTextScale provides scale) {
        MaterialTheme(
            colorScheme =
                base.copy(
                    primary = foreground,
                    primaryContainer = chosen,
                    onPrimaryContainer = onChosen,
                    onPrimary = onChosen,
                    secondaryContainer = lerp(base.background, chosen, .2f),
                    onSecondaryContainer = foreground,
                    inversePrimary = foreground,
                ),
            typography = appTypography(fontStyle, scale),
            shapes =
                Shapes(
                    small = RoundedCornerShape(12.dp),
                    medium = RoundedCornerShape(16.dp),
                    large = RoundedCornerShape(22.dp),
                    extraLarge = RoundedCornerShape(26.dp),
                ),
            content = content,
        )
    }
}
