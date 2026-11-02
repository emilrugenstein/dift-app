package app.dift.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import app.dift.data.db.entity.ChallengeEntity
import app.dift.data.db.entity.ChallengeEntryEntity
import kotlinx.coroutines.flow.Flow

/** Challenges and their logged entries (docs/features/challenges.md). */
@Dao
interface ChallengeDao {

    @Query("SELECT * FROM challenges ORDER BY startDay DESC, id DESC")
    fun observeChallenges(): Flow<List<ChallengeEntity>>

    @Query("SELECT * FROM challenges WHERE id = :id")
    suspend fun challengeById(id: Long): ChallengeEntity?

    @Query("SELECT * FROM challenges ORDER BY id")
    suspend fun allChallenges(): List<ChallengeEntity>

    @Query("SELECT * FROM challenge_entries ORDER BY startEpochMs")
    suspend fun allEntries(): List<ChallengeEntryEntity>

    @Insert
    suspend fun insertChallenge(challenge: ChallengeEntity): Long

    @Update
    suspend fun updateChallenge(challenge: ChallengeEntity)

    /** Entries go with it (FK cascade). */
    @Query("DELETE FROM challenges WHERE id = :id")
    suspend fun deleteChallenge(id: Long)

    @Insert
    suspend fun insertEntry(entry: ChallengeEntryEntity): Long

    @Query("DELETE FROM challenge_entries WHERE id = :id")
    suspend fun deleteEntry(id: Long)

    @Query("SELECT * FROM challenge_entries WHERE challengeId = :challengeId ORDER BY startEpochMs DESC")
    fun observeEntries(challengeId: Long): Flow<List<ChallengeEntryEntity>>

    @Query(
        "SELECT dayLocal, SUM(endEpochMs - startEpochMs) AS totalMs FROM challenge_entries " +
            "WHERE challengeId = :challengeId GROUP BY dayLocal",
    )
    fun observeDayTotals(challengeId: Long): Flow<List<DayTotal>>

    /** Timed entries (real clock positions) overlapping [fromMs, toMs) — the overview's night view. */
    @Query(
        "SELECT * FROM challenge_entries WHERE manual = 0 AND endEpochMs > :fromMs AND startEpochMs < :toMs " +
            "ORDER BY startEpochMs",
    )
    suspend fun timedEntriesInRange(fromMs: Long, toMs: Long): List<ChallengeEntryEntity>

    /** All entries (manual included) summed per day in [fromDayLocal, toDayLocal] — the totals view. */
    @Query(
        "SELECT dayLocal, SUM(endEpochMs - startEpochMs) AS totalMs FROM challenge_entries " +
            "WHERE dayLocal >= :fromDayLocal AND dayLocal <= :toDayLocal GROUP BY dayLocal",
    )
    suspend fun dayTotalsInRange(fromDayLocal: String, toDayLocal: String): List<DayTotal>
}
