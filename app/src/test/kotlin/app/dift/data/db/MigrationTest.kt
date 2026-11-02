package app.dift.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Updates must never lose history (CLAUDE.md invariant #11). This builds a *real* v2 database from
 * the committed schema export, fills it with the kind of rows a phone has, opens it with the
 * production Room configuration (so the AutoMigration to the current version runs) and checks that
 * every row is still there and the new tables exist. A future schema change that drops data fails
 * here, in CI, before it can reach the device.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MigrationTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()
    private val dbName = "migration-test.db"

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    /** Creates the database exactly as v2 shipped it: every table and index from `2.json`. */
    private fun createV2Database(populate: (SQLiteDatabase) -> Unit) {
        val schema = JSONObject(File(SCHEMA_DIR, "2.json").readText()).getJSONObject("database")
        assertEquals(2, schema.getInt("version"))
        val file = context.getDatabasePath(dbName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            val entities = schema.getJSONArray("entities")
            for (i in 0 until entities.length()) {
                val entity = entities.getJSONObject(i)
                val table = entity.getString("tableName")
                db.execSQL(entity.getString("createSql").replace("\${TABLE_NAME}", table))
                val indices = entity.getJSONArray("indices")
                for (j in 0 until indices.length()) {
                    val index = indices.getJSONObject(j)
                    db.execSQL(
                        index.getString("createSql")
                            .replace("\${INDEX_NAME}", index.getString("name"))
                            .replace("\${TABLE_NAME}", table),
                    )
                }
            }
            populate(db)
            db.version = 2
        }
    }

    @Test
    fun `migrating a populated v2 database keeps every usage row, block and exemption`() = runTest {
        createV2Database { db ->
            db.execSQL(
                "INSERT INTO usage_sessions (packageName, startEpochMs, endEpochMs, dayLocal) VALUES " +
                    "('com.a', 1000, 61000, '2024-12-31'), ('com.a', 90000, 150000, '2025-03-01'), " +
                    "('com.b', 200000, 260000, '2025-03-01')",
            )
            db.execSQL(
                "INSERT INTO daily_usage (dayLocal, packageName, totalMs, sessionCount) VALUES " +
                    "('2024-12-31', 'com.a', 60000, 1), ('2025-03-01', 'com.a', 60000, 1), " +
                    "('2025-03-01', 'com.b', 60000, 1)",
            )
            db.execSQL(
                "INSERT INTO block_rules (name, type, enabled, strictness, deviceWide, frictionDelaySeconds, " +
                    "grantMinutes, createdAt, updatedAt, scheduleStartMinuteOfDay, scheduleEndMinuteOfDay, " +
                    "scheduleDaysMask, maxBurstSeconds) " +
                    "VALUES ('Night', 'USAGE_DEBT', 1, 'HARD', 1, 30, 0, 0, 0, 1350, 360, 127, 60)",
            )
            db.execSQL("INSERT INTO rule_apps (ruleId, packageName) VALUES (1, 'com.exempt')")
        }

        // The production builder: declared migrations only, no destructive fallback.
        val room = Room.databaseBuilder(context, DiftDatabase::class.java, dbName)
            .addMigrations(MIGRATION_1_2)
            .allowMainThreadQueries()
            .build()
        try {
            assertEquals(3, room.usageDao().sessionsInRange(0, Long.MAX_VALUE).size)
            assertEquals("2024-12-31", room.usageDao().earliestSessionDay())
            assertEquals(3, room.usageDao().dayAppTotalsInRange("2024-01-01", "2025-12-31").size)
            val block = room.blockDao().observeBlocks().first().single()
            assertEquals("Night", block.name)
            assertEquals(listOf("com.exempt"), room.blockDao().observeExemptApps().first().map { it.packageName })
            // The new tables exist and are empty.
            assertTrue(room.challengeDao().observeChallenges().first().isEmpty())
            assertEquals(CURRENT_VERSION, room.openHelper.readableDatabase.version)
        } finally {
            room.close()
        }
    }

    private companion object {
        /** Gradle runs unit tests with the module directory as the working directory. */
        const val SCHEMA_DIR = "schemas/app.dift.data.db.DiftDatabase"
        const val CURRENT_VERSION = 3
    }
}
