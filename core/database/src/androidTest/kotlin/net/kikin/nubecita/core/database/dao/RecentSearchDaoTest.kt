package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.RecentSearchEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class RecentSearchDaoTest : DatabaseTest() {
    private lateinit var dao: RecentSearchDao

    @Before
    fun resolveDao() {
        dao = db.recentSearchDao()
    }

    @Test
    fun observeAll_emptyByDefault() =
        runTest {
            assertEquals(emptyList<RecentSearchEntity>(), dao.observeAll().first())
        }

    @Test
    fun upsertAndTrim_reinsertingSameQuery_dedupsAndUpdatesTimestamp() =
        runTest {
            val older = entity("kotlin", epoch = 1_000)
            val newer = entity("kotlin", epoch = 2_000)

            dao.upsertAndTrim(older, capacity = 10)
            dao.upsertAndTrim(newer, capacity = 10)

            val rows = dao.observeAll().first()
            assertEquals(1, rows.size)
            assertEquals(newer, rows.single())
        }

    @Test
    fun upsertAndTrim_capEnforced_keepsMostRecentN() =
        runTest {
            // Insert 12 distinct queries at strictly-increasing timestamps.
            repeat(12) { i ->
                dao.upsertAndTrim(entity(query = "q$i", epoch = (i + 1) * 1_000L), capacity = 10)
            }

            val rows = dao.observeAll().first()
            assertEquals(10, rows.size)
            // Most-recent first: q11 → q2 (q0 and q1 evicted).
            assertEquals(List(10) { "q${11 - it}" }, rows.map(RecentSearchEntity::query))
        }

    @Test
    fun observeAll_emitsRowsInRecordedAtDescOrder() =
        runTest {
            dao.upsertAndTrim(entity("alpha", epoch = 1_000), capacity = 10)
            dao.upsertAndTrim(entity("beta", epoch = 3_000), capacity = 10)
            dao.upsertAndTrim(entity("gamma", epoch = 2_000), capacity = 10)

            val rows = dao.observeAll().first()
            assertEquals(listOf("beta", "gamma", "alpha"), rows.map(RecentSearchEntity::query))
        }

    @Test
    fun clearAll_emptiesTheTable() =
        runTest {
            dao.upsertAndTrim(entity("kotlin", epoch = 1_000), capacity = 10)
            dao.upsertAndTrim(entity("compose", epoch = 2_000), capacity = 10)

            dao.clearAll()

            assertTrue(dao.observeAll().first().isEmpty())
        }

    private fun entity(
        query: String,
        epoch: Long,
    ): RecentSearchEntity = RecentSearchEntity(query = query, recordedAt = Instant.fromEpochMilliseconds(epoch))
}
