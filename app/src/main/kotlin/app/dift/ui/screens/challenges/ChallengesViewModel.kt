package app.dift.ui.screens.challenges

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.datastore.SettingsRepository
import app.dift.data.repo.ChallengeRepository
import app.dift.domain.engine.ChallengeProgress
import app.dift.domain.model.Challenge
import app.dift.domain.model.ChallengeEntry
import app.dift.domain.model.ChallengeTimer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * The Challenge tab (docs/features/challenges.md): the shown challenge (the one covering today
 * unless another was tapped), its live progress, logged entries, and the single global timer.
 * The timer is only a `(challengeId, startedAt)` pair in DataStore until Stop writes the entry.
 */
@HiltViewModel
class ChallengesViewModel @Inject constructor(
    private val repository: ChallengeRepository,
    private val settings: SettingsRepository,
) : ViewModel() {

    data class UiState(
        val challenges: List<Challenge> = emptyList(),
        val selected: Challenge? = null,
        val snapshot: ChallengeProgress.Snapshot? = null,
        val entries: List<ChallengeEntry> = emptyList(),
        /** A timer is running for [selected]; [liveElapsedMs] is its elapsed time (0 otherwise). */
        val timerRunningHere: Boolean = false,
        val liveElapsedMs: Long = 0L,
        /** The challenge a timer is running for when it is NOT [selected] (Start is disabled then). */
        val timerElsewhere: Challenge? = null,
    )

    private val selectedId = MutableStateFlow<Long?>(null)
    private val tick = MutableStateFlow(0L)
    private val zone: ZoneId get() = ZoneId.systemDefault()

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<UiState> = combine(repository.challenges, selectedId) { challenges, id ->
        val today = LocalDate.now()
        val selected = challenges.firstOrNull { it.id == id }
            ?: challenges.firstOrNull { it.covers(today) }
            ?: challenges.firstOrNull()
        challenges to selected
    }.flatMapLatest { (challenges, selected) ->
        if (selected == null) {
            flowOf(UiState(challenges = challenges))
        } else {
            combine(
                repository.observeEntries(selected.id),
                repository.observeDayTotals(selected.id),
                settings.challengeTimer,
                tick,
            ) { entries, totals, timer, now -> build(challenges, selected, entries, totals, timer, now) }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), UiState())

    init {
        // One-second ticks only while a timer runs; the loop restarts when the timer changes.
        viewModelScope.launch {
            settings.challengeTimer.collectLatest { timer ->
                if (timer == null) return@collectLatest
                while (true) {
                    tick.value = System.currentTimeMillis()
                    delay(TICK_MS)
                }
            }
        }
    }

    @Suppress("LongParameterList") // one assembly point for the combined flows
    private fun build(
        challenges: List<Challenge>,
        selected: Challenge,
        entries: List<ChallengeEntry>,
        totals: Map<LocalDate, Long>,
        timer: ChallengeTimer?,
        nowMs: Long,
    ): UiState {
        val today = LocalDate.now()
        val runningHere = timer?.takeIf { it.challengeId == selected.id }
        val live = runningHere?.let { (nowMs - it.startedAtMs).coerceAtLeast(0L) } ?: 0L
        val withLive = if (live > 0L) totals + (today to (totals[today] ?: 0L) + live) else totals
        val elsewhere = if (runningHere == null) timer else null
        return UiState(
            challenges = challenges,
            selected = selected,
            snapshot = ChallengeProgress.snapshot(selected, withLive, today),
            entries = entries,
            timerRunningHere = runningHere != null,
            liveElapsedMs = live,
            timerElsewhere = elsewhere?.let { t -> challenges.firstOrNull { it.id == t.challengeId } },
        )
    }

    fun select(challengeId: Long) {
        selectedId.value = challengeId
    }

    fun startTimer() {
        viewModelScope.launch {
            val selected = uiState.value.selected ?: return@launch
            if (settings.challengeTimer.first() != null) return@launch // one timer at a time
            settings.setChallengeTimer(ChallengeTimer(selected.id, System.currentTimeMillis()))
        }
    }

    /** Stop writes the timed entry (attributed to the day it started) and clears the timer. */
    fun stopTimer() {
        viewModelScope.launch {
            val timer = settings.challengeTimer.first() ?: return@launch
            val nowMs = System.currentTimeMillis()
            if (nowMs - timer.startedAtMs >= MIN_ENTRY_MS) {
                val day = Instant.ofEpochMilli(timer.startedAtMs).atZone(zone).toLocalDate()
                repository.addEntry(timer.challengeId, timer.startedAtMs, nowMs, day, manual = false)
            }
            settings.setChallengeTimer(null)
        }
    }

    /** Hindsight entry: anchored at noon of [day], only the duration matters. */
    fun addTime(day: LocalDate, minutes: Int) {
        viewModelScope.launch {
            val selected = uiState.value.selected ?: return@launch
            val startMs = day.atTime(NOON, 0).atZone(zone).toInstant().toEpochMilli()
            repository.addEntry(selected.id, startMs, startMs + minutes * MINUTE_MS, day, manual = true)
        }
    }

    fun deleteEntry(entryId: Long) {
        viewModelScope.launch { repository.deleteEntry(entryId) }
    }

    fun delete(challengeId: Long) {
        viewModelScope.launch {
            if (settings.challengeTimer.first()?.challengeId == challengeId) settings.setChallengeTimer(null)
            repository.delete(challengeId)
            if (selectedId.value == challengeId) selectedId.value = null
        }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val TICK_MS = 1_000L
        const val MIN_ENTRY_MS = 1_000L
        const val MINUTE_MS = 60_000L
        const val NOON = 12
    }
}
