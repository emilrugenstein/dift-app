package app.dift.ui.screens.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.data.db.entity.BlockEventEntity
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val timeFormat = DateTimeFormatter.ofPattern("MMM d HH:mm")

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = hiltViewModel()) {
    val events by viewModel.events.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (events.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(events, key = { it.id }) { event -> HistoryRow(event) }
    }
}

@Composable
private fun HistoryRow(event: BlockEventEntity) {
    val time = Instant.ofEpochMilli(event.timestampEpochMs)
        .atZone(ZoneId.systemDefault())
        .format(timeFormat)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text(event.packageName, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = "${event.reason.name} · ${event.outcome.name}",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Text(time, style = MaterialTheme.typography.bodySmall)
    }
}
