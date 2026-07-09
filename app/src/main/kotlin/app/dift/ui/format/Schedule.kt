package app.dift.ui.format

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.dift.R
import app.dift.domain.model.Block

/** "22:30" — 24-hour time from a minute-of-day. Not prose, so it lives outside strings.xml. */
fun formatMinuteOfDay(minute: Int): String {
    val hh = (minute / 60).toString().padStart(2, '0')
    val mm = (minute % 60).toString().padStart(2, '0')
    return "$hh:$mm"
}

/** "Every day" / "Weekdays" / "Weekends" / "Mon, Wed, Fri" for a weekday mask (bit 0 = Monday). */
@Composable
fun daysSummary(mask: Int): String = when (mask) {
    Block.ALL_DAYS -> stringResource(R.string.days_every_day)
    WEEKDAYS_MASK -> stringResource(R.string.days_weekdays)
    WEEKENDS_MASK -> stringResource(R.string.days_weekends)
    else -> {
        val initials = stringResource(R.string.editor_day_initials).split(",")
        (0 until DAYS).filter { (mask shr it) and 1 == 1 }
            .joinToString(", ") { initials[it] }
    }
}

private const val DAYS = 7
private const val WEEKDAYS_MASK = 0b0011111 // Mon–Fri
private const val WEEKENDS_MASK = 0b1100000 // Sat, Sun
