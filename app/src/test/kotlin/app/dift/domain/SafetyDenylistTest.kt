package app.dift.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyDenylistTest {

    @Test
    fun `dift itself is never blockable`() {
        assertTrue(SafetyDenylist.isNeverBlockable(SafetyDenylist.DIFT_PACKAGE, emptySet()))
    }

    @Test
    fun `system ui, settings, dialer and emergency are never blockable`() {
        listOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.phone",
            "com.android.emergency",
        ).forEach { pkg ->
            assertTrue("$pkg must be denylisted", SafetyDenylist.isNeverBlockable(pkg, emptySet()))
        }
    }

    @Test
    fun `runtime-resolved packages such as the launcher are never blockable`() {
        val runtime = setOf("com.fairphone.launcher", "com.android.inputmethod.latin")
        assertTrue(SafetyDenylist.isNeverBlockable("com.fairphone.launcher", runtime))
        assertTrue(SafetyDenylist.isNeverBlockable("com.android.inputmethod.latin", runtime))
    }

    @Test
    fun `ordinary apps are blockable`() {
        assertFalse(SafetyDenylist.isNeverBlockable("com.instagram.android", emptySet()))
    }
}
