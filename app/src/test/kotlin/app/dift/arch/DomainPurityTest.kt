package app.dift.arch

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertFalse
import org.junit.Test

/**
 * Architecture gates (CLAUDE.md invariant #2): the domain layer stays pure Kotlin.
 * These tests are the enforcement mechanism — if one fails, fix the code, never the test.
 */
class DomainPurityTest {

    private val domainFiles = Konsist
        .scopeFromProduction()
        .files
        .filter { it.packagee?.name?.startsWith("app.dift.domain") == true }

    @Test
    fun `domain layer does not import android or androidx`() {
        domainFiles.assertFalse(testName = "domain purity") { file ->
            file.imports.any {
                it.name.startsWith("android.") || it.name.startsWith("androidx.")
            }
        }
    }

    @Test
    fun `domain layer never reads the wall clock directly`() {
        // Time is always injected (a `now` parameter) so the rule engine stays deterministic
        // and testable. Instant/ZonedDateTime/LocalDate `.now()` and currentTimeMillis are banned.
        val bannedCalls = listOf(
            "System.currentTimeMillis",
            "Instant.now(",
            "ZonedDateTime.now(",
            "LocalDateTime.now(",
            "LocalDate.now(",
            "LocalTime.now(",
        )
        domainFiles.assertFalse(testName = "injected clock only") { file ->
            bannedCalls.any { banned -> file.text.contains(banned) }
        }
    }
}
