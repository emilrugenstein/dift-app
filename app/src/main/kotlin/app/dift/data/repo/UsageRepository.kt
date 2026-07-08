package app.dift.data.repo

import app.dift.data.db.dao.DayTotal
import app.dift.data.db.dao.UsageDao
import app.dift.data.db.entity.DailyUsageEntity
import app.dift.data.db.entity.UsageSessionEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UsageRepository @Inject constructor(
    private val usageDao: UsageDao,
) {
    fun observeDay(dayLocal: String): Flow<List<DailyUsageEntity>> = usageDao.observeDay(dayLocal)

    fun observeDayTotals(fromDayLocal: String): Flow<List<DayTotal>> =
        usageDao.observeDayTotals(fromDayLocal)

    suspend fun totalFor(dayLocal: String, packageName: String): Long =
        usageDao.totalFor(dayLocal, packageName) ?: 0L

    suspend fun sessionsInRange(fromMs: Long, toMs: Long): List<UsageSessionEntity> =
        usageDao.sessionsInRange(fromMs, toMs)

    suspend fun earliestSessionDay(): String? = usageDao.earliestSessionDay()
}
