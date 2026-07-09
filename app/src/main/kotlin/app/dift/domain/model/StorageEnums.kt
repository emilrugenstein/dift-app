package app.dift.domain.model

/**
 * Persistence-only enums. Dift's single blocking mechanism is the usage-debt block, so new code
 * never branches on these — they exist because the Room tables (block_rules, block_events,
 * unblock_grants) keep their original columns for schema stability (no structural migration; see
 * docs/DATA_MODEL.md and Migrations.kt). Values are stored by name; never rename a constant.
 */
enum class RuleType { ALWAYS, DAILY_LIMIT, SCHEDULE, USAGE_DEBT }

enum class BlockReason { ALWAYS, LIMIT_EXHAUSTED, IN_SCHEDULE, USAGE_DEBT }

enum class BlockOutcome { SHOWN, UNBLOCKED_TAP, UNBLOCKED_FRICTION, ABANDONED, HOME_KICKED, DEBT_SERVED }

enum class GrantMethod { TAP, FRICTION }
