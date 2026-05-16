# SearchActorsViewModel + People tab UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship the per-tab `SearchActorsViewModel` + People tab UI (`PeopleTabContent`, `ActorRow`, empty/error/loading bodies) that vrba.8 will host inside the search results screen. Implements decisions A1–A6 plus inherited D1–D4 + D8–D9 of `docs/superpowers/specs/2026-05-16-search-actors-viewmodel-and-tab-design.md`. Single bd child (`nubecita-vrba.7`), single PR.

**Architecture:** A new `SearchActorsViewModel` (Hilt) consumes the already-merged `SearchActorsRepository`, reacts to a `MutableStateFlow<FetchKey(query, incarnation)>` via `mapLatest`, projects results into a sealed `SearchActorsLoadStatus` sum (Idle / InitialLoading / Loaded / Empty / InitialError), and exposes events for `LoadMore` (single-flight with stale-completion guard), `Retry` (incarnation-token bump), `ClearQueryClicked`, `ActorTapped`. The stateful `SearchActorsScreen` Composable hoists the VM, collects effects, routes `NavigateToProfile` to `LocalMainShellNavState.current.add(Profile(handle = effect.handle))`. The stateless `PeopleTabContent` branches on the load status; `Loaded` uses a new `ActorRow` composable with query-substring highlighting via the existing `:designsystem/HighlightedText` utility.

**Tech Stack:** Kotlin, Jetpack Compose, MVI on `ViewModel` + `StateFlow`, Hilt, Material 3, `kotlinx.coroutines.flow.mapLatest`, `kotlinx.collections.immutable`, JUnit 5 + Turbine + hand-written fakes, Compose `@PreviewTest` screenshot tests.

---

## File map

### New (under `:feature:search:impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/`)

| File | Responsibility |
|---|---|
| `SearchActorsContract.kt` | `SearchActorsState`, `SearchActorsLoadStatus`, `SearchActorsEvent`, `SearchActorsEffect`. |
| `SearchActorsError.kt` | Sealed `SearchActorsError` + `Throwable.toSearchActorsError()` extension. |
| `SearchActorsViewModel.kt` | `@HiltViewModel` presenter. `MutableStateFlow<FetchKey>` pipeline + stale-completion-guarded `loadMore`. |
| `SearchActorsScreen.kt` | Stateful entry. Hoists `hiltViewModel<SearchActorsViewModel>()`, wires `setQuery` via `LaunchedEffect`, collects effects, routes `NavigateToProfile` to `LocalMainShellNavState`. |
| `ui/PeopleTabContent.kt` | Stateless body. Branches on `SearchActorsLoadStatus`. For `Loaded`, renders a `LazyColumn` of `ActorRow` items with HorizontalDivider between rows. |
| `ui/PeopleLoadingBody.kt` | Three skeleton actor rows. |
| `ui/PeopleEmptyBody.kt` | Single-CTA "Clear search" empty state. |
| `ui/PeopleInitialErrorBody.kt` | Full-screen retry layout mirroring `PostsInitialErrorBody`. |
| `ui/ActorRow.kt` | Avatar + name (with match-highlight) + handle (with match-highlight) + click handler. |

### Modified

| File | Change |
|---|---|
| `feature/search/impl/build.gradle.kts` | Add `implementation(project(":feature:profile:api"))` for `Profile` NavKey. |
| `feature/search/impl/src/main/res/values/strings.xml` | People-tab strings (empty heading/body/button, error titles+bodies, loading content description). |

### New unit tests (`:feature:search:impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/`)

| File | Responsibility |
|---|---|
| `SearchActorsErrorTest.kt` | `Throwable.toSearchActorsError()` mapping cases (IOException → Network, unknown → Unknown(message), null-message → Unknown(null)). |
| `SearchActorsViewModelTest.kt` | All 13 cases enumerated in the spec, including the stale-completion-guard regression test (baked in from day one). |
| `data/FakeSearchActorsRepository.kt` (test-source) | Hand-written fake mirroring vrba.6's `FakeSearchPostsRepository`. CompletableDeferred-gated for cancel/in-flight tests; happy-path mode returns prebuilt pages; `respond` / `fail` / `gate` / `clearGate` helpers. |

### New screenshot tests (`:feature:search:impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/search/impl/ui/`)

| File | What it captures |
|---|---|
| `ActorRowScreenshotTest.kt` | With + without `displayName`, with + without `query` match × light/dark (4 baselines). |
| `PeopleLoadingBodyScreenshotTest.kt` | Light/dark (2 baselines). |
| `PeopleEmptyBodyScreenshotTest.kt` | Light/dark (2 baselines). |
| `PeopleInitialErrorBodyScreenshotTest.kt` | Network + RateLimited + Unknown × light/dark (6 baselines). |
| `PeopleTabContentScreenshotTest.kt` | InitialLoading + Empty + Loaded (with match) + Loaded+Appending + InitialError × light/dark (~10 baselines). |

---

## Pre-flight notes (read before coding)

- The branch is created via the `bd-worktree` skill at `../nubecita-vrba.7` (controller handles this — the implementer runs against `/Users/velazquez/code/nubecita-vrba.7`).
- All Kotlin code lives in package `net.kikin.nubecita.feature.search.impl[.ui|.data]`. Existing files in `:feature:search:impl` import the long-form package — match them.
- **`:designsystem` and `:feature:postdetail:api` are already transitively wired** into `:feature:search:impl` from vrba.6. The only new dep is `:feature:profile:api`.
- Commit subjects all-lowercase (commitlint `subject-case`). Conventional Commits. `Refs: nubecita-vrba.7` in intermediate commit footers; `Closes: nubecita-vrba.7` in the PR body ONLY (not in commit subjects — squash-merge double-closes).
- Pre-commit hooks gate everything. NEVER use `--no-verify`.
- `MockK` is NOT in this module's `testImplementation`. Use hand-written fakes (mirrors vrba.6's `FakeSearchPostsRepository`).
- The VM does NOT use `snapshotFlow` — pipeline is `MutableStateFlow<FetchKey>` only. Tests need `testScheduler.runCurrent()` after `setQuery`/`handleEvent` but no `Snapshot.sendApplyNotifications()`, no `advanceTimeBy` (no debounce inside this VM).
- **Bake the stale-completion guard into `loadMore` from Task 4 — don't wait for review to catch it.** The vrba.6 code-quality review caught this race; we're inheriting the lesson.

## Lessons baked in from vrba.6 (apply pre-emptively)

1. **`try/catch` with `CancellationException` FIRST** — already in the data layer; the VM relies on cancellation propagation.
2. **Commit subjects all-lowercase** for commitlint.
3. **No `private` modifier on `TAG` inside `private companion object`** (redundant). For vrba.7, simplest move: don't add a `companion object` at all unless you need a `TAG` for actual logging. The VM doesn't need one.
4. **NEVER `runBlocking` inside `runTest`.** Plain `@Test fun` + `runBlocking` inside `assertThrows` is the synchronous-throwing pattern. SearchActorsViewModelTest doesn't have a synchronous-throwing path, so this likely doesn't apply — but watch for it.
5. **`:feature:search:impl` depends on api modules only**, never `:impl`. Task 0 adds `:feature:profile:api`.
6. **Happy-path tests must exercise the mapping path** — don't ship tests with empty fixture arrays.
7. **`Loaded.items.size`-only `LaunchedEffect` key for pagination** — don't include `isAppending` (vrba.6 code-quality lesson). Use `rememberUpdatedState(status)` to read live `endReached`/`isAppending` inside the `.collect` lambda.
8. **`loadMore` stale-completion guard:** capture `fetchKey.value` at start; in both `onSuccess` and `onFailure`, `if (fetchKey.value != capturedKey) return@onXxx` before mutating state. Regression test exercises a query change mid-pagination.

---

## Task 0: Setup — worktree + gradle dependency

**Files:**
- Modify: `feature/search/impl/build.gradle.kts`

- [ ] **Step 1: Confirm worktree state**

Verify the branch is correct:

```bash
cd /Users/velazquez/code/nubecita-vrba.7
git rev-parse --abbrev-ref HEAD   # expect feat/nubecita-vrba.7-...
git log --oneline -3
```

- [ ] **Step 2: Add `:feature:profile:api` to the gradle file**

Edit `feature/search/impl/build.gradle.kts`. Below the existing `implementation(project(":feature:postdetail:api"))` line (added in vrba.6), add:

