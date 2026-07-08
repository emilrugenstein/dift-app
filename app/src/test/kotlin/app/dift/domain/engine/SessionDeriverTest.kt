package app.dift.domain.engine

import app.dift.domain.model.OpenSession
import app.dift.domain.model.UsageEvent
import app.dift.domain.model.UsageEventType.PAUSED
import app.dift.domain.model.UsageEventType.RESUMED
import app.dift.domain.model.UsageEventType.SCREEN_OFF
import app.dift.domain.model.UsageEventType.SHUTDOWN
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneId

class SessionDeriverTest {

    private val zone = ZoneId.of("UTC")

    private fun at(iso: String): Long = Instant.parse(iso).toEpochMilli()

    @Test
    fun `resume then pause produces one session on the right day`() {
        val result = SessionDeriver.derive(
            events = listOf(
                UsageEvent("com.a", RESUMED, at("2026-07-07T10:00:00Z")),
                UsageEvent("com.a", PAUSED, at("2026-07-07T10:30:00Z")),
            ),
            pendingOpen = emptyList(),
            zone = zone,
        )
        assertEquals(1, result.sessions.size)
        val session = result.sessions.single()
        assertEquals("com.a", session.packageName)
        assertEquals(30 * 60_000L, session.durationMs)
        assertEquals("2026-07-07", session.dayLocal)
        assertTrue(result.openSessions.isEmpty())
    }

    @Test
    fun `unmatched resume is carried as open and closed by the next batch`() {
        val first = SessionDeriver.derive(
            events = listOf(UsageEvent("com.a", RESUMED, at("2026-07-07T10:00:00Z"))),
            pendingOpen = emptyList(),
            zone = zone,
        )
        assertTrue(first.sessions.isEmpty())
        assertEquals(listOf(OpenSession("com.a", at("2026-07-07T10:00:00Z"))), first.openSessions)

        val second = SessionDeriver.derive(
            events = listOf(UsageEvent("com.a", PAUSED, at("2026-07-07T10:45:00Z"))),
            pendingOpen = first.openSessions,
            zone = zone,
        )
        assertEquals(1, second.sessions.size)
        assertEquals(at("2026-07-07T10:00:00Z"), second.sessions.single().startMs)
        assertTrue(second.openSessions.isEmpty())
    }

    @Test
    fun `screen off closes every open session`() {
        val result = SessionDeriver.derive(
            events = listOf(
                UsageEvent("com.a", RESUMED, at("2026-07-07T10:00:00Z")),
                UsageEvent("com.b", RESUMED, at("2026-07-07T10:05:00Z")),
                UsageEvent(null, SCREEN_OFF, at("2026-07-07T10:10:00Z")),
            ),
            pendingOpen = emptyList(),
            zone = zone,
        )
        assertEquals(2, result.sessions.size)
        assertTrue(result.openSessions.isEmpty())
        assertEquals(10 * 60_000L, result.sessions.first { it.packageName == "com.a" }.durationMs)
        assertEquals(5 * 60_000L, result.sessions.first { it.packageName == "com.b" }.durationMs)
    }

    @Test
    fun `shutdown closes open sessions like screen off`() {
        val result = SessionDeriver.derive(
            events = listOf(
                UsageEvent("com.a", RESUMED, at("2026-07-07T10:00:00Z")),
                UsageEvent(null, SHUTDOWN, at("2026-07-07T10:03:00Z")),
            ),
            pendingOpen = emptyList(),
            zone = zone,
        )
        assertEquals(1, result.sessions.size)
        assertTrue(result.openSessions.isEmpty())
    }

    @Test
    fun `session crossing local midnight is split with exact day attribution`() {
        val result = SessionDeriver.derive(
            events = listOf(
                UsageEvent("com.a", RESUMED, at("2026-07-07T23:30:00Z")),
                UsageEvent("com.a", PAUSED, at("2026-07-08T00:30:00Z")),
            ),
            pendingOpen = emptyList(),
            zone = zone,
        )
        assertEquals(2, result.sessions.size)
        val (first, second) = result.sessions.sortedBy { it.startMs }
        assertEquals("2026-07-07", first.dayLocal)
        assertEquals(30 * 60_000L, first.durationMs)
        assertEquals("2026-07-08", second.dayLocal)
        assertEquals(30 * 60_000L, second.durationMs)
        assertEquals(first.endMs, second.startMs)
    }

    @Test
    fun `zero length sessions are dropped`() {
        val ts = at("2026-07-07T10:00:00Z")
        val result = SessionDeriver.derive(
            events = listOf(
                UsageEvent("com.a", RESUMED, ts),
                UsageEvent("com.a", PAUSED, ts),
            ),
            pendingOpen = emptyList(),
            zone = zone,
        )
        assertTrue(result.sessions.isEmpty())
    }

    @Test
    fun `duplicate resume keeps the original start`() {
        val result = SessionDeriver.derive(
            events = listOf(
                UsageEvent("com.a", RESUMED, at("2026-07-07T10:00:00Z")),
                UsageEvent("com.a", RESUMED, at("2026-07-07T10:05:00Z")),
                UsageEvent("com.a", PAUSED, at("2026-07-07T10:10:00Z")),
            ),
            pendingOpen = emptyList(),
            zone = zone,
        )
        assertEquals(1, result.sessions.size)
        assertEquals(at("2026-07-07T10:00:00Z"), result.sessions.single().startMs)
    }

    @Test
    fun `pause without a matching open session is ignored`() {
        val result = SessionDeriver.derive(
            events = listOf(UsageEvent("com.a", PAUSED, at("2026-07-07T10:00:00Z"))),
            pendingOpen = emptyList(),
            zone = zone,
        )
        assertTrue(result.sessions.isEmpty())
        assertTrue(result.openSessions.isEmpty())
    }

    @Test
    fun `overlapping apps in split screen are both counted`() {
        val result = SessionDeriver.derive(
            events = listOf(
                UsageEvent("com.a", RESUMED, at("2026-07-07T10:00:00Z")),
                UsageEvent("com.b", RESUMED, at("2026-07-07T10:00:00Z")),
                UsageEvent("com.a", PAUSED, at("2026-07-07T10:10:00Z")),
                UsageEvent("com.b", PAUSED, at("2026-07-07T10:20:00Z")),
            ),
            pendingOpen = emptyList(),
            zone = zone,
        )
        assertEquals(10 * 60_000L, result.sessions.first { it.packageName == "com.a" }.durationMs)
        assertEquals(20 * 60_000L, result.sessions.first { it.packageName == "com.b" }.durationMs)
    }

    @Test
    fun `derivation is idempotent for the same inputs`() {
        val events = listOf(
            UsageEvent("com.a", RESUMED, at("2026-07-07T10:00:00Z")),
            UsageEvent("com.a", PAUSED, at("2026-07-07T10:30:00Z")),
        )
        val once = SessionDeriver.derive(events, emptyList(), zone)
        val twice = SessionDeriver.derive(events, emptyList(), zone)
        assertEquals(once, twice)
    }
}
