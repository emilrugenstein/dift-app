package app.dift.domain.engine

import app.dift.domain.model.BlockReason
import app.dift.domain.model.Strictness

sealed interface Verdict {
    data object Allow : Verdict

    data class Block(
        val ruleId: Long,
        val reason: BlockReason,
        val strictness: Strictness,
        /** Epoch ms when the block condition ends, if knowable (HARD countdown display). */
        val blockedUntilMs: Long?,
        /** Friction parameters resolved from the winning rule. */
        val frictionDelaySeconds: Int,
        val grantMinutes: Int,
    ) : Verdict
}
