package com.example.pix.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.drawText
import androidx.compose.ui.unit.dp
import com.example.pix.R
import com.example.pix.data.HabitLogEntity
import com.example.pix.domain.HabitChartData
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.text.NumberFormat

@Composable
fun HabitCharts(logs: List<HabitLogEntity>, today: Long, unit: String, accent: Color) {
    var periodName by rememberSaveable { mutableStateOf(HabitChartData.Period.DAY.name) }
    val period = HabitChartData.Period.valueOf(periodName)
    val date = LocalDate.ofEpochDay(today)
    val daily = remember(logs, today) { HabitChartData.bars(logs, date, HabitChartData.Period.DAY, 7) }
    val bars = remember(logs, today, period) { HabitChartData.bars(logs, date, period) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        HabitBarChart(stringResource(R.string.h_chart_daily), daily, HabitChartData.Period.DAY, unit, false, accent)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.h_chart_averages), style = MaterialTheme.typography.titleMedium)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HabitChartData.Period.entries.forEach { p ->
                        FilterChip(period == p, { periodName = p.name }, label = { Text(stringResource(when(p) {
                            HabitChartData.Period.DAY -> R.string.h_chart_day
                            HabitChartData.Period.WEEK -> R.string.h_week
                            HabitChartData.Period.MONTH -> R.string.h_month
                            HabitChartData.Period.YEAR -> R.string.h_year
                        }), maxLines = 1) }, colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = accent.copy(alpha = .18f), selectedLabelColor = MaterialTheme.colorScheme.onSurface))
                    }
                }
                ChartContent(bars, period, unit, true, accent)
                Text(stringResource(R.string.h_chart_mean_short), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun HabitBarChart(title: String, bars: List<HabitChartData.Bar>, period: HabitChartData.Period, unit: String, mean: Boolean, accent: Color) {
    Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        ChartContent(bars, period, unit, mean, accent)
    } }
}

@Composable
private fun ChartContent(bars: List<HabitChartData.Bar>, period: HabitChartData.Period, unit: String, showMean: Boolean, accent: Color) {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val number = remember(locale) { NumberFormat.getNumberInstance(locale).apply { maximumFractionDigits = 2 } }
    val mean = HabitChartData.mean(bars)
    if (showMean) Text(stringResource(when(period) {
        HabitChartData.Period.DAY -> R.string.h_mean_day
        HabitChartData.Period.WEEK -> R.string.h_mean_week
        HabitChartData.Period.MONTH -> R.string.h_mean_month
        HabitChartData.Period.YEAR -> R.string.h_mean_year
    }, number.format(mean), unit), style = MaterialTheme.typography.titleLarge)
    val rangeFormat = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }
    Text("${bars.first().start.format(rangeFormat)} – ${bars.last().end.format(rangeFormat)}", style = MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.h_chart_axis, unit), style = MaterialTheme.typography.labelMedium)
    val labels = bars.map { it.start.format(DateTimeFormatter.ofPattern(when(period) {
        HabitChartData.Period.DAY, HabitChartData.Period.WEEK -> "d MMM"
        HabitChartData.Period.MONTH -> "MMM yyyy"
        HabitChartData.Period.YEAR -> "yyyy"
    }, locale)) }
    val primary = accent
    val ink = MaterialTheme.colorScheme.onSurface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val axis = MaterialTheme.colorScheme.outline
    val style = MaterialTheme.typography.labelMedium.copy(color = ink)
    val measurer = rememberTextMeasurer()
    val description = bars.joinToString("; ") { "${it.start.format(rangeFormat)} – ${it.end.format(rangeFormat)}: ${number.format(it.total)} $unit" }
    // Each column has enough space for labels even with large accessibility fonts.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val fontScale = density.fontScale
    val step = HabitChartData.tickStep((bars.maxOfOrNull { it.total } ?: 0).toDouble())
    val tickLabels = (0..4).map { measurer.measure(number.format(step * it), style) }
    // Reserve only the actual label width, rather than a fixed empty gutter.
    val axisWidth = with(density) { tickLabels.maxOf { it.size.width }.toDp() } + 8.dp
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val chartWidth = maxOf(maxWidth - axisWidth, (bars.size * 80 * fontScale).dp)
        Row {
            // Keep the numeric axis visible while scrolling through dates.
            Canvas(Modifier.width(axisWidth).height((280 * fontScale).dp)) {
                val top = 30.dp.toPx() * fontScale
                val bottom = size.height - 42.dp.toPx() * fontScale
                for (i in 0..4) {
                    val y = bottom - (bottom - top) * i / 4
                    val label = tickLabels[i]
                    drawText(label, topLeft = Offset(size.width - label.size.width - 8.dp.toPx(), y - label.size.height / 2))
                }
                drawLine(axis, Offset(size.width - 1.dp.toPx(), top), Offset(size.width - 1.dp.toPx(), bottom), 2.dp.toPx())
            }
        Box(Modifier.weight(1f).horizontalScroll(rememberScrollState())) {
            Canvas(Modifier.width(chartWidth).height((280 * fontScale).dp).semantics { contentDescription = description }) {
                val left = 0f
                val top = 30.dp.toPx() * fontScale
                val bottom = size.height - 42.dp.toPx() * fontScale
                val right = size.width - 12.dp.toPx()
                val height = bottom - top
                val ceiling = step * 4
                fun y(v: Double) = bottom - (v / ceiling * height).toFloat()
                for (i in 0..4) {
                    val value = step * i
                    drawLine(grid, Offset(left, y(value)), Offset(right, y(value)), 1.dp.toPx())
                }
                drawLine(axis, Offset(left, bottom), Offset(right, bottom), 1.5.dp.toPx())
                val slot = (right - left) / bars.size
                bars.forEachIndexed { i, bar ->
                    val x = left + slot * (i + .5f)
                    val barY = y(bar.total.toDouble())
                    if (bar.total > 0) drawRect(primary, Offset(x - slot * .25f, barY), Size(slot * .5f, bottom - barY))
                    val value = measurer.measure(number.format(bar.total), style)
                    drawText(value, topLeft = Offset(x - value.size.width / 2, barY - value.size.height - 4.dp.toPx()))
                    val label = measurer.measure(labels[i], style)
                    drawText(label, topLeft = Offset(x - label.size.width / 2, bottom + 10.dp.toPx()))
                    drawLine(axis, Offset(x, bottom), Offset(x, bottom + 4.dp.toPx()), 1.dp.toPx())
                }
                if (showMean) drawLine(primary, Offset(left, y(mean)), Offset(right, y(mean)), 2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 6.dp.toPx())))
            }
        }
        }
    }
}
