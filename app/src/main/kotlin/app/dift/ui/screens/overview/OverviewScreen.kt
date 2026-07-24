package app.dift.ui.screens.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.domain.engine.NightTimeline
import app.dift.domain.engine.NightTimeline.NightColumn
import app.dift.domain.engine.UsageAverages
import app.dift.ui.format.formatDuration
import app.dift.ui.format.formatMinuteOfDay
import java.time.format.DateTimeFormatter

private val rangeFormat = DateTimeFormatter.ofPattern("MMM d")
private const val DAYS = 7
private const val CHART_HEIGHT_DP = 340
private const val AXIS_WIDTH_DP = 28
private const val CHART_START_PAD_DP = 8
private const val COLUMN_GAP_FRACTION = 0.16f
private const val MIN_SPAN_DP = 2f
private const val CORNER_PX = 5f
private const val MINUTE_MS = 60_000L

// Night-window hues, per mode (dark mode is its own step, not a flip). Both validated against
// the usage marks for CVD separation and surface contrast; the bright dark-mode teal deliberately
// sits above the mark-lightness band — it is a background field, not a mark, and the wide field
// vs. thin solid marks is the secondary (shape) encoding.
private val NightTealDark = Color(0xFF2DD4BF)
private val NightTealLight = Color(0xFF0D9488)
private const val NIGHT_ALPHA = 0.55f
private const val NIGHT_ALPHA_SELECTED = 0.85f

// "Not bad" usage marks: the app accent, nudged darker so the marks read as "spent" against the
// bright night field (still ≥3:1 on both surfaces).
private const val USAGE_DARKEN = 0.15f

// Everything-else usage marks: a plum-leaning dark violet. Pure violets are CVD-indistinguishable
// from the darkened accent (protan/deutan ΔE ≈ 3–5); these steps pass all palette checks against
// it (#C73E9E: ΔE 9.8 protan / 24 normal; #86198F: ΔE 12 deutan / 20 normal on light).
private val OtherVioletDark = Color(0xFFC73E9E)
private val OtherVioletLight = Color(0xFF86198F)

// Daily totals: y-scale never drops below 5 h so bar heights stay comparable across weeks.
private const val TOTALS_HEIGHT_DP = 420
private const val TOTALS_LABEL_SPACE_DP = 20
private const val TOTALS_SCALE_FLOOR_MS = 5 * 3_600_000L
private const val STACK_GAP_DP = 2f

@Composable
fun OverviewScreen(viewModel: OverviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    val notBadColor = lerp(MaterialTheme.colorScheme.primary, Color.Black, USAGE_DARKEN)
    val otherColor = if (isSystemInDarkTheme()) OtherVioletDark else OtherVioletLight

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        WeekHeader(state, viewModel::prevWeek, viewModel::nextWeek)
        ModeToggle(state.mode, viewModel::setMode)
        when (state.mode) {
            OverviewViewModel.Mode.NIGHT -> NightChart(state.nights, notBadColor, otherColor)
            OverviewViewModel.Mode.TOTALS ->
                TotalsChart(state.totals, state.averages, notBadColor, otherColor)
        }
    }
}

