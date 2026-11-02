package app.dift.ui.screens.challengeeditor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.domain.model.Challenge
import app.dift.ui.components.DayPickerDialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val editorDayFormat = DateTimeFormatter.ofPattern("MMM d, yyyy")

@Composable
fun ChallengeEditorScreen(
    challengeId: Long,
    onSaved: () -> Unit,
    viewModel: ChallengeEditorViewModel = hiltViewModel(),
) {
    LaunchedEffect(challengeId) { viewModel.load(challengeId) }
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OutlinedTextField(
            value = state.name,
            onValueChange = viewModel::setName,
            label = { Text(stringResource(R.string.challenge_editor_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        DayField(stringResource(R.string.challenge_editor_start), state.startDay, onChange = viewModel::setStart)
        DayField(
            label = stringResource(R.string.challenge_editor_end),
            day = state.endDay,
            minDay = state.startDay,
            onChange = viewModel::setEnd,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = viewModel::presetWholeMonth) {
                Text(stringResource(R.string.challenge_editor_preset_month))
            }
            OutlinedButton(onClick = viewModel::presetOneWeek) {
                Text(stringResource(R.string.challenge_editor_preset_week))
            }
        }
        GoalStepper(state.goalMinutes, viewModel::changeGoal)
        Button(
            onClick = { viewModel.save(onSaved) },
            enabled = state.isValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.challenge_editor_save))
        }
    }
}

@Composable
private fun DayField(label: String, day: LocalDate, onChange: (LocalDate) -> Unit, minDay: LocalDate? = null) {
    var showPicker by rememberSaveable { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { showPicker = true }) {
            Text(day.format(editorDayFormat), style = MaterialTheme.typography.titleMedium)
        }
    }
    if (showPicker) {
        DayPickerDialog(
            initial = day,
            minDay = minDay,
            onConfirm = { picked ->
                showPicker = false
                onChange(picked)
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun GoalStepper(goalMinutes: Int, onChange: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.challenge_editor_goal), modifier = Modifier.weight(1f))
        OutlinedButton(onClick = { onChange(-Challenge.GOAL_STEP_MINUTES) }) { Text("−") }
        Text(
            text = stringResource(R.string.challenge_minutes, goalMinutes),
            style = MaterialTheme.typography.titleMedium,
        )
        OutlinedButton(onClick = { onChange(Challenge.GOAL_STEP_MINUTES) }) { Text("+") }
    }
}
