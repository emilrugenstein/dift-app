package app.dift.domain.engine

import app.dift.domain.model.DebtEvent
import app.dift.domain.model.DebtState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtReducerTest {

    private val config = DebtReducer.Config(maxBurstSeconds = 60, debtRatio = 1.0f)
    private val sec = 1_000L

    private fun tick(inWindow: Boolean = true, unlocked: Boolean = true, overlay: Boolean = false) =
        DebtEvent.Tick(inWindow = inWindow, unlocked = unlocked, overlayShowing = overlay)

    @Test
    fun `unlock inside window starts a burst`() {
        val state = DebtReducer.reduce(DebtState.IDLE, DebtEvent.Unlock(inWindow = true), 1000, config)
        assertEquals(1000L, state.burstStartedAtMs)
        assertNull(state.debtUntilMs)
    }

    @Test
    fun `unlock outside window does not start a burst`() {
        val state = DebtReducer.reduce(DebtState.IDLE, DebtEvent.Unlock(inWindow = false), 1000, config)
        assertNull(state.burstStartedAtMs)
    }

    @Test
    fun `burst reaching the cap on a tick fires equal debt and ends the burst`() {
        var state = DebtReducer.reduce(DebtState.IDLE, DebtEvent.Unlock(inWindow = true), 0, config)
        // Tick still under the cap: nothing fires.
        state = DebtReducer.reduce(state, tick(), 59 * sec, config)
        assertNull(state.debtUntilMs)
        // Tick at the cap: debt = 60 s.
        state = DebtReducer.reduce(state, tick(), 60 * sec, config)
        assertNull(state.burstStartedAtMs)
        assertEquals(60 * sec + 60 * sec, state.debtUntilMs)
    }

    @Test
    fun `locking mid-burst converts elapsed time to equal debt`() {
        var state = DebtReducer.reduce(DebtState.IDLE, DebtEvent.Unlock(inWindow = true), 0, config)
        state = DebtReducer.reduce(state, DebtEvent.Lock, 40 * sec, config)
        assertNull(state.burstStartedAtMs)
        assertEquals(40 * sec + 40 * sec, state.debtUntilMs)
    }

    @Test
    fun `debt ratio scales the penalty`() {
        val doubleRatio = DebtReducer.Config(maxBurstSeconds = 60, debtRatio = 2.0f)
        var state = DebtReducer.reduce(DebtState.IDLE, DebtEvent.Unlock(inWindow = true), 0, doubleRatio)
        state = DebtReducer.reduce(state, DebtEvent.Lock, 30 * sec, doubleRatio)
        assertEquals(30 * sec + 60 * sec, state.debtUntilMs) // 30s used -> 60s debt
    }

    @Test
    fun `unlocking during active debt does not start a burst`() {
        val debt = DebtState(debtUntilMs = 100 * sec)
        val state = DebtReducer.reduce(debt, DebtEvent.Unlock(inWindow = true), 50 * sec, config)
        assertNull(state.burstStartedAtMs)
        assertEquals(100 * sec, state.debtUntilMs)
    }

    @Test
    fun `overlay time does not accrue burst`() {
        val debt = DebtState(debtUntilMs = 100 * sec)
        // Ticking while the overlay is up and debt is serving: unchanged, still no burst.
        val state = DebtReducer.reduce(debt, tick(overlay = true), 70 * sec, config)
        assertNull(state.burstStartedAtMs)
        assertEquals(100 * sec, state.debtUntilMs)
    }

    @Test
    fun `debt is served once the deadline passes`() {
        val debt = DebtState(debtUntilMs = 100 * sec)
        val serving = DebtReducer.reduce(debt, tick(overlay = true), 99 * sec, config)
        assertTrue(serving.debtActiveAt(99 * sec))
        val served = DebtReducer.reduce(debt, tick(overlay = true), 100 * sec, config)
        assertNull(served.debtUntilMs)
        assertFalse(served.debtActiveAt(100 * sec))
    }

    @Test
    fun `leaving the window cancels the burst without creating debt`() {
        var state = DebtReducer.reduce(DebtState.IDLE, DebtEvent.Unlock(inWindow = true), 0, config)
        state = DebtReducer.reduce(state, tick(inWindow = false), 30 * sec, config)
        assertNull(state.burstStartedAtMs)
        assertNull(state.debtUntilMs)
    }

    @Test
    fun `a fresh burst can start after debt is served`() {
        // Debt served on this tick...
        var state = DebtReducer.reduce(DebtState(debtUntilMs = 100 * sec), tick(overlay = true), 100 * sec, config)
        assertNull(state.debtUntilMs)
        // ...next tick with the overlay gone starts a new burst.
        state = DebtReducer.reduce(state, tick(overlay = false), 101 * sec, config)
        assertEquals(101 * sec, state.burstStartedAtMs)
    }

    @Test
    fun `locking while serving debt keeps the debt intact`() {
        val debt = DebtState(debtUntilMs = 100 * sec)
        val state = DebtReducer.reduce(debt, DebtEvent.Lock, 50 * sec, config)
        assertEquals(100 * sec, state.debtUntilMs)
    }

    @Test
    fun `reboot restore is transparent - persisted debt keeps counting down`() {
        // Simulates state reloaded from DataStore after process death: a tick just resumes it.
        val restored = DebtState(debtUntilMs = 200 * sec)
        val stillServing = DebtReducer.reduce(restored, tick(overlay = true), 150 * sec, config)
        assertEquals(200 * sec, stillServing.debtUntilMs)
    }

    @Test
    fun `rapid lock unlock does not lose accumulated debt`() {
        var state = DebtReducer.reduce(DebtState.IDLE, DebtEvent.Unlock(inWindow = true), 0, config)
        state = DebtReducer.reduce(state, DebtEvent.Lock, 20 * sec, config) // 20s debt
        val debtUntil = state.debtUntilMs
        state = DebtReducer.reduce(state, DebtEvent.Unlock(inWindow = true), 25 * sec, config)
        assertEquals(debtUntil, state.debtUntilMs)
        assertNull(state.burstStartedAtMs)
    }
}