```kotlin
    // Tap-to-open an actor row pushes a Profile(handle) onto the MainShell
    // back stack. The api module ships just the NavKey — :feature:search:impl
    // never depends on :impl, matching the Chats / Feed / Postdetail pattern.
    implementation(project(":feature:profile:api"))
```

- [ ] **Step 3: Verify the build still configures**

```bash
./gradlew :feature:search:impl:help -q
```

Expected: completes without error.

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/build.gradle.kts
git commit -m "chore(feature/search/impl): add profile:api dep for nav effect

Refs: nubecita-vrba.7"
```

---

## Task 1: Create `SearchActorsError` + `Throwable.toSearchActorsError()` (TDD)

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsError.kt`
- Create: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsErrorTest.kt`

Mirrors vrba.6's `SearchPostsError` shape. Three branches, one extension function.

- [ ] **Step 1: Write the failing test**

Create `feature/search/impl/src/test/kotlin/.../SearchActorsErrorTest.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class SearchActorsErrorTest {
    @Test
    fun ioException_mapsToNetwork() {
        val mapped = IOException("connection reset").toSearchActorsError()
        assertEquals(SearchActorsError.Network, mapped)
    }

    @Test
    fun unknownThrowable_mapsToUnknown_withMessage() {
        val mapped = IllegalStateException("totally unexpected").toSearchActorsError()
        assertTrue(mapped is SearchActorsError.Unknown)
        assertEquals("totally unexpected", (mapped as SearchActorsError.Unknown).cause)
    }

    @Test
    fun throwableWithNullMessage_mapsToUnknown_withNullCause() {
        val mapped = RuntimeException().toSearchActorsError()
        assertTrue(mapped is SearchActorsError.Unknown)
        assertEquals(null, (mapped as SearchActorsError.Unknown).cause)
    }
}
```

- [ ] **Step 2: Run the failing tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchActorsErrorTest" -i
```

Expected: compilation fails on `SearchActorsError`, `.toSearchActorsError()`.

- [ ] **Step 3: Create `SearchActorsError.kt`**

Create `feature/search/impl/src/main/kotlin/.../SearchActorsError.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl

import java.io.IOException

/**
 * UI-resolvable error categories surfaced by the Search People tab.
 * Mirrors the [SearchPostsError] shape — same lifecycle, same mapping
 * extension pattern. Atproto-kotlin doesn't expose a typed
 * RateLimited (429) exception today; the branch is reserved for that
 * eventual SDK surface — when it lands, extend the `when` below; no
 * contract change at the VM/UI boundary.
 */
internal sealed interface SearchActorsError {
    /** Underlying network or transport failure. */
    data object Network : SearchActorsError

    /** Bluesky / atproto rate-limit response (HTTP 429). */
    data object RateLimited : SearchActorsError

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    data class Unknown(val cause: String?) : SearchActorsError
}

internal fun Throwable.toSearchActorsError(): SearchActorsError =
    when (this) {
        is IOException -> SearchActorsError.Network
        // Future: SDK-typed 429 exception. File against
        // kikin81/atproto-kotlin (per the project's existing convention)
        // when needed.
        else -> SearchActorsError.Unknown(cause = message)
    }
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchActorsErrorTest" -i
```

Expected: 3 tests pass.

- [ ] **Step 5: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsError.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsErrorTest.kt
git commit -m "feat(feature/search/impl): add searchactorserror typed mapping

Sealed Network / RateLimited / Unknown sum + Throwable extension.
Mirrors SearchPostsError; RateLimited reserved for an eventual
SDK-typed 429 surface.

Refs: nubecita-vrba.7"
```

---

## Task 2: Define `SearchActorsContract.kt`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsContract.kt`

Smaller than vrba.6's contract (no `sort` field, no `SortClicked` event, no `NavigateToChangeSort` effect).

- [ ] **Step 1: Create the contract file**

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi

/**
 * MVI state for the Search People tab.
 *
 * [currentQuery] mirrors the latest non-debounced query set via
 * [SearchActorsViewModel.setQuery]. Held here (rather than re-derived
 * from the parent) so the empty-state copy + match-highlighting have a
 * stable, recompose-friendly source.
 *
 * [loadStatus] is the mutually-exclusive lifecycle sum — per CLAUDE.md's
 * MVI carve-out, this stays sealed so the type system forbids
 * combinations like "InitialLoading AND Loaded.isAppending".
 */
@Immutable
internal data class SearchActorsState(
    val currentQuery: String = "",
    val loadStatus: SearchActorsLoadStatus = SearchActorsLoadStatus.Idle,
) : UiState

/**
 * Mutually-exclusive load lifecycle. Five variants, each rendered by a
 * distinct body composable in [net.kikin.nubecita.feature.search.impl.ui.PeopleTabContent].
 *
 * - [Idle]: query is blank; render nothing.
 * - [InitialLoading]: first-page fetch for the current query.
 * - [Loaded]: results in hand; [items], [nextCursor], [endReached] track pagination; [isAppending] is the transient pagination-in-flight flag.
 * - [Empty]: query returned zero results.
 * - [InitialError]: full-screen retry layout against the typed error.
 */
internal sealed interface SearchActorsLoadStatus {
    @Immutable
    data object Idle : SearchActorsLoadStatus

    @Immutable
    data object InitialLoading : SearchActorsLoadStatus

    @Immutable
    data class Loaded(
        val items: ImmutableList<ActorUi>,
        val nextCursor: String?,
        val endReached: Boolean,
        val isAppending: Boolean = false,
    ) : SearchActorsLoadStatus

    @Immutable
    data object Empty : SearchActorsLoadStatus

    @Immutable
    data class InitialError(val error: SearchActorsError) : SearchActorsLoadStatus
}

internal sealed interface SearchActorsEvent : UiEvent {
    /** Append-on-scroll. Single-flight + idempotent past `endReached`. */
    data object LoadMore : SearchActorsEvent

    /** Re-run the initial fetch after [SearchActorsLoadStatus.InitialError]. */
    data object Retry : SearchActorsEvent

    /** Empty-state "Clear search" button. Parent VM clears the field via effect. */
    data object ClearQueryClicked : SearchActorsEvent

    /** Tap on an actor row. Emits [SearchActorsEffect.NavigateToProfile]. */
    data class ActorTapped(val handle: String) : SearchActorsEvent
}

internal sealed interface SearchActorsEffect : UiEffect {
    /** Push [net.kikin.nubecita.feature.profile.api.Profile] onto the MainShell nav stack. */
    data class NavigateToProfile(val handle: String) : SearchActorsEffect

    /** Snackbar-surface for append-time failures; existing results stay visible. */
    data class ShowAppendError(val error: SearchActorsError) : SearchActorsEffect

    /**
     * Empty-state CTA dispatched up to vrba.8's screen; vrba.8 forwards
     * to the parent [SearchViewModel] which owns the canonical
     * `TextFieldState`. SearchActorsViewModel can't reach the parent VM
     * directly (MVI rule: no Hilt ViewModel-into-ViewModel injection).
     */
    data object NavigateToClearQuery : SearchActorsEffect
}
```

- [ ] **Step 2: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsContract.kt
git commit -m "feat(feature/search/impl): add searchactorscontract types

SearchActorsState (flat currentQuery + sealed loadStatus), sealed
SearchActorsLoadStatus with five variants, SearchActorsEvent
(LoadMore/Retry/ClearQueryClicked/ActorTapped), SearchActorsEffect
(NavigateToProfile/ShowAppendError/NavigateToClearQuery). Smaller
than SearchPostsContract — no sort, no SortClicked.

Refs: nubecita-vrba.7"
```

---

## Task 3: Hand-written `FakeSearchActorsRepository`

**Files:**
- Create: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/FakeSearchActorsRepository.kt`

Mirror vrba.6's `FakeSearchPostsRepository` shape exactly, substituting `ActorUi` for `FeedItemUi.Single` and dropping the `sort` axis from the key.

- [ ] **Step 1: Write the file**

```kotlin
package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import net.kikin.nubecita.data.models.ActorUi
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Hand-written fake for [SearchActorsRepository]. Same shape as
 * `FakeSearchPostsRepository` minus the `sort` axis.
 *
 *  - [respond] / [fail] register a result for a `(query, cursor)`
 *    pair. The first matching call wins; subsequent calls fall back to
 *    [fallback] (default: empty page).
 *  - [gate] returns a [CompletableDeferred] for the pair, suspending
 *    the call until the test completes it. Used by single-flight +
 *    mapLatest-cancellation tests.
 *  - [clearGate] removes a registered gate so a subsequent `respond`
 *    can replace it (used by the Retry test path).
 *
 * [callLog] is the chronological list of every `(query, cursor)` the
 * VM passed.
 */