@Composable
private fun WeekHeader(state: OverviewViewModel.UiState, onPrev: () -> Unit, onNext: () -> Unit) {
    val range = stringResource(
        R.string.overview_week_range,
        state.weekMonday.format(rangeFormat),
        state.weekMonday.plusDays(DAYS - 1L).format(rangeFormat),
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev, enabled = state.canGoPrev) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.overview_prev_week),
            )
        }
        Text(text = range, style = MaterialTheme.typography.titleMedium)
        IconButton(onClick = onNext, enabled = state.canGoNext) {
            Icon(
                imageVector = Icons.Filled.KeyboardArrowRight,
                contentDescription = stringResource(R.string.overview_next_week),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModeToggle(mode: OverviewViewModel.Mode, onSelect: (OverviewViewModel.Mode) -> Unit) {
    val options = listOf(
        OverviewViewModel.Mode.NIGHT to R.string.overview_mode_night,
        OverviewViewModel.Mode.TOTALS to R.string.overview_mode_totals,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (value, labelRes) ->
            SegmentedButton(
                selected = mode == value,
                onClick = { onSelect(value) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

@Composable
private fun NightChart(nights: List<NightColumn>, notBadColor: Color, otherColor: Color) {
    val dayLabels = stringResource(R.string.overview_day_labels).split(",")
    var selected by remember(nights) { mutableIntStateOf(-1) }
    val nightColor = if (isSystemInDarkTheme()) NightTealDark else NightTealLight
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Column {
        // Evening day of each night — the column reads "Su ↓ Mo": dusk on top, morning below.
        DayLabelRow(
            labels = List(DAYS) { dayLabels[(it + DAYS - 1) % DAYS] },
            dimmed = true,
            startPadding = (AXIS_WIDTH_DP + CHART_START_PAD_DP).dp,
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .width(AXIS_WIDTH_DP.dp)
                    .height(CHART_HEIGHT_DP.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End,
            ) {
                listOf(12, 18, 0, 6, 12).forEach { hour ->
                    Text(hour.toString().padStart(2, '0'), style = MaterialTheme.typography.labelSmall)
                }
            }
            Canvas(
                modifier = Modifier
                    .weight(1f)
                    .height(CHART_HEIGHT_DP.dp)
                    .padding(start = CHART_START_PAD_DP.dp)
                    .pointerInput(nights) {
                        detectTapGestures { offset ->
                            val hit = sleepHit(nights, offset, size.width, size.height)
                            selected = if (hit == selected) -1 else hit
                        }
                    },
            ) {
                drawNight(nights, selected, notBadColor, otherColor, nightColor, lineColor)
            }
        }
        DayLabelRow(
            labels = dayLabels.take(DAYS),
            dimmed = false,
            startPadding = (AXIS_WIDTH_DP + CHART_START_PAD_DP).dp,
        )
        ChartLegend(notBadColor, otherColor)
        nights.getOrNull(selected)?.let { night ->
            night.sleep?.let { sleep ->
                NightDetails(
                    sleep = sleep,
                    eveningLabel = dayLabels[(selected + DAYS - 1) % DAYS],
                    morningLabel = dayLabels[selected],
                )
            }
        }
    }
}

@Composable
private fun DayLabelRow(labels: List<String>, dimmed: Boolean, startPadding: Dp = 0.dp) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dimmed) 0.5f else 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = startPadding, top = 4.dp, bottom = 4.dp),
    ) {
        labels.forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChartLegend(notBadColor: Color, otherColor: Color) {
    Row(
        modifier = Modifier.padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LegendEntry(notBadColor, stringResource(R.string.overview_legend_not_bad))
        LegendEntry(otherColor, stringResource(R.string.overview_legend_other))
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, CircleShape),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/** Which column's sleep span was tapped, or -1. Coordinates are in the canvas space. */
private fun sleepHit(nights: List<NightColumn>, offset: Offset, width: Int, height: Int): Int {
    if (nights.isEmpty() || width <= 0 || height <= 0) return -1
    val col = (offset.x / (width / DAYS.toFloat())).toInt().coerceIn(0, DAYS - 1)
    val minute = (offset.y / height * NightTimeline.MINUTES_PER_DAY).toInt()
    val sleep = nights.getOrNull(col)?.sleep ?: return -1
    return if (minute in sleep.startMinute..sleep.endMinute) col else -1
}

@Composable
private fun NightDetails(sleep: NightTimeline.Span, eveningLabel: String, morningLabel: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.overview_night_title, eveningLabel, morningLabel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Text(
            text = formatDuration((sleep.endMinute - sleep.startMinute) * MINUTE_MS),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = stringResource(
                R.string.overview_night_range,
                clockLabel(sleep.startMinute),
                clockLabel(sleep.endMinute),
            ),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Column minutes run from noon; convert back to a wall-clock "HH:mm". */
private fun clockLabel(minuteFromNoon: Int): String {
    val half = NightTimeline.MINUTES_PER_DAY / 2
    return formatMinuteOfDay((minuteFromNoon + half) % NightTimeline.MINUTES_PER_DAY)
}

private fun DrawScope.drawNight(
    nights: List<NightColumn>,
    selectedIndex: Int,
    notBadColor: Color,
    otherColor: Color,
    nightColor: Color,
    lineColor: Color,
) {
    if (nights.isEmpty()) return
    val columnWidth = size.width / DAYS
    val gap = columnWidth * COLUMN_GAP_FRACTION
    val minSpan = MIN_SPAN_DP.dp.toPx()
    val corner = CornerRadius(CORNER_PX, CORNER_PX)
    val total = NightTimeline.MINUTES_PER_DAY.toFloat()

    fun y(minute: Int) = minute / total * size.height

    nights.forEachIndexed { i, column ->
        val left = i * columnWidth + gap
        val barWidth = columnWidth - gap * 2
        column.sleep?.let { s ->
            val alpha = if (i == selectedIndex) NIGHT_ALPHA_SELECTED else NIGHT_ALPHA
            drawRoundRect(
                color = nightColor.copy(alpha = alpha),
                topLeft = Offset(left, y(s.startMinute)),
                size = Size(barWidth, y(s.endMinute) - y(s.startMinute)),
                cornerRadius = corner,
            )
        }
        // Violet ("other") first so overlapping "not bad" use stays visible on top.
        column.usage.sortedBy { it.notBad }.forEach { span ->
            val top = y(span.startMinute)
            val height = (y(span.endMinute) - top).coerceAtLeast(minSpan)
            val color = if (span.notBad) notBadColor else otherColor
            drawRoundRect(color, Offset(left, top), Size(barWidth, height), corner)
        }
    }

    val midY = size.height / 2
    drawLine(
        color = lineColor,
        start = Offset(0f, midY),
        end = Offset(size.width, midY),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f)),
    )
}

@Composable
private fun TotalsChart(
    totals: List<OverviewViewModel.DayBar>,
    averages: UsageAverages.Result?,
    notBadColor: Color,
    otherColor: Color,
) {
    val dayLabels = stringResource(R.string.overview_day_labels).split(",")
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurface)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
    // formatDuration is composable (strings.xml), so bar value labels are prepared up front.
    val valueLabels = totals.map { formatDuration(it.totalMs) }

    Column {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOTALS_HEIGHT_DP.dp),
        ) {
            drawTotals(
                bars = totals,
                valueLabels = valueLabels,
                avgMs = averages?.avgMsPerDay,
                notBadColor = notBadColor,
                otherColor = otherColor,
                lineColor = lineColor,
                textMeasurer = textMeasurer,
                labelStyle = labelStyle,
            )
        }
        DayLabelRow(labels = dayLabels.take(DAYS), dimmed = false)
        averages?.let { AverageSummary(it) }
        ChartLegend(notBadColor, otherColor)
    }
}

@Composable
private fun AverageSummary(averages: UsageAverages.Result) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.overview_avg_per_day, formatDuration(averages.avgMsPerDay)),
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            averages.vsPrevWeekPct?.let {
                Text(
                    text = stringResource(
                        R.string.overview_vs_last_week,
                        stringResource(R.string.overview_percent, it),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
            averages.vsPrevMonthPct?.let {
                Text(
                    text = stringResource(
                        R.string.overview_vs_last_month,
                        stringResource(R.string.overview_percent, it),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                )
            }
        }
    }
}

@Suppress("LongParameterList") // a draw routine fed straight from composition state
private fun DrawScope.drawTotals(
    bars: List<OverviewViewModel.DayBar>,
    valueLabels: List<String>,
    avgMs: Long?,
    notBadColor: Color,
    otherColor: Color,
    lineColor: Color,
    textMeasurer: TextMeasurer,
    labelStyle: TextStyle,
) {
    if (bars.isEmpty()) return
    val scaleMax = maxOf(TOTALS_SCALE_FLOOR_MS, bars.maxOf { it.totalMs }).toFloat()
    val columnWidth = size.width / bars.size
    val gap = columnWidth * COLUMN_GAP_FRACTION
    val corner = CornerRadius(CORNER_PX, CORNER_PX)
    val minSpan = MIN_SPAN_DP.dp.toPx()
    val stackGap = STACK_GAP_DP.dp.toPx()
    val barArea = size.height - TOTALS_LABEL_SPACE_DP.dp.toPx()

    fun heightOf(ms: Long): Float =
        if (ms <= 0L) 0f else (ms / scaleMax * barArea).coerceAtLeast(minSpan)

    bars.forEachIndexed { i, bar ->
        val left = i * columnWidth + gap
        val barWidth = columnWidth - gap * 2
        val notBadHeight = heightOf(bar.notBadMs)
        val otherHeight = heightOf(bar.otherMs)
        val bottom = size.height
        // "Not bad" (blue) sits on the baseline; "other" (violet) stacks above a 2px surface gap.
        if (notBadHeight > 0f) {
            drawRoundRect(notBadColor, Offset(left, bottom - notBadHeight), Size(barWidth, notBadHeight), corner)
        }
        if (otherHeight > 0f) {
            val below = if (notBadHeight > 0f) notBadHeight + stackGap else 0f
            drawRoundRect(
                otherColor,
                Offset(left, bottom - below - otherHeight),
                Size(barWidth, otherHeight),
                cornerRadius = corner,
            )
        }
        if (bar.totalMs > 0L) {
            val stackTop = bottom - notBadHeight - otherHeight -
                (if (notBadHeight > 0f && otherHeight > 0f) stackGap else 0f)
            val measured = textMeasurer.measure(AnnotatedString(valueLabels[i]), labelStyle)
            drawText(
                textLayoutResult = measured,
                topLeft = Offset(
                    x = left + barWidth / 2 - measured.size.width / 2,
                    y = (stackTop - measured.size.height - 2.dp.toPx()).coerceAtLeast(0f),
                ),
            )
        }
    }

    avgMs?.let {
        val y = size.height - (it / scaleMax * barArea)
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f)),
        )
    }
}
