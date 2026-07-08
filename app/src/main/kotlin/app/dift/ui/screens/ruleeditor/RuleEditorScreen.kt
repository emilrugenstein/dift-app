package app.dift.ui.screens.ruleeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.domain.model.RuleType
import app.dift.domain.model.Strictness

@Composable
fun RuleEditorScreen(
    ruleId: Long,
    onSaved: () -> Unit,
    viewModel: RuleEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(ruleId) { viewModel.load(ruleId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
        item { TypeSelector(state.type, viewModel::setType) }
        item { StrictnessSelector(state.strictness, viewModel::setStrictness) }

        when (state.type) {
            RuleType.DAILY_LIMIT -> item { LimitField(state.limitMinutes, viewModel::setLimitMinutes) }
            RuleType.SCHEDULE -> {
                item {
                    TimeStepper(
                        label = stringResource(R.string.editor_schedule_start),
                        minuteOfDay = state.scheduleStartMinute,
                        onChange = viewModel::setScheduleStart,
                    )
                }
                item {
                    TimeStepper(
                        label = stringResource(R.string.editor_schedule_end),
                        minuteOfDay = state.scheduleEndMinute,
                        onChange = viewModel::setScheduleEnd,
                    )
                }
                item { DaySelector(state.daysMask, viewModel::toggleDay) }
            }
            else -> Unit
        }

        item {
            Text(
                text = stringResource(R.string.editor_apps_label),
                style = MaterialTheme.typography.titleSmall,
            )
        }
        items(state.apps, key = { it.packageName }) { app ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = app.packageName in state.selectedPackages,
                    onCheckedChange = { viewModel.togglePackage(app.packageName) },
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TypeSelector(selected: RuleType, onSelect: (RuleType) -> Unit) {
    val options = listOf(
        RuleType.ALWAYS to R.string.editor_type_always,
        RuleType.DAILY_LIMIT to R.string.editor_type_limit,
        RuleType.SCHEDULE to R.string.editor_type_schedule,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        options.forEachIndexed { index, (type, labelRes) ->
            SegmentedButton(
                selected = selected == type,
                onClick = { onSelect(type) },
                shape = SegmentedButtonDefaults.itemShape(index, options.size),
            ) {
                Text(stringResource(labelRes))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StrictnessSelector(selected: Strictness, onSelect: (Strictness) -> Unit) {
    val options = listOf(
        Strictness.TAP_THROUGH to R.string.editor_strictness_tap,
        Strictness.FRICTION to R.string.editor_strictness_friction,
        Strictness.HARD to R.string.editor_strictness_hard,
    )
    Column {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (strictness, labelRes) ->
                SegmentedButton(
                    selected = selected == strictness,
                    onClick = { onSelect(strictness) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) {
                    Text(stringResource(labelRes))
                }
            }
        }
        if (selected == Strictness.HARD) {
            Text(
                text = stringResource(R.string.editor_strictness_hard_warning),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun LimitField(minutes: Int, onChange: (Int) -> Unit) {
    OutlinedTextField(
        value = if (minutes > 0) minutes.toString() else "",
        onValueChange = { onChange(it.toIntOrNull() ?: 0) },
        label = { Text(stringResource(R.string.editor_limit_label)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun TimeStepper(label: String, minuteOfDay: Int, onChange: (Int) -> Unit) {
    val step = 30
    val dayMinutes = 24 * 60
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange((minuteOfDay - step + dayMinutes) % dayMinutes) }) {
            Text("-")
        }
        Text(formatMinuteOfDay(minuteOfDay), style = MaterialTheme.typography.titleMedium)
        OutlinedButton(onClick = { onChange((minuteOfDay + step) % dayMinutes) }) {
            Text("+")
        }
    }
}

private fun formatMinuteOfDay(minute: Int): String {
    val hh = (minute / 60).toString().padStart(2, '0')
    val mm = (minute % 60).toString().padStart(2, '0')
    return "$hh:$mm"
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
                label = { Text(label) },
            )
        }
    }
}
