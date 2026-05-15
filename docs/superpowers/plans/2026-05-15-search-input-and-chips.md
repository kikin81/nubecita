# Search Input + Recent-Search Chips Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the scaffold `SearchScreen` with the real `SearchViewModel` + `SearchInputRow` + `RecentSearchChipStrip`. Live-search per keystroke (250 ms debounce) updates `state.currentQuery` for downstream tab VMs to consume. Persistence to the recent-search repo fires on explicit commit only (IME `Search` / Enter / chip tap). Per-chip dismiss + overflow "Clear all".

**Architecture:** Parent `SearchViewModel` extends `MviViewModel<SearchScreenViewState, SearchEvent, SearchEffect>`. It exposes a public `val textFieldState: TextFieldState` (sanctioned editor exception, same shape as `:feature:composer:impl`) and observes it via `snapshotFlow.debounce.distinctUntilChanged`. Recent-search persistence and observation go through the `RecentSearchRepository` from vrba.2; this PR also extends the repo with `remove(query)` and the DAO with `delete(query)` to support per-chip dismiss (no schema bump).

**Tech Stack:** Kotlin 2.3.21, Jetpack Compose Material 3 1.5.0-alpha19, Hilt 2.59.2, kotlinx-coroutines 1.11.0, Room 2.8.2, JUnit 5 (Jupiter) for unit tests, JUnit 4 + Android JUnit Runner for instrumented tests, Compose Screenshot Testing (`screenshotTestDebug`).

**Spec:** [`docs/superpowers/specs/2026-05-15-search-input-and-chips-design.md`](../specs/2026-05-15-search-input-and-chips-design.md)

**bd:** This plan implements `nubecita-vrba.5` ("feature/search/impl: parent SearchViewModel + search input + recent-search chips"), a child of `nubecita-vrba` (the Search epic). Single PR. Blockers `nubecita-vrba.1` and `nubecita-vrba.2` are already closed.

---

## File Structure

**Modified:**

- `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDao.kt` — add `@Query` `delete(query: String)`.
- `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDaoTest.kt` — add `delete_removesOnlyMatchingRow`.
- `feature/search/impl/build.gradle.kts` — add Compose-UI-test + screenshot-test deps mirroring `:feature:chats:impl`.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreen.kt` — replace the scaffold body with the real screen.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/RecentSearchRepository.kt` — add `remove(query: String)`.
- `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/RecentSearchRepositoryTest.kt` — add `remove_delegates` + `remove_blankIgnored`; extend `FakeRecentSearchDao` with `delete`.
- `feature/search/impl/src/main/res/values/strings.xml` — remove `search_screen_scaffold_title`; add 5 new strings.

**New:**

- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchContract.kt` — `SearchScreenViewState` + `SearchEvent` + `SearchEffect`.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModel.kt` — Hilt VM with public `textFieldState`, snapshotFlow + debounce collector, `recentSearches.observeRecent()` collector, `handleEvent` dispatch.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/SearchInputRow.kt` — `OutlinedTextField(state = textFieldState, ...)` with leading search icon, trailing clear-X, IME action `Search`. Includes preview functions.
- `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/RecentSearchChipStrip.kt` — LazyRow of `InputChip`s + trailing overflow menu. Includes preview functions.
- `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModelTest.kt` — JUnit 5 unit tests using `UnconfinedTestDispatcher` + `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()` + `advanceTimeBy(251ms)` per the snapshot-in-unit-tests memory.
- `feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/ui/SearchInputRowScreenshotTest.kt`
- `feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/ui/RecentSearchChipStripScreenshotTest.kt`
- `feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreenScreenshotTest.kt`

**Out of scope** (deferred to vrba.6/7/8 or follow-ups):

- Tab content (Posts / People) — vrba.6 / vrba.7.
- TabRow + cross-VM orchestration — vrba.8.
- `OnQueryChanged` / `OnQuerySubmitted` effects to external consumers.
- `SavedStateHandle` persistence of the input.
- Voice input / haptics / curated suggestions.

---

## Task 1: Add `delete` to `RecentSearchDao` + DAO test

**Files:**
- Modify: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDao.kt`
- Modify: `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDaoTest.kt`

- [ ] **Step 1: Add the failing instrumented test**

Append to `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDaoTest.kt`, immediately before the `private fun entity(...)` helper at the bottom of the class:

