package app.dift.ui.screens.challengeeditor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Pure validation + defaults for the challenge editor (ChallengeEditorViewModel.UiState). */
class ChallengeEditorValidationTest {

    private val nov1 = LocalDate.parse("2026-11-01")
    private val nov30 = LocalDate.parse("2026-11-30")

    private fun state(
        name: String = "Mobility",
        start: LocalDate = nov1,
        end: LocalDate = nov30,
        goal: Int = 30,
    ) = ChallengeEditorViewModel.UiState(name = name, startDay = start, endDay = end, goalMinutes = goal)

    @Test
    fun `a well-formed challenge passes`() {
        assertTrue(state().isValid)
    }

    @Test
    fun `blank name fails`() {
        assertFalse(state(name = " ").isValid)
    }

    @Test
    fun `end before start fails but a single day passes`() {
        assertFalse(state(start = nov30, end = nov1).isValid)
        assertTrue(state(start = nov1, end = nov1).isValid)
    }

    @Test
    fun `goal below five minutes fails`() {
        assertFalse(state(goal = 0).isValid)
    }

    @Test
    fun `a new challenge defaults to the current calendar month`() {
        val s = ChallengeEditorViewModel.UiState.defaultFor(LocalDate.parse("2026-11-14"))
        assertEquals(nov1, s.startDay)
        assertEquals(nov30, s.endDay)
        assertEquals(30, s.goalMinutes)
    }
}
