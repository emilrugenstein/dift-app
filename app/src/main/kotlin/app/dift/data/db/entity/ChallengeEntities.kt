package app.dift.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A challenge: a named day range with a daily time goal (docs/features/challenges.md). */
@Entity(tableName = "challenges")
data class ChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    /** Inclusive local calendar days, ISO "2026-11-01" — sorts and compares as text. */
    val startDay: String,
    val endDay: String,
    val goalMinutesPerDay: Int,
    val createdAt: Long,
)

/**
 * One logged stretch of challenge time. Timed entries keep their real clock position; a manual
 * (hindsight) entry is anchored at noon of [dayLocal] and only its duration matters.
 */
@Entity(
    tableName = "challenge_entries",
    foreignKeys = [
        ForeignKey(
            entity = ChallengeEntity::class,
            parentColumns = ["id"],
            childColumns = ["challengeId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("challengeId", "dayLocal"), Index("startEpochMs")],
)
data class ChallengeEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val challengeId: Long,
    val startEpochMs: Long,
    val endEpochMs: Long,
    val dayLocal: String,
    val manual: Boolean,
)
