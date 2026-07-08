package app.dift.data.repo

import app.dift.data.db.dao.GrantDao
import app.dift.data.db.entity.UnblockGrantEntity
import app.dift.domain.model.GrantMethod
import app.dift.domain.model.UnblockGrant
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GrantRepository @Inject constructor(
    private val grantDao: GrantDao,
) {
    fun observeActive(nowMs: Long): Flow<List<UnblockGrant>> =
        grantDao.observeActive(nowMs).map { grants ->
            grants.map { UnblockGrant(it.packageName, it.ruleId, it.expiresAtEpochMs) }
        }

    suspend fun activeAt(nowMs: Long): List<UnblockGrant> =
        grantDao.activeAt(nowMs).map { UnblockGrant(it.packageName, it.ruleId, it.expiresAtEpochMs) }

    suspend fun grant(
        packageName: String,
        ruleId: Long,
        method: GrantMethod,
        durationMinutes: Int,
        nowMs: Long,
    ) {
        grantDao.insert(
            UnblockGrantEntity(
                packageName = packageName,
                ruleId = ruleId,
                grantedAtEpochMs = nowMs,
                expiresAtEpochMs = nowMs + durationMinutes * MINUTE_MS,
                method = method,
            ),
        )
    }

    private companion object {
        const val MINUTE_MS = 60_000L
    }
}
