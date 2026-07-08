package app.dift.data.repo

import app.dift.data.db.dao.RuleDao
import app.dift.data.db.entity.BlockRuleEntity
import app.dift.domain.model.Rule
import app.dift.domain.model.RuleType
import app.dift.domain.model.Strictness
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RuleRepository @Inject constructor(
    private val ruleDao: RuleDao,
) {

    /** Domain rules with their package sets joined in. */
    val rules: Flow<List<Rule>> = combine(
        ruleDao.observeRules(),
        ruleDao.observeRuleApps(),
    ) { entities, refs ->
        val packagesByRule = refs.groupBy({ it.ruleId }, { it.packageName })
        entities.map { it.toDomain(packagesByRule[it.id]?.toSet() ?: emptySet()) }
    }

    suspend fun upsert(rule: Rule, nowMs: Long): Long {
        val existing = if (rule.id != 0L) ruleDao.ruleById(rule.id) else null
        val entity = rule.toEntity(createdAt = existing?.createdAt ?: nowMs, updatedAt = nowMs)
        return ruleDao.upsertRuleWithApps(entity, rule.packages)
    }

    suspend fun setEnabled(ruleId: Long, enabled: Boolean, nowMs: Long) =
        ruleDao.setEnabled(ruleId, enabled, nowMs)

    suspend fun delete(ruleId: Long) {
        ruleDao.ruleById(ruleId)?.let { ruleDao.deleteRule(it) }
    }

    /**
     * Quick "block always" toggle from the Apps screen: maintains one ALWAYS rule per package,
     * named after the app. Returns true if the package is now blocked.
     */
    suspend fun quickBlockToggle(packageName: String, label: String, nowMs: Long): Boolean {
        val current = rules.first().firstOrNull {
            it.type == RuleType.ALWAYS && it.packages == setOf(packageName)
        }
        return if (current == null) {
            upsert(
                Rule(
                    id = 0,
                    name = label,
                    type = RuleType.ALWAYS,
                    enabled = true,
                    strictness = Strictness.FRICTION,
                    packages = setOf(packageName),
                ),
                nowMs,
            )
            true
        } else {
            delete(current.id)
            false
        }
    }

    private fun BlockRuleEntity.toDomain(packages: Set<String>) = Rule(
        id = id,
        name = name,
        type = type,
        enabled = enabled,
        strictness = strictness,
        packages = packages,
        deviceWide = deviceWide,
        limitMinutes = limitMinutes,
        scheduleStartMinuteOfDay = scheduleStartMinuteOfDay,
        scheduleEndMinuteOfDay = scheduleEndMinuteOfDay,
        scheduleDaysMask = scheduleDaysMask,
        maxBurstSeconds = maxBurstSeconds,
        debtRatio = debtRatio,
        frictionDelaySeconds = frictionDelaySeconds,
        frictionPhrase = frictionPhrase,
        grantMinutes = grantMinutes,
    )

    private fun Rule.toEntity(createdAt: Long, updatedAt: Long) = BlockRuleEntity(
        id = id,
        name = name,
        type = type,
        enabled = enabled,
        strictness = strictness,
        deviceWide = deviceWide,
        limitMinutes = limitMinutes,
        scheduleStartMinuteOfDay = scheduleStartMinuteOfDay,
        scheduleEndMinuteOfDay = scheduleEndMinuteOfDay,
        scheduleDaysMask = scheduleDaysMask,
        maxBurstSeconds = maxBurstSeconds,
        debtRatio = debtRatio,
        frictionDelaySeconds = frictionDelaySeconds,
        frictionPhrase = frictionPhrase,
        grantMinutes = grantMinutes,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
}
