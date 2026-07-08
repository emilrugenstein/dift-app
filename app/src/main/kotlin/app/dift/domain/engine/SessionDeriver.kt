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
 * - Multiple apps can be open at once (split screen): each RESUMED opens its own session.
 * - SCREEN_OFF / SHUTDOWN close every open session at that instant.
 * - Sessions still open at the end of the batch are returned as [Result.openSessions] and
 *   must be fed back as [pendingOpen] on the next ingest — this makes checkpointed,
 *   incremental ingestion deterministic and idempotent.
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
        val open = LinkedHashMap<String, Long>()
        pendingOpen.forEach { open.putIfAbsent(it.packageName, it.startMs) }
        val sessions = mutableListOf<Session>()

        for (event in events.sortedBy { it.timestampMs }) {
            val pkg = event.packageName
            when (event.type) {
                UsageEventType.RESUMED ->
                    if (pkg != null) open.putIfAbsent(pkg, event.timestampMs)

                UsageEventType.PAUSED -> {
                    val start = if (pkg != null) open.remove(pkg) else null
                    if (pkg != null && start != null) {
                        sessions += splitAtMidnights(pkg, start, event.timestampMs, zone)
                    }
                }

                UsageEventType.SCREEN_OFF, UsageEventType.SHUTDOWN -> {
                    open.forEach { (openPkg, start) ->
                        sessions += splitAtMidnights(openPkg, start, event.timestampMs, zone)
                    }
                    open.clear()
                }
            }
        }

        return Result(
            sessions = sessions,
            openSessions = open.map { (pkg, start) -> OpenSession(pkg, start) },
        )
    }

    fun dayLocalOf(timestampMs: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(timestampMs).atZone(zone).toLocalDate().toString()

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
