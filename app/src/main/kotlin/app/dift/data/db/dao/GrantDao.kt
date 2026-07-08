package app.dift.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import app.dift.data.db.entity.UnblockGrantEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface GrantDao {

    @Insert
    suspend fun insert(grant: UnblockGrantEntity)

    @Query("SELECT * FROM unblock_grants WHERE expiresAtEpochMs > :nowMs")
    fun observeActive(nowMs: Long): Flow<List<UnblockGrantEntity>>

    @Query("SELECT * FROM unblock_grants WHERE expiresAtEpochMs > :nowMs")
    suspend fun activeAt(nowMs: Long): List<UnblockGrantEntity>

    @Query("DELETE FROM unblock_grants WHERE expiresAtEpochMs <= :nowMs")
    suspend fun pruneExpired(nowMs: Long)
}
