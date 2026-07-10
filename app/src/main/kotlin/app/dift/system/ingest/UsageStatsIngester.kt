package app.dift.system.ingest

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import androidx.room.withTransaction
import app.dift.data.datastore.SettingsRepository
import app.dift.data.db.DiftDatabase
import app.dift.data.db.entity.UsageSessionEntity
import app.dift.domain.engine.SessionDeriver
import app.dift.domain.model.UsageEvent
import app.dift.domain.model.UsageEventType
import app.dift.system.permissions.PermissionsChecker
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Checkpointed, idempotent ingestion of usage events into Room (ADR-0005).
 * Reads events since `lastIngestedEventTime`, derives sessions via the pure SessionDeriver
 * (open sessions carried in the checkpoint), and recomputes affected daily rollups in one
 * transaction. Safe to call from workers, the dashboard, and the blocking path concurrently.
 */
@Singleton
class UsageStatsIngester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: DiftDatabase,
    private val settings: SettingsRepository,
    private val permissionsChecker: PermissionsChecker,
) {
    private val mutex = Mutex()

    suspend fun ingestNow() {
        if (!permissionsChecker.state.value.usageAccess) return
        mutex.withLock {
            val now = System.currentTimeMillis()
            val checkpoint = settings.lastIngestedEventTime.first()
                .takeIf { it > 0 } ?: (now - INITIAL_LOOKBACK_MS)
            if (now <= checkpoint) return

            val events = readEvents(beginMs = checkpoint + 1, endMs = now)
            val pendingOpen = settings.openSessions.first()
            val result = SessionDeriver.derive(events, pendingOpen, ZoneId.systemDefault())

            val usageDao = database.usageDao()
            database.withTransaction {
                if (result.sessions.isNotEmpty()) {
                    usageDao.insertSessions(
                        result.sessions.map {
                            UsageSessionEntity(
                                packageName = it.packageName,
                                startEpochMs = it.startMs,
                                endEpochMs = it.endMs,
                                dayLocal = it.dayLocal,
                            )
                        },
                    )
                    result.sessions.map { it.dayLocal }.distinct().forEach { usageDao.recomputeDay(it) }
                }
            }
            settings.setIngestCheckpoint(now, result.openSessions)
        }
    }

    private fun readEvents(beginMs: Long, endMs: Long): List<UsageEvent> {
        val manager = context.getSystemService(UsageStatsManager::class.java)
        val usageEvents = manager.queryEvents(beginMs, endMs) ?: return emptyList()
        val out = mutableListOf<UsageEvent>()
        val event = UsageEvents.Event()
        while (usageEvents.hasNextEvent()) {
            usageEvents.getNextEvent(event)
            val type = when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> UsageEventType.RESUMED
                UsageEvents.Event.ACTIVITY_PAUSED,
                UsageEvents.Event.ACTIVITY_STOPPED,
                -> UsageEventType.PAUSED
                UsageEvents.Event.SCREEN_NON_INTERACTIVE -> UsageEventType.SCREEN_OFF
                UsageEvents.Event.DEVICE_SHUTDOWN -> UsageEventType.SHUTDOWN
                else -> null
            }
            if (type != null) {
                out += UsageEvent(event.packageName, type, event.timeStamp, event.className)
            }
        }
        return out
    }

    private companion object {
        const val INITIAL_LOOKBACK_MS = 24L * 60 * 60 * 1000
    }
}
