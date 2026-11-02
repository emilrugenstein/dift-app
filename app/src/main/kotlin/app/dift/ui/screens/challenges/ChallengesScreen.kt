package app.dift.ui.screens.challenges

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.dift.domain.engine.ChallengeProgress
import app.dift.domain.model.Challenge
import app.dift.domain.model.ChallengeEntry
import app.dift.ui.components.DayPickerDialog
import app.dift.ui.format.formatClock
import app.dift.ui.format.formatDuration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val dayFormat = DateTimeFormatter.ofPattern("MMM d")
private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun ChallengesScreen(
    onAddChallenge: () -> Unit,
    onEditChallenge: (Long) -> Unit,
    viewModel: ChallengesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAddTime by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Challenge?>(null) }
    val selected = state.selected
    val snapshot = state.snapshot

    Box {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(R.string.challenges_title), style = MaterialTheme.typography.titleMedium)
                    Button(onClick = onAddChallenge) { Text(stringResource(R.string.challenges_add)) }
                }
            }
            if (state.challenges.isEmpty()) {
                item { Text(stringResource(R.string.challenges_empty), style = MaterialTheme.typography.bodyMedium) }
            }
            if (selected != null && snapshot != null) {
                item {
                    ChallengeCard(
                        challenge = selected,
                        snapshot = snapshot,
                        state = state,
                        onStart = viewModel::startTimer,
                        onStop = viewModel::stopTimer,
                        onAddTime = { showAddTime = true },
                        onEdit = { onEditChallenge(selected.id) },
                        onDelete = { pendingDelete = selected },
                    )
                }
                item { SectionLabel(R.string.challenge_entries_title) }
                if (state.entries.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.challenge_entries_empty),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                items(state.entries, key = { it.id }) { entry ->
                    EntryRow(entry = entry, onDelete = { viewModel.deleteEntry(entry.id) })
                }
            }
            val others = state.challenges.filter { it.id != selected?.id }
            if (others.isNotEmpty()) {
                item { SectionLabel(R.string.challenge_others_title) }
                items(others, key = { it.id }) { challenge ->
                    ChallengeRow(challenge = challenge, onClick = { viewModel.select(challenge.id) })
                }
            }
        }
        if (showAddTime && selected != null) {
            AddTimeDialog(
                challenge = selected,
                onConfirm = { day, minutes ->
                    viewModel.addTime(day, minutes)
                    showAddTime = false
                },
                onDismiss = { showAddTime = false },
            )
        }
        pendingDelete?.let { challenge ->
            DeleteConfirmDialog(
                challenge = challenge,
                onConfirm = {
                    viewModel.delete(challenge.id)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

@Composable
private fun SectionLabel(resId: Int) {
    Text(
        text = stringResource(resId),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun periodLine(challenge: Challenge): String = stringResource(
    R.string.challenge_period_goal,
    challenge.startDay.format(dayFormat),
    challenge.endDay.format(dayFormat),
    challenge.goalMinutesPerDay,
)

@Composable
private fun ChallengeCard(
    challenge: Challenge,
    snapshot: ChallengeProgress.Snapshot,
    state: ChallengesViewModel.UiState,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onAddTime: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(challenge.name, style = MaterialTheme.typography.headlineSmall)
        Text(
            text = periodLine(challenge),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
        )
        TimerBlock(state, onStart, onStop)
        StatRow(
            label = stringResource(R.string.challenge_today),
            value = stringResource(
                R.string.challenge_of_goal,
                formatDuration(snapshot.todayMs),
                formatDuration(snapshot.goalMs),
            ),
            fraction = snapshot.todayFraction,
        )
        StatRow(
            label = stringResource(R.string.challenge_total),
            value = stringResource(
                R.string.challenge_of_goal,
                formatDuration(snapshot.totalMs),
                formatDuration(snapshot.targetTotalMs),
            ),
            fraction = snapshot.totalFraction,
        )
        AverageLine(snapshot)
        Text(text = outlookLine(challenge, snapshot), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onAddTime) { Text(stringResource(R.string.challenge_add_time)) }
            TextButton(onClick = onEdit) { Text(stringResource(R.string.challenge_edit)) }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.challenge_delete)) }
        }
    }
}

