package app.dift.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dift.data.db.entity.UsageSessionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UsageDaoTest {

    private lateinit var db: DiftDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DiftDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `recomputeDay aggregates sessions into daily totals`() = runTest {
        val dao = db.usageDao()
        dao.insertSessions(
            listOf(
                UsageSessionEntity(packageName = "com.a", startEpochMs = 0, endEpochMs = 60_000, dayLocal = "2026-07-07"),
                UsageSessionEntity(packageName = "com.a", startEpochMs = 100_000, endEpochMs = 160_000, dayLocal = "2026-07-07"),
                UsageSessionEntity(packageName = "com.b", startEpochMs = 0, endEpochMs = 30_000, dayLocal = "2026-07-07"),
            ),
        )
        dao.recomputeDay("2026-07-07")

        val day = dao.observeDay("2026-07-07").first()
        assertEquals(2, day.size)
        assertEquals("com.a", day[0].packageName) // ordered by totalMs DESC
        assertEquals(120_000L, day[0].totalMs)
        assertEquals(2, day[0].sessionCount)
        assertEquals(30_000L, day[1].totalMs)
    }

    @Test
    fun `recomputeDay is idempotent`() = runTest {
        val dao = db.usageDao()
        dao.insertSessions(
            listOf(
                UsageSessionEntity(packageName = "com.a", startEpochMs = 0, endEpochMs = 60_000, dayLocal = "2026-07-07"),
            ),
        )
        dao.recomputeDay("2026-07-07")
        dao.recomputeDay("2026-07-07")

        val day = dao.observeDay("2026-07-07").first()
        assertEquals(1, day.size)
        assertEquals(60_000L, day[0].totalMs)
    }

    @Test
    fun `day totals aggregate across packages and days`() = runTest {
        val dao = db.usageDao()
        dao.insertSessions(
            listOf(
                UsageSessionEntity(packageName = "com.a", startEpochMs = 0, endEpochMs = 60_000, dayLocal = "2026-07-06"),
                UsageSessionEntity(packageName = "com.a", startEpochMs = 0, endEpochMs = 60_000, dayLocal = "2026-07-07"),
                UsageSessionEntity(packageName = "com.b", startEpochMs = 0, endEpochMs = 30_000, dayLocal = "2026-07-07"),
            ),
        )
        dao.recomputeDay("2026-07-06")
        dao.recomputeDay("2026-07-07")

        val totals = dao.observeDayTotals("2026-07-06").first()
        assertEquals(listOf("2026-07-06", "2026-07-07"), totals.map { it.dayLocal })
        assertEquals(60_000L, totals[0].totalMs)
        assertEquals(90_000L, totals[1].totalMs)
    }

    @Test
    fun `pruning removes only days before the cutoff`() = runTest {
        val dao = db.usageDao()
        dao.insertSessions(
            listOf(
                UsageSessionEntity(packageName = "com.a", startEpochMs = 0, endEpochMs = 1_000, dayLocal = "2026-01-01"),
                UsageSessionEntity(packageName = "com.a", startEpochMs = 0, endEpochMs = 1_000, dayLocal = "2026-07-07"),
            ),
        )
        dao.recomputeDay("2026-01-01")
        dao.recomputeDay("2026-07-07")

        dao.pruneSessionsBefore("2026-06-01")
        dao.pruneDailyBefore("2026-06-01")

        assertEquals(0, dao.observeDay("2026-01-01").first().size)
        assertEquals(1, dao.observeDay("2026-07-07").first().size)
    }
}
