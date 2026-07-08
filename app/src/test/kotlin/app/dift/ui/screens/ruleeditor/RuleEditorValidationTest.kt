package app.dift.ui.screens.ruleeditor

import app.dift.domain.model.RuleType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure validation rules for the rule editor's save button (RuleEditorViewModel.UiState). */
class RuleEditorValidationTest {

    private fun state(
        name: String = "Rule",
        type: RuleType = RuleType.ALWAYS,
        packages: Set<String> = setOf("com.a"),
        limitMinutes: Int = 30,
    ) = RuleEditorViewModel.UiState(
        name = name,
        type = type,
        selectedPackages = packages,
        limitMinutes = limitMinutes,
    )

    @Test
    fun `valid always rule passes`() {
        assertTrue(state().isValid)
    }

    @Test
    fun `blank name fails`() {
        assertFalse(state(name = "  ").isValid)
    }

    @Test
    fun `no selected apps fails`() {
        assertFalse(state(packages = emptySet()).isValid)
    }

    @Test
    fun `daily limit with zero minutes fails`() {
        assertFalse(state(type = RuleType.DAILY_LIMIT, limitMinutes = 0).isValid)
    }

    @Test
    fun `daily limit with positive minutes passes`() {
        assertTrue(state(type = RuleType.DAILY_LIMIT, limitMinutes = 15).isValid)
    }
}
