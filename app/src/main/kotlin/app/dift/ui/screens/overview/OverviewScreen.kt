package app.dift.ui.screens.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.domain.engine.NightTimeline
import app.dift.domain.engine.NightTimeline.NightColumn
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

// Sleep-highlight hue, validated against the indigo accent on both surfaces
// (CVD ΔE 75, in-band lightness, ≥3:1 contrast). The wide translucent band vs. thin
// solid usage marks is the secondary (shape) encoding.
private val SleepTeal = Color(0xFF16A190)
private const val SLEEP_ALPHA = 0.30f
private const val SLEEP_ALPHA_SELECTED = 0.55f

@Composable
fun OverviewScreen(viewModel: OverviewViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

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
            OverviewViewModel.Mode.NIGHT -> NightChart(state.nights)
            OverviewViewModel.Mode.TOTALS -> TotalsChart(state.totals)
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
private fun NightChart(nights: List<NightColumn>) {
    val dayLabels = stringResource(R.string.overview_day_labels).split(",")
    var selected by remember(nights) { mutableIntStateOf(-1) }
    val usageColor = MaterialTheme.colorScheme.primary
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)

    Column {
        // Evening day of each night — the column reads "Su ↓ Mo": dusk on top, morning below.
        DayLabelRow(labels = List(DAYS) { dayLabels[(it + DAYS - 1) % DAYS] }, dimmed = true)
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
                drawNight(nights, selected, usageColor, lineColor)
            }
        }
        DayLabelRow(labels = dayLabels.take(DAYS), dimmed = false)
        nights.getOrNull(selected)?.let { night ->
            night.sleep?.let { sleep ->
                SleepDetails(
                    sleep = sleep,
                    eveningLabel = dayLabels[(selected + DAYS - 1) % DAYS],
                    morningLabel = dayLabels[selected],
                )
            }
        }
    }
}

@Composable
private fun DayLabelRow(labels: List<String>, dimmed: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (dimmed) 0.5f else 1f)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (AXIS_WIDTH_DP + CHART_START_PAD_DP).dp, top = 4.dp, bottom = 4.dp),
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

/** Which column's sleep span was tapped, or -1. Coordinates are in the canvas space. */
private fun sleepHit(nights: List<NightColumn>, offset: Offset, width: Int, height: Int): Int {
    if (nights.isEmpty() || width <= 0 || height <= 0) return -1
    val col = (offset.x / (width / DAYS.toFloat())).toInt().coerceIn(0, DAYS - 1)
    val minute = (offset.y / height * NightTimeline.MINUTES_PER_DAY).toInt()
    val sleep = nights.getOrNull(col)?.sleep ?: return -1
    return if (minute in sleep.startMinute..sleep.endMinute) col else -1
}

@Composable
private fun SleepDetails(sleep: NightTimeline.Span, eveningLabel: String, morningLabel: String) {
    Column(modifier = Modifier.padding(top = 8.dp)) {
        Text(
            text = stringResource(R.string.overview_sleep_title, eveningLabel, morningLabel),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        Text(
            text = formatDuration((sleep.endMinute - sleep.startMinute) * MINUTE_MS),
            style = MaterialTheme.typography.displaySmall,
        )
        Text(
            text = stringResource(
                R.string.overview_sleep_range,
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
    usageColor: Color,
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
            val alpha = if (i == selectedIndex) SLEEP_ALPHA_SELECTED else SLEEP_ALPHA
            drawRoundRect(
                color = SleepTeal.copy(alpha = alpha),
                topLeft = Offset(left, y(s.startMinute)),
                size = Size(barWidth, y(s.endMinute) - y(s.startMinute)),
                cornerRadius = corner,
            )
        }
        column.usage.forEach { span ->
            val top = y(span.startMinute)
            val height = (y(span.endMinute) - top).coerceAtLeast(minSpan)
            drawRoundRect(usageColor, Offset(left, top), Size(barWidth, height), corner)
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
private fun TotalsChart(totals: List<OverviewViewModel.DayBar>) {
    val dayLabels = stringResource(R.string.overview_day_labels).split(",")
    val max = totals.maxOfOrNull { it.totalMs }?.coerceAtLeast(1L) ?: 1L
    val barColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        totals.forEachIndexed { index, bar ->
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom,
            ) {
                Text(formatDuration(bar.totalMs), style = MaterialTheme.typography.labelSmall)
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height((bar.totalMs.toFloat() / max * TOTALS_BAR_MAX_DP).coerceAtLeast(2f).dp)
                        .padding(top = 4.dp),
                ) {
                    drawRoundRect(barColor, cornerRadius = CornerRadius(CORNER_PX, CORNER_PX))
                }
                Text(
                    text = dayLabels.getOrElse(index) { "" },
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

private const val TOTALS_BAR_MAX_DP = 260f
