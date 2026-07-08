package app.dift.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import app.dift.domain.model.BlockOutcome
import app.dift.domain.model.BlockReason
import app.dift.domain.model.GrantMethod
import app.dift.domain.model.RuleType
import app.dift.domain.model.Strictness

/** Block rules; field semantics in docs/DATA_MODEL.md. */
@Entity(tableName = "block_rules")
data class BlockRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: RuleType,
    val enabled: Boolean = true,
    val strictness: Strictness,
    val deviceWide: Boolean = false,
    val limitMinutes: Int? = null,
    val scheduleStartMinuteOfDay: Int? = null,
    val scheduleEndMinuteOfDay: Int? = null,
    val scheduleDaysMask: Int? = null,
    val maxBurstSeconds: Int? = null,
    val debtRatio: Float? = null,
    val frictionDelaySeconds: Int = 30,
    val frictionPhrase: String? = null,
    val grantMinutes: Int = 10,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "rule_apps",
    primaryKeys = ["ruleId", "packageName"],
    foreignKeys = [
        ForeignKey(
            entity = BlockRuleEntity::class,
            parentColumns = ["id"],
            childColumns = ["ruleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("packageName")],
)
data class RuleAppCrossRef(
    val ruleId: Long,
    val packageName: String,
)

@Entity(
    tableName = "unblock_grants",
    indices = [Index("packageName", "expiresAtEpochMs")],
)
data class UnblockGrantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val ruleId: Long,
    val grantedAtEpochMs: Long,
    val expiresAtEpochMs: Long,
    val method: GrantMethod,
)

@Entity(
    tableName = "block_events",
    indices = [Index("timestampEpochMs")],
)
data class BlockEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val ruleId: Long?,
    val timestampEpochMs: Long,
    val reason: BlockReason,
    val outcome: BlockOutcome,
    val detectionLatencyMs: Long? = null,
)
