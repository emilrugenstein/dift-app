package app.dift.ui.screens.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.domain.model.Block
import app.dift.ui.format.daysSummary
import app.dift.ui.format.formatMinuteOfDay

@Composable
fun BlocksScreen(
    onAddBlock: () -> Unit,
    onEditBlock: (Long) -> Unit,
    viewModel: BlocksViewModel = hiltViewModel(),
) {
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(
                text = stringResource(R.string.blocks_title),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        item {
            Button(onClick = onAddBlock, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.blocks_add))
            }
        }
        if (blocks.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.blocks_empty),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(blocks, key = { it.id }) { block ->
            BlockRow(
                block = block,
                onEdit = { onEditBlock(block.id) },
                onToggle = { viewModel.setEnabled(block.id, it) },
                onDelete = { viewModel.delete(block.id) },
            )
        }
    }
}

@Composable
private fun BlockRow(
    block: Block,
    onEdit: () -> Unit,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(block.name, style = MaterialTheme.typography.bodyLarge)
            Text(text = subtitle(block), style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.blocks_delete)) }
        Switch(checked = block.enabled, onCheckedChange = onToggle)
    }
}

@Composable
private fun subtitle(block: Block): String {
    val window = stringResource(
        R.string.block_window_format,
        formatMinuteOfDay(block.startMinuteOfDay),
        formatMinuteOfDay(block.endMinuteOfDay),
    )
    val burst = stringResource(R.string.block_burst_format, block.maxBurstSeconds)
    return "${daysSummary(block.daysMask)} · $window · $burst"
}