```kotlin
    @Test
    fun delete_removesOnlyMatchingRow() = runTest {
        dao.upsertAndTrim(entity("kotlin", epoch = 1_000), capacity = 10)
        dao.upsertAndTrim(entity("compose", epoch = 2_000), capacity = 10)
        dao.upsertAndTrim(entity("room", epoch = 3_000), capacity = 10)

        dao.delete("compose")

        val rows = dao.observeAll().first()
        assertEquals(listOf("room", "kotlin"), rows.map(RecentSearchEntity::query))
    }
```

- [ ] **Step 2: Verify compile-time failure**

Run: `./gradlew :core:database:compileDebugAndroidTestKotlin -q`
Expected: BUILD FAILED with `Unresolved reference: delete` (the DAO method doesn't exist yet).

- [ ] **Step 3: Add the DAO method**

In `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDao.kt`, immediately before the existing `@Query("DELETE FROM recent_search") suspend fun clearAll()` method, add:

```kotlin
    @Query("DELETE FROM recent_search WHERE `query` = :query")
    suspend fun delete(query: String)
```

The backtick-quoting around `` `query` `` is mandatory — `query` is a SQLite reserved keyword.

- [ ] **Step 4: Verify androidTest compiles**

Run: `./gradlew :core:database:assembleDebugAndroidTest -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDao.kt \
        core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/dao/RecentSearchDaoTest.kt

git commit -m "$(cat <<'EOF'
feat(core/database): add RecentSearchDao.delete + test

Single-query DELETE WHERE `query` = :query. Enables per-chip dismiss
in the search input UI (nubecita-vrba.5). No schema change — the
table shape from v2 stays intact, just gains another query method
on the same DAO.

Refs: nubecita-vrba.5
EOF
)"
```

The instrumented test itself runs in Task 11 (single emulator pass).

---

## Task 2: Add `remove` to `RecentSearchRepository` + unit tests

**Files:**
- Modify: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/RecentSearchRepository.kt`
- Modify: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/RecentSearchRepositoryTest.kt`

- [ ] **Step 1: Add the failing tests**

In `RecentSearchRepositoryTest`, append two tests after the existing `clearAll_delegatesToDao` test:

```kotlin
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
```

Extend `FakeRecentSearchDao` at the bottom of the same file with the `delete` override (right after `clearAll`):

```kotlin
    override suspend fun delete(query: String) {
        state.update { current -> current.filterNot { it.query == query } }
    }
```

- [ ] **Step 2: Verify compile-time failure**

Run: `./gradlew :feature:search:impl:compileDebugUnitTestKotlin -q`
Expected: BUILD FAILED with `Unresolved reference: remove` on `repo.remove(...)`.

- [ ] **Step 3: Add the repo method**

In `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/RecentSearchRepository.kt`, immediately after `suspend fun record(query: String) { ... }`, add:

```kotlin
        suspend fun remove(query: String) {
            val trimmed = query.trim()
            if (trimmed.isEmpty()) return
            dao.delete(trimmed)
        }
```

(Keep the same 8-column indent style the existing methods use — the class body is nested under `@Inject constructor(...) { ... }`.)

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :feature:search:impl:testDebugUnitTest -q`
Expected: BUILD SUCCESSFUL — 6 tests pass (4 pre-existing + 2 new).

- [ ] **Step 5: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/data/RecentSearchRepository.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/RecentSearchRepositoryTest.kt

git commit -m "$(cat <<'EOF'
feat(feature/search/impl): add RecentSearchRepository.remove + tests

Single-query delegation to RecentSearchDao.delete. Mirrors record()'s
shape: trim, ignore blank, delegate. Two new unit tests against the
hand-written FakeRecentSearchDao (extended with a delete override).

Required for the per-chip dismiss event in the search input UI
(SearchEvent.RecentChipRemoved, handled in a later commit).

Refs: nubecita-vrba.5
EOF
)"
```

---

## Task 3: MVI contract — `SearchContract.kt`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchContract.kt`

- [ ] **Step 1: Write the contract**

Create the new file with the following verbatim content:

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Search tab home (input row + recent-search chips).
 *
 * [currentQuery] is the debounced, trimmed view of [SearchViewModel.textFieldState];
 * the search-tab content (vrba.6 / vrba.7 / vrba.8) will subscribe to it via
 * the screen Composable and re-issue searchPosts / searchActors RPCs whenever
 * it changes.
 *
 * [recentSearches] mirrors the LRU list owned by `RecentSearchRepository`.
 * It is empty when the user has no recent searches; the chip-strip composable
 * does not render in that case.
 */
@Immutable
data class SearchScreenViewState(
    val recentSearches: ImmutableList<String> = persistentListOf(),
    val currentQuery: String = "",
    val isQueryBlank: Boolean = true,
) : UiState

sealed interface SearchEvent : UiEvent {
    /** IME action `Search` / hardware Enter. Persists the current non-blank text. */
    data object SubmitClicked : SearchEvent

    /** Tap on a chip body. Seeds the text field and bumps the chip's recency. */
    data class RecentChipTapped(val query: String) : SearchEvent

    /** Tap on the trailing X icon on a chip. Removes that chip. */
    data class RecentChipRemoved(val query: String) : SearchEvent

    /** Overflow-menu "Clear all" item. Wipes the recent-search list. */
    data object ClearAllRecentsClicked : SearchEvent
}

/**
 * Empty for vrba.5. The cross-VM orchestration (vrba.8) emits a
 * `NavigateTo(target: NavKey)` effect when the user taps a post or actor
 * row in the (still-pending) tab content; until then this screen signals
 * nothing externally.
 */
sealed interface SearchEffect : UiEffect
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :feature:search:impl:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchContract.kt

git commit -m "$(cat <<'EOF'
feat(feature/search/impl): MVI contract for search input + chips

SearchScreenViewState carries the debounced currentQuery (downstream
tab VMs will subscribe), the LRU recent-search list, and a derived
isQueryBlank flag.

SearchEvent covers the four discrete UI signals the input layer
emits: submit (IME/Enter), chip tap (seed + bump), chip remove,
clear-all. SearchEffect is intentionally empty until vrba.8 wires
the tab fan-out.

Refs: nubecita-vrba.5
EOF
)"
```

---

## Task 4: `SearchViewModel` skeleton + snapshotFlow collector + repo collector (TDD)

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModel.kt`
- Create: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModelTest.kt`

This is the largest task — it adds the VM skeleton, the snapshotFlow + debounce collector, the `recentSearches.observeRecent()` collector, and the test infrastructure (including the snapshot-in-unit-tests setup). `handleEvent` is added in Task 5.

- [ ] **Step 1: Write the failing test file (skeleton + 2 tests for this task's scope)**

Create `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModelTest.kt` with:

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.snapshots.Snapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.TestScope
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

            assertEquals(listOf("kotlin", "compose"), vm.uiState.value.recentSearches.toList())
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

    override fun observeAll(): Flow<List<RecentSearchEntity>> =
        state.map { it.sortedByDescending(RecentSearchEntity::recordedAt) }

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
```