internal class FakeSearchActorsRepository : SearchActorsRepository {
    private data class Key(val query: String, val cursor: String?)

    data class Call(val query: String, val cursor: String?, val limit: Int)

    private val gates = mutableMapOf<Key, CompletableDeferred<Result<SearchActorsPage>>>()
    private var fallback: Result<SearchActorsPage> =
        Result.success(SearchActorsPage(items = persistentListOf(), nextCursor = null))
    val callLog: MutableList<Call> = CopyOnWriteArrayList()

    fun respond(
        query: String,
        cursor: String?,
        items: List<ActorUi>,
        nextCursor: String? = null,
    ) {
        gate(query, cursor).complete(
            Result.success(
                SearchActorsPage(
                    items = items.toImmutableList(),
                    nextCursor = nextCursor,
                ),
            ),
        )
    }

    fun fail(
        query: String,
        cursor: String?,
        throwable: Throwable,
    ) {
        gate(query, cursor).complete(Result.failure(throwable))
    }

    fun gate(
        query: String,
        cursor: String?,
    ): CompletableDeferred<Result<SearchActorsPage>> = gates.getOrPut(Key(query, cursor)) { CompletableDeferred() }

    /** Drop a registered gate so a subsequent `respond` can replace it. */
    fun clearGate(query: String, cursor: String?) {
        gates.remove(Key(query, cursor))
    }

    fun setFallback(result: Result<SearchActorsPage>) {
        fallback = result
    }

    override suspend fun searchActors(
        query: String,
        cursor: String?,
        limit: Int,
    ): Result<SearchActorsPage> {
        callLog += Call(query = query, cursor = cursor, limit = limit)
        val key = Key(query, cursor)
        val deferred = gates[key]
        return deferred?.await() ?: fallback
    }
}

/** Tiny actor fixture for VM tests. Minimal fields exercised by the VM. */
internal fun actorFixture(
    did: String = "did:plc:fake",
    handle: String = "fake.bsky.social",
    displayName: String? = "Fake",
    avatarUrl: String? = null,
): ActorUi = ActorUi(did = did, handle = handle, displayName = displayName, avatarUrl = avatarUrl)
```

- [ ] **Step 2: Compile**

```bash
./gradlew :feature:search:impl:compileDebugUnitTestKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit (combined with Task 4 — no standalone commit yet)**

The fake's only consumers are the VM tests in Task 4. Don't commit yet; it'll land with the VM tests.

---

## Task 4: Implement `SearchActorsViewModel` — query pipeline + LoadMore + handlers (TDD)

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsViewModel.kt`
- Create: `feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsViewModelTest.kt`

Bake everything from the spec into this task: query pipeline, LoadMore with single-flight + stale-completion guard, Retry/ClearQueryClicked/ActorTapped handlers. The stale-completion guard goes in from day one — don't wait for review.

- [ ] **Step 1: Write the first batch of failing tests (Idle, Loaded, Empty, InitialError, mapLatest cancellation)**

Create `feature/search/impl/src/test/kotlin/.../SearchActorsViewModelTest.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.feature.search.impl.data.FakeSearchActorsRepository
import net.kikin.nubecita.feature.search.impl.data.actorFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Unit tests for [SearchActorsViewModel]. Same harness shape as
 * SearchPostsViewModelTest — `Dispatchers.setMain(UnconfinedTestDispatcher())`
 * + `runTest { runCurrent() }`. No snapshotFlow involved, no debounce
 * inside this VM, so no `Snapshot.sendApplyNotifications()` /
 * `advanceTimeBy` needed.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SearchActorsViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = FakeSearchActorsRepository()

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun setQuery_blank_stateStaysIdle() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            runCurrent()

            assertEquals(SearchActorsLoadStatus.Idle, vm.uiState.value.loadStatus)
            assertEquals("", vm.uiState.value.currentQuery)
            assertEquals(0, repo.callLog.size, "blank query must not hit the repo")
        }

    @Test
    fun setQuery_nonBlank_fetchesFirstPage_emitsLoaded() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            val hit = actorFixture(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice")
            repo.respond(
                query = "alice",
                cursor = null,
                items = listOf(hit),
                nextCursor = "c2",
            )

            vm.setQuery("alice")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded, "expected Loaded, was $status")
            status as SearchActorsLoadStatus.Loaded
            assertEquals(listOf(hit), status.items.toList())
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
            assertEquals("alice", vm.uiState.value.currentQuery)
        }

    @Test
    fun setQuery_emptyResponse_emitsEmpty() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            repo.respond(
                query = "no-matches",
                cursor = null,
                items = emptyList(),
                nextCursor = null,
            )

            vm.setQuery("no-matches")
            runCurrent()

            assertEquals(SearchActorsLoadStatus.Empty, vm.uiState.value.loadStatus)
        }

    @Test
    fun setQuery_failure_emitsInitialError_withMappedError() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            repo.fail(query = "alice", cursor = null, throwable = java.io.IOException("disconnected"))

            vm.setQuery("alice")
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.InitialError, "expected InitialError, was $status")
            assertEquals(
                SearchActorsError.Network,
                (status as SearchActorsLoadStatus.InitialError).error,
            )
        }

    @Test
    fun setQuery_rapidChange_cancelsPrior_viaMapLatest() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            val aliGate = repo.gate(query = "ali", cursor = null)
            repo.respond(
                query = "alic",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:alice", handle = "alice.bsky.social", displayName = "Alice")),
                nextCursor = null,
            )

            vm.setQuery("ali")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchActorsLoadStatus.InitialLoading)

            vm.setQuery("alic")
            runCurrent()

            aliGate.complete(
                Result.success(
                    net.kikin.nubecita.feature.search.impl.data.SearchActorsPage(
                        items = kotlinx.collections.immutable.persistentListOf(
                            actorFixture(did = "did:plc:stale", handle = "stale.bsky.social", displayName = "Stale"),
                        ),
                        nextCursor = null,
                    ),
                ),
            )
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded, "expected Loaded for alic, was $status")
            assertEquals(
                "did:plc:alice",
                (status as SearchActorsLoadStatus.Loaded).items.single().did,
                "stale 'ali' completion must not clobber 'alic' results",
            )
        }
}
```

- [ ] **Step 2: Run the failing tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchActorsViewModelTest" -i
```

Expected: compilation fails — `SearchActorsViewModel` doesn't exist yet.

- [ ] **Step 3: Implement the VM with EVERYTHING (query pipeline + LoadMore + handlers + stale-completion guard)**

Create `feature/search/impl/src/main/kotlin/.../SearchActorsViewModel.kt`:

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.search.impl.data.SearchActorsRepository
import javax.inject.Inject

