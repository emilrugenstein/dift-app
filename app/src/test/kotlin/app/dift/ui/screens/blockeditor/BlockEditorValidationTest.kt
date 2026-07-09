package app.dift.ui.screens.blockeditor

import app.dift.domain.model.Block
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure validation for the block editor's save button (BlockEditorViewModel.UiState). */
class BlockEditorValidationTest {

    private fun state(
        name: String = "Night",
        daysMask: Int = Block.ALL_DAYS,
        startMinute: Int = Block.DEFAULT_START_MINUTE,
        endMinute: Int = Block.DEFAULT_END_MINUTE,
        maxBurstSeconds: Int = Block.DEFAULT_MAX_BURST_SECONDS,
    ) = BlockEditorViewModel.UiState(
        name = name,
        daysMask = daysMask,
        startMinute = startMinute,
        endMinute = endMinute,
        maxBurstSeconds = maxBurstSeconds,
    )

    @Test
    fun `a well-formed block passes`() {
        assertTrue(state().isValid)
    }

    @Test
    fun `blank name fails`() {
        assertFalse(state(name = "  ").isValid)
    }

    @Test
    fun `no selected days fails`() {
        assertFalse(state(daysMask = 0).isValid)
    }

    @Test
    fun `a zero-length window fails`() {
        assertFalse(state(startMinute = 6 * 60, endMinute = 6 * 60).isValid)
    }

    @Test
    fun `a non-positive burst cap fails`() {
        assertFalse(state(maxBurstSeconds = 0).isValid)
    }
}
