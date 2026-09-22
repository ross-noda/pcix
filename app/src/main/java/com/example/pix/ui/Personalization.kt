package com.example.pix.ui

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.pix.R
import com.example.pix.data.*

val listIcons =
    listOf(
        "📋",
        "🏠",
        "💼",
        "📚",
        "🛒",
        "🌱",
        "✈️",
        "❤️",
        "⭐",
        "🎨",
        "💻",
        "🎯",
        "🧱",
        "📦",
        "🎵",
        "🐾",
        "🏃",
        "💡",
    )

@Composable
fun ListIconPicker(current: String, dismiss: () -> Unit, choose: (String) -> Unit) {
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.list_icon)) },
        text = {
            Column {
                listIcons.chunked(4).forEach { row ->
                    Row {
                        row.forEach { icon ->
                            IconButton(
                                onClick = { choose(icon) },
                                modifier =
                                    Modifier.size(48.dp)
                                        .background(
                                            if (current == icon)
                                                MaterialTheme.colorScheme.primary.copy(alpha = .16f)
                                            else Color.Transparent,
                                            CircleShape,
                                        ),
                            ) {
                                Text(icon, style = MaterialTheme.typography.titleLarge)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun PersonalizationSettings(model: TasksViewModel) {
    val textSize by model.textSize.collectAsStateWithLifecycle()
    val fontStyle by model.fontStyle.collectAsStateWithLifecycle()
    val sizes = listOf(R.string.font_small, R.string.font_standard, R.string.font_large)
    val fonts = listOf(R.string.font_standard, R.string.font_serif, R.string.font_mono)
    val startup by model.startupView.collectAsStateWithLifecycle()
    val dimmed by model.dimmedLists.collectAsStateWithLifecycle()
    val lists by model.lists.collectAsStateWithLifecycle()
    val accent by model.accent.collectAsStateWithLifecycle()
    var panel by remember { mutableStateOf<String?>(null) }
    val modes =
        listOf(
            "ALL" to R.string.all,
            "TODAY" to R.string.today,
            "TOMORROW" to R.string.tomorrow,
            "WEEK" to R.string.week,
            "OVERDUE" to R.string.overdue,
        )
    Surface(shape = MaterialTheme.shapes.large) {
        Column {
            ListItem(
                headlineContent = { Text(stringResource(R.string.text_size)) },
                supportingContent = { Text(stringResource(sizes[textSize])) },
                trailingContent = { PixIcon(PixSymbol.CHEVRON) },
                modifier = Modifier.clickable { panel = "size" },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.font_family)) },
                supportingContent = { Text(stringResource(fonts[fontStyle])) },
                trailingContent = { PixIcon(PixSymbol.CHEVRON) },
                modifier = Modifier.clickable { panel = "font" },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.main_color)) },
                leadingContent = {
                    Box(Modifier.size(24.dp).background(Color(accent), CircleShape))
                },
                trailingContent = { PixIcon(PixSymbol.CHEVRON) },
                modifier = Modifier.clickable { panel = "color" },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.startup_view)) },
                supportingContent = {
                    Text(
                        stringResource(
                            modes.firstOrNull { it.first == startup }?.second ?: R.string.all
                        )
                    )
                },
                trailingContent = { PixIcon(PixSymbol.CHEVRON) },
                modifier = Modifier.clickable { panel = "startup" },
            )
            ListItem(
                headlineContent = { Text(stringResource(R.string.dim_lists)) },
                supportingContent = { Text(stringResource(R.string.dim_lists_hint)) },
                trailingContent = { PixIcon(PixSymbol.CHEVRON) },
                modifier = Modifier.clickable { panel = "dim" },
            )
        }
    }
    if (panel == "size" || panel == "font") {
        val sizePanel = panel == "size"
        AlertDialog(
            onDismissRequest = { panel = null },
            title = {
                Text(stringResource(if (sizePanel) R.string.text_size else R.string.font_family))
            },
            text = {
                Column {
                    (if (sizePanel) sizes else fonts).forEachIndexed { index, label ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                if (sizePanel) model.setTextSize(index)
                                else model.setFontStyle(index)
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                (if (sizePanel) textSize else fontStyle) == index,
                                onClick = {
                                    if (sizePanel) model.setTextSize(index)
                                    else model.setFontStyle(index)
                                },
                            )
                            Text(stringResource(label))
                        }
                    }
                    Text(
                        stringResource(R.string.font_preview),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { panel = null }) { Text(stringResource(R.string.done)) }
            },
        )
    }
    if (panel == "color")
        AccentPicker(accent, { panel = null }) {
            model.setAccent(it)
            panel = null
        }
    if (panel == "startup")
        AlertDialog(
            onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.startup_view)) },
            text = {
                Column {
                    modes.forEach { (id, label) ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                model.setStartupView(id)
                                panel = null
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                startup == id,
                                {
                                    model.setStartupView(id)
                                    panel = null
                                },
                            )
                            Text(stringResource(label))
                        }
                    }
                }
            },
            confirmButton = {},
        )
    if (panel == "dim")
        AlertDialog(
            onDismissRequest = { panel = null },
            title = { Text(stringResource(R.string.dim_lists)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.dim_lists_hint))
                    lists.forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                row.list.id in dimmed,
                                { selected ->
                                    model.setDimmedLists(
                                        if (selected) dimmed + row.list.id else dimmed - row.list.id
                                    )
                                },
                            )
                            Text(row.list.icon + " " + row.list.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { panel = null }) { Text(stringResource(R.string.done)) }
            },
        )
}

