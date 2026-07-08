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

@Dao
interface RuleDao {

    @Query("SELECT * FROM block_rules ORDER BY createdAt")
    fun observeRules(): Flow<List<BlockRuleEntity>>

    @Query("SELECT * FROM rule_apps")
    fun observeRuleApps(): Flow<List<RuleAppCrossRef>>

    @Query("SELECT * FROM block_rules WHERE id = :id")
    suspend fun ruleById(id: Long): BlockRuleEntity?

    @Query("SELECT packageName FROM rule_apps WHERE ruleId = :ruleId")
    suspend fun packagesFor(ruleId: Long): List<String>

    @Insert
    suspend fun insertRule(rule: BlockRuleEntity): Long

    @Update
    suspend fun updateRule(rule: BlockRuleEntity)

    @Insert
    suspend fun insertRuleApps(refs: List<RuleAppCrossRef>)

    @Query("DELETE FROM rule_apps WHERE ruleId = :ruleId")
    suspend fun deleteRuleApps(ruleId: Long)

    @Delete
    suspend fun deleteRule(rule: BlockRuleEntity)

    @Query("UPDATE block_rules SET enabled = :enabled, updatedAt = :updatedAt WHERE id = :ruleId")
    suspend fun setEnabled(ruleId: Long, enabled: Boolean, updatedAt: Long)

    @Transaction
    suspend fun upsertRuleWithApps(rule: BlockRuleEntity, packages: Set<String>): Long {
        val id = if (rule.id == 0L) {
            insertRule(rule)
        } else {
            updateRule(rule)
            deleteRuleApps(rule.id)
            rule.id
        }
        insertRuleApps(packages.map { RuleAppCrossRef(id, it) })
        return id
    }
}
