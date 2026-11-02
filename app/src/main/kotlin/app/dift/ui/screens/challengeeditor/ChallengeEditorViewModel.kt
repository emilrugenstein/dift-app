package app.dift.ui.screens.challengeeditor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.dift.data.repo.ChallengeRepository
import app.dift.domain.model.Challenge
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class ChallengeEditorViewModel @Inject constructor(
    private val repository: ChallengeRepository,
) : ViewModel() {

    data class UiState(
        val challengeId: Long = 0,
        val name: String = "",
        val startDay: LocalDate,
        val endDay: LocalDate,
        val goalMinutes: Int = Challenge.DEFAULT_GOAL_MINUTES,
    ) {
        /** A challenge needs a name, an end on or after its start, and a goal of at least 5 min. */
        val isValid: Boolean
            get() = name.isNotBlank() && !endDay.isBefore(startDay) && goalMinutes >= Challenge.MIN_GOAL_MINUTES

        companion object {
            /** New challenges default to the current calendar month. */
            fun defaultFor(today: LocalDate) = UiState(
                startDay = today.withDayOfMonth(1),
                endDay = today.withDayOfMonth(today.lengthOfMonth()),
            )
        }
    }

    private val state = MutableStateFlow(UiState.defaultFor(LocalDate.now()))
    val uiState: StateFlow<UiState> = state.asStateFlow()

    fun load(challengeId: Long) {
        if (challengeId == 0L) return
        viewModelScope.launch {
            repository.challenge(challengeId)?.let { c ->
                state.value = UiState(c.id, c.name, c.startDay, c.endDay, c.goalMinutesPerDay)
            }
        }
    }

    fun setName(value: String) = state.update { it.copy(name = value) }

    /** Moving the start past the end drags the end along. */
    fun setStart(day: LocalDate) = state.update { it.copy(startDay = day, endDay = maxOf(it.endDay, day)) }

    fun setEnd(day: LocalDate) = state.update { it.copy(endDay = day) }

    fun changeGoal(deltaMinutes: Int) = state.update {
        val next = (it.goalMinutes + deltaMinutes).coerceIn(Challenge.MIN_GOAL_MINUTES, Challenge.MAX_GOAL_MINUTES)
        it.copy(goalMinutes = next)
    }

    /** Preset: the whole calendar month the start day lies in. */
    fun presetWholeMonth() = state.update {
        val first = it.startDay.withDayOfMonth(1)
        it.copy(startDay = first, endDay = first.withDayOfMonth(first.lengthOfMonth()))
    }

    /** Preset: seven days from the start day. */
    fun presetOneWeek() = state.update { it.copy(endDay = it.startDay.plusDays(DAYS_PER_WEEK - 1L)) }

    fun save(onSaved: () -> Unit) {
        val current = state.value
        if (!current.isValid) return
        viewModelScope.launch {
            repository.upsert(
                Challenge(
                    id = current.challengeId,
                    name = current.name.trim(),
                    startDay = current.startDay,
                    endDay = current.endDay,
                    goalMinutesPerDay = current.goalMinutes,
                ),
                System.currentTimeMillis(),
            )
            onSaved()
        }
    }

    private companion object {
        const val DAYS_PER_WEEK = 7
    }
}
