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

    // --- "Not bad" apps (preselected as exempt in new blocks; blue in the usage charts) ---
    // A package set is a setting, not a table: it lives here as an encoded string (like the
    // open-session checkpoint) because a structural Room migration cannot be verified in CI
    // (docs/DATA_MODEL.md).

    val notBadApps: Flow<Set<String>> = dataStore.data.map { decodePackages(it[NOT_BAD_APPS] ?: "") }

    suspend fun toggleNotBadApp(packageName: String) {
        dataStore.edit {
            val current = decodePackages(it[NOT_BAD_APPS] ?: "")
            val next = if (packageName in current) current - packageName else current + packageName
            it[NOT_BAD_APPS] = next.joinToString(",")
        }
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
        val NOT_BAD_APPS = stringPreferencesKey("not_bad_apps")
        val LAST_INGESTED_EVENT_TIME = longPreferencesKey("last_ingested_event_time")
        val OPEN_SESSIONS = stringPreferencesKey("open_sessions")
        val RETENTION_DAYS = intPreferencesKey("retention_days")
        val FORCE_POLLING = booleanPreferencesKey("force_polling_mode")
        val COOLDOWN_STARTED_AT = longPreferencesKey("debt_cooldown_started_at")
        val COOLDOWN_UNTIL = longPreferencesKey("debt_cooldown_until")

        const val DEFAULT_RETENTION_DAYS = 365

        // "pkg|start|cls1;cls2" entries joined by ',' — package and activity-class names never
        // contain '|', ',' or ';'. The class list is the deriver's resumed-activity set; entries
        // written by versions before it existed have two fields and decode to an empty set (the
        // deriver then falls back to close-on-any-pause for that one carried session).
        fun encodeOpenSessions(sessions: List<OpenSession>): String =
            sessions.joinToString(",") {
                "${it.packageName}|${it.startMs}|${it.resumedClasses.joinToString(";")}"
            }

        fun decodePackages(encoded: String): Set<String> =
            encoded.split(',').filter { it.isNotBlank() }.toSet()

        fun decodeOpenSessions(encoded: String): List<OpenSession> =
            encoded.split(",").filter { it.contains('|') }.mapNotNull { entry ->
                val fields = entry.split('|')
                val start = fields.getOrNull(1)?.toLongOrNull() ?: return@mapNotNull null
                val classes = fields.getOrNull(2)
                    ?.split(';')?.filter { it.isNotBlank() }?.toSet()
                    .orEmpty()
                OpenSession(fields.first(), start, classes)
            }
    }
}
