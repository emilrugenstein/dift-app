package app.dift.system.detect

import kotlinx.coroutines.flow.StateFlow

/** Abstracts the two detection sources (accessibility vs. polling) behind one stream. */
interface ForegroundAppDetector {
    /** Current foreground package, or null when unknown / on the home screen. */
    val foregroundPackage: StateFlow<String?>
}