/**
 * Presenter for the Search People tab. Reactive on a
 * [MutableStateFlow] of [FetchKey] — the screen Composable's
 * `LaunchedEffect(parentState.currentQuery)` calls [setQuery] whenever
 * the parent [SearchViewModel] emits a new debounced query, and
 * [Retry] updates the key from inside [handleEvent].
 *
 * The init pipeline runs:
 *
 *   fetchKey
 *     .onEach { setState(currentQuery) }
 *     .filter { it.query.isNotBlank() }
 *     .mapLatest { runFirstPage(it) }
 *     .launchIn(viewModelScope)
 *
 * `mapLatest` cancels the prior in-flight fetch on a new key. Retry
 * bumps an internal incarnation token so the pipeline fires even when
 * the query hasn't changed.
 *
 * Does NOT inject the parent [SearchViewModel] — Hilt-injecting
 * ViewModels into each other is a smell. The screen Composable is the
 * orchestration seam.
 *
 * `loadMore` carries a stale-completion guard: captures the current
 * [FetchKey] at start, verifies it's unchanged before applying the
 * append. Prevents a stale page-N completion from being spliced onto
 * a different query's `Loaded` list after the user typed past the
 * pagination boundary. Inherited from vrba.6's code-quality lesson.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
internal class SearchActorsViewModel
    @Inject
    constructor(
        private val repository: SearchActorsRepository,
    ) : MviViewModel<SearchActorsState, SearchActorsEvent, SearchActorsEffect>(SearchActorsState()) {
        private data class FetchKey(
            val query: String,
            /** Bumps on [SearchActorsEvent.Retry] to force a re-emit when query didn't change. */
            val incarnation: Int,
        )

        private val fetchKey = MutableStateFlow(FetchKey(query = "", incarnation = 0))

        init {
            fetchKey
                .onEach { key -> setState { copy(currentQuery = key.query) } }
                .filter { it.query.isNotBlank() }
                .mapLatest { key -> runFirstPage(key) }
                .launchIn(viewModelScope)
        }

        /** Called by the screen Composable from a `LaunchedEffect(parent.currentQuery)`. */
        fun setQuery(query: String) {
            fetchKey.update { it.copy(query = query) }
        }

        override fun handleEvent(event: SearchActorsEvent) {
            when (event) {
                SearchActorsEvent.LoadMore -> loadMore()
                SearchActorsEvent.Retry ->
                    fetchKey.update { it.copy(incarnation = it.incarnation + 1) }
                SearchActorsEvent.ClearQueryClicked ->
                    sendEffect(SearchActorsEffect.NavigateToClearQuery)
                is SearchActorsEvent.ActorTapped ->
                    sendEffect(SearchActorsEffect.NavigateToProfile(event.handle))
            }
        }

        private suspend fun runFirstPage(key: FetchKey) {
            setState { copy(loadStatus = SearchActorsLoadStatus.InitialLoading) }
            repository
                .searchActors(query = key.query, cursor = null)
                .onSuccess { page ->
                    val nextStatus =
                        if (page.items.isEmpty()) {
                            SearchActorsLoadStatus.Empty
                        } else {
                            SearchActorsLoadStatus.Loaded(
                                items = page.items,
                                nextCursor = page.nextCursor,
                                endReached = page.nextCursor == null,
                            )
                        }
                    setState { copy(loadStatus = nextStatus) }
                }.onFailure { throwable ->
                    setState {
                        copy(loadStatus = SearchActorsLoadStatus.InitialError(throwable.toSearchActorsError()))
                    }
                }
        }

        private fun loadMore() {
            val status = uiState.value.loadStatus
            if (status !is SearchActorsLoadStatus.Loaded) return
            if (status.endReached) return
            if (status.isAppending) return

            setState { copy(loadStatus = status.copy(isAppending = true)) }
            val cursor = status.nextCursor
            val capturedKey = fetchKey.value
            viewModelScope.launch {
                repository
                    .searchActors(query = capturedKey.query, cursor = cursor)
                    .onSuccess { page ->
                        // Stale-completion guard: if the user typed past
                        // this fetch's boundary (or hit Retry) while
                        // it was in flight, the mapLatest pipeline has
                        // already moved the state on. Don't splice old-
                        // query results onto a new-query list.
                        if (fetchKey.value != capturedKey) return@onSuccess
                        val current = uiState.value.loadStatus as? SearchActorsLoadStatus.Loaded ?: return@onSuccess
                        val appended = (current.items + page.items).toImmutableList()
                        setState {
                            copy(
                                loadStatus =
                                    current.copy(
                                        items = appended,
                                        nextCursor = page.nextCursor,
                                        endReached = page.nextCursor == null,
                                        isAppending = false,
                                    ),
                            )
                        }
                    }.onFailure { throwable ->
                        // Same stale guard for failures.
                        if (fetchKey.value != capturedKey) return@onFailure
                        val current = uiState.value.loadStatus as? SearchActorsLoadStatus.Loaded ?: return@onFailure
                        setState { copy(loadStatus = current.copy(isAppending = false)) }
                        sendEffect(SearchActorsEffect.ShowAppendError(throwable.toSearchActorsError()))
                    }
            }
        }
    }
```

No `private companion object { TAG }` — the VM doesn't log directly (the repository does), and an unused TAG was a code-quality finding in vrba.6.

- [ ] **Step 4: Run the first batch of tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchActorsViewModelTest" -i
```

Expected: 5 tests pass.

- [ ] **Step 5: Add the LoadMore + Retry + handler tests**

Append to `SearchActorsViewModelTest.kt`:

```kotlin
    @Test
    fun loadMore_loaded_appendsNextPage_andClearsIsAppending() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            val page1 = listOf(actorFixture(did = "did:plc:a", handle = "a.bsky.social"))
            val page2 = listOf(
                actorFixture(did = "did:plc:b", handle = "b.bsky.social"),
                actorFixture(did = "did:plc:c", handle = "c.bsky.social"),
            )
            repo.respond(query = "x", cursor = null, items = page1, nextCursor = "c2")
            repo.respond(query = "x", cursor = "c2", items = page2, nextCursor = "c3")

            vm.setQuery("x")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchActorsLoadStatus.Loaded)

            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded)
            status as SearchActorsLoadStatus.Loaded
            assertEquals(
                listOf("did:plc:a", "did:plc:b", "did:plc:c"),
                status.items.map { it.did },
            )
            assertEquals("c3", status.nextCursor)
            assertEquals(false, status.endReached)
            assertEquals(false, status.isAppending)
        }

    @Test
    fun loadMore_endReached_isNoOp() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            repo.respond(
                query = "x",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:a", handle = "a.bsky.social")),
                nextCursor = null,
            )

            vm.setQuery("x")
            runCurrent()
            val beforeCallCount = repo.callLog.size

            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()

            assertEquals(beforeCallCount, repo.callLog.size, "endReached must short-circuit before hitting repo")
        }

    @Test
    fun loadMore_alreadyAppending_isNoOp_singleFlight() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            repo.respond(
                query = "x",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:a", handle = "a.bsky.social")),
                nextCursor = "c2",
            )
            // Gate the second-page fetch so isAppending stays true.
            repo.gate(query = "x", cursor = "c2")

            vm.setQuery("x")
            runCurrent()

            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()
            val callsAfterFirstLoadMore = repo.callLog.size
            val statusMid = vm.uiState.value.loadStatus
            assertTrue(statusMid is SearchActorsLoadStatus.Loaded)
            assertEquals(true, (statusMid as SearchActorsLoadStatus.Loaded).isAppending)

            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()
            assertEquals(
                callsAfterFirstLoadMore,
                repo.callLog.size,
                "concurrent LoadMore must not double-fire the repo",
            )
        }

    @Test
    fun loadMore_failure_emitsShowAppendError_keepsExistingItems() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            repo.respond(
                query = "x",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:a", handle = "a.bsky.social")),
                nextCursor = "c2",
            )
            repo.fail(query = "x", cursor = "c2", throwable = java.io.IOException("flap"))

            vm.setQuery("x")
            runCurrent()

            app.cash.turbine.test(vm.effects) {
                vm.handleEvent(SearchActorsEvent.LoadMore)
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchActorsEffect.ShowAppendError)
                assertEquals(SearchActorsError.Network, (effect as SearchActorsEffect.ShowAppendError).error)
                cancelAndIgnoreRemainingEvents()
            }

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded)
            status as SearchActorsLoadStatus.Loaded
            assertEquals(listOf("did:plc:a"), status.items.map { it.did })
            assertEquals("c2", status.nextCursor)
            assertEquals(false, status.isAppending)
        }

    @Test
    fun loadMore_inFlight_whenQueryChanges_doesNotClobberNewQueryItems() =
        runTest {
            // Regression test for the stale-completion guard inherited
            // from vrba.6's code-quality review.
            val vm = SearchActorsViewModel(repo)
            // Page 1 "alice": one item + nextCursor=c2 so loadMore is valid.
            repo.respond(
                query = "alice",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:alice", handle = "alice.bsky.social")),
                nextCursor = "c2",
            )
            // Page 1 "bob": a single fresh item, end-of-results.
            repo.respond(
                query = "bob",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:bob", handle = "bob.bsky.social")),
                nextCursor = null,
            )
            // Gate the page-2-alice fetch so we control completion timing.
            val pageTwoAliceGate = repo.gate(query = "alice", cursor = "c2")

            // 1. Initial query → Loaded(alice).
            vm.setQuery("alice")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchActorsLoadStatus.Loaded)

            // 2. LoadMore on alice → isAppending=true, page-2-alice fetch suspended.
            vm.handleEvent(SearchActorsEvent.LoadMore)
            runCurrent()
            assertTrue(
                (vm.uiState.value.loadStatus as SearchActorsLoadStatus.Loaded).isAppending,
                "page-2-alice fetch should be in flight",
            )

            // 3. User types a different query. mapLatest fires runFirstPage(bob).
            vm.setQuery("bob")
            runCurrent()
            val afterTyping = vm.uiState.value.loadStatus
            assertTrue(afterTyping is SearchActorsLoadStatus.Loaded)
            assertEquals(
                "did:plc:bob",
                (afterTyping as SearchActorsLoadStatus.Loaded).items.single().did,
                "bob results should have landed",
            )

            // 4. The stale page-2-alice completion arrives AFTER the query change.
            //    Without the stale guard, this would splice alice-page-2 items
            //    onto the bob list.
            pageTwoAliceGate.complete(
                Result.success(
                    net.kikin.nubecita.feature.search.impl.data.SearchActorsPage(
                        items = kotlinx.collections.immutable.persistentListOf(
                            actorFixture(did = "did:plc:stale-alice", handle = "stale.bsky.social"),
                        ),
                        nextCursor = "c3",
                    ),
                ),
            )
            runCurrent()

            val finalStatus = vm.uiState.value.loadStatus
            assertTrue(finalStatus is SearchActorsLoadStatus.Loaded)
            finalStatus as SearchActorsLoadStatus.Loaded
            assertEquals(
                listOf("did:plc:bob"),
                finalStatus.items.map { it.did },
                "stale alice-page-2 items must not appear on the bob list",
            )
            assertEquals(null, finalStatus.nextCursor, "cursor must remain bob's null cursor")
        }

    @Test
    fun retry_initialError_retriggersFirstPage_viaIncarnationBump() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            repo.fail(query = "x", cursor = null, throwable = java.io.IOException("network"))

            vm.setQuery("x")
            runCurrent()
            assertTrue(vm.uiState.value.loadStatus is SearchActorsLoadStatus.InitialError)

            // Replace the failure with success for the same key.
            repo.clearGate(query = "x", cursor = null)
            repo.respond(
                query = "x",
                cursor = null,
                items = listOf(actorFixture(did = "did:plc:retry", handle = "retry.bsky.social")),
                nextCursor = null,
            )

            vm.handleEvent(SearchActorsEvent.Retry)
            runCurrent()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is SearchActorsLoadStatus.Loaded, "expected Loaded after retry, was $status")
        }

    @Test
    fun actorTapped_emitsNavigateToProfileEffect() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            app.cash.turbine.test(vm.effects) {
                vm.handleEvent(SearchActorsEvent.ActorTapped("alice.bsky.social"))
                runCurrent()

                val effect = awaitItem()
                assertTrue(effect is SearchActorsEffect.NavigateToProfile)
                assertEquals(
                    "alice.bsky.social",
                    (effect as SearchActorsEffect.NavigateToProfile).handle,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun clearQueryClicked_emitsNavigateToClearQueryEffect() =
        runTest {
            val vm = SearchActorsViewModel(repo)
            app.cash.turbine.test(vm.effects) {
                vm.handleEvent(SearchActorsEvent.ClearQueryClicked)
                runCurrent()

                assertEquals(SearchActorsEffect.NavigateToClearQuery, awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
```