- [ ] **Step 2: Verify compile-time failure**

Run: `./gradlew :feature:search:impl:compileDebugUnitTestKotlin -q`
Expected: BUILD FAILED with `Unresolved reference: SearchViewModel`.

- [ ] **Step 3: Write the VM (just enough to pass the two tests above)**

Create `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModel.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.search.impl.data.RecentSearchRepository

/**
 * Presenter for the Search tab home (input row + recent-search chips).
 *
 * Owns a public [textFieldState] per the sanctioned editor exception
 * (see `SearchContract.kt` KDoc + `:feature:composer:impl`'s
 * `ComposerViewModel`). The screen Composable wires
 * `OutlinedTextField(state = vm.textFieldState, ...)` directly so IME
 * writes don't round-trip through `handleEvent`.
 *
 * `init` launches two collectors:
 *   - `snapshotFlow { textFieldState.text.toString() }.debounce(DEBOUNCE)
 *     .distinctUntilChanged()` updates [SearchScreenViewState.currentQuery]
 *     and the derived [SearchScreenViewState.isQueryBlank].
 *   - [RecentSearchRepository.observeRecent] updates
 *     [SearchScreenViewState.recentSearches].
 *
 * Event handling (submit / chip tap / remove / clear-all) lands in a
 * later step.
 */
@OptIn(FlowPreview::class)
@HiltViewModel
internal class SearchViewModel
    @Inject
    constructor(
        private val recentSearches: RecentSearchRepository,
    ) : MviViewModel<SearchScreenViewState, SearchEvent, SearchEffect>(SearchScreenViewState()) {
        val textFieldState: TextFieldState = TextFieldState()

        init {
            snapshotFlow { textFieldState.text.toString() }
                .debounce(DEBOUNCE.inWholeMilliseconds)
                .distinctUntilChanged()
                .onEach { raw ->
                    val trimmed = raw.trim()
                    setState { copy(currentQuery = trimmed, isQueryBlank = trimmed.isEmpty()) }
                }
                .launchIn(viewModelScope)

            recentSearches
                .observeRecent()
                .onEach { list -> setState { copy(recentSearches = list.toImmutableList()) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: SearchEvent) {
            // Implemented in the next task.
        }

        private companion object {
            val DEBOUNCE = 250.milliseconds
        }
    }
```

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :feature:search:impl:testDebugUnitTest -q`
Expected: BUILD SUCCESSFUL. 8 tests pass (6 repo + 2 VM).

- [ ] **Step 5: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModel.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModelTest.kt

git commit -m "$(cat <<'EOF'
feat(feature/search/impl): SearchViewModel snapshotFlow + repo collector

Hilt-injected VM extending MviViewModel<SearchScreenViewState,
SearchEvent, SearchEffect>. Public val textFieldState (sanctioned
editor exception) plus two init collectors:
 - snapshotFlow on textFieldState.text, debounced 250ms,
   distinctUntilChanged, writes to state.currentQuery + isQueryBlank.
 - RecentSearchRepository.observeRecent() writes to state.recentSearches.

handleEvent body intentionally empty in this commit; the four event
handlers (submit, chip tap, chip remove, clear-all) land next.

Unit tests use the canonical Snapshot.sendApplyNotifications() +
testScheduler.runCurrent() + advanceTimeBy pattern from the composer
typeahead tests. Mirrors :feature:composer:impl test setup.

Refs: nubecita-vrba.5
EOF
)"
```

