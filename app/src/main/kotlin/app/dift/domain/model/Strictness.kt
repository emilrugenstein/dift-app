package app.dift.domain.model

/**
 * How hard it is to bypass an active block. Ordered weakest → strongest so that
 * "most-strict rule wins" can be resolved with [ordinal] comparison (see RuleEngine, M2+).
 */
enum class Strictness {
    /** A single tap dismisses the block and grants temporary access. */
    TAP_THROUGH,

    /** Unblocking requires deliberate friction: a wait timer plus a typed phrase. */
    FRICTION,

    /** No unblock path. The block holds until its trigger condition ends. */
    HARD,
}
