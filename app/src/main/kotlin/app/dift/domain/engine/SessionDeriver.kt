package app.dift.domain.engine

import app.dift.domain.model.OpenSession
import app.dift.domain.model.Session
import app.dift.domain.model.UsageEvent
import app.dift.domain.model.UsageEventType
import java.time.Instant
import java.time.ZoneId

/**
 * Pure derivation of per-app sessions from a batch of usage events (ADR-0005).
 *
 * A package is "in session" while **at least one of its activities is resumed**, so open state
 * is a per-package set of resumed activity classes. This is what makes within-app navigation
 * safe: Android emits `PAUSED(act1) → RESUMED(act2) → STOPPED(act1)` when moving between two
 * activities of the same app, and a package-keyed model would let that trailing STOPPED close
 * the fresh act2 session — silently dropping all foreground time until the next event (the
 * classic undercount vs Digital Wellbeing). With the class set, the trailing STOPPED removes a
 * class that is no longer in the set and the session survives.
 *
 * - Events without a class (screen events; legacy checkpoints) fall back to close-on-any-pause.
 * - Multiple apps can be open at once (split screen): each package has its own session.
 * - SCREEN_OFF / SHUTDOWN close every open session at that instant.
 * - Sessions still open at the end of the batch are returned as [Result.openSessions] and must
 *   be fed back as [pendingOpen] on the next ingest — this makes checkpointed, incremental
 *   ingestion deterministic and idempotent.
 * - Closed sessions are split at local midnight so `dayLocal` is exact per day.
 */
object SessionDeriver {

    data class Result(
        val sessions: List<Session>,
        val openSessions: List<OpenSession>,
    )

    fun derive(
        events: List<UsageEvent>,
        pendingOpen: List<OpenSession>,
        zone: ZoneId,
    ): Result {
        val state = Derivation(pendingOpen, zone)
        for (event in events.sortedBy { it.timestampMs }) {
            when (event.type) {
                UsageEventType.RESUMED -> state.onResumed(event)
                UsageEventType.PAUSED -> state.onPaused(event)
                UsageEventType.SCREEN_OFF, UsageEventType.SHUTDOWN ->
                    state.closeAll(event.timestampMs)
            }
        }
        return Result(
            sessions = state.sessions,
            openSessions = state.open.map { (pkg, entry) ->
                OpenSession(pkg, entry.startMs, entry.classes.toSet())
            },
        )
    }

    fun dayLocalOf(timestampMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate().toString()

    private class OpenPackage(val startMs: Long, val classes: MutableSet<String>)

    private class Derivation(pendingOpen: List<OpenSession>, private val zone: ZoneId) {
        val open = LinkedHashMap<String, OpenPackage>()
        val sessions = mutableListOf<Session>()

        init {
            pendingOpen.forEach {
                open.putIfAbsent(it.packageName, OpenPackage(it.startMs, it.resumedClasses.toMutableSet()))
            }
        }

        fun onResumed(event: UsageEvent) {
            val pkg = event.packageName ?: return
            val entry = open[pkg]
            if (entry == null) {
                val classes = event.className?.let { mutableSetOf(it) } ?: mutableSetOf()
                open[pkg] = OpenPackage(event.timestampMs, classes)
            } else {
                event.className?.let(entry.classes::add)
            }
        }

        fun onPaused(event: UsageEvent) {
            val pkg = event.packageName ?: return
            val entry = open[pkg] ?: return
            val cls = event.className
            if (cls == null || entry.classes.isEmpty()) {
                // Class-unknown event or a legacy carried session: close on any pause.
                close(pkg, event.timestampMs)
            } else {
                entry.classes.remove(cls)
                if (entry.classes.isEmpty()) close(pkg, event.timestampMs)
            }
        }

        fun closeAll(endMs: Long) {
            open.keys.toList().forEach { close(it, endMs) }
        }

        private fun close(pkg: String, endMs: Long) {
            val entry = open.remove(pkg) ?: return
            sessions += splitAtMidnights(pkg, entry.startMs, endMs, zone)
        }
    }

    private fun splitAtMidnights(
        packageName: String,
        startMs: Long,
        endMs: Long,
        zone: ZoneId,
    ): List<Session> {
        if (endMs <= startMs) return emptyList()
        val out = mutableListOf<Session>()
        var cursor = startMs
        while (cursor < endMs) {
            val day = Instant.ofEpochMilli(cursor).atZone(zone).toLocalDate()
            val nextMidnightMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val segmentEnd = minOf(endMs, nextMidnightMs)
            if (segmentEnd > cursor) {
                out += Session(packageName, cursor, segmentEnd, day.toString())
            }
            cursor = segmentEnd
        }
        return out
    }
}
