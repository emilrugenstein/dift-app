package app.dift.ui.screens.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.ui.format.formatDuration

@Composable
fun AppsScreen(viewModel: AppsViewModel = hiltViewModel()) {
    val rows by viewModel.state.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        viewModel.refresh()
        onPauseOrDispose { }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.apps_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        items(rows, key = { it.packageName }) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = formatDuration(row.todayMs),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                FilterChip(
                    selected = row.blockedAlways,
                    onClick = { viewModel.toggleBlock(row) },
                    label = {
                        Text(
                            stringResource(
                                if (row.blockedAlways) {
                                    R.string.apps_blocked
                                } else {
                                    R.string.apps_block
                                },
                            ),
                        )
                    },
                )
            }
        }
    }
}
