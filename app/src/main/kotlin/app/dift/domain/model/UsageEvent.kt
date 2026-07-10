package app.dift.domain.model

/**
 * Minimal, Android-free projection of a UsageStatsManager event.
 * The system layer maps ACTIVITY_RESUMED -> RESUMED, ACTIVITY_PAUSED/STOPPED -> PAUSED,
 * SCREEN_NON_INTERACTIVE -> SCREEN_OFF, DEVICE_SHUTDOWN -> SHUTDOWN (ADR-0005).
 */
enum class UsageEventType { RESUMED, PAUSED, SCREEN_OFF, SHUTDOWN }

data class UsageEvent(
    val packageName: String?,
    val type: UsageEventType,
    val timestampMs: Long,
    /**
     * Activity class for RESUMED/PAUSED events; null for screen events (and unknown sources).
     * The SessionDeriver needs it to tell "left the app" apart from "navigated within the app" —
     * see the class-set model in SessionDeriver.
     */
    val className: String? = null,
)