---

## Task 5: `handleEvent` implementation (TDD)

**Files:**
- Modify: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModel.kt`
- Modify: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModelTest.kt`

- [ ] **Step 1: Append failing tests**

In `SearchViewModelTest`, after the existing `textFieldState_typing_updatesCurrentQuery_afterDebounce` test, append:

```kotlin
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
```

- [ ] **Step 2: Verify failures**

Run: `./gradlew :feature:search:impl:testDebugUnitTest -q`
Expected: BUILD FAILED — 5 new tests fail (the VM's `handleEvent` body is empty, so nothing is persisted / removed).

- [ ] **Step 3: Implement `handleEvent`**

In `SearchViewModel.kt`, replace the empty `override fun handleEvent(event: SearchEvent) { }` with:

```kotlin
        override fun handleEvent(event: SearchEvent) {
            when (event) {
                SearchEvent.SubmitClicked -> persistCurrent()
                is SearchEvent.RecentChipTapped -> {
                    textFieldState.setTextAndPlaceCursorAtEnd(event.query)
                    persistCurrent()
                }
                is SearchEvent.RecentChipRemoved ->
                    viewModelScope.launch {
                        recentSearches.remove(event.query)
                    }
                SearchEvent.ClearAllRecentsClicked ->
                    viewModelScope.launch {
                        recentSearches.clearAll()
                    }
            }
        }

        private fun persistCurrent() {
            val text = textFieldState.text.toString().trim()
            if (text.isEmpty()) return
            viewModelScope.launch { recentSearches.record(text) }
        }
```

`setTextAndPlaceCursorAtEnd` is already imported from `androidx.compose.foundation.text.input.*` via `TextFieldState` — but it's an extension function. Add the import explicitly at the top of the file:

```kotlin
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
```

- [ ] **Step 4: Run unit tests**

Run: `./gradlew :feature:search:impl:testDebugUnitTest -q`
Expected: BUILD SUCCESSFUL. All 13 tests pass (6 repo + 7 VM).

- [ ] **Step 5: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModel.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchViewModelTest.kt

git commit -m "$(cat <<'EOF'
feat(feature/search/impl): SearchViewModel handleEvent + persistence

Dispatches the four UI events to the recent-search repo:
 - SubmitClicked / RecentChipTapped both call persistCurrent(), which
   trims the textFieldState's current text and skips blank.
 - RecentChipTapped first seeds the field via
   setTextAndPlaceCursorAtEnd before persisting (re-tapping a chip
   bumps its recency).
 - RecentChipRemoved + ClearAllRecentsClicked delegate to the repo.

Five new unit tests cover each dispatch arm and the blank-submit
no-op.

Refs: nubecita-vrba.5
EOF
)"
```

---

## Task 6: Strings + `SearchInputRow` composable + previews

**Files:**
- Modify: `feature/search/impl/src/main/res/values/strings.xml`
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/SearchInputRow.kt`

- [ ] **Step 1: Replace the strings file content**

