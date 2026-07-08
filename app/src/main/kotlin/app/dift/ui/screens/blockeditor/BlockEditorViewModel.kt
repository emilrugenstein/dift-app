package app.dift.ui.screens.blockeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.BlockRepository
import app.dift.domain.SafetyDenylist
import app.dift.domain.model.Block
import app.dift.system.packages.InstalledApp
import app.dift.system.packages.InstalledAppsProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BlockEditorViewModel @Inject constructor(
    private val blockRepository: BlockRepository,
    private val appsProvider: InstalledAppsProvider,
) : ViewModel() {

    data class UiState(
        val blockId: Long = 0,
        val name: String = "",
        val enabled: Boolean = true,
        val daysMask: Int = Block.ALL_DAYS,
        val startMinute: Int = Block.DEFAULT_START_MINUTE,
        val endMinute: Int = Block.DEFAULT_END_MINUTE,
        val maxBurstSeconds: Int = Block.DEFAULT_MAX_BURST_SECONDS,
        val exemptPackages: Set<String> = emptySet(),
        val apps: List<InstalledApp> = emptyList(),
    ) {
        /** A block needs a name, at least one weekday, a non-empty window, and a positive cap. */
        val isValid: Boolean
            get() = name.isNotBlank() && daysMask != 0 && startMinute != endMinute && maxBurstSeconds > 0
    }

    private val state = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = state.asStateFlow()

    fun load(blockId: Long) {
        viewModelScope.launch {
            val apps = appsProvider.launchableApps()
                .filter { it.packageName !in SafetyDenylist.staticPackages }
            val existing = if (blockId != 0L) {
                blockRepository.blocks.first().firstOrNull { it.id == blockId }
            } else {
                null
            }
            state.value = existing?.toUiState(apps) ?: UiState(apps = apps)
        }
    }

    fun setName(value: String) = state.update { it.copy(name = value) }

    fun toggleDay(dayIndex: Int) = state.update { it.copy(daysMask = it.daysMask xor (1 shl dayIndex)) }

    fun setStart(minute: Int) = state.update { it.copy(startMinute = minute) }

    fun setEnd(minute: Int) = state.update { it.copy(endMinute = minute) }

    fun changeMaxBurst(deltaSeconds: Int) = state.update {
        it.copy(maxBurstSeconds = (it.maxBurstSeconds + deltaSeconds).coerceIn(MIN_BURST, MAX_BURST))
    }

    fun toggleExempt(packageName: String) = state.update {
        val next = if (packageName in it.exemptPackages) {
            it.exemptPackages - packageName
        } else {
            it.exemptPackages + packageName
        }
        it.copy(exemptPackages = next)
    }

    fun save(onSaved: () -> Unit) {
        val current = state.value
        if (!current.isValid) return
        viewModelScope.launch {
            blockRepository.upsert(current.toBlock(), System.currentTimeMillis())
            onSaved()
        }
    }

    private fun UiState.toBlock() = Block(
        id = blockId,
        name = name.trim(),
        enabled = enabled,
        daysMask = daysMask,
        startMinuteOfDay = startMinute,
        endMinuteOfDay = endMinute,
        maxBurstSeconds = maxBurstSeconds,
        exemptPackages = exemptPackages,
    )

    private fun Block.toUiState(apps: List<InstalledApp>) = UiState(
        blockId = id,
        name = name,
        enabled = enabled,
        daysMask = daysMask,
        startMinute = startMinuteOfDay,
        endMinute = endMinuteOfDay,
        maxBurstSeconds = maxBurstSeconds,
        exemptPackages = exemptPackages,
        apps = apps,
    )

    private companion object {
        const val MIN_BURST = 15
        const val MAX_BURST = 3600
    }
}
