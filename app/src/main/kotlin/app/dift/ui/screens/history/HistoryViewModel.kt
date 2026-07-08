package app.dift.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.db.entity.BlockEventEntity
import app.dift.data.repo.BlockEventRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class HistoryViewModel @Inject constructor(
    blockEventRepository: BlockEventRepository,
) : ViewModel() {

    val events: StateFlow<List<BlockEventEntity>> = blockEventRepository.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