That's 8 more tests, total 13 in the file (matches the spec target).

- [ ] **Step 6: Run all tests**

```bash
./gradlew :feature:search:impl:testDebugUnitTest --tests "*.SearchActorsViewModelTest" -i
```

Expected: 13 tests pass.

- [ ] **Step 7: Run spotless + lint**

```bash
./gradlew :feature:search:impl:spotlessCheck :feature:search:impl:lintDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsViewModel.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsViewModelTest.kt \
        feature/search/impl/src/test/kotlin/net/kikin/nubecita/feature/search/impl/data/FakeSearchActorsRepository.kt
git commit -m "feat(feature/search/impl): add searchactorsviewmodel + handlers

MutableStateFlow<FetchKey>.mapLatest pipeline projecting to Idle /
InitialLoading / Loaded / Empty / InitialError. loadMore carries the
stale-completion guard (capture fetchKey at start, verify before
mutating) inherited from vrba.6's code-quality lesson. Retry bumps
incarnation token. 13 unit tests including the stale-completion
regression test.

Refs: nubecita-vrba.7"
```

---

## Task 5: Add `strings.xml` entries

**Files:**
- Modify: `feature/search/impl/src/main/res/values/strings.xml`

The UI bodies in Tasks 6–10 reference these strings. Add them up-front so the composables compile.

- [ ] **Step 1: Add the new entries**

Edit `feature/search/impl/src/main/res/values/strings.xml`. Add into the existing `<resources>` block:

```xml
    <!-- vrba.7: Search People tab strings -->

    <!-- Loading state -->
    <string name="search_people_loading_content_description">Searching people</string>

    <!-- Empty state -->
    <string name="search_people_empty_title">No people match \"%1$s\"</string>
    <string name="search_people_empty_body">Try a different handle or display name.</string>
    <string name="search_people_empty_clear">Clear search</string>
    <string name="search_people_empty_icon_content_description">No matching people</string>

    <!-- Initial-error state -->
    <string name="search_people_error_network_title">No connection</string>
    <string name="search_people_error_network_body">Check your connection and try again.</string>
    <string name="search_people_error_rate_limited_title">Slow down</string>
    <string name="search_people_error_rate_limited_body">You\'ve been searching a lot. Wait a moment and try again.</string>
    <string name="search_people_error_unknown_title">Something went wrong</string>
    <string name="search_people_error_unknown_body">We couldn\'t load your search. Try again.</string>
    <string name="search_people_error_retry">Try again</string>

    <!-- Actor row -->
    <string name="search_people_actor_handle">@%1$s</string>

    <!-- Append failure snackbar -->
    <string name="search_people_append_error_network">Couldn\'t load more people. Check your connection.</string>
    <string name="search_people_append_error_rate_limited">Slow down — you\'ve been searching a lot.</string>
    <string name="search_people_append_error_unknown">Couldn\'t load more people. Try again.</string>
```

- [ ] **Step 2: Verify lint passes**

```bash
./gradlew :feature:search:impl:lintDebug
```

- [ ] **Step 3: Commit**

```bash
git add feature/search/impl/src/main/res/values/strings.xml
git commit -m "feat(feature/search/impl): add people-tab strings

Loading / empty / error / actor handle / append-snackbar strings
consumed by the UI bodies in subsequent commits.

Refs: nubecita-vrba.7"
```

---

## Task 6: Build the `ActorRow` composable

**Files:**
- Create: `feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/ActorRow.kt`

Per spec A2. Avatar + name (highlighted) + handle (highlighted) + click handler.

- [ ] **Step 1: Verify `NubecitaAvatar` signature**

```bash
grep -n "fun NubecitaAvatar" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/component/NubecitaAvatar.kt
```

Expect a `(model, contentDescription, modifier, size?)` shape. Adapt the call below if the actual signature differs.

- [ ] **Step 2: Create `ActorRow.kt`**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.HighlightedText
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.feature.search.impl.R

/**
 * Single-actor row for the People tab.
 *
 * Renders: leading avatar, primary line (displayName or fallback to
 * handle), secondary line (`@handle`) only when displayName is non-null.
 * Both lines support case-insensitive query-substring highlighting via
 * the `:designsystem`'s [HighlightedText].
 *
 * Stateless. Click dispatch is via [onClick]; the parent
 * [PeopleTabContent] wires it to a `SearchActorsEvent.ActorTapped`.
 *
 * Not in `:designsystem` because composer's typeahead uses a similar
 * but distinct row (`OutlinedCard`-wrapped, no highlight); promotion
 * happens when a third consumer surfaces — see spec A2.
 */
@Composable
internal fun ActorRow(
    actor: ActorUi,
    query: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NubecitaAvatar(
            model = actor.avatarUrl,
            contentDescription = actor.displayName ?: actor.handle,
        )
        Column(modifier = Modifier.weight(1f)) {
            HighlightedText(
                text = actor.displayName ?: actor.handle,
                match = query.takeIf { it.isNotBlank() },
                style = MaterialTheme.typography.titleMedium,
            )
            if (actor.displayName != null) {
                HighlightedText(
                    text = stringResource(R.string.search_people_actor_handle, actor.handle),
                    match = query.takeIf { it.isNotBlank() },
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                )
            }
        }
    }
}

