package app.dift.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.dift.data.db.entity.DailyUsageEntity
import app.dift.data.db.entity.UsageSessionEntity
import kotlinx.coroutines.flow.Flow

data class DayTotal(val dayLocal: String, val totalMs: Long)

@Dao
interface UsageDao {

    @Insert
    suspend fun insertSessions(sessions: List<UsageSessionEntity>)

    /** Recompute one day's rollup from its sessions (called inside the ingest transaction). */
    @Query(
        "INSERT OR REPLACE INTO daily_usage (dayLocal, packageName, totalMs, sessionCount) " +
            "SELECT dayLocal, packageName, SUM(endEpochMs - startEpochMs), COUNT(*) " +
            "FROM usage_sessions WHERE dayLocal = :dayLocal GROUP BY packageName",
    )
    suspend fun recomputeDay(dayLocal: String)

    @Query("SELECT * FROM daily_usage WHERE dayLocal = :dayLocal ORDER BY totalMs DESC")
    fun observeDay(dayLocal: String): Flow<List<DailyUsageEntity>>

    @Query(
        "SELECT dayLocal, SUM(totalMs) AS totalMs FROM daily_usage " +
            "WHERE dayLocal >= :fromDayLocal GROUP BY dayLocal ORDER BY dayLocal",
    )
    fun observeDayTotals(fromDayLocal: String): Flow<List<DayTotal>>

    @Query("SELECT totalMs FROM daily_usage WHERE dayLocal = :dayLocal AND packageName = :packageName")
    suspend fun totalFor(dayLocal: String, packageName: String): Long?

    /** Closed sessions overlapping [fromMs, toMs) — feeds the night-aligned overview chart. */
    @Query(
        "SELECT * FROM usage_sessions WHERE endEpochMs > :fromMs AND startEpochMs < :toMs " +
            "ORDER BY startEpochMs",
    )
    suspend fun sessionsInRange(fromMs: Long, toMs: Long): List<UsageSessionEntity>

    /** Earliest recorded day ("2026-07-05"), or null when there is no usage yet. Bounds the pager. */
    @Query("SELECT MIN(dayLocal) FROM usage_sessions")
    suspend fun earliestSessionDay(): String?

    @Query("DELETE FROM usage_sessions WHERE dayLocal < :cutoffDayLocal")
    suspend fun pruneSessionsBefore(cutoffDayLocal: String)

    @Query("DELETE FROM daily_usage WHERE dayLocal < :cutoffDayLocal")
    suspend fun pruneDailyBefore(cutoffDayLocal: String)
}
