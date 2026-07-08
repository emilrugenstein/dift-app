package app.dift.system.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import app.dift.data.datastore.SettingsRepository
import app.dift.data.db.DiftDatabase
import app.dift.domain.engine.SessionDeriver
import app.dift.system.ingest.UsageStatsIngester
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.time.ZoneId
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Periodic incremental ingest so "used today" is never stale by more than ~15 min. */
@HiltWorker
class IngestWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ingester: UsageStatsIngester,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ingester.ingestNow()
        return Result.success()
    }
}

/** Nightly finalization: one last ingest, then prune history beyond the retention window. */
@HiltWorker
class RollupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val ingester: UsageStatsIngester,
    private val database: DiftDatabase,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        ingester.ingestNow()
        val retentionDays = settings.retentionDays.first()
        val now = System.currentTimeMillis()
        val cutoffDay = SessionDeriver.dayLocalOf(
            now - retentionDays * DAY_MS,
            ZoneId.systemDefault(),
        )
        database.usageDao().pruneSessionsBefore(cutoffDay)
        database.usageDao().pruneDailyBefore(cutoffDay)
        database.blockEventDao().pruneBefore(now - retentionDays * DAY_MS)
        database.grantDao().pruneExpired(now)
        return Result.success()
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

@Singleton
class WorkScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun ensureScheduled() {
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniquePeriodicWork(
            "ingest",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<IngestWorker>(15, TimeUnit.MINUTES).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            "rollup",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<RollupWorker>(24, TimeUnit.HOURS).build(),
        )
        workManager.enqueueUniquePeriodicWork(
            "watchdog",
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WatchdogWorker>(15, TimeUnit.MINUTES).build(),
        )
    }
}
