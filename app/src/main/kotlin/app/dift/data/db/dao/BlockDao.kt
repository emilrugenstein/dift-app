package app.dift.data.db.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import app.dift.data.db.entity.BlockRuleEntity
import app.dift.data.db.entity.RuleAppCrossRef
import kotlinx.coroutines.flow.Flow

/**
 * Access to usage-debt blocks. Backed by the v1 tables (`block_rules`, `rule_apps`); the queries
 * filter to `type = 'USAGE_DEBT'` so retired rule kinds can never surface even if one slipped past
 * [app.dift.data.db.MIGRATION_1_2]. `rule_apps` now stores each block's **exempt** packages.
 */
@Dao
interface BlockDao {

    @Query("SELECT * FROM block_rules WHERE type = 'USAGE_DEBT' ORDER BY createdAt")
    fun observeBlocks(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM rule_apps")
    fun observeExemptApps(): Flow<List<RuleAppCrossRef>>

    @Query("SELECT * FROM block_rules WHERE id = :id AND type = 'USAGE_DEBT'")
    suspend fun blockById(id: Long): BlockRuleEntity?

    @Insert
    suspend fun insertBlock(block: BlockRuleEntity): Long

    @Update
    suspend fun updateBlock(block: BlockRuleEntity)

    @Insert
    suspend fun insertExemptApps(refs: List<RuleAppCrossRef>)

    @Query("DELETE FROM rule_apps WHERE ruleId = :blockId")
    suspend fun deleteExemptApps(blockId: Long)

    @Delete
    suspend fun deleteBlock(block: BlockRuleEntity)

    @Query("UPDATE block_rules SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :blockId")
    suspend fun setEnabled(blockId: Long, enabled: Boolean, updatedAt: Long)

    @Transaction
    suspend fun upsertBlockWithExempts(block: BlockRuleEntity, exemptPackages: Set<String>): Long {
        val id = if (block.id == 0L) {
            insertBlock(block)
        } else {
            updateBlock(block)
            deleteExemptApps(block.id)
            block.id
        }
        insertExemptApps(exemptPackages.map { RuleAppCrossRef(id, it) })
        return id
    }
}
