package app.dift.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Closed foreground sessions, pre-split at local midnight (docs/DATA_MODEL.md). */
@Entity(
    tableName = "usage_sessions",
    indices = [Index("dayLocal", "packageName"), Index("endEpochMs")],
)
data class UsageSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val dayLocal: String,
)

/** Per-day per-app rollup, recomputed transactionally by the ingester. */
@Entity(tableName = "daily_usage", primaryKeys = ["dayLocal", "packageName"])
data class DailyUsageEntity(
    val dayLocal: String,
    val packageName: String,
    val totalMs: Long,
    val sessionCount: Int,
)
