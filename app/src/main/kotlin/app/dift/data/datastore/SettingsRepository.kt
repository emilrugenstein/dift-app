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

    val defaultFrictionPhrase: Flow<String> =
        dataStore.data.map { it[FRICTION_PHRASE] ?: DEFAULT_FRICTION_PHRASE }

    suspend fun setDefaultFrictionPhrase(value: String) {
        dataStore.edit { it[FRICTION_PHRASE] = value }
    }

    val forcePollingMode: Flow<Boolean> = dataStore.data.map { it[FORCE_POLLING] ?: false }

    suspend fun setForcePollingMode(value: Boolean) {
        dataStore.edit { it[FORCE_POLLING] = value }
    }

    // --- Night usage-debt state (must survive process death and reboot) ---

    val activeBurstStartedAt: Flow<Long?> = dataStore.data.map { it[BURST_STARTED_AT] }

    val debtUntil: Flow<Long?> = dataStore.data.map { it[DEBT_UNTIL] }

    suspend fun setDebtState(burstStartedAt: Long?, debtUntil: Long?) {
        dataStore.edit {
            if (burstStartedAt == null) it.remove(BURST_STARTED_AT) else it[BURST_STARTED_AT] = burstStartedAt
            if (debtUntil == null) it.remove(DEBT_UNTIL) else it[DEBT_UNTIL] = debtUntil
        }
    }

    private companion object {
        val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")
        val LAST_INGESTED_EVENT_TIME = longPreferencesKey("last_ingested_event_time")
        val OPEN_SESSIONS = stringPreferencesKey("open_sessions")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val FRICTION_PHRASE = stringPreferencesKey("default_friction_phrase")
        val FORCE_POLLING = booleanPreferencesKey("force_polling_mode")
        val BURST_STARTED_AT = longPreferencesKey("debt_burst_started_at")
        val DEBT_UNTIL = longPreferencesKey("debt_until")

        const val DEFAULT_RETENTION_DAYS = 365
        const val DEFAULT_FRICTION_PHRASE = "I choose to waste this time"

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
