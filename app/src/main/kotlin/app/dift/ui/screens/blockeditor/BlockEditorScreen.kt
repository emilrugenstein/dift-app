package app.dift.ui.screens.blockeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.ui.format.formatMinuteOfDay

private const val BURST_STEP_SECONDS = 15
private const val MINUTES_PER_HOUR = 60

@Composable
fun BlockEditorScreen(
    blockId: Long,
    onSaved: () -> Unit,
    viewModel: BlockEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(blockId) { viewModel.load(blockId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var appQuery by rememberSaveable { mutableStateOf("") }
    val visibleApps = state.apps.filter { it.label.contains(appQuery.trim(), ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.editor_name_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item { SectionLabel(R.string.editor_days_label) }
        item { DaySelector(state.daysMask, viewModel::toggleDay) }
        item {
            TimeField(stringResource(R.string.editor_schedule_start), state.startMinute, viewModel::setStart)
        }
        item {
            TimeField(stringResource(R.string.editor_schedule_end), state.endMinute, viewModel::setEnd)
        }
        item { BurstStepper(state.maxBurstSeconds, viewModel::changeMaxBurst) }
        item { SectionLabel(R.string.editor_exempt_label) }
        item {
            Text(
                text = stringResource(R.string.editor_exempt_description),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            OutlinedTextField(
                value = appQuery,
                onValueChange = { appQuery = it },
                label = { Text(stringResource(R.string.editor_search_apps)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        items(visibleApps, key = { it.packageName }) { app ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = app.packageName in state.exemptPackages,
                    onCheckedChange = { viewModel.toggleExempt(app.packageName) },
                )
                Text(app.label, modifier = Modifier.padding(start = 8.dp))
            }
        }
        item {
            Button(
                onClick = { viewModel.save(onSaved) },
                enabled = state.isValid,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.editor_save))
            }
        }
    }
}

@Composable
private fun SectionLabel(resId: Int) {
    Text(text = stringResource(resId), style = MaterialTheme.typography.titleSmall)
}

@Composable
private fun DaySelector(daysMask: Int, onToggle: (Int) -> Unit) {
    val labels = stringResource(R.string.editor_day_initials).split(",")
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = (daysMask shr index) and 1 == 1,
                onClick = { onToggle(index) },
                label = {
                    Text(
                        text = label,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth(),
                    )
                },
                // weight (not intrinsic width) so all seven always fit on screen
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun TimeField(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { showPicker = true }) {
            Text(formatMinuteOfDay(minuteOfDay), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (showPicker) {
        TimePickerDialog(
            initialMinuteOfDay = minuteOfDay,
            onConfirm = { picked ->
                showPicker = false
                onChange(picked)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerDialog(initialMinuteOfDay: Int, onConfirm: (Int) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberTimePickerState(
        initialHour = initialMinuteOfDay / MINUTES_PER_HOUR,
        initialMinute = initialMinuteOfDay % MINUTES_PER_HOUR,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onConfirm(pickerState.hour * MINUTES_PER_HOUR + pickerState.minute) }) {
                Text(stringResource(R.string.action_ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        text = { TimePicker(state = pickerState) },
    )
}

@Composable
private fun BurstStepper(maxBurstSeconds: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.editor_burst_label), modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(-BURST_STEP_SECONDS) }) { Text("−") }
        Text(
            text = stringResource(R.string.block_burst_format, maxBurstSeconds),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = { onChange(BURST_STEP_SECONDS) }) { Text("+") }
    }
}
