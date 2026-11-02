package app.dift.data.export

import app.dift.data.datastore.SettingsRepository
import app.dift.data.db.DiftDatabase
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedOutputStream
import java.io.OutputStream
import java.time.Instant
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The full on-device data set as one zip of CSV files plus `meta.json`
 * (docs/features/export.md). Written straight to a stream the user chose (Storage Access
 * Framework) — nothing leaves the phone unless the user shares the file.
 */
@Singleton
class DataExporter @Inject constructor(
    private val database: DiftDatabase,
    private val settings: SettingsRepository,
) {

    /** Writes the export to [output] and closes it. */
    suspend fun writeZip(output: OutputStream, nowMs: Long, appVersion: String) {
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            zip.text(META_FILE, meta(nowMs, appVersion).toString(2))
            zip.text(
                "usage_sessions.csv",
                Csv.table(
                    listOf("id", "packageName", "startEpochMs", "endEpochMs", "dayLocal"),
                    database.usageDao().allSessions(),
                ) { listOf(it.id, it.packageName, it.startEpochMs, it.endEpochMs, it.dayLocal) },
            )
            zip.text(
                "daily_usage.csv",
                Csv.table(
                    listOf("dayLocal", "packageName", "totalMs", "sessionCount"),
                    database.usageDao().allDaily(),
                ) { listOf(it.dayLocal, it.packageName, it.totalMs, it.sessionCount) },
            )
            val exemptByBlock = database.blockDao().allExemptApps().groupBy({ it.ruleId }, { it.packageName })
            zip.text(
                "blocks.csv",
                Csv.table(
                    listOf(
                        "id", "name", "enabled", "daysMask", "startMinuteOfDay", "endMinuteOfDay",
                        "maxBurstSeconds", "exemptPackages", "createdAt", "updatedAt",
                    ),
                    database.blockDao().allBlocks(),
                ) {
                    listOf(
                        it.id, it.name, it.enabled, it.scheduleDaysMask, it.scheduleStartMinuteOfDay,
                        it.scheduleEndMinuteOfDay, it.maxBurstSeconds,
                        exemptByBlock[it.id].orEmpty().joinToString(";"), it.createdAt, it.updatedAt,
                    )
                },
            )
            zip.text(
                "block_events.csv",
                Csv.table(
                    listOf("id", "packageName", "ruleId", "timestampEpochMs", "reason", "outcome"),
                    database.blockEventDao().all(),
                ) { listOf(it.id, it.packageName, it.ruleId, it.timestampEpochMs, it.reason.name, it.outcome.name) },
            )
            zip.text(
                "challenges.csv",
                Csv.table(
                    listOf("id", "name", "startDay", "endDay", "goalMinutesPerDay", "createdAt"),
                    database.challengeDao().allChallenges(),
                ) { listOf(it.id, it.name, it.startDay, it.endDay, it.goalMinutesPerDay, it.createdAt) },
            )
            zip.text(
                "challenge_entries.csv",
                Csv.table(
                    listOf("id", "challengeId", "startEpochMs", "endEpochMs", "dayLocal", "manual"),
                    database.challengeDao().allEntries(),
                ) { listOf(it.id, it.challengeId, it.startEpochMs, it.endEpochMs, it.dayLocal, it.manual) },
            )
        }
    }

    private suspend fun meta(nowMs: Long, appVersion: String): JSONObject = JSONObject()
        .put("exportedAt", Instant.ofEpochMilli(nowMs).toString())
        .put("appVersion", appVersion)
        .put("databaseVersion", database.openHelper.readableDatabase.version)
        .put("retentionDays", settings.retentionDays.first())
        .put("notBadApps", JSONArray(settings.notBadApps.first().sorted()))
        .put("timestamps", "epoch milliseconds, UTC; dayLocal/startDay/endDay are local calendar days")

    private fun ZipOutputStream.text(name: String, content: String) {
        putNextEntry(ZipEntry(name))
        write(content.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    companion object {
        const val META_FILE = "meta.json"
        const val MIME_TYPE = "application/zip"
    }
}

/** Minimal RFC 4180 writer: a field is quoted only when it contains a comma, quote or newline. */
object Csv {

    fun <T> table(header: List<String>, rows: List<T>, cells: (T) -> List<Any?>): String = buildString {
        appendLine(header.joinToString(","))
        rows.forEach { row -> appendLine(cells(row).joinToString(",") { escape(it) }) }
    }

    fun escape(value: Any?): String {
        val text = value?.toString().orEmpty()
        val needsQuotes = text.any { it == ',' || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuotes) "\"" + text.replace("\"", "\"\"") + "\"" else text
    }
}
