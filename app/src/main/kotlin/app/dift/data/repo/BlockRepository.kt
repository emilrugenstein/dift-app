package app.dift.data.repo

import app.dift.data.db.dao.BlockDao
import app.dift.data.db.entity.BlockRuleEntity
import app.dift.domain.model.Block
import app.dift.domain.model.RuleType
import app.dift.domain.model.Strictness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes usage-debt [Block]s. The storage entity ([BlockRuleEntity]) keeps its v1
 * columns; the vestigial ones are written with fixed constants here (every block is a device-wide
 * HARD USAGE_DEBT rule) and ignored on read. `rule_apps` carries the exempt package set.
 */
@Singleton
class BlockRepository @Inject constructor(
    private val blockDao: BlockDao,
) {

    val blocks: Flow<List<Block>> = combine(
        blockDao.observeBlocks(),
        blockDao.observeExemptApps(),
    ) { entities, refs ->
        val exemptByBlock = refs.groupBy({ it.ruleId }, { it.packageName })
        entities.map { it.toDomain(exemptByBlock[it.id]?.toSet() ?: emptySet()) }
    }

    suspend fun upsert(block: Block, nowMs: Long): Long {
        val existing = if (block.id != 0L) blockDao.blockById(block.id) else null
        val entity = block.toEntity(createdAt = existing?.createdAt ?: nowMs, updatedAt = nowMs)
        return blockDao.upsertBlockWithExempts(entity, block.exemptPackages)
    }

    suspend fun setEnabled(blockId: Long, enabled: Boolean, nowMs: Long) =
        blockDao.setEnabled(blockId, enabled, nowMs)

    suspend fun delete(blockId: Long) {
        blockDao.blockById(blockId)?.let { blockDao.deleteBlock(it) }
    }

    private fun BlockRuleEntity.toDomain(exempt: Set<String>) = Block(
        id = id,
        name = name,
        enabled = enabled,
        daysMask = scheduleDaysMask ?: Block.ALL_DAYS,
        startMinuteOfDay = scheduleStartMinuteOfDay ?: Block.DEFAULT_START_MINUTE,
        endMinuteOfDay = scheduleEndMinuteOfDay ?: Block.DEFAULT_END_MINUTE,
        maxBurstSeconds = maxBurstSeconds ?: Block.DEFAULT_MAX_BURST_SECONDS,
        exemptPackages = exempt,
    )

    private fun Block.toEntity(createdAt: Long, updatedAt: Long) = BlockRuleEntity(
        id = id,
        name = name,
        type = RuleType.USAGE_DEBT,
        enabled = enabled,
        strictness = Strictness.HARD,
        deviceWide = true,
        limitMinutes = null,
        scheduleStartMinuteOfDay = startMinuteOfDay,
        scheduleEndMinuteOfDay = endMinuteOfDay,
        scheduleDaysMask = daysMask,
        maxBurstSeconds = maxBurstSeconds,
        debtRatio = null,
        frictionPhrase = null,
        grantMinutes = 0,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