Open `feature/search/impl/src/main/res/values/strings.xml` and replace its body with:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <!-- Placeholder text inside the search bar's OutlinedTextField. -->
    <string name="search_input_hint">Search Bluesky</string>

    <!-- Content description for the leading search icon inside the input row. -->
    <string name="search_input_leading_icon_content_desc">Search</string>

    <!-- Content description for the trailing X that clears the current input. -->
    <string name="search_input_clear_content_desc">Clear search</string>

    <!-- Content description for the trailing X on an individual recent-search chip. -->
    <string name="search_recent_remove_content_desc">Remove recent search</string>

    <!-- Content description for the overflow menu trigger next to the chip strip. -->
    <string name="search_recent_overflow_content_desc">Recent searches options</string>

    <!-- Overflow menu item label: clears every entry from the recent-search list. -->
    <string name="search_recent_clear_all">Clear all</string>
</resources>
```

Note: this removes the old `search_screen_scaffold_title` string (it was only used by the scaffold body we're about to replace).

- [ ] **Step 2: Create the `SearchInputRow` composable + previews**

Create `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/SearchInputRow.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.R

/**
 * Search bar: a single-line `OutlinedTextField(state = textFieldState, ...)`
 * with a leading search icon, a trailing clear-X (rendered only when the
 * field is non-blank), and IME action `Search` wired to [onSubmit].
 *
 * The composable is stateless beyond the externally-owned [textFieldState];
 * [isQueryBlank] is read from `SearchScreenViewState` so the trailing icon
 * can hide without sampling Snapshot state from inside the row.
 */
@Composable
internal fun SearchInputRow(
    textFieldState: TextFieldState,
    isQueryBlank: Boolean,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = textFieldState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        placeholder = { Text(stringResource(R.string.search_input_hint)) },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = stringResource(R.string.search_input_leading_icon_content_desc),
            )
        },
        trailingIcon = {
            if (!isQueryBlank) {
                IconButton(onClick = { textFieldState.clearText() }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = stringResource(R.string.search_input_clear_content_desc),
                    )
                }
            }
        },
        lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
    )
}

@PreviewTest
@Preview(name = "search-input-blank-light", showBackground = true)
@Preview(
    name = "search-input-blank-dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchInputRowBlankPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchInputRow(
                textFieldState = rememberTextFieldState(),
                isQueryBlank = true,
                onSubmit = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "search-input-typed-light", showBackground = true)
@Preview(
    name = "search-input-typed-dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchInputRowTypedPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchInputRow(
                textFieldState = rememberTextFieldState(initialText = "kotlin"),
                isQueryBlank = false,
                onSubmit = {},
            )
        }
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :feature:search:impl:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/src/main/res/values/strings.xml \
        feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/SearchInputRow.kt

git commit -m "$(cat <<'EOF'
feat(feature/search/impl): SearchInputRow composable + strings

Material 3 OutlinedTextField(state = textFieldState) with leading
search icon, trailing clear-X (hidden when blank), IME action Search
wired to onSubmit. Single line, 16dp horizontal padding.

strings.xml drops the scaffold-only title (replaced wholesale next)
and adds six new entries covering the placeholder, the leading +
trailing icon content descriptions, the per-chip remove icon, the
overflow menu trigger, and the Clear all menu item.

Two screenshot-testable previews (blank + typed × light/dark).

Refs: nubecita-vrba.5
EOF
)"
```

---

## Task 7: `RecentSearchChipStrip` composable + previews

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/RecentSearchChipStrip.kt`

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.R

/**
 * Horizontal strip of recent-search [InputChip]s. The chip body tap fires
 * [onChipTap]; the trailing X fires [onChipRemove]. A trailing overflow
 * `IconButton` opens a one-item `DropdownMenu` with "Clear all" firing
 * [onClearAll].
 *
 * The caller is responsible for skipping this composable when [items] is
 * empty — there is no internal empty-state rendering.
 */
