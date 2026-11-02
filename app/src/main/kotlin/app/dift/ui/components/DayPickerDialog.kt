package app.dift.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.dift.R
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * A Material date picker for a local calendar day, optionally bounded to [minDay]..[maxDay]
 * (inclusive). The picker works in UTC millis; conversions stay inside this file.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayPickerDialog(
    initial: LocalDate,
    onConfirm: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    minDay: LocalDate? = null,
    maxDay: LocalDate? = null,
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toUtcMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean {
                val day = dayFromUtcMillis(utcTimeMillis)
                return (minDay == null || !day.isBefore(minDay)) && (maxDay == null || !day.isAfter(maxDay))
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(dayFromUtcMillis(it)) } }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    ) {
        DatePicker(state = state)
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun dayFromUtcMillis(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
