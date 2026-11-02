package app.dift.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dift.data.db.entity.ChallengeEntity
import app.dift.data.db.entity.ChallengeEntryEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ChallengeDaoTest {

    private lateinit var db: DiftDatabase

    private fun challenge(name: String = "Mobility") = ChallengeEntity(
        name = name,
        startDay = "2026-11-01",
        endDay = "2026-11-30",
        goalMinutesPerDay = 30,
        createdAt = 0L,
    )

    private fun entry(challengeId: Long, startMs: Long, endMs: Long, day: String, manual: Boolean = false) =
        ChallengeEntryEntity(
            challengeId = challengeId,
            startEpochMs = startMs,
            endEpochMs = endMs,
            dayLocal = day,
            manual = manual,
        )

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
    fun `day totals sum timed and manual entries per day`() = runTest {
        val dao = db.challengeDao()
        val id = dao.insertChallenge(challenge())
        dao.insertEntry(entry(id, 1_000_000, 1_600_000, "2026-11-01")) // 10 min timed
        dao.insertEntry(entry(id, 2_000_000, 2_900_000, "2026-11-01", manual = true)) // 15 min manual
        dao.insertEntry(entry(id, 90_000_000, 91_800_000, "2026-11-02")) // 30 min timed

        val totals = dao.observeDayTotals(id).first().associate { it.dayLocal to it.totalMs }
        assertEquals(1_500_000L, totals["2026-11-01"])
        assertEquals(1_800_000L, totals["2026-11-02"])

        val ranged = dao.dayTotalsInRange("2026-11-02", "2026-11-30")
        assertEquals(1, ranged.size)
        assertEquals("2026-11-02", ranged.single().dayLocal)
    }

    @Test
    fun `timed entries in range exclude manual ones`() = runTest {
        val dao = db.challengeDao()
        val id = dao.insertChallenge(challenge())
        dao.insertEntry(entry(id, 1_000_000, 1_600_000, "2026-11-01"))
        dao.insertEntry(entry(id, 1_000_000, 1_600_000, "2026-11-01", manual = true))

        val timed = dao.timedEntriesInRange(0, 5_000_000)
        assertEquals(1, timed.size)
        assertEquals(false, timed.single().manual)
    }

    @Test
    fun `deleting a challenge cascades to its entries`() = runTest {
        val dao = db.challengeDao()
        val id = dao.insertChallenge(challenge())
        dao.insertEntry(entry(id, 1_000_000, 1_600_000, "2026-11-01"))
        dao.deleteChallenge(id)
        assertTrue(dao.observeEntries(id).first().isEmpty())
        assertTrue(dao.observeChallenges().first().isEmpty())
    }
}
