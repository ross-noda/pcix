@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.example.pix.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.pix.R

private val Inter =
    FontFamily(
        Font(
            R.font.inter,
            FontWeight.Normal,
            variationSettings = FontVariation.Settings(FontVariation.weight(400)),
        ),
        Font(
            R.font.inter,
            FontWeight.Medium,
            variationSettings = FontVariation.Settings(FontVariation.weight(500)),
        ),
        Font(
            R.font.inter,
            FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
    )
private val Manrope =
    FontFamily(
        Font(
            R.font.manrope,
            FontWeight.SemiBold,
            variationSettings = FontVariation.Settings(FontVariation.weight(600)),
        ),
        Font(
            R.font.manrope,
            FontWeight.Bold,
            variationSettings = FontVariation.Settings(FontVariation.weight(700)),
        ),
    )
val Typography =
    Typography(
        headlineSmall =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                lineHeight = 32.sp,
            ),
        headlineLarge =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.Bold,
                fontSize = 28.sp,
                lineHeight = 36.sp,
            ),
        titleLarge =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
        titleMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                lineHeight = 22.sp,
            ),
        titleSmall =
            TextStyle(
                fontFamily = Manrope,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            ),
        bodyLarge = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 17.sp),
        labelLarge =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                letterSpacing = 0.1.sp,
            ),
        labelMedium =
            TextStyle(
                fontFamily = Inter,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                lineHeight = 17.sp,
            ),
        labelSmall = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 17.sp),
    )

fun appTypography(style: Int, scale: Float = 1f): Typography {
    val family = when(style) { 1 -> FontFamily.Serif; 2 -> FontFamily.Monospace; else -> null }
    fun TextStyle.adapt() = copy(fontFamily = family ?: fontFamily, fontSize = fontSize * scale, lineHeight = lineHeight * scale)
    return Typography.copy(
        displayLarge = Typography.displayLarge.adapt(),
        displayMedium = Typography.displayMedium.adapt(),
        displaySmall = Typography.displaySmall.adapt(),
        headlineLarge = Typography.headlineLarge.adapt(),
        headlineMedium = Typography.headlineMedium.adapt(),
        headlineSmall = Typography.headlineSmall.adapt(),
        titleLarge = Typography.titleLarge.adapt(),
        titleMedium = Typography.titleMedium.adapt(),
        titleSmall = Typography.titleSmall.adapt(),
        bodyLarge = Typography.bodyLarge.adapt(),
        bodyMedium = Typography.bodyMedium.adapt(),
        bodySmall = Typography.bodySmall.adapt(),
        labelLarge = Typography.labelLarge.adapt(),
        labelMedium = Typography.labelMedium.adapt(),
        labelSmall = Typography.labelSmall.adapt(),
    )
}
