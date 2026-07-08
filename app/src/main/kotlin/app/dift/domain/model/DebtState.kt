package app.dift.domain.model

/**
 * Persisted state of the night usage-debt mechanic (docs/features/usage-debt.md).
 * Both fields are epoch millis; null means "not active". Survives process death and reboot
 * via DataStore — see SettingsRepository.
 */
data class DebtState(
    val burstStartedAtMs: Long? = null,
    val debtUntilMs: Long? = null,
) {
    fun debtActiveAt(nowMs: Long): Boolean = debtUntilMs != null && nowMs < debtUntilMs

    companion object {
        val IDLE = DebtState()
    }
}