@Composable
internal fun RecentSearchChipStrip(
    items: ImmutableList<String>,
    onChipTap: (String) -> Unit,
    onChipRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var overflowExpanded by remember { mutableStateOf(false) }
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
    ) {
        items(items = items, key = { it }) { query ->
            InputChip(
                selected = false,
                onClick = { onChipTap(query) },
                label = { Text(query) },
                trailingIcon = {
                    IconButton(onClick = { onChipRemove(query) }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_recent_remove_content_desc),
                        )
                    }
                },
                modifier = Modifier.padding(),
            )
        }
        item(key = "overflow") {
            IconButton(onClick = { overflowExpanded = true }) {
                Icon(
                    imageVector = Icons.Default.MoreVert,
                    contentDescription = stringResource(R.string.search_recent_overflow_content_desc),
                )
            }
            DropdownMenu(expanded = overflowExpanded, onDismissRequest = { overflowExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.search_recent_clear_all)) },
                    onClick = {
                        overflowExpanded = false
                        onClearAll()
                    },
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "recent-chip-strip-light", showBackground = true)
@Preview(
    name = "recent-chip-strip-dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun RecentSearchChipStripPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            RecentSearchChipStrip(
                items = persistentListOf("kotlin", "compose", "room", "bluesky", "navigation 3"),
                onChipTap = {},
                onChipRemove = {},
                onClearAll = {},
            )
        }
    }
}
```

The bare `Modifier.padding()` after the InputChip's `modifier =` is a placeholder for any future per-chip spacing — leave it as written (Compose's `Modifier.padding()` with no args is a no-op and lints clean). Remove the line entirely if spotless flags it:

```bash
./gradlew :feature:search:impl:spotlessApply -q
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :feature:search:impl:compileDebugKotlin -q`
Expected: BUILD SUCCESSFUL. If `Modifier.padding()` with no args fails to resolve, delete the entire `modifier = Modifier.padding()` line from the `InputChip` call site (the chip's default modifier is fine for v1).

- [ ] **Step 3: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/RecentSearchChipStrip.kt

git commit -m "$(cat <<'EOF'
feat(feature/search/impl): RecentSearchChipStrip composable + preview

LazyRow of Material 3 InputChips with per-chip trailing X and a
trailing overflow IconButton that opens a DropdownMenu with a single
Clear all item. The chip body fires onChipTap; the X fires
onChipRemove; the menu item fires onClearAll.

Empty-state rendering is the caller's responsibility — this
composable assumes items.isNotEmpty(). One screenshot-testable
preview (5 chips × light/dark).

Refs: nubecita-vrba.5
EOF
)"
```

---

## Task 8: Replace `SearchScreen` body + add screen-level preview

**Files:**
- Modify: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreen.kt`

- [ ] **Step 1: Replace the file contents**

Overwrite `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreen.kt` verbatim:

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.ui.RecentSearchChipStrip
import net.kikin.nubecita.feature.search.impl.ui.SearchInputRow
import androidx.compose.foundation.text.input.TextFieldState

/**
 * Stateful Search tab home. Hoists [SearchViewModel] and renders the input
 * row + (optionally) the recent-search chip strip. The tab content
 * (Posts / People) is added below this Column in nubecita-vrba.8; this
 * commit ships only the input half.
 */
@Composable
internal fun SearchScreen(
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    SearchScreenContent(
        textFieldState = viewModel.textFieldState,
        isQueryBlank = state.isQueryBlank,
        recentSearches = state.recentSearches,
        onSubmit = { viewModel.handleEvent(SearchEvent.SubmitClicked) },
        onChipTap = { viewModel.handleEvent(SearchEvent.RecentChipTapped(it)) },
        onChipRemove = { viewModel.handleEvent(SearchEvent.RecentChipRemoved(it)) },
        onClearAll = { viewModel.handleEvent(SearchEvent.ClearAllRecentsClicked) },
        modifier = modifier,
    )
}

/**
 * Stateless screen body. Extracted so preview / screenshot-test
 * composables can drive the layout without a Hilt-graph dependency on
 * [SearchViewModel].
 */
@Composable
internal fun SearchScreenContent(
    textFieldState: TextFieldState,
    isQueryBlank: Boolean,
    recentSearches: kotlinx.collections.immutable.ImmutableList<String>,
    onSubmit: () -> Unit,
    onChipTap: (String) -> Unit,
    onChipRemove: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        SearchInputRow(
            textFieldState = textFieldState,
            isQueryBlank = isQueryBlank,
            onSubmit = onSubmit,
        )
        if (recentSearches.isNotEmpty()) {
            RecentSearchChipStrip(
                items = recentSearches,
                onChipTap = onChipTap,
                onChipRemove = onChipRemove,
                onClearAll = onClearAll,
            )
        }
        // The TabRow + tab content (Posts / People) land in nubecita-vrba.8.
    }
}

@PreviewTest
@Preview(name = "search-screen-empty-light", showBackground = true)
@Preview(
    name = "search-screen-empty-dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchScreenEmptyPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchScreenContent(
                textFieldState = TextFieldState(),
                isQueryBlank = true,
                recentSearches = persistentListOf(),
                onSubmit = {},
                onChipTap = {},
                onChipRemove = {},
                onClearAll = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "search-screen-with-chips-light", showBackground = true)
@Preview(
    name = "search-screen-with-chips-dark",
    showBackground = true,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun SearchScreenWithChipsPreview() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            SearchScreenContent(
                textFieldState = TextFieldState(),
                isQueryBlank = true,
                recentSearches = persistentListOf("kotlin", "compose", "room"),
                onSubmit = {},
                onChipTap = {},
                onChipRemove = {},
                onClearAll = {},
            )
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :feature:search:impl:compileDebugKotlin :feature:search:impl:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Verify Hilt graph still resolves end-to-end**

Run: `./gradlew :app:assembleDebug -q`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreen.kt

git commit -m "$(cat <<'EOF'
feat(feature/search/impl): real SearchScreen body (input + chips)

Replaces the placeholder centered-title scaffold with a stateful
Column hoisting SearchViewModel + collecting uiState. Renders
SearchInputRow + (when non-empty) RecentSearchChipStrip.

Extracts SearchScreenContent so previews / screenshot tests don't
need a Hilt graph. Two screen-level previews (empty + with chips
× light/dark).

The TabRow + tab content land in nubecita-vrba.8.

Refs: nubecita-vrba.5
EOF
)"
```

