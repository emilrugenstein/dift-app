package app.dift.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dift.data.db.entity.BlockEventEntity
import app.dift.domain.model.BlockOutcome
import app.dift.domain.model.BlockReason
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
class BlockEventDaoTest {

    private lateinit var db: DiftDatabase

    private fun event(pkg: String, at: Long, outcome: BlockOutcome = BlockOutcome.SHOWN) =
        BlockEventEntity(
            packageName = pkg,
            ruleId = 1,
            timestampEpochMs = at,
            reason = BlockReason.ALWAYS,
            outcome = outcome,
        )

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, DiftDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `recent events are returned newest first`() = runTest {
        val dao = db.blockEventDao()
        dao.insert(event("com.a", at = 100))
        dao.insert(event("com.b", at = 300))
        dao.insert(event("com.c", at = 200))

        val recent = dao.observeRecent(10).first()
        assertEquals(listOf("com.b", "com.c", "com.a"), recent.map { it.packageName })
    }

    @Test
    fun `shown count since counts only shown events in range`() = runTest {
        val dao = db.blockEventDao()
        dao.insert(event("com.a", at = 100, outcome = BlockOutcome.SHOWN))
        dao.insert(event("com.a", at = 200, outcome = BlockOutcome.UNBLOCKED_TAP))
        dao.insert(event("com.a", at = 300, outcome = BlockOutcome.SHOWN))

        assertEquals(1, dao.observeShownCountSince(250).first())
        assertEquals(2, dao.observeShownCountSince(0).first())
    }

    @Test
    fun `retention prune removes events before the cutoff`() = runTest {
        val dao = db.blockEventDao()
        dao.insert(event("com.old", at = 100))
        dao.insert(event("com.new", at = 1_000))

        dao.pruneBefore(500)

        val remaining = dao.observeRecent(10).first()
        assertEquals(listOf("com.new"), remaining.map { it.packageName })
    }
}
