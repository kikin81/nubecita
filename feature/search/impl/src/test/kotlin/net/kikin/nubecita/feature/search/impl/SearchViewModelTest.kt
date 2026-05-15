package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.database.dao.RecentSearchDao
import net.kikin.nubecita.core.database.model.RecentSearchEntity
import net.kikin.nubecita.feature.search.impl.data.RecentSearchRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SearchViewModel].
 *
 * The VM owns a Compose `TextFieldState` and observes it via
 * `snapshotFlow { textFieldState.text.toString() }`. In unit tests there
 * is no recomposer driving Snapshot propagation, so every text mutation
 * must be followed by `Snapshot.sendApplyNotifications()` + the test
 * scheduler's `runCurrent()` to let the VM's collector observe the
 * change. The 250 ms debounce is driven explicitly via
 * `testScheduler.advanceTimeBy(DEBOUNCE_MS + 1)`. Pattern mirrored from
 * `:feature:composer:impl/src/test/.../ComposerViewModelTypeaheadTest.kt`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val dao = FakeRecentSearchDao()
    private val repo = RecentSearchRepository(dao)

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_seedsRecentSearchesFromRepo() =
        runTest {
            dao.seed(
                RecentSearchEntity("kotlin", Instant.fromEpochMilliseconds(2_000)),
                RecentSearchEntity("compose", Instant.fromEpochMilliseconds(1_000)),
            )

            val vm = SearchViewModel(repo)
            runCurrent()

            assertEquals(
                listOf("kotlin", "compose"),
                vm.uiState.value.recentSearches
                    .toList(),
            )
        }

    @Test
    fun textFieldState_typing_updatesCurrentQuery_afterDebounce() =
        runTest {
            val vm = SearchViewModel(repo)
            runCurrent()

            vm.textFieldState.setTextAndPlaceCursorAtEnd("kotlin")
            Snapshot.sendApplyNotifications()
            runCurrent()
            // Before debounce: currentQuery still empty.
            assertEquals("", vm.uiState.value.currentQuery)
            assertTrue(vm.uiState.value.isQueryBlank)

            advanceTimeBy(DEBOUNCE_MS + 1)

            assertEquals("kotlin", vm.uiState.value.currentQuery)
            assertEquals(false, vm.uiState.value.isQueryBlank)
        }

    @Test
    fun submitClicked_persistsCurrentNonBlankText() =
        runTest {
            val vm = SearchViewModel(repo)
            runCurrent()
            vm.textFieldState.setTextAndPlaceCursorAtEnd("  kotlin  ")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(DEBOUNCE_MS + 1)

            vm.handleEvent(SearchEvent.SubmitClicked)
            runCurrent()

            assertEquals(listOf("kotlin"), dao.snapshot().map(RecentSearchEntity::query))
        }

    @Test
    fun submitClicked_blank_isNoOp() =
        runTest {
            val vm = SearchViewModel(repo)
            runCurrent()
            vm.textFieldState.setTextAndPlaceCursorAtEnd("   ")
            Snapshot.sendApplyNotifications()
            advanceTimeBy(DEBOUNCE_MS + 1)

            vm.handleEvent(SearchEvent.SubmitClicked)
            runCurrent()

            assertTrue(dao.snapshot().isEmpty())
        }

    @Test
    fun recentChipTapped_seedsTextField_andPersists() =
        runTest {
            val vm = SearchViewModel(repo)
            runCurrent()

            vm.handleEvent(SearchEvent.RecentChipTapped("compose"))
            // Drain the chip-tap's setTextAndPlaceCursorAtEnd through the snapshotFlow path.
            Snapshot.sendApplyNotifications()
            advanceTimeBy(DEBOUNCE_MS + 1)
            runCurrent()

            assertEquals("compose", vm.textFieldState.text.toString())
            assertEquals("compose", vm.uiState.value.currentQuery)
            assertEquals(listOf("compose"), dao.snapshot().map(RecentSearchEntity::query))
        }

    @Test
    fun recentChipRemoved_delegatesToRepo() =
        runTest {
            dao.seed(
                RecentSearchEntity("kotlin", Instant.fromEpochMilliseconds(2_000)),
                RecentSearchEntity("compose", Instant.fromEpochMilliseconds(1_000)),
            )
            val vm = SearchViewModel(repo)
            runCurrent()

            vm.handleEvent(SearchEvent.RecentChipRemoved("compose"))
            runCurrent()

            assertEquals(listOf("kotlin"), dao.snapshot().map(RecentSearchEntity::query))
        }

    @Test
    fun clearAllRecentsClicked_delegatesToRepo() =
        runTest {
            dao.seed(RecentSearchEntity("kotlin", Instant.fromEpochMilliseconds(1_000)))
            val vm = SearchViewModel(repo)
            runCurrent()

            vm.handleEvent(SearchEvent.ClearAllRecentsClicked)
            runCurrent()

            assertTrue(dao.snapshot().isEmpty())
        }

    private companion object {
        const val DEBOUNCE_MS = 250L
    }
}

/**
 * Hand-written fake DAO backed by a [MutableStateFlow]. Identical shape to
 * the one in `RecentSearchRepositoryTest` (FakeRecentSearchDao), copied
 * here so the VM test stays self-contained. Both fakes drift in lockstep
 * when the DAO surface changes.
 */
private class FakeRecentSearchDao : RecentSearchDao {
    private val state = MutableStateFlow<List<RecentSearchEntity>>(emptyList())

    fun snapshot(): List<RecentSearchEntity> = state.value

    fun seed(vararg entities: RecentSearchEntity) {
        state.update { entities.toList() }
    }

    override fun observeAll(): Flow<List<RecentSearchEntity>> = state.map { it.sortedByDescending(RecentSearchEntity::recordedAt) }

    override suspend fun upsert(entity: RecentSearchEntity) {
        state.update { current -> current.filterNot { it.query == entity.query } + entity }
    }

    override suspend fun trimToCapacity(capacity: Int) {
        state.update { current -> current.sortedByDescending(RecentSearchEntity::recordedAt).take(capacity) }
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