@Preview(name = "ActorRow — with displayName, no match", showBackground = true)
@Composable
private fun ActorRowWithDisplayNameNoMatchPreview() {
    NubecitaTheme {
        ActorRow(
            actor = ActorUi(
                did = "did:plc:alice",
                handle = "alice.bsky.social",
                displayName = "Alice Chen",
                avatarUrl = null,
            ),
            query = "",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — with displayName + match", showBackground = true)
@Composable
private fun ActorRowWithMatchPreview() {
    NubecitaTheme {
        ActorRow(
            actor = ActorUi(
                did = "did:plc:alice",
                handle = "alice.bsky.social",
                displayName = "Alice Chen",
                avatarUrl = null,
            ),
            query = "ali",
            onClick = {},
        )
    }
}

@Preview(name = "ActorRow — no displayName, match on handle", showBackground = true)
@Composable
private fun ActorRowNoDisplayNamePreview() {
    NubecitaTheme {
        ActorRow(
            actor = ActorUi(
                did = "did:plc:nodisplay",
                handle = "anon42.bsky.social",
                displayName = null,
                avatarUrl = null,
            ),
            query = "anon",
            onClick = {},
        )
    }
}

@Preview(
    name = "ActorRow — dark, with avatar URL",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ActorRowDarkPreview() {
    NubecitaTheme {
        ActorRow(
            actor = ActorUi(
                did = "did:plc:withavatar",
                handle = "avatar.bsky.social",
                displayName = "With Avatar",
                avatarUrl = "https://example.com/avatar.jpg",
            ),
            query = "avatar",
            onClick = {},
        )
    }
}
```

If `NubecitaAvatar` requires a `modifier` for sizing (e.g., `Modifier.size(48.dp)`), add it. Avatars in `:feature:composer:impl`'s suggestion row likely show the canonical pattern.

- [ ] **Step 3: Compile**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
```

- [ ] **Step 4: Commit**

```bash
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/ActorRow.kt
git commit -m "feat(feature/search/impl): add actorrow composable

Avatar + name (HighlightedText) + @handle (HighlightedText, only
when displayName non-null) + click handler. Reuses :designsystem's
HighlightedText shipped in vrba.6.

Refs: nubecita-vrba.7"
```

---

## Task 7: Build `PeopleLoadingBody`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../ui/PeopleLoadingBody.kt`

Three skeleton actor rows. Hand-rolled shimmer placeholders (no shared `PostCardShimmer` analogue for actors).

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.R

/**
 * Loading body for the People tab. Three skeleton actor-row
 * placeholders. Hand-rolled (no shared shimmer primitive for actors).
 * The parent Column carries a single contentDescription so TalkBack
 * announces "Searching people" instead of three empty rows.
 */
@Composable
internal fun PeopleLoadingBody(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.search_people_loading_content_description)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
    ) {
        repeat(3) {
            ActorRowSkeleton()
        }
    }
}

@Composable
private fun ActorRowSkeleton() {
    val placeholderColor = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(placeholderColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholderColor),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(fraction = 0.4f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholderColor),
            )
        }
    }
}