---

## Task 9: Screenshot tests (separate test files mirroring chats' layout)

**Files:**
- Create: `feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/ui/SearchInputRowScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/ui/RecentSearchChipStripScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/SearchScreenScreenshotTest.kt`

The `@PreviewTest @Preview` blocks already inside the composable files (Tasks 6, 7, 8) drive the screenshot harness. The dedicated `screenshotTest/` files exist to host any same-screen-with-different-fixture variations the previews above don't cover. For vrba.5 the in-file previews cover all required variants, so each `screenshotTest/` file is a thin re-export of the same previews to give the screenshot test runner an alternate discovery surface. Mirror the layout of `feature/chats/impl/src/screenshotTest/` — the existing files there are exactly this shape (a single file per UI component, each containing one or more `@PreviewTest @Preview` blocks).

- [ ] **Step 1: Confirm the existing previews are picked up by the screenshot test task**

Run: `./gradlew :feature:search:impl:updateDebugScreenshotTest -q` (or `validateDebugScreenshotTest` if reference images already exist locally). On first run this generates baseline PNGs under `feature/search/impl/src/screenshotTestDebug/reference/`.

Expected: BUILD SUCCESSFUL. Inspect the generated PNGs under `feature/search/impl/build/outputs/screenshotTest-results/preview/debug/rendered/` and `.../src/screenshotTestDebug/reference/`. There should be 10 reference images (5 previews × light/dark):
- `search-input-blank-light`, `search-input-blank-dark`
- `search-input-typed-light`, `search-input-typed-dark`
- `recent-chip-strip-light`, `recent-chip-strip-dark`
- `search-screen-empty-light`, `search-screen-empty-dark`
- `search-screen-with-chips-light`, `search-screen-with-chips-dark`

If any preview renders wrong (truncated text, wrong color), fix in the composable's `@Preview` arguments (`widthDp`, `heightDp`, `backgroundColor` if needed) and re-run.

- [ ] **Step 2: Stage the generated reference images**

```bash
git add feature/search/impl/src/screenshotTestDebug/
```

- [ ] **Step 3: Verify the screenshot test passes against the just-generated baseline**

Run: `./gradlew :feature:search:impl:validateDebugScreenshotTest -q`
Expected: BUILD SUCCESSFUL (all 10 images match their reference).

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
test(feature/search/impl): screenshot baselines for input + chips + screen

Generates the reference PNGs for the 5 @PreviewTest functions added
in Tasks 6-8 (input blank/typed, chip strip, screen empty / with
chips) × light/dark. Mirrors :feature:chats:impl's screenshot test
layout — same-file PreviewTest blocks, baselines committed under
src/screenshotTestDebug/reference/.

Refs: nubecita-vrba.5
EOF
)"
```

---

## Task 10: Verification gates + connected DAO test

**Files:** none modified in this task; verification only.

- [ ] **Step 1: Run the full local gate**

```bash
./gradlew :feature:search:impl:assembleDebug \
          :feature:search:impl:testDebugUnitTest \
          :feature:search:impl:lintDebug \
          :feature:search:impl:validateDebugScreenshotTest \
          :core:database:assembleDebugAndroidTest \
          :app:assembleDebug \
          spotlessCheck -q
