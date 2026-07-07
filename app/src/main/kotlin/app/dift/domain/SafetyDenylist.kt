package app.dift.domain

/**
 * Packages Dift must NEVER block, regardless of any rule configuration.
 *
 * INVARIANT (CLAUDE.md #4): entries are only ever added, never removed. Blocking the dialer
 * is a genuine safety hazard (emergency calls); blocking Settings/SystemUI/the launcher or
 * Dift itself would wedge the device or the app.
 *
 * Pure Kotlin: packages that can only be resolved at runtime (default launcher, current IME,
 * default dialer) are resolved in the system layer and passed in as [runtimeResolved].
 */
object SafetyDenylist {

    const val DIFT_PACKAGE = "app.dift"

    val staticPackages: Set<String> = setOf(
        DIFT_PACKAGE,
        "com.android.systemui",
        "com.android.settings",
        "com.android.emergency",
        "com.android.phone",
    )

    fun isNeverBlockable(packageName: String, runtimeResolved: Set<String>): Boolean =
        packageName in staticPackages || packageName in runtimeResolved
}
