package app.dift.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.dift.data.db.entity.BlockEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockEventDao {

    @Insert
    suspend fun insert(event: BlockEventEntity)

    @Query("SELECT * FROM block_events ORDER BY timestampEpochMs DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<BlockEventEntity>>

    @Query(
        "SELECT COUNT(*) FROM block_events WHERE timestampEpochMs >= :fromMs " +
            "AND outcome = 'SHOWN'",
    )
    fun observeShownCountSince(fromMs: Long): Flow<Int>

    @Query("DELETE FROM block_events WHERE timestampEpochMs < :cutoffMs")
    suspend fun pruneBefore(cutoffMs: Long)
}
