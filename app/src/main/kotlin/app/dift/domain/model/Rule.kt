package app.dift.domain.model

enum class RuleType { ALWAYS, DAILY_LIMIT, SCHEDULE, USAGE_DEBT }

enum class BlockReason { ALWAYS, LIMIT_EXHAUSTED, IN_SCHEDULE, USAGE_DEBT }

enum class BlockOutcome { SHOWN, UNBLOCKED_TAP, UNBLOCKED_FRICTION, ABANDONED, HOME_KICKED, DEBT_SERVED }

enum class GrantMethod { TAP, FRICTION }

/**
 * Domain view of a block rule (see docs/DATA_MODEL.md for field semantics).
 * Schedule windows may wrap midnight; a wrapping window belongs to its START day's weekday
 * mask (bit 0 = Monday). USAGE_DEBT rules are always deviceWide and HARD.
 */
data class Rule(
    val id: Long,
    val name: String,
    val type: RuleType,
    val enabled: Boolean,
    val strictness: Strictness,
    val packages: Set<String>,
    val deviceWide: Boolean = false,
    val limitMinutes: Int? = null,
    val scheduleStartMinuteOfDay: Int? = null,
    val scheduleEndMinuteOfDay: Int? = null,
    val scheduleDaysMask: Int? = null,
    val maxBurstSeconds: Int? = null,
    val debtRatio: Float? = null,
    val frictionDelaySeconds: Int = DEFAULT_FRICTION_DELAY_SECONDS,
    val frictionPhrase: String? = null,
    val grantMinutes: Int = DEFAULT_GRANT_MINUTES,
) {
    companion object {
        const val DEFAULT_FRICTION_DELAY_SECONDS = 30
        const val DEFAULT_GRANT_MINUTES = 10
        const val DEFAULT_MAX_BURST_SECONDS = 60
        const val DEFAULT_DEBT_RATIO = 1.0f
    }
}

/** A temporary exemption produced by a successful TAP/FRICTION unblock. Never defeats HARD. */
data class UnblockGrant(
    val packageName: String,
    val ruleId: Long,
    val expiresAtMs: Long,
)