```

Expected: BUILD SUCCESSFUL across the board.

- [ ] **Step 2: Check for a connected device**

```bash
adb devices
```

If the output lists only `List of devices attached` with no device line, start an emulator via the user's `android-cli` convention:

```bash
android emulator list
android emulator start medium_phone   # or whichever AVD is listed first
```

Notify the user during the wait (emulators take ~30-60s to boot).

- [ ] **Step 3: Run the DAO instrumented tests**

```bash
./gradlew :core:database:connectedDebugAndroidTest -q
```

Expected: 7 tests pass — the existing 6 from vrba.2 (`NubecitaDatabaseSmokeTest` + 5 `RecentSearchDaoTest` cases) plus the new `delete_removesOnlyMatchingRow`. The JUnit XML lives at `core/database/build/outputs/androidTest-results/connected/debug/TEST-*.xml`; spot-check that the new test case name appears with `time > 0`.

- [ ] **Step 4: No commit in this task** — verification only.

---

## Task 11: Push branch + open PR

- [ ] **Step 1: Push the branch**

```bash
git push -u origin HEAD
```

- [ ] **Step 2: Open the PR**

```bash
gh pr create --title "feat(feature/search/impl): SearchViewModel + input row + recent-search chips" --body "$(cat <<'EOF'
## Summary

Replaces the scaffold \`SearchScreen\` with the real parent \`SearchViewModel\` + \`SearchInputRow\` + \`RecentSearchChipStrip\`. Live-search per keystroke (250 ms debounce) updates \`state.currentQuery\` for downstream tab VMs (\`vrba.6\` / \`vrba.7\` / \`vrba.8\`) to consume. Persistence to the recent-search repo fires on explicit commit only (IME \`Search\` / Enter / chip tap). Per-chip dismiss (X) + overflow menu \"Clear all\".

Also extends \`:core:database\` and \`:feature:search:impl/data\` with \`delete(query)\` / \`remove(query)\` — required by the per-chip dismiss UI; no schema bump.

## Test plan

- [x] \`:feature:search:impl:testDebugUnitTest\` — 13 unit tests pass.
- [x] \`:feature:search:impl:validateDebugScreenshotTest\` — 10 reference PNGs match.
- [x] \`:feature:search:impl:lintDebug\` — clean (no \`HardcodedText\`).
- [x] \`:app:assembleDebug\` — Hilt graph still resolves.
- [x] \`:core:database:connectedDebugAndroidTest\` — 7 tests pass on the connected emulator (the new \`delete_removesOnlyMatchingRow\` plus the existing 6).

CI: the \`run-instrumented\` label is set.

Spec: \`docs/superpowers/specs/2026-05-15-search-input-and-chips-design.md\`
Plan: \`docs/superpowers/plans/2026-05-15-search-input-and-chips.md\`

Closes: nubecita-vrba.5

🤖 Generated with [Claude Code](https://claude.com/claude-code)
EOF
)"
```

- [ ] **Step 3: Add the `run-instrumented` label**

```bash
PR_NUM=$(gh pr view --json number --jq '.number')
gh pr edit "$PR_NUM" --add-label run-instrumented
```

---

## Self-Review Notes

- **Spec coverage.** Every numbered Decision in the spec maps to a task: D1 (live-search debounce) → Task 4. D2 (persist on commit only) → Task 5. D3 (TextFieldState exception) → Task 4. D4 (`currentQuery` in `UiState`) → Tasks 3+4. D5 (per-chip X + overflow Clear all) → Tasks 1, 2, 7. D6 (hide strip when empty) → Task 8. D7 (no SavedStateHandle) → Task 4 (intentional non-implementation). D8 (`remove`/`delete` in this PR) → Tasks 1, 2.
- **Placeholder scan.** None. No `TBD` / `TODO` / `Similar to Task N` references.
- **Type consistency.** `SearchScreenViewState`, `SearchEvent`, `SearchEffect`, `SearchViewModel`, `SearchScreen`, `SearchScreenContent`, `SearchInputRow`, `RecentSearchChipStrip`, `RecentSearchRepository.remove`, `RecentSearchDao.delete`, `FakeRecentSearchDao.delete` — all spellings identical across Tasks 1–8.
- **Test fixture coverage.** `SearchViewModelTest` covers all five `handleEvent` arms + the snapshotFlow + debounce + repo-collector init paths. `RecentSearchRepositoryTest` covers the new `remove` + the trim-blank case.
- **Out-of-scope reminder.** No tab content, no cross-VM orchestration, no `OnQuerySubmitted` effect, no `SavedStateHandle`. The PR description and the screen Composable's KDoc both forward-point to vrba.6/7/8.
- **CI label.** Task 11 covers the `run-instrumented` label (memory: `feedback_run_instrumented_label_on_androidtest_prs.md`).
