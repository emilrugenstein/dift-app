package app.dift.domain.engine

import app.dift.domain.SafetyDenylist
import app.dift.domain.model.BlockReason
import app.dift.domain.model.DebtState
import app.dift.domain.model.Rule
import app.dift.domain.model.RuleType
import app.dift.domain.model.Strictness
import app.dift.domain.model.UnblockGrant
import java.time.DayOfWeek
import java.time.ZonedDateTime

/**
 * The single place blocking decisions are made (ADR-0004). Pure: time is injected, no I/O.
 *
 * Resolution policy (docs/features/blocking.md):
 * - SafetyDenylist packages are always allowed, no exceptions.
 * - Among matching active rules, the most-strict block wins (HARD > FRICTION > TAP_THROUGH).
 * - An unexpired grant for the package defeats TAP_THROUGH/FRICTION blocks, never HARD.
 * - Schedule windows may wrap midnight; a wrapping window belongs to its START day's mask.
 * - Daily limits reset at local midnight ([EvaluationInput.now]'s zone).
 * - USAGE_DEBT is device-wide, always HARD, and active while debt is being served in-window
 *   (burst-cap firing is decided by the DebtReducer, which feeds [EvaluationInput.debtState]).
 */
object RuleEngine {

    data class EvaluationInput(
        val packageName: String,
        val rules: List<Rule>,
        val usedTodayMs: Long,
        val activeGrants: List<UnblockGrant>,
        val debtState: DebtState,
        val runtimeDenylist: Set<String>,
        val now: ZonedDateTime,
    )

    fun evaluate(input: EvaluationInput): Verdict {
        if (SafetyDenylist.isNeverBlockable(input.packageName, input.runtimeDenylist)) {
            return Verdict.Allow
        }
        val nowMs = input.now.toInstant().toEpochMilli()

        val blocks = input.rules
            .filter { it.enabled && it.appliesTo(input.packageName) }
            .mapNotNull { rule -> blockFor(rule, input, nowMs) }
        if (blocks.isEmpty()) return Verdict.Allow

        val strongest = blocks.maxBy { it.strictness.ordinal }
        val grantActive = input.activeGrants.any {
            it.packageName == input.packageName && it.expiresAtMs > nowMs
        }
        return if (grantActive && strongest.strictness != Strictness.HARD) {
            Verdict.Allow
        } else {
            strongest
        }
    }

    private fun Rule.appliesTo(packageName: String): Boolean =
        deviceWide || packageName in packages

    private fun blockFor(rule: Rule, input: EvaluationInput, nowMs: Long): Verdict.Block? =
        when (rule.type) {
            RuleType.ALWAYS -> block(rule, BlockReason.ALWAYS, blockedUntilMs = null)

            RuleType.DAILY_LIMIT -> {
                val limitMs = (rule.limitMinutes ?: 0) * MINUTE_MS
                if (limitMs in 1..input.usedTodayMs) {
                    block(rule, BlockReason.LIMIT_EXHAUSTED, nextLocalMidnightMs(input.now))
                } else {
                    null
                }
            }

            RuleType.SCHEDULE -> {
                if (scheduleActive(rule, input.now)) {
                    block(rule, BlockReason.IN_SCHEDULE, windowEndMs(rule, input.now))
                } else {
                    null
                }
            }

            RuleType.USAGE_DEBT -> {
                if (input.debtState.debtActiveAt(nowMs)) {
                    Verdict.Block(
                        ruleId = rule.id,
                        reason = BlockReason.USAGE_DEBT,
                        strictness = Strictness.HARD,
                        blockedUntilMs = input.debtState.debtUntilMs,
                        frictionDelaySeconds = rule.frictionDelaySeconds,
                        grantMinutes = 0,
                    )
                } else {
                    null
                }
            }
        }

    private fun block(rule: Rule, reason: BlockReason, blockedUntilMs: Long?): Verdict.Block =
        Verdict.Block(
            ruleId = rule.id,
            reason = reason,
            strictness = rule.strictness,
            blockedUntilMs = blockedUntilMs,
            frictionDelaySeconds = rule.frictionDelaySeconds,
            grantMinutes = rule.grantMinutes,
        )

    // --- schedule windows ---

    fun scheduleActive(rule: Rule, now: ZonedDateTime): Boolean {
        val start = rule.scheduleStartMinuteOfDay ?: return false
        val end = rule.scheduleEndMinuteOfDay ?: return false
        val minuteOfDay = now.hour * MINUTES_PER_HOUR + now.minute
        return if (start <= end) {
            dayEnabled(rule.scheduleDaysMask, now.dayOfWeek) && minuteOfDay in start until end
        } else {
            // Wrapping window (e.g. 22:30 -> 06:00): the stretch after `start` belongs to
            // today's mask; the stretch before `end` belongs to YESTERDAY's mask.
            (minuteOfDay >= start && dayEnabled(rule.scheduleDaysMask, now.dayOfWeek)) ||
                (minuteOfDay < end && dayEnabled(rule.scheduleDaysMask, now.dayOfWeek.minus(1)))
        }
    }

    private fun windowEndMs(rule: Rule, now: ZonedDateTime): Long? {
        val start = rule.scheduleStartMinuteOfDay ?: return null
        val end = rule.scheduleEndMinuteOfDay ?: return null
        val minuteOfDay = now.hour * MINUTES_PER_HOUR + now.minute
        val endToday = now.toLocalDate().atStartOfDay(now.zone).plusMinutes(end.toLong())
        val endInstant = when {
            start <= end -> endToday
            minuteOfDay >= start -> endToday.plusDays(1) // wrapped, before midnight
            else -> endToday // wrapped, after midnight
        }
        return endInstant.toInstant().toEpochMilli()
    }

    private fun nextLocalMidnightMs(now: ZonedDateTime): Long =
        now.toLocalDate().plusDays(1).atStartOfDay(now.zone).toInstant().toEpochMilli()

    private fun dayEnabled(mask: Int?, day: DayOfWeek): Boolean {
        if (mask == null) return true
        return (mask shr (day.value - 1)) and 1 == 1 // bit 0 = Monday
    }

    private const val MINUTE_MS = 60_000L
    private const val MINUTES_PER_HOUR = 60
}
