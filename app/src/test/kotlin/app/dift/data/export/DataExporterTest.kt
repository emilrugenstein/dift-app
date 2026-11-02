package app.dift.data.export

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dift.data.datastore.SettingsRepository
import app.dift.data.db.DiftDatabase
import app.dift.data.db.entity.ChallengeEntity
import app.dift.data.db.entity.ChallengeEntryEntity
import app.dift.data.db.entity.UsageSessionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DataExporterTest {

    private lateinit var db: DiftDatabase
    private lateinit var exporter: DataExporter
    private val storeScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DiftDatabase::class.java).allowMainThreadQueries().build()
        val dataStore = PreferenceDataStoreFactory.create(scope = storeScope) {
            context.filesDir.resolve("export-test.preferences_pb")
        }
        exporter = DataExporter(db, SettingsRepository(dataStore))
    }

    @After
    fun tearDown() {
        db.close()
        storeScope.cancel()
    }

    private suspend fun export(): Map<String, String> {
        val bytes = ByteArrayOutputStream()
        exporter.writeZip(bytes, nowMs = 1_700_000_000_000L, appVersion = "0.4.1")
        val files = mutableMapOf<String, String>()
        ZipInputStream(ByteArrayInputStream(bytes.toByteArray())).use { zip ->
            generateSequence { zip.nextEntry }.forEach { entry ->
                files[entry.name] = zip.readBytes().toString(Charsets.UTF_8)
            }
        }
        return files
    }

    @Test
    fun `the zip holds every table as csv plus meta json`() = runTest {
        db.usageDao().insertSessions(
            listOf(
                UsageSessionEntity(
                    packageName = "com.a",
                    startEpochMs = 0,
                    endEpochMs = 60_000,
                    dayLocal = "2026-11-01",
                ),
            ),
        )
        db.usageDao().recomputeDay("2026-11-01")
        val challengeId = db.challengeDao().insertChallenge(
            ChallengeEntity(
                name = "Mobility, \"easy\"",
                startDay = "2026-11-01",
                endDay = "2026-11-30",
                goalMinutesPerDay = 30,
                createdAt = 0,
            ),
        )
        db.challengeDao().insertEntry(
            ChallengeEntryEntity(
                challengeId = challengeId,
                startEpochMs = 10,
                endEpochMs = 20,
                dayLocal = "2026-11-01",
                manual = true,
            ),
        )

        val files = export()
        assertEquals(
            setOf(
                "meta.json", "usage_sessions.csv", "daily_usage.csv", "blocks.csv",
                "block_events.csv", "challenges.csv", "challenge_entries.csv",
            ),
            files.keys,
        )
        val sessions = files.getValue("usage_sessions.csv").trim().lines()
        assertEquals("id,packageName,startEpochMs,endEpochMs,dayLocal", sessions[0])
        assertEquals("1,com.a,0,60000,2026-11-01", sessions[1])
        assertEquals("2026-11-01,com.a,60000,1", files.getValue("daily_usage.csv").trim().lines()[1])
        // A name with a comma and quotes is RFC 4180-quoted.
        assertTrue(files.getValue("challenges.csv").contains("\"Mobility, \"\"easy\"\"\""))
        assertEquals("1,1,10,20,2026-11-01,true", files.getValue("challenge_entries.csv").trim().lines()[1])

        val meta = JSONObject(files.getValue("meta.json"))
        assertEquals("0.4.1", meta.getString("appVersion"))
        assertEquals(3, meta.getInt("databaseVersion"))
        assertEquals("2023-11-14T22:13:20Z", meta.getString("exportedAt"))
    }

    @Test
    fun `csv escaping only quotes what needs quoting`() {
        assertEquals("plain", Csv.escape("plain"))
        assertEquals("", Csv.escape(null))
        assertEquals("\"a,b\"", Csv.escape("a,b"))
        assertEquals("\"say \"\"hi\"\"\"", Csv.escape("say \"hi\""))
        assertEquals("\"two\nlines\"", Csv.escape("two\nlines"))
    }
}