@Composable
private fun TimerBlock(state: ChallengesViewModel.UiState, onStart: () -> Unit, onStop: () -> Unit) {
    val running = state.timerRunningHere
    Text(
        text = formatClock(state.liveElapsedMs),
        style = MaterialTheme.typography.displayMedium,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
    )
    Button(
        onClick = if (running) onStop else onStart,
        enabled = state.timerElsewhere == null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(stringResource(if (running) R.string.challenge_timer_stop else R.string.challenge_timer_start))
    }
    state.timerElsewhere?.let {
        Text(
            text = stringResource(R.string.challenge_timer_elsewhere, it.name),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun StatRow(label: String, value: String, fraction: Float) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

@Composable
private fun AverageLine(snapshot: ChallengeProgress.Snapshot) {
    val balance = if (snapshot.onTrack) {
        stringResource(R.string.challenge_balance_ahead, formatDuration(snapshot.balanceMs))
    } else {
        stringResource(R.string.challenge_balance_behind, formatDuration(-snapshot.balanceMs))
    }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.challenge_average), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = "${formatDuration(snapshot.averagePerDayMs)} · $balance",
            style = MaterialTheme.typography.bodyMedium,
            color = if (snapshot.onTrack) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun outlookLine(challenge: Challenge, snapshot: ChallengeProgress.Snapshot): String = when {
    snapshot.remainingDays == 0 -> stringResource(R.string.challenge_finished)
    snapshot.elapsedDays == 0 -> stringResource(R.string.challenge_not_started, challenge.startDay.format(dayFormat))
    else -> stringResource(
        R.string.challenge_days_left,
        snapshot.remainingDays,
        formatDuration(snapshot.neededPerRemainingDayMs),
    )
}

@Composable
private fun EntryRow(entry: ChallengeEntry, onDelete: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val detail = if (entry.manual) {
        stringResource(R.string.challenge_entry_manual, entry.day.format(dayFormat))
    } else {
        stringResource(
            R.string.challenge_entry_timed,
            entry.day.format(dayFormat),
            Instant.ofEpochMilli(entry.startMs).atZone(zone).format(timeFormat),
            Instant.ofEpochMilli(entry.endMs).atZone(zone).format(timeFormat),
        )
    }
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(formatDuration(entry.durationMs), style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodySmall)
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.challenge_entry_delete))
        }
    }
}

@Composable
private fun ChallengeRow(challenge: Challenge, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Text(challenge.name, style = MaterialTheme.typography.bodyLarge)
        Text(periodLine(challenge), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AddTimeDialog(challenge: Challenge, onConfirm: (LocalDate, Int) -> Unit, onDismiss: () -> Unit) {
    val today = LocalDate.now()
    val latest = minOf(challenge.endDay, today)
    var day by remember { mutableStateOf(if (challenge.covers(today)) today else latest) }
    var minutes by rememberSaveable { mutableIntStateOf(challenge.goalMinutesPerDay) }
    var showPicker by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.challenge_add_time_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.challenge_add_time_hint), style = MaterialTheme.typography.bodySmall)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.challenge_add_time_day), modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showPicker = true }) { Text(day.format(dayFormat)) }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = {
                        minutes = (minutes - Challenge.GOAL_STEP_MINUTES).coerceAtLeast(Challenge.GOAL_STEP_MINUTES)
                    }) { Text("−") }
                    Text(
                        text = stringResource(R.string.challenge_minutes, minutes),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedButton(onClick = {
                        minutes = (minutes + Challenge.GOAL_STEP_MINUTES).coerceAtMost(Challenge.MAX_GOAL_MINUTES)
                    }) { Text("+") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(day, minutes) }) { Text(stringResource(R.string.action_ok)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
    if (showPicker) {
        DayPickerDialog(
            initial = day,
            minDay = challenge.startDay,
            maxDay = latest,
            onConfirm = {
                day = it
                showPicker = false
            },
            onDismiss = { showPicker = false },
        )
    }
}

@Composable
private fun DeleteConfirmDialog(challenge: Challenge, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.challenge_delete_confirm_title)) },
        text = { Text(stringResource(R.string.challenge_delete_confirm_message, challenge.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.challenge_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
