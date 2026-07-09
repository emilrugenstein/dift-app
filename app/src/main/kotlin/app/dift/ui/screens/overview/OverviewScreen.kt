package app.dift.ui.screens.overview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
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
import java.time.format.DateTimeFormatter

private val rangeFormat = DateTimeFormatter.ofPattern("MMM d")
private const val DAYS = 7
private const val CHART_HEIGHT_DP = 340
private const val COLUMN_GAP_FRACTION = 0.16f
private const val MIN_SPAN_DP = 2f
private const val CORNER_PX = 5f

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
    val usageColor = MaterialTheme.colorScheme.primary
    val sleepColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
    val lineColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
    val axisStyle = MaterialTheme.typography.labelSmall
    val labelStyle = MaterialTheme.typography.labelMedium

    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .width(28.dp)
                .height(CHART_HEIGHT_DP.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End,
        ) {
            listOf(12, 18, 0, 6, 12).forEach { hour ->
                Text(hour.toString().padStart(2, '0'), style = axisStyle)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(CHART_HEIGHT_DP.dp)
                    .padding(start = 8.dp),
            ) {
                drawNight(nights, usageColor, sleepColor, lineColor)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, top = 4.dp),
            ) {
                dayLabels.take(DAYS).forEach { label ->
                    Text(
                        text = label,
                        style = labelStyle,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

private fun DrawScope.drawNight(
    nights: List<NightColumn>,
    usageColor: Color,
    sleepColor: Color,
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
            drawRoundRect(
                color = sleepColor,
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
