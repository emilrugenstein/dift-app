package app.dift.ui.screens.blocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.BlockRepository
import app.dift.domain.engine.BlockEngine
import app.dift.domain.model.Block
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZonedDateTime
import javax.inject.Inject

@HiltViewModel
class BlocksViewModel @Inject constructor(
    private val blockRepository: BlockRepository,
) : ViewModel() {

    val blocks: StateFlow<List<Block>> = blockRepository.blocks
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    fun setEnabled(blockId: Long, enabled: Boolean) {
        viewModelScope.launch {
            blockRepository.setEnabled(blockId, enabled, System.currentTimeMillis())
        }
    }

    fun delete(blockId: Long) {
        viewModelScope.launch {
            val block = blockRepository.blocks.first().firstOrNull { it.id == blockId } ?: return@launch
            // Backstop for the UI guard: a block whose window is running is never deletable —
            // deleting the night block AT night is exactly the impulse Dift exists to resist.
            if (BlockEngine.windowActive(block, ZonedDateTime.now())) return@launch
            blockRepository.delete(blockId)
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
