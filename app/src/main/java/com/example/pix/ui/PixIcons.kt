package com.example.pix.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

enum class PixSymbol {
    TASKS,
    CALENDAR,
    LISTS,
    SETTINGS,
    MENU,
    SEARCH,
    MORE,
    PLUS,
    BACK,
    CLOSE,
    SEND,
    FLAG,
    TAG,
    INBOX,
    REPEAT,
    CHECK,
    CHEVRON,
    DELETE,
    COPY,
    CLOCK,
    SUBTASK,
    EXPAND,
    IMAGE,
}

@Composable
fun PixIcon(
    symbol: PixSymbol,
    description: String? = null,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Canvas(
        modifier
            .size(24.dp)
            .then(
                if (description != null) Modifier.semantics { contentDescription = description }
                else Modifier
            )
    ) {
        val unit = size.minDimension / 24f
        scale(unit, unit, pivot = Offset.Zero) {
            val stroke = Stroke(1.8f, cap = StrokeCap.Round)
            fun line(vararg points: Float) {
                val p = Path()
                p.moveTo(points[0], points[1])
                for (i in 2 until points.size step 2) p.lineTo(points[i], points[i + 1])
                drawPath(p, tint, style = stroke)
            }
            fun box(x: Float, y: Float, w: Float, h: Float, r: Float = 3f) {
                drawRoundRect(tint, Offset(x, y), Size(w, h), CornerRadius(r, r), style = stroke)
            }
            when (symbol) {
                PixSymbol.MENU -> {
                    line(3f, 6f, 21f, 6f)
                    line(3f, 12f, 21f, 12f)
                    line(3f, 18f, 21f, 18f)
                }
                PixSymbol.PLUS -> {
                    line(12f, 4f, 12f, 20f)
                    line(4f, 12f, 20f, 12f)
                }
                PixSymbol.CLOSE -> {
                    line(6f, 6f, 18f, 18f)
                    line(18f, 6f, 6f, 18f)
                }
                PixSymbol.BACK -> {
                    line(13f, 4f, 5f, 12f, 13f, 20f)
                    line(5f, 12f, 21f, 12f)
                }
                PixSymbol.SEND -> {
                    line(4f, 11f, 12f, 3f, 20f, 11f)
                    line(12f, 3f, 12f, 21f)
                }
                PixSymbol.CHEVRON -> line(9f, 6f, 15f, 12f, 9f, 18f)
                PixSymbol.MORE ->
                    listOf(5f, 12f, 19f).forEach { drawCircle(tint, 1.5f, Offset(12f, it)) }
                PixSymbol.SEARCH -> {
                    drawCircle(tint, 7f, Offset(10f, 10f), style = stroke)
                    line(15f, 15f, 21f, 21f)
                }
                PixSymbol.CHECK -> line(5f, 12f, 10f, 17f, 20f, 6f)
                PixSymbol.TASKS -> {
                    box(3f, 3f, 18f, 18f)
                    line(7f, 12f, 10f, 15f, 17f, 8f)
                }
                PixSymbol.CALENDAR -> {
                    box(3f, 5f, 18f, 16f)
                    line(3f, 10f, 21f, 10f)
                    line(7f, 3f, 7f, 7f)
                    line(17f, 3f, 17f, 7f)
                    drawCircle(tint, 1f, Offset(8f, 14f))
                    drawCircle(tint, 1f, Offset(15f, 14f))
                }
                PixSymbol.LISTS -> {
                    box(3f, 3f, 7f, 7f, 2f)
                    box(14f, 3f, 7f, 7f, 2f)
                    box(3f, 14f, 7f, 7f, 2f)
                    box(14f, 14f, 7f, 7f, 2f)
                }
                PixSymbol.SETTINGS -> {
                    line(12f, 2f, 21f, 7f, 21f, 17f, 12f, 22f, 3f, 17f, 3f, 7f, 12f, 2f)
                    drawCircle(tint, 3.5f, Offset(12f, 12f), style = stroke)
                }
                PixSymbol.FLAG -> {
                    line(5f, 22f, 5f, 3f, 20f, 3f, 16f, 8f, 20f, 13f, 5f, 13f)
                }
                PixSymbol.TAG -> {
                    line(3f, 3f, 12f, 3f, 22f, 13f, 13f, 22f, 3f, 12f, 3f, 3f)
                    drawCircle(tint, 1.4f, Offset(7.5f, 7.5f), style = stroke)
                }
                PixSymbol.INBOX -> {
                    box(3f, 4f, 18f, 17f)
                    line(3f, 13f, 8f, 13f, 9f, 16f, 15f, 16f, 16f, 13f, 21f, 13f)
                }
                PixSymbol.REPEAT -> {
                    line(3f, 10f, 3f, 6f, 20f, 6f, 16f, 2f)
                    line(21f, 14f, 21f, 18f, 4f, 18f, 8f, 22f)
                }
                PixSymbol.DELETE -> {
                    line(3f, 6f, 21f, 6f)
                    line(8f, 6f, 8f, 3f, 16f, 3f, 16f, 6f)
                    line(5f, 6f, 6f, 21f, 18f, 21f, 19f, 6f)
                    line(10f, 10f, 10f, 17f)
                    line(14f, 10f, 14f, 17f)
                }
                PixSymbol.COPY -> {
                    box(8f, 8f, 13f, 13f)
                    line(15f, 5f, 15f, 3f, 3f, 3f, 3f, 15f, 5f, 15f)
                }
                PixSymbol.CLOCK -> {
                    drawCircle(tint, 9f, Offset(12f, 12f), style = stroke)
                    line(12f, 6f, 12f, 12f, 16f, 14f)
                }
                PixSymbol.EXPAND -> {
                    line(3f, 9f, 3f, 3f, 9f, 3f)
                    line(15f, 21f, 21f, 21f, 21f, 15f)
                    line(3f, 3f, 9f, 9f)
                    line(15f, 15f, 21f, 21f)
                }
                PixSymbol.IMAGE -> {
                    box(3f, 3f, 18f, 18f)
                    drawCircle(tint, 2f, Offset(8f, 8f), style = stroke)
                    line(3f, 18f, 9f, 12f, 13f, 16f, 17f, 11f, 21f, 15f)
                }
                PixSymbol.SUBTASK -> {
                    box(3f, 4f, 5f, 5f, 1f)
                    box(3f, 15f, 5f, 5f, 1f)
                    line(12f, 6f, 21f, 6f)
                    line(12f, 17f, 21f, 17f)
                }
            }
        }
    }
}