@Preview(name = "PeopleLoadingBody — light", showBackground = true)
@Preview(
    name = "PeopleLoadingBody — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PeopleLoadingBodyPreview() {
    NubecitaTheme {
        PeopleLoadingBody()
    }
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/PeopleLoadingBody.kt
git commit -m "feat(feature/search/impl): add peopleloadingbody skeleton rows

Three hand-rolled actor-row skeletons (no shared shimmer primitive
for actors). Single semantics contentDescription so TalkBack
announces a single loading state.

Refs: nubecita-vrba.7"
```

---

## Task 8: Build `PeopleEmptyBody`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../ui/PeopleEmptyBody.kt`

Per spec A4. Icon + heading-with-query + body + single "Clear search" button.

- [ ] **Step 1: Choose the icon**

```bash
grep -n "PersonOff\|PersonSearch\|Inbox" designsystem/src/main/kotlin/net/kikin/nubecita/designsystem/icon/NubecitaIconName.kt
```

If `PersonOff` exists, use it. Otherwise fall back to `Inbox` (vrba.6's tested fallback). Document the choice in the file's KDoc.

- [ ] **Step 2: Create `PeopleEmptyBody.kt`**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R

/**
 * Empty-state body for the People tab. Per design A4: large icon,
 * display-style heading with the user's query, body copy, and one
 * action — "Clear search" (filled tonal). No sort toggle (the People
 * tab has no sort).
 *
 * Icon: NubecitaIconName.PersonOff if available, else Inbox (the
 * fallback used by vrba.6's PostsEmptyBody).
 */
@Composable
internal fun PeopleEmptyBody(
    currentQuery: String,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            // Substitute `Inbox` if `PersonOff` is absent — document the swap inline.
            name = NubecitaIconName.PersonOff,
            contentDescription = stringResource(R.string.search_people_empty_icon_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(R.string.search_people_empty_title, currentQuery),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.search_people_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onClearQuery) {
            Text(stringResource(R.string.search_people_empty_clear))
        }
    }
}

private val ICON_SIZE = 96.dp

@Preview(name = "PeopleEmptyBody — light", showBackground = true)
@Composable
private fun PeopleEmptyBodyLightPreview() {
    NubecitaTheme {
        PeopleEmptyBody(currentQuery = "alice", onClearQuery = {})
    }
}

@Preview(
    name = "PeopleEmptyBody — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PeopleEmptyBodyDarkPreview() {
    NubecitaTheme {
        PeopleEmptyBody(currentQuery = "xyzqq", onClearQuery = {})
    }
}
```

- [ ] **Step 3: Compile + commit**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/PeopleEmptyBody.kt
git commit -m "feat(feature/search/impl): add peopleemptybody single-cta layout

Display heading with query + single FilledTonalButton 'Clear search'.
No sort toggle (People has no sort).

Refs: nubecita-vrba.7"
```

---

## Task 9: Build `PeopleInitialErrorBody`

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../ui/PeopleInitialErrorBody.kt`

Mirror `PostsInitialErrorBody`. Maps `SearchActorsError` → stringResource via `when`.

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.SearchActorsError

@Composable
internal fun PeopleInitialErrorBody(
    error: SearchActorsError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val (titleRes, bodyRes) =
        when (error) {
            SearchActorsError.Network ->
                R.string.search_people_error_network_title to R.string.search_people_error_network_body
            SearchActorsError.RateLimited ->
                R.string.search_people_error_rate_limited_title to R.string.search_people_error_rate_limited_body
            is SearchActorsError.Unknown ->
                R.string.search_people_error_unknown_title to R.string.search_people_error_unknown_body
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(contentPadding)
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.search_people_error_retry))
        }
    }
}

private val ICON_SIZE = 64.dp

@Preview(name = "PeopleInitialErrorBody — Network (light)", showBackground = true)
@Composable
private fun PeopleInitialErrorBodyNetworkPreview() {
    NubecitaTheme {
        PeopleInitialErrorBody(error = SearchActorsError.Network, onRetry = {})
    }
}

@Preview(
    name = "PeopleInitialErrorBody — RateLimited (dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PeopleInitialErrorBodyRateLimitedDarkPreview() {
    NubecitaTheme {
        PeopleInitialErrorBody(error = SearchActorsError.RateLimited, onRetry = {})
    }
}

@Preview(name = "PeopleInitialErrorBody — Unknown (light)", showBackground = true)
@Composable
private fun PeopleInitialErrorBodyUnknownPreview() {
    NubecitaTheme {
        PeopleInitialErrorBody(
            error = SearchActorsError.Unknown(cause = "Decode failure"),
            onRetry = {},
        )
    }
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/PeopleInitialErrorBody.kt
git commit -m "feat(feature/search/impl): add peopleinitialerrorbody

Full-screen retry layout mirroring PostsInitialErrorBody. Maps each
SearchActorsError variant to a title/body string pair.

Refs: nubecita-vrba.7"
```

---

## Task 10: Build `PeopleTabContent` (stateless)

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../ui/PeopleTabContent.kt`

Branches on `SearchActorsLoadStatus`. For `Loaded`, `LazyColumn` of `ActorRow` items with `HorizontalDivider` between rows. Pagination trigger uses the vrba.6 code-quality-lesson shape: key on `(listState, items.size, endReached)` (NOT `isAppending`), with `rememberUpdatedState(status)` inside the `.collect`.

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import net.kikin.nubecita.feature.search.impl.SearchActorsEvent
import net.kikin.nubecita.feature.search.impl.SearchActorsLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchActorsState

/**
 * Stateless body for the People tab. Branches on
 * [SearchActorsState.loadStatus]. For Loaded, renders a LazyColumn of
 * [ActorRow]s with HorizontalDivider between rows. Pagination trigger
 * keyed on `(listState, items.size, endReached)` — NOT isAppending —
 * per vrba.6's code-quality lesson. The inner `.collect` reads the
 * live status via [rememberUpdatedState] so the guard sees current
 * `isAppending`.
 *
 * `onEvent` dispatches every user intent upward. Doesn't hoist
 * `hiltViewModel` itself — that's [SearchActorsScreen]'s job — so
 * previews + screenshot tests can drive this Composable without a
 * Hilt graph.
 */
@Composable
internal fun PeopleTabContent(
    state: SearchActorsState,
    onEvent: (SearchActorsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    when (val status = state.loadStatus) {
        SearchActorsLoadStatus.Idle ->
            Box(modifier = modifier.fillMaxSize())
        SearchActorsLoadStatus.InitialLoading ->
            PeopleLoadingBody(modifier = modifier)
        is SearchActorsLoadStatus.Loaded -> {
            val currentStatus by rememberUpdatedState(status)
            LoadedBody(
                items = status.items,
                isAppending = status.isAppending,
                currentQuery = state.currentQuery,
                listState = listState,
                onEvent = onEvent,
                modifier = modifier,
            )
            LaunchedEffect(listState, status.items.size, status.endReached) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    last to info.totalItemsCount
                }.distinctUntilChanged()
                    .filter { (last, total) ->
                        total > 0 && last >= total - PAGINATION_LOAD_AHEAD
                    }.collect {
                        if (!currentStatus.endReached && !currentStatus.isAppending) {
                            onEvent(SearchActorsEvent.LoadMore)
                        }
                    }
            }
        }
        SearchActorsLoadStatus.Empty ->
            PeopleEmptyBody(
                currentQuery = state.currentQuery,
                onClearQuery = { onEvent(SearchActorsEvent.ClearQueryClicked) },
                modifier = modifier,
            )
        is SearchActorsLoadStatus.InitialError ->
            PeopleInitialErrorBody(
                error = status.error,
                onRetry = { onEvent(SearchActorsEvent.Retry) },
                modifier = modifier,
            )
    }
}

@Composable
private fun LoadedBody(
    items: kotlinx.collections.immutable.ImmutableList<net.kikin.nubecita.data.models.ActorUi>,
    isAppending: Boolean,
    currentQuery: String,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onEvent: (SearchActorsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        items(items = items, key = { it.did }) { actor ->
            ActorRow(
                actor = actor,
                query = currentQuery,
                onClick = { onEvent(SearchActorsEvent.ActorTapped(actor.handle)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (isAppending) {
            item("appending-indicator") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        }
    }
}

private const val PAGINATION_LOAD_AHEAD = 5

// Previews for the five status variants

@androidx.compose.ui.tooling.preview.Preview(name = "PeopleTabContent — InitialLoading", showBackground = true)
@androidx.compose.runtime.Composable
private fun PeopleTabContentInitialLoadingPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PeopleTabContent(
            state = SearchActorsState(loadStatus = SearchActorsLoadStatus.InitialLoading, currentQuery = "alice"),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PeopleTabContent — Empty", showBackground = true)
@androidx.compose.runtime.Composable
private fun PeopleTabContentEmptyPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PeopleTabContent(
            state = SearchActorsState(loadStatus = SearchActorsLoadStatus.Empty, currentQuery = "xyzqq"),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PeopleTabContent — Loaded with match", showBackground = true)
@androidx.compose.runtime.Composable
private fun PeopleTabContentLoadedPreview() {
    val actors = kotlinx.collections.immutable.persistentListOf(
        net.kikin.nubecita.data.models.ActorUi(
            did = "did:plc:alice",
            handle = "alice.bsky.social",
            displayName = "Alice Chen",
            avatarUrl = null,
        ),
        net.kikin.nubecita.data.models.ActorUi(
            did = "did:plc:alex",
            handle = "alex.bsky.social",
            displayName = "Alex Park",
            avatarUrl = null,
        ),
    )
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PeopleTabContent(
            state = SearchActorsState(
                currentQuery = "al",
                loadStatus = SearchActorsLoadStatus.Loaded(
                    items = actors,
                    nextCursor = "c2",
                    endReached = false,
                    isAppending = false,
                ),
            ),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PeopleTabContent — Loaded + Appending", showBackground = true)
@androidx.compose.runtime.Composable
private fun PeopleTabContentLoadedAppendingPreview() {
    val actors = kotlinx.collections.immutable.persistentListOf(
        net.kikin.nubecita.data.models.ActorUi(
            did = "did:plc:alice",
            handle = "alice.bsky.social",
            displayName = "Alice Chen",
            avatarUrl = null,
        ),
    )
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PeopleTabContent(
            state = SearchActorsState(
                currentQuery = "al",
                loadStatus = SearchActorsLoadStatus.Loaded(
                    items = actors,
                    nextCursor = "c2",
                    endReached = false,
                    isAppending = true,
                ),
            ),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PeopleTabContent — InitialError(Network)", showBackground = true)
@androidx.compose.runtime.Composable
private fun PeopleTabContentInitialErrorPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PeopleTabContent(
            state = SearchActorsState(
                loadStatus = SearchActorsLoadStatus.InitialError(
                    error = net.kikin.nubecita.feature.search.impl.SearchActorsError.Network,
                ),
                currentQuery = "alice",
            ),
            onEvent = {},
        )
    }
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/ui/PeopleTabContent.kt
git commit -m "feat(feature/search/impl): add peopletabcontent stateless body

LazyColumn-based Loaded body with ActorRow + HorizontalDividers,
pagination trigger keyed on (listState, items.size, endReached) per
vrba.6's code-quality lesson (live status read via
rememberUpdatedState inside the .collect lambda). Five preview
variants.

Refs: nubecita-vrba.7"
```

---

## Task 11: Build `SearchActorsScreen` (stateful entry)

**Files:**
- Create: `feature/search/impl/src/main/kotlin/.../SearchActorsScreen.kt`

Mirror `SearchPostsScreen`. Hoists VM, wires `setQuery`, collects effects, routes `NavigateToProfile` to `LocalMainShellNavState.current.add(Profile(handle = ...))`.

- [ ] **Step 1: Create the file**

```kotlin
package net.kikin.nubecita.feature.search.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.impl.ui.PeopleTabContent

/**
 * Stateful entry for the People tab. Hoists [SearchActorsViewModel],
 * wires the parent's debounced query via [LaunchedEffect], and routes
 * [SearchActorsEffect.NavigateToProfile] to [LocalMainShellNavState] —
 * mirroring `SearchPostsScreen`'s pattern.
 *
 * Two effects propagate via callback up to the (future) vrba.8
 * search-results screen:
 *  - [onClearQuery]: parent SearchViewModel owns the canonical
 *    TextFieldState and is the only thing that can reset it.
 *  - [onShowAppendError]: append-time failures surface as snackbars
 *    in the host's SnackbarHostState, which lives at the search-
 *    screen level (not inside the per-tab content).
 */
@Composable
internal fun SearchActorsScreen(
    currentQuery: String,
    onClearQuery: () -> Unit,
    onShowAppendError: (SearchActorsError) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchActorsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val navState = LocalMainShellNavState.current
    val currentOnClearQuery by rememberUpdatedState(onClearQuery)
    val currentOnShowAppendError by rememberUpdatedState(onShowAppendError)

    LaunchedEffect(currentQuery) {
        viewModel.setQuery(currentQuery)
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is SearchActorsEffect.NavigateToProfile ->
                    navState.add(Profile(handle = effect.handle))
                is SearchActorsEffect.ShowAppendError ->
                    currentOnShowAppendError(effect.error)
                SearchActorsEffect.NavigateToClearQuery ->
                    currentOnClearQuery()
            }
        }
    }

    PeopleTabContent(
        state = state,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Compile + commit**

```bash
./gradlew :feature:search:impl:compileDebugKotlin
git add feature/search/impl/src/main/kotlin/net/kikin/nubecita/feature/search/impl/SearchActorsScreen.kt
git commit -m "feat(feature/search/impl): add stateful searchactorsscreen

Hoists SearchActorsViewModel, pushes parent's debounced query via
LaunchedEffect, collects effects in a single LaunchedEffect, routes
NavigateToProfile to LocalMainShellNavState (Profile(handle)) and
hoists NavigateToClearQuery + ShowAppendError as callbacks for
vrba.8.

Refs: nubecita-vrba.7"
```

---

## Task 12: Add screenshot tests for the new composables

**Files:**
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/ActorRowScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/PeopleLoadingBodyScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/PeopleEmptyBodyScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/PeopleInitialErrorBodyScreenshotTest.kt`
- Create: `feature/search/impl/src/screenshotTest/kotlin/.../ui/PeopleTabContentScreenshotTest.kt`

Mirror the pattern from `feature/search/impl/src/screenshotTest/kotlin/.../ui/PostsSortRowScreenshotTest.kt` (already in the repo from vrba.6). One `@PreviewTest` per variant, stacked `@Preview`s for light + dark.

- [ ] **Step 1: Write the files**

Example for the actor row:

```kotlin
package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest
@Preview(name = "actor-row-with-displayname-no-match-light", showBackground = true)
@Preview(
    name = "actor-row-with-displayname-no-match-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun ActorRowWithDisplayNameNoMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ActorRow(
                actor = ActorUi(
                    did = "did:plc:alice",
                    handle = "alice.bsky.social",
                    displayName = "Alice Chen",
                    avatarUrl = null,
                ),
                query = "",
                onClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "actor-row-with-match-light", showBackground = true)
@Preview(name = "actor-row-with-match-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowWithMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ActorRow(
                actor = ActorUi(
                    did = "did:plc:alice",
                    handle = "alice.bsky.social",
                    displayName = "Alice Chen",
                    avatarUrl = null,
                ),
                query = "ali",
                onClick = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "actor-row-no-displayname-light", showBackground = true)
@Preview(name = "actor-row-no-displayname-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ActorRowNoDisplayNameScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            ActorRow(
                actor = ActorUi(
                    did = "did:plc:nodisplay",
                    handle = "anon42.bsky.social",
                    displayName = null,
                    avatarUrl = null,
                ),
                query = "anon",
                onClick = {},
            )
        }
    }
}
```

Repeat the same shape for the four other files. For `PeopleTabContentScreenshotTest`, use the same fixture actors as the previews. Implementer writes them all in one pass.

- [ ] **Step 2: Generate baselines**

```bash
./gradlew :feature:search:impl:updateDebugScreenshotTest
./gradlew :feature:search:impl:validateDebugScreenshotTest
```

Expected: ~24 new PNG baselines under `feature/search/impl/src/screenshotTestDebug/reference/`.

- [ ] **Step 3: Commit**

```bash
git add feature/search/impl/src/screenshotTest/ feature/search/impl/src/screenshotTestDebug/
git commit -m "test(feature/search/impl): add screenshot baselines for people tab ui

ActorRow (with displayName / no displayName / with match), loading,
empty, three error variants, and PeopleTabContent across status
variants × light/dark.

Refs: nubecita-vrba.7"
```

---

## Task 13: Final verification

- [ ] **Step 1: Run the full module + designsystem + feed test suites**

```bash
./gradlew :feature:search:impl:testDebugUnitTest \
          :feature:search:impl:validateDebugScreenshotTest \
          :designsystem:testDebugUnitTest \
          :designsystem:validateDebugScreenshotTest \
          :feature:feed:impl:testDebugUnitTest \
          :feature:feed:impl:validateDebugScreenshotTest
```

Expected: BUILD SUCCESSFUL. Feed module unchanged; designsystem unchanged.

- [ ] **Step 2: Run spotless + lint**

```bash
./gradlew :feature:search:impl:spotlessCheck :feature:search:impl:lintDebug
```

Fix any auto-format issues with `./gradlew spotlessApply` and re-commit.

- [ ] **Step 3: Run app assemble (Hilt graph check)**

```bash
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL. Hilt resolves `SearchActorsViewModel`.

- [ ] **Step 4: Run pre-commit hooks against the full diff**

```bash
pre-commit run --all-files
```

Expected: PASS.

- [ ] **Step 5: Push the branch + open the PR**

Try SSH first, fall back to HTTPS-insteadof if SSH signing fails:

```bash
git push -u origin HEAD || \
  git -c "url.https://github.com/.insteadOf=git@github.com:" push -u origin HEAD
```

PR title (lowercase per commitlint):

```
feat(feature/search/impl): add searchactorsviewmodel + people tab ui
```

PR body skeleton (use HEREDOC; include `Closes: nubecita-vrba.7` in the body ONLY, NOT in commit messages):

```
## Summary
- New SearchActorsViewModel + People tab UI for the search-results
  screen (vrba.8 will host this alongside SearchPostsScreen).
- New ActorRow composable in :feature:search:impl/ui/ with query-
  substring highlighting on displayName and handle via the existing
  :designsystem/HighlightedText.
- Tap-through routes via the existing :feature:profile:api Profile
  NavKey through LocalMainShellNavState.

## Spec compliance
Implements all decisions A1-A6 plus inherited D1-D4 + D8-D9 in
docs/superpowers/specs/2026-05-16-search-actors-viewmodel-and-tab-design.md.

## Test plan
- [ ] :feature:search:impl:testDebugUnitTest — 13 new
      SearchActorsViewModelTest cases (including stale-completion-guard
      regression test inherited from vrba.6's lesson) + 3
      SearchActorsErrorTest.
- [ ] :feature:search:impl:validateDebugScreenshotTest — ~24 new
      baselines for ActorRow + the four body composables +
      PeopleTabContent variants × light/dark.
- [ ] :designsystem and :feature:feed:impl — green (no behavior change
      from this PR).
- [ ] :app:assembleDebug — Hilt resolves SearchActorsViewModel.
- [ ] pre-commit run --all-files — clean.

Closes: nubecita-vrba.7
```

Do NOT add the `run-instrumented` label — vrba.7 ships only unit + screenshot tests, no `src/androidTest/` files. Verify with `git diff --name-only main...HEAD | grep androidTest` returning empty.

---

## Self-review checklist (run before opening the PR)

After Task 13, walk through the spec one more time:

- **A1** (no sort) — verified: `FetchKey` has no `sort` field, no `SortClicked` event. ✓
- **A2** (fresh `ActorRow` in `:feature:search:impl/ui/`) — verified: Task 6 lands the row locally; composer's row untouched. ✓
- **A3** (tap-through to `Profile(handle)`) — verified: `SearchActorsScreen` calls `navState.add(Profile(handle = ...))`. ✓
- **A4** (single-CTA empty body) — verified: `PeopleEmptyBody` has one `FilledTonalButton`. ✓
- **A5** (error body mirroring posts) — verified: `PeopleInitialErrorBody` mirrors `PostsInitialErrorBody`'s shape. ✓
- **A6** (loading body — three skeleton actor rows) — verified: `PeopleLoadingBody` renders 3× `ActorRowSkeleton`. ✓
- **Inherited D1** (mapLatest pipeline) — verified Task 4. ✓
- **Inherited D2** (sealed status sum, no `Refreshing`) — verified Task 2. ✓
- **Inherited D3** (typed errors) — verified Task 1. ✓
- **Inherited D4** (LoadMore single-flight + stale-completion guard) — verified Task 4. ✓
- **Inherited D8** (stateless tab content + stateful screen) — verified Tasks 10 + 11. ✓
- **Inherited D9** (nav effect via LocalMainShellNavState) — verified Task 11. ✓
- **Unit test coverage** — 13 SearchActorsViewModelTest cases including the stale-completion regression test from day one. ✓
- **Screenshot coverage** — ~24 baselines (4 ActorRow + 2 PeopleLoadingBody + 2 PeopleEmptyBody + 6 PeopleInitialErrorBody + ~10 PeopleTabContent). ✓

## Risk + rollback

- **Risk: `NubecitaIconName.PersonOff` may not exist.** Task 8 verifies; falls back to `Inbox` per spec Risk note, documented inline.
- **Risk: `NubecitaAvatar` may require a `Modifier.size(...)`.** Task 6 verifies by reading the composable; sets size if needed.
- **Rollback:** PR independently revertible. No changes to vrba.6's shipped files (PostCard, HighlightedText, SearchPostsContract — all untouched). Reverting search-people doesn't break the Posts tab.

## Out of scope (per spec, restated)

- Typeahead screen (vrba.10).
- Feeds tab (vrba.11).
- `searchActorsTypeahead` substitution (different RPC, no cursor).
- Inline `Follow` button on actor rows.
- `description` (bio) snippet in the row.
- Promotion of `ActorRow` to `:designsystem`.
- Adaptive tablet/foldable layouts.