@Composable
fun AccentPicker(initial: Int, dismiss: () -> Unit, choose: (Int) -> Unit) {
    val initialHsv =
        remember(initial) { FloatArray(3).also { AndroidColor.colorToHSV(initial, it) } }
    var hue by remember { mutableFloatStateOf(initialHsv[0]) }
    var saturation by remember { mutableFloatStateOf(initialHsv[1]) }
    var value by remember { mutableFloatStateOf(initialHsv[2]) }
    val color = Color.hsv(hue, saturation, value)
    val foreground = if (color.luminance() > .45f) Color.Black else Color.White
    val update by
        rememberUpdatedState<(Offset, Float, Float) -> Unit>({ p, w, h ->
            saturation = (p.x / w).coerceIn(0f, 1f)
            value = (1 - p.y / h).coerceIn(0f, 1f)
        })
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text(stringResource(R.string.main_color)) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    color = color,
                    contentColor = foreground,
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.color_preview),
                        Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Canvas(
                    Modifier.fillMaxWidth()
                        .height(150.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                update(it, size.width.toFloat(), size.height.toFloat())
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures { change, _ ->
                                change.consume()
                                update(change.position, size.width.toFloat(), size.height.toFloat())
                            }
                        }
                ) {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(hue, 1f, 1f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    drawCircle(
                        Color.White,
                        8.dp.toPx(),
                        Offset(saturation * size.width, (1 - value) * size.height),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()),
                    )
                }
                Text(stringResource(R.string.hue))
                Slider(hue, { hue = it }, valueRange = 0f..359f)
                Text(stringResource(R.string.saturation))
                Slider(saturation, { saturation = it })
                Text(stringResource(R.string.brightness))
                Slider(value, { value = it })
                Text(
                    "#%06X".format(java.util.Locale.ROOT, color.toArgb() and 0xFFFFFF),
                    style = MaterialTheme.typography.labelLarge,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0xFF5275FF, 0xFFC70070, 0xFF00A980, 0xFFFFA126, 0xFF9B63EF).forEach {
                        argb ->
                        Box(
                            Modifier.size(36.dp).background(Color(argb), CircleShape).clickable {
                                val hsv = FloatArray(3)
                                AndroidColor.colorToHSV(argb.toInt(), hsv)
                                hue = hsv[0]
                                saturation = hsv[1]
                                value = hsv[2]
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { choose(color.toArgb()) }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
