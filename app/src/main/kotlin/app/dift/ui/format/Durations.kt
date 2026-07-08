package app.dift.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.dift.R

private const val MINUTE_MS = 60_000L
private const val MINUTES_PER_HOUR = 60

/** "2h 14m" / "14m" / "<1m" — the only duration formatting used anywhere in the UI. */
@Composable
fun formatDuration(ms: Long): String {
    val totalMinutes = ms / MINUTE_MS
    val hours = totalMinutes / MINUTES_PER_HOUR
    val minutes = totalMinutes % MINUTES_PER_HOUR
    return when {
        hours > 0 -> stringResource(R.string.duration_hours_minutes, hours, minutes)
        totalMinutes > 0 -> stringResource(R.string.duration_minutes, minutes)
        else -> stringResource(R.string.duration_under_minute)
    }
}
