package app.dift.ui.screens.blocks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.BlockRepository
import app.dift.domain.model.Block
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
        viewModelScope.launch { blockRepository.delete(blockId) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
