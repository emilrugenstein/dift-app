package app.dift.data.repo

import app.dift.data.db.dao.BlockEventDao
import app.dift.data.db.entity.BlockEventEntity
import app.dift.domain.model.BlockOutcome
import app.dift.domain.model.BlockReason
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BlockEventRepository @Inject constructor(
    private val blockEventDao: BlockEventDao,
) {
    fun observeRecent(limit: Int = 100): Flow<List<BlockEventEntity>> =
        blockEventDao.observeRecent(limit)

    fun observeShownCountSince(fromMs: Long): Flow<Int> =
        blockEventDao.observeShownCountSince(fromMs)

    suspend fun log(
        packageName: String,
        ruleId: Long?,
        reason: BlockReason,
        outcome: BlockOutcome,
        nowMs: Long,
        detectionLatencyMs: Long? = null,
    ) {
        blockEventDao.insert(
            BlockEventEntity(
                packageName = packageName,
                ruleId = ruleId,
                timestampEpochMs = nowMs,
                reason = reason,
                outcome = outcome,
                detectionLatencyMs = detectionLatencyMs,
            ),
        )
    }
}
