package app.dift.ui.screens.blocks

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.dift.R
import app.dift.domain.engine.BlockEngine
import app.dift.domain.model.Block
import app.dift.ui.format.daysSummary
import app.dift.ui.format.formatMinuteOfDay
import java.time.ZonedDateTime

@Composable
fun BlocksScreen(
    onAddBlock: () -> Unit,
    onEditBlock: (Long) -> Unit,
    viewModel: BlocksViewModel = hiltViewModel(),
) {
    val blocks by viewModel.blocks.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<Block?>(null) }

    Box {
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
                    // No self-sabotage: while an ENABLED block's window is running it can be
                    // neither deleted nor switched off. A disabled block is not enforcing
                    // anything, so it stays freely editable.
                    lockedNow = block.enabled &&
                        BlockEngine.windowActive(block, ZonedDateTime.now()),
                    onEdit = { onEditBlock(block.id) },
                    onToggle = { viewModel.setEnabled(block.id, it) },
                    onDelete = { pendingDelete = block },
                )
            }
        }
        pendingDelete?.let { block ->
            DeleteConfirmDialog(
                block = block,
                onConfirm = {
                    viewModel.delete(block.id)
                    pendingDelete = null
                },
                onDismiss = { pendingDelete = null },
            )
        }
    }
}

@Composable
private fun DeleteConfirmDialog(block: Block, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.blocks_delete_confirm_title)) },
        text = { Text(stringResource(R.string.blocks_delete_confirm_message, block.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.blocks_delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}

@Composable
private fun BlockRow(
    block: Block,
    lockedNow: Boolean,
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
        TextButton(onClick = onDelete, enabled = !lockedNow) {
            Text(stringResource(R.string.blocks_delete))
        }
        Switch(checked = block.enabled, onCheckedChange = onToggle, enabled = !lockedNow)
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
