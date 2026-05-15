package net.kikin.nubecita.feature.search.impl.data

import app.cash.turbine.test
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.database.dao.RecentSearchDao
import net.kikin.nubecita.core.database.model.RecentSearchEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class RecentSearchRepositoryTest {
    private val dao = FakeRecentSearchDao()
    private val repo = RecentSearchRepository(dao)

    @Test
    fun record_blank_isIgnored() =
        runTest {
            repo.record("")
            repo.record("   ")

            assertTrue(dao.snapshot().isEmpty())
        }

    @Test
    fun record_nonBlank_isTrimmedAndPersisted() =
        runTest {
            repo.record("  kotlin  ")

            assertEquals(listOf("kotlin"), dao.snapshot().map(RecentSearchEntity::query))
        }

    @Test
    fun observeRecent_emitsQueryTextOnly_recencyOrdered() =
        runTest {
            // Pre-populate the fake with entries in arbitrary insertion order.
            dao.seed(
                RecentSearchEntity("alpha", Instant.fromEpochMilliseconds(1_000)),
                RecentSearchEntity("beta", Instant.fromEpochMilliseconds(3_000)),
                RecentSearchEntity("gamma", Instant.fromEpochMilliseconds(2_000)),
            )

            repo.observeRecent().test {
                assertEquals(listOf("beta", "gamma", "alpha"), awaitItem())
                cancelAndConsumeRemainingEvents()
            }
        }

    @Test
    fun clearAll_delegatesToDao() =
        runTest {
            repo.record("kotlin")
            repo.clearAll()

            assertTrue(dao.snapshot().isEmpty())
        }

    @Test
    fun remove_delegates() =
        runTest {
            dao.seed(
                RecentSearchEntity("kotlin", Instant.fromEpochMilliseconds(1_000)),
                RecentSearchEntity("compose", Instant.fromEpochMilliseconds(2_000)),
            )

            repo.remove("kotlin")

            assertEquals(listOf("compose"), dao.snapshot().map(RecentSearchEntity::query))
        }

    @Test
    fun remove_blankIgnored() =
        runTest {
            dao.seed(RecentSearchEntity("kotlin", Instant.fromEpochMilliseconds(1_000)))

            repo.remove("")
            repo.remove("   ")

            assertEquals(listOf("kotlin"), dao.snapshot().map(RecentSearchEntity::query))
        }
}

private class FakeRecentSearchDao : RecentSearchDao {
    private val state = MutableStateFlow<List<RecentSearchEntity>>(emptyList())

    fun snapshot(): List<RecentSearchEntity> = state.value

    fun seed(vararg entities: RecentSearchEntity) {
        state.update { entities.toList() }
    }

    override fun observeAll(): Flow<List<RecentSearchEntity>> = state.map { it.sortedByDescending(RecentSearchEntity::recordedAt) }

    override suspend fun upsert(entity: RecentSearchEntity) {
        state.update { current ->
            current.filterNot { it.query == entity.query } + entity
        }
    }

    override suspend fun trimToCapacity(capacity: Int) {
        state.update { current ->
            current.sortedByDescending(RecentSearchEntity::recordedAt).take(capacity)
        }
    }

    override suspend fun upsertAndTrim(
        entity: RecentSearchEntity,
        capacity: Int,
    ) {
        upsert(entity)
        trimToCapacity(capacity)
    }

    override suspend fun clearAll() {
        state.update { emptyList() }
    }

    override suspend fun delete(query: String) {
        state.update { current -> current.filterNot { it.query == query } }
    }
}
