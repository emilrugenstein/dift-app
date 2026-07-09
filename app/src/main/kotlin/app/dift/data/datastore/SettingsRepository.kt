package app.dift.data.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.dift.domain.model.OpenSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Scalars only (docs/DATA_MODEL.md): settings, the ingest checkpoint, and the persisted
 * night-debt state. Lists of things belong in Room.
 */
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {

    // --- Onboarding ---

    val onboardingCompleted: Flow<Boolean> = dataStore.data.map { it[ONBOARDING_COMPLETED] ?: false }

    suspend fun setOnboardingCompleted(value: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETED] = value }
    }

    // --- Ingest checkpoint (see UsageStatsIngester) ---

    val lastIngestedEventTime: Flow<Long> = dataStore.data.map { it[LAST_INGESTED_EVENT_TIME] ?: 0L }

    val openSessions: Flow<List<OpenSession>> =
        dataStore.data.map { decodeOpenSessions(it[OPEN_SESSIONS] ?: "") }

    suspend fun setIngestCheckpoint(eventTime: Long, openSessions: List<OpenSession>) {
        dataStore.edit {
            it[LAST_INGESTED_EVENT_TIME] = eventTime
            it[OPEN_SESSIONS] = encodeOpenSessions(openSessions)
        }
    }

    // --- General settings ---

    val retentionDays: Flow<Int> = dataStore.data.map { it[RETENTION_DAYS] ?: DEFAULT_RETENTION_DAYS }

    suspend fun setRetentionDays(value: Int) {
        dataStore.edit { it[RETENTION_DAYS] = value }
    }

    val forcePollingMode: Flow<Boolean> = dataStore.data.map { it[FORCE_POLLING] ?: false }

    suspend fun setForcePollingMode(value: Boolean) {
        dataStore.edit { it[FORCE_POLLING] = value }
    }

    // --- Usage-debt cooldown (the only debt state that must survive process death and reboot) ---

    val cooldownStartedAt: Flow<Long?> = dataStore.data.map { it[COOLDOWN_STARTED_AT] }

    val cooldownUntil: Flow<Long?> = dataStore.data.map { it[COOLDOWN_UNTIL] }

    suspend fun setCooldown(startedAt: Long?, until: Long?) {
        dataStore.edit {
            if (startedAt == null) it.remove(COOLDOWN_STARTED_AT) else it[COOLDOWN_STARTED_AT] = startedAt
            if (until == null) it.remove(COOLDOWN_UNTIL) else it[COOLDOWN_UNTIL] = until
        }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_INGESTED_EVENT_TIME = longPreferencesKey("last_ingested_event_time")
        val OPEN_SESSIONS = stringPreferencesKey("open_sessions")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val FORCE_POLLING = booleanPreferencesKey("force_polling_mode")
        val COOLDOWN_STARTED_AT = longPreferencesKey("debt_cooldown_started_at")
        val COOLDOWN_UNTIL = longPreferencesKey("debt_cooldown_until")

        const val DEFAULT_RETENTION_DAYS = 365

        // "pkg|start,pkg|start" — package names never contain '|' or ','.
        fun encodeOpenSessions(sessions: List<OpenSession>): String =
            sessions.joinToString(",") { "${it.packageName}|${it.startMs}" }

        fun decodeOpenSessions(encoded: String): List<OpenSession> =
            encoded.split(",").filter { it.contains('|') }.mapNotNull { entry ->
                val pkg = entry.substringBefore('|')
                val start = entry.substringAfter('|').toLongOrNull() ?: return@mapNotNull null
                OpenSession(pkg, start)
            }
    }
}
