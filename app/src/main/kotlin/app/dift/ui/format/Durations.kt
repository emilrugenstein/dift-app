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

private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3600
private const val SECOND_MS = 1_000L

/** "12:34" / "1:02:03" — a running stopwatch readout. Not prose, so it lives outside strings.xml. */
fun formatClock(ms: Long): String {
    val totalSeconds = (ms / SECOND_MS).coerceAtLeast(0L)
    val hours = totalSeconds / SECONDS_PER_HOUR
    val minutes = (totalSeconds % SECONDS_PER_HOUR) / SECONDS_PER_MINUTE
    val seconds = totalSeconds % SECONDS_PER_MINUTE
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%02d:%02d".format(minutes, seconds)
}
