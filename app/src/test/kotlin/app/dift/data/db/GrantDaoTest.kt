package app.dift.data.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.dift.data.db.entity.UnblockGrantEntity
import app.dift.domain.model.GrantMethod
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
class GrantDaoTest {

    private lateinit var db: DiftDatabase

    private fun grant(pkg: String, expiresAt: Long) = UnblockGrantEntity(
        packageName = pkg,
        ruleId = 1,
        grantedAtEpochMs = 0,
        expiresAtEpochMs = expiresAt,
        method = GrantMethod.TAP,
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
    fun `only unexpired grants are active`() = runTest {
        val dao = db.grantDao()
        dao.insert(grant("com.a", expiresAt = 100))
        dao.insert(grant("com.b", expiresAt = 50))

        val activeAt75 = dao.activeAt(75)
        assertEquals(listOf("com.a"), activeAt75.map { it.packageName })
    }

    @Test
    fun `pruning removes expired grants`() = runTest {
        val dao = db.grantDao()
        dao.insert(grant("com.a", expiresAt = 100))
        dao.insert(grant("com.b", expiresAt = 50))

        dao.pruneExpired(75)

        assertEquals(listOf("com.a"), dao.observeActive(0).first().map { it.packageName })
    }
}
