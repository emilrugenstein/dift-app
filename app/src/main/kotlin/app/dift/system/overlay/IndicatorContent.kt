package app.dift.system.overlay

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.dift.R
import app.dift.ui.theme.DiftTheme
import kotlinx.coroutines.flow.StateFlow

private const val RING_SIZE_DP = 44
private const val RING_STROKE_DP = 5
private const val TOP_ANGLE = -90f
private const val FULL_SWEEP = 360f
private const val ANIM_MS = 950 // just under the 1 s tick so motion stays continuous

/**
 * The corner status pip. Sets its content once; re-renders from [stateFlow] as the coordinator
 * pushes new fractions each second. Counting fills the ring; Blocked drains it and shows a lock.
 */
@Composable
fun IndicatorContent(stateFlow: StateFlow<IndicatorState>) {
    val state by stateFlow.collectAsState()
    DiftTheme {
        when (val s = state) {
            is IndicatorState.Counting -> Ring(
                fraction = s.fraction,
                color = MaterialTheme.colorScheme.primary,
                locked = false,
            )
            is IndicatorState.Blocked -> Ring(
                fraction = s.fraction,
                color = MaterialTheme.colorScheme.error,
                locked = true,
            )
            IndicatorState.Hidden -> Unit
        }
    }
}

@Composable
private fun Ring(fraction: Float, color: Color, locked: Boolean) {
    val animated by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = ANIM_MS),
        label = "indicatorFraction",
    )
    val track = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
    val backdrop = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)

    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(RING_SIZE_DP.dp)) {
            val stroke = RING_STROKE_DP.dp.toPx()
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            val topLeft = Offset(inset, inset)
            drawCircle(color = backdrop, radius = size.minDimension / 2)
            drawArc(
                color = track,
                startAngle = TOP_ANGLE,
                sweepAngle = FULL_SWEEP,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
            drawArc(
                color = color,
                startAngle = TOP_ANGLE,
                sweepAngle = FULL_SWEEP * animated,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke),
            )
        }
        if (locked) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = stringResource(R.string.indicator_blocked),
                tint = color,
                modifier = Modifier.size((RING_SIZE_DP / 2).dp),
            )
        }
    }
}
