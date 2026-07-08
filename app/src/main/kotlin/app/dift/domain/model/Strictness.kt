package app.dift.domain.model

/**
 * Persistence-only holdover (like the enums in StorageEnums.kt). Usage-debt blocks are always
 * [HARD] — there is no bypass — but the `block_rules.strictness` column survives from v1 for
 * schema stability, so the enum stays. New code does not branch on it.
 */
enum class Strictness {
    TAP_THROUGH,
    FRICTION,
    HARD,
}
