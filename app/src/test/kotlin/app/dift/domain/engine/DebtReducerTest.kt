package app.dift.domain.engine

import app.dift.domain.model.DebtEvent
import app.dift.domain.model.DebtState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DebtReducerTest {

    private val config = DebtReducer.Config(maxBurstSeconds = 60)
    private val sec = 1_000L

    private fun tick(inWindow: Boolean = true, using: Boolean = true) =
        DebtEvent.Tick(inWindow = inWindow, using = using)

    private fun reduce(state: DebtState, event: DebtEvent, nowSec: Long) =
        DebtReducer.reduce(state, event, nowSec * sec, config)

    @Test
    fun `using inside window starts a burst`() {
        val state = reduce(DebtState.IDLE, tick(), 1)
        assertEquals(1 * sec, state.burstStartedAtMs)
        assertNull(state.cooldownUntilMs)
    }

    @Test
    fun `burst reaching the cap fires an equal cooldown and clears the burst`() {
        var state = reduce(DebtState.IDLE, tick(), 0)
        state = reduce(state, tick(), 59) // still under the cap
        assertNull(state.cooldownUntilMs)
        state = reduce(state, tick(), 60) // at the cap
        assertNull(state.burstStartedAtMs)
        assertEquals(120 * sec, state.cooldownUntilMs) // 60s used -> 60s cooldown
    }

    @Test
    fun `locking mid-burst banks elapsed time as equal cooldown`() {
        var state = reduce(DebtState.IDLE, tick(), 0)
        state = reduce(state, DebtEvent.Lock, 45)
        assertNull(state.burstStartedAtMs)
        assertEquals(90 * sec, state.cooldownUntilMs) // 45s used -> 45s cooldown
    }

    @Test
    fun `locking with nothing used creates no cooldown`() {
        val state = reduce(DebtState.IDLE, DebtEvent.Lock, 5)
        assertNull(state.cooldownUntilMs)
        assertNull(state.burstStartedAtMs)
    }

    @Test
    fun `an exempt-app pause freezes the accumulator and resuming continues it`() {
        var state = reduce(DebtState.IDLE, tick(), 0)
        state = reduce(state, tick(using = false), 30) // switched to an exempt app
        assertEquals(30 * sec, state.burstElapsedMs)
        assertNull(state.burstStartedAtMs)
        state = reduce(state, tick(using = false), 300) // stayed on it for minutes: frozen
        assertEquals(30 * sec, state.burstElapsedMs)
        state = reduce(state, tick(), 310) // back to a blockable app
        state = reduce(state, tick(), 340) // 30 banked + 30 more = cap
        assertEquals((340 + 60) * sec, state.cooldownUntilMs)
    }

    @Test
    fun `leaving the window cancels the burst without creating debt`() {
        var state = reduce(DebtState.IDLE, tick(), 0)
        state = reduce(state, tick(inWindow = false), 30)
        assertNull(state.burstStartedAtMs)
        assertEquals(0L, state.burstElapsedMs)
        state = reduce(state, DebtEvent.Lock, 35)
        assertNull(state.cooldownUntilMs)
    }

    @Test
    fun `serving a cooldown resets any burst but keeps the deadline`() {
        val debt = DebtState(burstElapsedMs = 5 * sec, cooldownStartedAtMs = 50 * sec, cooldownUntilMs = 100 * sec)
        val state = reduce(debt, tick(), 70)
        assertEquals(0L, state.burstElapsedMs)
        assertEquals(100 * sec, state.cooldownUntilMs)
    }

    @Test
    fun `cooldown clears once its deadline passes`() {
        val debt = DebtState(cooldownStartedAtMs = 50 * sec, cooldownUntilMs = 100 * sec)
        val state = reduce(debt, tick(using = false), 100)
        assertNull(state.cooldownUntilMs)
        assertFalse(state.inCooldown(100 * sec))
    }

    @Test
    fun `locking while serving a cooldown keeps it intact`() {
        val debt = DebtState(cooldownStartedAtMs = 50 * sec, cooldownUntilMs = 100 * sec)
        val state = reduce(debt, DebtEvent.Lock, 70)
        assertEquals(100 * sec, state.cooldownUntilMs)
    }

    @Test
    fun `a persisted cooldown keeps counting down after a reboot`() {
        val restored = DebtState(cooldownStartedAtMs = 0, cooldownUntilMs = 200 * sec)
        val state = reduce(restored, tick(using = false), 150)
        assertEquals(200 * sec, state.cooldownUntilMs)
        assertTrue(state.inCooldown(150 * sec))
    }

    @Test
    fun `rapid lock unlock does not lose the cooldown`() {
        var state = reduce(DebtState.IDLE, tick(), 0)
        state = reduce(state, DebtEvent.Lock, 20) // 20s -> cooldown until 40s
        val deadline = state.cooldownUntilMs
        state = reduce(state, tick(using = false), 25) // "unlocked" briefly, still serving
        assertEquals(deadline, state.cooldownUntilMs)
        assertNull(state.burstStartedAtMs)
    }

    @Test
    fun `cooldown remaining fraction drains from one to zero`() {
        val debt = DebtState(cooldownStartedAtMs = 0, cooldownUntilMs = 100 * sec)
        assertEquals(1f, debt.cooldownRemainingFraction(0), 0.001f)
        assertEquals(0.5f, debt.cooldownRemainingFraction(50 * sec), 0.001f)
        assertEquals(0f, debt.cooldownRemainingFraction(100 * sec), 0.001f)
    }
}
