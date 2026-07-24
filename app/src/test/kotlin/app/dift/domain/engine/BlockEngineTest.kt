package app.dift.domain.engine

import app.dift.domain.model.Block
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class BlockEngineTest {

    private val zone = ZoneId.of("UTC")

    private fun at(iso: String): ZonedDateTime = ZonedDateTime.parse(iso).withZoneSameInstant(zone)

    private fun block(
        id: Long = 1,
        enabled: Boolean = true,
        daysMask: Int = Block.ALL_DAYS,
        start: Int = 9 * 60,
        end: Int = 17 * 60,
        maxBurst: Int = 60,
        exempt: Set<String> = emptySet(),
    ) = Block(
        id = id,
        name = "b$id",
        enabled = enabled,
        daysMask = daysMask,
        startMinuteOfDay = start,
        endMinuteOfDay = end,
        maxBurstSeconds = maxBurst,
        exemptPackages = exempt,
    )

    @Test
    fun `non-wrapping window is active only inside it`() {
        val b = block(start = 9 * 60, end = 17 * 60)
        assertTrue(BlockEngine.windowActive(b, at("2026-07-07T12:00:00Z"))) // Tuesday noon
        assertFalse(BlockEngine.windowActive(b, at("2026-07-07T18:00:00Z")))
    }

    @Test
    fun `wrapping window spans midnight and belongs to the start day's mask`() {
        val tuesday = 1 shl 1
        val b = block(daysMask = tuesday, start = 22 * 60 + 30, end = 6 * 60)
        assertTrue(BlockEngine.windowActive(b, at("2026-07-07T23:00:00Z"))) // Tue 23:00
        assertTrue(BlockEngine.windowActive(b, at("2026-07-08T05:00:00Z"))) // Wed 05:00, still Tue's night
        assertFalse(BlockEngine.windowActive(b, at("2026-07-08T23:00:00Z"))) // Wed not in mask
        assertFalse(BlockEngine.windowActive(b, at("2026-07-07T05:00:00Z"))) // Tue 05:00 belongs to Mon
    }

    @Test
    fun `strictest active block wins - smallest max burst`() {
        val lenient = block(id = 1, start = 22 * 60, end = 6 * 60, maxBurst = 60)
        val strict = block(id = 2, start = 22 * 60, end = 6 * 60, maxBurst = 30)
        val winner = BlockEngine.activeBlock(listOf(lenient, strict), at("2026-07-07T23:00:00Z"))
        assertEquals(2L, winner?.id)
    }

    @Test
    fun `ties on max burst are broken by lowest id`() {
        val a = block(id = 5, start = 22 * 60, end = 6 * 60, maxBurst = 30)
        val b = block(id = 3, start = 22 * 60, end = 6 * 60, maxBurst = 30)
        val winner = BlockEngine.activeBlock(listOf(a, b), at("2026-07-07T23:00:00Z"))
        assertEquals(3L, winner?.id)
    }

    @Test
    fun `no active block outside every window`() {
        val b = block(start = 22 * 60, end = 6 * 60)
        assertNull(BlockEngine.activeBlock(listOf(b), at("2026-07-07T12:00:00Z")))
    }

    @Test
    fun `disabled blocks never win`() {
        val b = block(enabled = false, start = 22 * 60, end = 6 * 60)
        assertNull(BlockEngine.activeBlock(listOf(b), at("2026-07-07T23:00:00Z")))
    }

    @Test
    fun `blockable respects safety denylist, exemptions and the null-block case`() {
        val b = block(exempt = setOf("com.exempt"))
        assertTrue(BlockEngine.isBlockable("com.instagram", b, emptySet()))
        assertFalse(BlockEngine.isBlockable("com.exempt", b, emptySet()))
        assertFalse(BlockEngine.isBlockable("com.android.phone", b, emptySet())) // static safety
        assertFalse(BlockEngine.isBlockable("com.fp.launcher", b, setOf("com.fp.launcher"))) // runtime
        assertTrue(BlockEngine.isBlockable("com.instagram", null, emptySet())) // cooldown past window
    }

    @Test
    fun `cooldown escapes are the safety set and exemptions, but never the launcher`() {
        val b = block(exempt = setOf("com.exempt"))
        val escapes = setOf("com.fp.dialer", "com.fp.keyboard") // runtime denylist WITHOUT launchers
        assertTrue(BlockEngine.cooldownEscaped("com.android.phone", b, escapes)) // static safety
        assertTrue(BlockEngine.cooldownEscaped("com.fp.dialer", b, escapes)) // runtime dialer
        assertTrue(BlockEngine.cooldownEscaped("com.exempt", b, escapes)) // block exemption
        assertFalse(BlockEngine.cooldownEscaped("com.fp.launcher", b, escapes)) // home gets no pass
        assertFalse(BlockEngine.cooldownEscaped("com.instagram", b, escapes))
        // A cooldown outliving its window keeps only the safety escapes.
        assertFalse(BlockEngine.cooldownEscaped("com.exempt", null, escapes))
        assertTrue(BlockEngine.cooldownEscaped("com.fp.dialer", null, escapes))
    }
}
