# New-Chat FAB + Recipient Picker Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users start a new DM via a New-Chat FAB → recipient picker (live actor search + recent-from-cache, self-excluded) that opens the existing per-DID chat thread.

**Architecture:** Adds the PR1-deferred cache read-side (`recentActors`) in `:core:actors`/`:core:database`; a `replaceTop` nav op in `:core:common`; a `NewChat` key in `:feature:chats:api`; and `NewChatViewModel`/`NewChatScreen` + FAB wiring in `:feature:chats:impl`. The picker selects an actor and `replaceTop(Chat(did))`s into the existing thread (which already resolves/creates the convo).

**Tech Stack:** Kotlin, Hilt, Room, Compose (M3), Nav3, kotlinx.coroutines Flow, JUnit5 + Turbine + MockK; AndroidX Compose screenshot tests.

**Spec:** `docs/superpowers/specs/2026-05-29-new-chat-fab-recipient-picker-design.md` · **bd:** `nubecita-b6uv.5` + `nubecita-b6uv.6` (covers `b6uv.7`)

---

## File structure

**Create**
- `feature/chats/impl/.../NewChatContract.kt` — `NewChatState`/`NewChatStatus`/`NewChatEvent`/`NewChatEffect`
- `feature/chats/impl/.../NewChatViewModel.kt`
- `feature/chats/impl/.../NewChatScreen.kt`
- `feature/chats/impl/.../ui/RecipientRow.kt`
- `feature/chats/impl/src/test/.../NewChatViewModelTest.kt`
- `feature/chats/impl/src/screenshotTest/.../NewChatScreenScreenshotTest.kt`

**Modify**
- `core/database/.../dao/ActorDao.kt` — add `recentActors`
- `core/database/src/androidTest/.../dao/ActorDaoTest.kt` — add cases
- `core/actors/.../ActorRepository.kt` — add `recentActors`
- `core/actors/.../internal/DefaultActorRepository.kt` — implement
- `core/actors/src/test/.../internal/DefaultActorRepositoryTest.kt` — add case
- `core/common/.../navigation/MainShellNavState.kt` — add `replaceTop`
- `core/common/src/test/.../navigation/MainShellNavStateTest.kt` — add case
- `feature/chats/api/.../Chats.kt` — add `NewChat`
- `feature/chats/impl/build.gradle.kts` — add `:core:actors`
- `feature/chats/impl/.../ChatsScreen.kt` — add `onNewChat`
- `feature/chats/impl/.../ChatsScreenContent.kt` — add FAB
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — wire FAB + `entry<NewChat>`

---

## Task 1: `recentActors` cache read-side (`:core:database` + `:core:actors`)

**Files:**
- Modify: `core/database/src/main/kotlin/net/kikin/nubecita/core/database/dao/ActorDao.kt`
- Test: `core/database/src/androidTest/kotlin/net/kikin/nubecita/core/database/dao/ActorDaoTest.kt`
- Modify: `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/ActorRepository.kt`
- Modify: `core/actors/src/main/kotlin/net/kikin/nubecita/core/actors/internal/DefaultActorRepository.kt`
- Test: `core/actors/src/test/kotlin/net/kikin/nubecita/core/actors/internal/DefaultActorRepositoryTest.kt`

- [ ] **Step 1: Add the failing DAO tests**

In `ActorDaoTest.kt` (it already has `internal class ActorDaoTest : DatabaseTest()` with a `dao` field and an `actor(did, handle, name, seen)` helper), add:

```kotlin
@Test
fun recentActors_ordersByLastSeenDesc_andLimits() = runTest {
    dao.upsert(listOf(
        actor("did:a", "a.bsky.social", "A", 1_000),
        actor("did:b", "b.bsky.social", "B", 3_000),
        actor("did:c", "c.bsky.social", "C", 2_000),
    ))
    dao.recentActors(selfDid = null, limit = 2).test {
        assertEquals(listOf("did:b", "did:c"), awaitItem().map { it.did }) // newest first, capped at 2
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun recentActors_excludesSelfDid() = runTest {
    dao.upsert(listOf(
        actor("did:self", "me.bsky.social", "Me", 3_000),
        actor("did:other", "other.bsky.social", "Other", 1_000),
    ))
    dao.recentActors(selfDid = "did:self", limit = 10).test {
        assertEquals(listOf("did:other"), awaitItem().map { it.did })
        cancelAndIgnoreRemainingEvents()
    }
}

@Test
fun recentActors_nullSelfDid_returnsAllRows() = runTest {
    dao.upsert(listOf(actor("did:a", "a.bsky.social", "A", 1_000)))
    dao.recentActors(selfDid = null, limit = 10).test {
        assertEquals(listOf("did:a"), awaitItem().map { it.did }) // null guard: NOT zero rows
        cancelAndIgnoreRemainingEvents()
    }
}
```

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :core:database:compileDebugAndroidTestKotlin`
Expected: FAIL — `recentActors` unresolved.

- [ ] **Step 3: Add the DAO query**

In `ActorDao.kt`, add (note the `:selfDid IS NULL OR …` guard — `did <> NULL` is `NULL` in SQL and would return zero rows):

```kotlin
@Query(
    "SELECT * FROM actors WHERE :selfDid IS NULL OR did <> :selfDid " +
        "ORDER BY last_seen_at DESC LIMIT :limit",
)
fun recentActors(selfDid: String?, limit: Int): Flow<List<ActorEntity>>
```

- [ ] **Step 4: Run the DAO tests on a connected emulator**

Run: `./gradlew :core:database:connectedDebugAndroidTest --tests "*ActorDaoTest"` (emulator-5554/5556 are connected)
Expected: all PASS (existing 5 + 3 new). If no device, ensure `:core:database:compileDebugAndroidTestKotlin` passes and defer execution to CI.

- [ ] **Step 5: Add the repo method (failing repo test first)**

In `DefaultActorRepositoryTest.kt` add:

```kotlin
@Test
fun recentActors_mapsEntitiesAndPassesSelfDid() = runTest {
    val entity = net.kikin.nubecita.core.database.model.ActorEntity(
        did = "did:a", handle = "a.bsky.social", displayName = "A", avatarUrl = null,
        lastSeenAt = kotlinx.datetime.Instant.fromEpochMilliseconds(1),
    )
    every { actorDao.recentActors("did:self", 20) } returns kotlinx.coroutines.flow.flowOf(listOf(entity))
    repo.recentActors(selfDid = "did:self").test {
        assertEquals(listOf("did:a"), awaitItem().map { it.did })
        cancelAndIgnoreRemainingEvents()
    }
}
```

- [ ] **Step 6: Add the interface + impl**

In `ActorRepository.kt`:
```kotlin
/** Recently-seen actors (most recent first) from the cache, excluding [selfDid]. */
fun recentActors(selfDid: String?, limit: Int = 20): Flow<List<ActorUi>>
```
In `DefaultActorRepository.kt` (add `import kotlinx.coroutines.flow.map` if absent):
```kotlin
override fun recentActors(selfDid: String?, limit: Int): Flow<List<ActorUi>> =
    actorDao.recentActors(selfDid, limit).map { rows -> rows.map { it.asExternalModel() } }
```

- [ ] **Step 7: Run repo tests**

Run: `./gradlew :core:actors:testDebugUnitTest`
Expected: all PASS.

- [ ] **Step 8: Commit**

```bash
./gradlew :core:actors:spotlessApply :core:database:spotlessApply -q
git add core/database/ core/actors/
git commit -m "$(printf 'feat(core/actors): add recentActors cache read (self-excluded)\n\nRefs: nubecita-b6uv.6')"
```

---

## Task 2: `replaceTop` on `MainShellNavState` (`:core:common`)

**Files:**
- Modify: `core/common/src/main/kotlin/net/kikin/nubecita/core/common/navigation/MainShellNavState.kt`
- Test: `core/common/src/test/kotlin/net/kikin/nubecita/core/common/navigation/MainShellNavStateTest.kt`

- [ ] **Step 1: Add the failing test**

The test file uses fixtures `TabFeed` (start route), `SubProfile`, `SubPost(...)` and builds state via a helper (see existing tests — reuse whatever constructor/helper they use, e.g. `newState()` or the `MainShellNavState(...)` ctor with `backStacks`). Add:

```kotlin
@Test
fun replaceTop_swapsTopOfActiveTab_andBackSkipsReplaced() {
    val state = newState() // start tab = TabFeed (reuse the file's existing builder)
    state.add(SubProfile)                       // [TabFeed, SubProfile]
    state.replaceTop(SubPost("at://x"))          // push + drop beneath
    assertEquals(listOf<NavKey>(TabFeed, SubPost("at://x")), state.backStack.toList())
    assertTrue(state.removeLast())               // back
    assertEquals(listOf<NavKey>(TabFeed), state.backStack.toList()) // SubProfile is gone
}

@Test
fun replaceTop_onTabHome_replacesSingleEntry() {
    val state = newState()                       // [TabFeed]
    state.replaceTop(SubProfile)                 // guard: don't drop the tab root into emptiness
    assertEquals(listOf<NavKey>(TabFeed, SubProfile), state.backStack.toList())
}
```

(If the file has no `newState()` helper, copy the construction the neighboring tests use verbatim.)

- [ ] **Step 2: Run to confirm failure**

Run: `./gradlew :core:common:testDebugUnitTest --tests "*MainShellNavStateTest"`
Expected: FAIL — `replaceTop` unresolved.

- [ ] **Step 3: Implement `replaceTop`**

In `MainShellNavState.kt`, after `add(key)`:

```kotlin
/**
 * Replace the current top of the active tab's back stack with [key]:
 * push [key], then remove the entry beneath it — in one snapshot block,
 * so NavDisplay renders a single forward transition (no intermediate
 * frame, no flicker) and Back skips the replaced route. If the active
 * tab is at its home (single entry), this degrades to a plain push so
 * the tab root is never dropped.
 */
fun replaceTop(key: NavKey) {
    val stack = backStacks.getValue(topLevelKey)
    stack.add(key)
    if (stack.size >= 3) {
        // [.., beneath, key] -> drop `beneath` (the route being replaced),
        // keeping the tab root at index 0.
        stack.removeAt(stack.size - 2)
    } else if (stack.size == 2 && stack[0] != topLevelKey) {
        // Defensive: only collapse when index 0 isn't the tab root.
        stack.removeAt(0)
    }
    _backStack.rebuild()
}
```

Note: in practice the picker case is `[Chats(root), NewChat]` → `add(Chat)` → `[Chats, NewChat, Chat]` (size 3) → drop index 1 (`NewChat`) → `[Chats, Chat]`. The `size == 2` branch is a guard for a degenerate stack and won't trigger for the picker (index 0 is always the tab root).

- [ ] **Step 4: Run the test**

Run: `./gradlew :core:common:testDebugUnitTest --tests "*MainShellNavStateTest"`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
./gradlew :core:common:spotlessApply -q
git add core/common/
git commit -m "$(printf 'feat(core/common): add MainShellNavState.replaceTop\n\nPush a route and drop the one beneath in one snapshot, for flicker-free\nreplace-current navigation (used by the New-Chat picker).\n\nRefs: nubecita-b6uv.6')"
```

---

## Task 3: `NewChat` NavKey (`:feature:chats:api`)

**Files:** Modify `feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt`

- [ ] **Step 1: Add the key**

Append:
```kotlin
/**
 * Recipient picker for starting a new DM. A `@MainShell` sub-route pushed
 * onto the Chats tab by the New-Chat FAB. Selecting a recipient replaces
 * this route with [Chat] (see `MainShellNavState.replaceTop`).
 */
@Serializable
data object NewChat : NavKey
```

- [ ] **Step 2: Verify + commit**

Run: `./gradlew :feature:chats:api:compileReleaseKotlin`
Expected: BUILD SUCCESSFUL.
```bash
git add feature/chats/api/
git commit -m "$(printf 'feat(chats): add NewChat NavKey\n\nRefs: nubecita-b6uv.5')"
```

---

## Task 4: `NewChatViewModel` + contract (`:feature:chats:impl`)

**Files:**
- Modify: `feature/chats/impl/build.gradle.kts`
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/NewChatContract.kt`
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/NewChatViewModel.kt`
- Test: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/NewChatViewModelTest.kt`

- [ ] **Step 1: Add the `:core:actors` dependency**

In `feature/chats/impl/build.gradle.kts`, add `implementation(project(":core:actors"))` (sorted). Run `./gradlew :feature:chats:impl:checkSortDependencies` and fix ordering if flagged.

- [ ] **Step 2: Create the contract**

`NewChatContract.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState
import net.kikin.nubecita.data.models.ActorUi

data class NewChatState(val status: NewChatStatus = NewChatStatus.Recent(persistentListOf())) : UiState

sealed interface NewChatStatus {
    data class Recent(val items: ImmutableList<ActorUi>) : NewChatStatus
    data object Searching : NewChatStatus
    data class Results(val items: ImmutableList<ActorUi>) : NewChatStatus
    data object NoResults : NewChatStatus
    data object Error : NewChatStatus // retryable; screen owns the copy (no localized string in the VM)
}

sealed interface NewChatEvent : UiEvent {
    data class RecipientSelected(val otherUserDid: String) : NewChatEvent
    data object RetryClicked : NewChatEvent
}

sealed interface NewChatEffect : UiEffect {
    data class OpenChat(val otherUserDid: String) : NewChatEffect
}
```

(Spec deviation, intentional: `Error` is a `data object`, not `Error(message)` — the VM must not hold a localized string; the screen renders the retry copy via `stringResource`, matching the `ChatsError`→`stringResource` pattern.)

- [ ] **Step 3: Write the failing VM tests**

`NewChatViewModelTest.kt` (JUnit5 + `MainDispatcherExtension` + Turbine + MockK; use a fake/relaxed `ActorRepository` and a `SessionStateProvider` stubbed to `SignedIn("me", "did:self")`). Cover the spec's cases:

```kotlin
@ExtendWith(MainDispatcherExtension::class)
class NewChatViewModelTest {
    private val repo = mockk<ActorRepository>(relaxed = true)
    private val session = mockk<SessionStateProvider> {
        every { state } returns MutableStateFlow(SessionState.SignedIn(handle = "me", did = "did:self"))
    }
    private fun vm() = NewChatViewModel(repo, session)

    private fun actor(did: String) = ActorUi(did, "$did.bsky", null, null)

    @Test fun blankQuery_loadsRecentFromCache_selfExcludedViaRepo() = runTest {
        every { repo.recentActors("did:self", any()) } returns flowOf(listOf(actor("did:a")))
        val vm = vm()
        vm.uiState.test {
            // initial state is Recent(empty); after collection, Recent([a])
            assertTrue(awaitItem().status is NewChatStatus.Recent)
            val loaded = awaitItem().status as NewChatStatus.Recent
            assertEquals(listOf("did:a"), loaded.items.map { it.did })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun typingQuery_emitsSearchingThenResults_selfFiltered() = runTest {
        every { repo.recentActors(any(), any()) } returns flowOf(emptyList())
        coEvery { repo.searchTypeahead("jay", any()) } returns Result.success(listOf(actor("did:self"), actor("did:jay")))
        val vm = vm()
        vm.uiState.test {
            skipItems(1) // initial Recent
            vm.queryFieldState.edit { replace(0, length, "jay") }
            Snapshot.sendApplyNotifications(); testScheduler.advanceUntilIdle()
            // self ("did:self") filtered out of search results
            val results = expectMostRecentItem().status
            assertTrue(results is NewChatStatus.Results)
            assertEquals(listOf("did:jay"), (results as NewChatStatus.Results).items.map { it.did })
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun clearingQuery_returnsToRecentImmediately() = runTest {
        every { repo.recentActors(any(), any()) } returns flowOf(listOf(actor("did:a")))
        coEvery { repo.searchTypeahead(any(), any()) } returns Result.success(listOf(actor("did:x")))
        val vm = vm()
        vm.uiState.test {
            vm.queryFieldState.edit { replace(0, length, "x") }
            Snapshot.sendApplyNotifications(); testScheduler.advanceUntilIdle()
            vm.queryFieldState.edit { replace(0, length, "") }
            Snapshot.sendApplyNotifications(); testScheduler.runCurrent() // NO advanceTimeBy needed — recent is instant
            assertTrue(expectMostRecentItem().status is NewChatStatus.Recent)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun searchFailure_thenRetry_reRunsSameQuery() = runTest {
        every { repo.recentActors(any(), any()) } returns flowOf(emptyList())
        coEvery { repo.searchTypeahead("jay", any()) } returnsMany listOf(
            Result.failure(IOException("net")), Result.success(listOf(actor("did:jay"))),
        )
        val vm = vm()
        vm.uiState.test {
            vm.queryFieldState.edit { replace(0, length, "jay") }
            Snapshot.sendApplyNotifications(); testScheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().status is NewChatStatus.Error)
            vm.handleEvent(NewChatEvent.RetryClicked)
            testScheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().status is NewChatStatus.Results) // re-ran despite unchanged text
            cancelAndIgnoreRemainingEvents()
        }
        coVerify(exactly = 2) { repo.searchTypeahead("jay", any()) }
    }

    @Test fun emptySearchResults_emitNoResults() = runTest {
        every { repo.recentActors(any(), any()) } returns flowOf(emptyList())
        coEvery { repo.searchTypeahead("zzz", any()) } returns Result.success(emptyList())
        val vm = vm()
        vm.uiState.test {
            vm.queryFieldState.edit { replace(0, length, "zzz") }
            Snapshot.sendApplyNotifications(); testScheduler.advanceUntilIdle()
            assertTrue(expectMostRecentItem().status is NewChatStatus.NoResults)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test fun recipientSelected_emitsOpenChatEffect() = runTest {
        every { repo.recentActors(any(), any()) } returns flowOf(emptyList())
        val vm = vm()
        vm.effects.test {
            vm.handleEvent(NewChatEvent.RecipientSelected("did:jay"))
            assertEquals(NewChatEffect.OpenChat("did:jay"), awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
```

(Imports: `io.mockk.*`, `kotlinx.coroutines.flow.*`, `kotlinx.coroutines.test.runTest`, `androidx.compose.runtime.snapshots.Snapshot`, `app.cash.turbine.test`, `java.io.IOException`, the MVI + actors + auth types. Match the exact MockDispatcher/Turbine imports the sibling `ChatViewModelTest` uses.)

- [ ] **Step 4: Run to confirm failure**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "*NewChatViewModelTest"`
Expected: FAIL — `NewChatViewModel` unresolved.

- [ ] **Step 5: Implement the ViewModel**

`NewChatViewModel.kt`:
```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.data.models.ActorUi
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewChatViewModel
    @Inject
    constructor(
        private val actorRepository: ActorRepository,
        sessionStateProvider: SessionStateProvider,
    ) : MviViewModel<NewChatState, NewChatEvent, NewChatEffect>(NewChatState()) {
        /** Editor-exception: the VM owns the search field's text + selection. */
        val queryFieldState: TextFieldState = TextFieldState()

        // Stable for the session — NewChat is only reachable inside MainShell (SignedIn).
        private val selfDid: String? = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        // Re-runs the *current* query when text alone wouldn't change (e.g. Retry).
        private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        init {
            merge(
                snapshotFlow { queryFieldState.text.toString() },
                retryTrigger.map { queryFieldState.text.toString() },
            )
                .flatMapLatest { raw ->
                    val q = raw.trim()
                    if (q.isEmpty()) {
                        // Instant — no debounce delay on the blank path.
                        actorRepository.recentActors(selfDid).map { actors ->
                            NewChatStatus.Recent(actors.toImmutableList())
                        }
                    } else {
                        flow {
                            emit(NewChatStatus.Searching)
                            delay(DEBOUNCE) // debounce lives inside the search branch only
                            emit(
                                actorRepository.searchTypeahead(q).fold(
                                    onSuccess = { actors ->
                                        val filtered = actors.filter { it.did != selfDid }
                                        if (filtered.isEmpty()) {
                                            NewChatStatus.NoResults
                                        } else {
                                            NewChatStatus.Results(filtered.toImmutableList())
                                        }
                                    },
                                    onFailure = { NewChatStatus.Error },
                                ),
                            )
                        }
                    }
                }
                .onEach { status -> setState { copy(status = status) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: NewChatEvent) {
            when (event) {
                is NewChatEvent.RecipientSelected -> sendEffect(NewChatEffect.OpenChat(event.otherUserDid))
                NewChatEvent.RetryClicked -> retryTrigger.tryEmit(Unit)
            }
        }

        private companion object {
            val DEBOUNCE = 250.milliseconds
        }
    }
```

- [ ] **Step 6: Run the VM tests**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "*NewChatViewModelTest"`
Expected: all PASS. (If `searchTypeahead`'s default `limit` arg trips a MockK strict-arg issue, stub with `searchTypeahead("jay", any())` as shown.)

- [ ] **Step 7: Commit**

```bash
./gradlew :feature:chats:impl:spotlessApply -q
git add feature/chats/impl/
git commit -m "$(printf 'feat(chats): add NewChatViewModel (recent + typeahead, self-excluded)\n\nRefs: nubecita-b6uv.6')"
```

---

## Task 5: `RecipientRow` + `NewChatScreen` (`:feature:chats:impl`)

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/RecipientRow.kt`
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/NewChatScreen.kt`
- Test: `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/NewChatScreenScreenshotTest.kt`
- Add strings to `feature/chats/impl/src/main/res/values/strings.xml`

- [ ] **Step 1: Add strings**

In the chats `strings.xml`, add (match existing naming):
```xml
<string name="new_chat_title">New message</string>
<string name="new_chat_search_placeholder">Search for someone</string>
<string name="new_chat_recent_header">Recent</string>
<string name="new_chat_no_results">No people found</string>
<string name="new_chat_error_body">Couldn\'t search right now.</string>
<string name="new_chat_retry">Retry</string>
<string name="new_chat_fab_content_description">New message</string>
```

- [ ] **Step 2: `RecipientRow`**

Read `feature/chats/impl/.../ui/ConvoListItem.kt` to reuse its avatar composable/sizing, then create `RecipientRow.kt`: a `Row` (clickable → `onClick`) with the same 40dp circle avatar (`NubecitaAsyncImage` with the same hue-fallback used by `ConvoListItem`/`ChatTopBarAvatar`), `actor.displayName ?: actor.handle` as the title, and `@${actor.handle}` as the subtitle. Signature:
```kotlin
@Composable
internal fun RecipientRow(actor: ActorUi, onClick: () -> Unit, modifier: Modifier = Modifier) { /* … */ }
```
Add a `@Preview` + `NubecitaComponentPreview` wrapper as the sibling rows do.

- [ ] **Step 3: `NewChatScreen`**

Create `NewChatScreen.kt` — stateful entry (mirrors `ChatScreen`): `hiltViewModel<NewChatViewModel>()`, collects `NewChatEffect.OpenChat` in a single `LaunchedEffect`, and renders a stateless `NewChatScreenContent`. The effect collector:
```kotlin
val navState = LocalMainShellNavState.current
val focusManager = LocalFocusManager.current
LaunchedEffect(Unit) {
    viewModel.effects.collect { effect ->
        when (effect) {
            is NewChatEffect.OpenChat -> {
                focusManager.clearFocus()                       // dismiss IME before the route change
                navState.replaceTop(Chat(otherUserDid = effect.otherUserDid))
            }
        }
    }
}
```
`NewChatScreenContent(state, queryFieldState, onEvent, onBack)`: a `Scaffold` (`containerColor = MaterialTheme.colorScheme.surface`, `contentWindowInsets = WindowInsets.safeDrawing`, `topBar` = `TopAppBar(title=new_chat_title, navigationIcon=back)`) with a body `Column(Modifier.padding(padding).consumeWindowInsets(padding))`:
- An `OutlinedTextField(state = queryFieldState, …)` with the search placeholder + `KeyboardOptions(imeAction = ImeAction.Search)`.
- `when (state.status)`:
  - `Recent` → header `new_chat_recent_header` + `LazyColumn` of `RecipientRow { onEvent(RecipientSelected(it.did)) }` (empty list → nothing/subtle empty text).
  - `Searching` → centered `CircularProgressIndicator`.
  - `Results` → `LazyColumn` of `RecipientRow`.
  - `NoResults` → centered `new_chat_no_results` text.
  - `Error` → `new_chat_error_body` + `Button(onClick = { onEvent(RetryClicked) })` labeled `new_chat_retry`.

(`onBack` → `navState.removeLast()`, wired in Task 6's entry provider.)

- [ ] **Step 4: Screenshot tests**

`NewChatScreenScreenshotTest.kt`: `@Preview`-driven via the project wrapper (mirror `ChatScreenContentScreenshotTest`), one per status — `Recent`, `Results`, `NoResults`, `Error` — feeding `NewChatScreenContent` a fixed `NewChatState` and a `rememberTextFieldState()`. Build baselines:
```bash
./gradlew :feature:chats:impl:updateDebugScreenshotTest
```

- [ ] **Step 5: Verify compile + screenshots**

Run: `./gradlew :feature:chats:impl:compileDebugKotlin :feature:chats:impl:validateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL (baselines committed).

- [ ] **Step 6: Commit**

```bash
./gradlew :feature:chats:impl:spotlessApply -q
git add feature/chats/impl/
git commit -m "$(printf 'feat(chats): add NewChatScreen + RecipientRow\n\nRefs: nubecita-b6uv.6')"
```

---

## Task 6: Wire the FAB + entry provider (`:feature:chats:impl`)

**Files:**
- Modify: `feature/chats/impl/.../ChatsScreen.kt`
- Modify: `feature/chats/impl/.../ChatsScreenContent.kt`
- Modify: `feature/chats/impl/.../di/ChatsNavigationModule.kt`
- Test: `feature/chats/impl/.../ChatsScreenContentScreenshotTest.kt` (FAB visible)

- [ ] **Step 1: Thread `onNewChat` through `ChatsScreen`**

In `ChatsScreen.kt`, add a param and pass it down:
```kotlin
internal fun ChatsScreen(
    onNavigateToChat: (otherUserDid: String) -> Unit,
    onNewChat: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatsViewModel = hiltViewModel(),
) {
    // … existing …
    ChatsScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onEvent = viewModel::handleEvent,
        onNewChat = onNewChat,
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Add the FAB to `ChatsScreenContent`**

Add `onNewChat: () -> Unit` param, and a `floatingActionButton` slot on the `Scaffold`:
```kotlin
floatingActionButton = {
    FloatingActionButton(onClick = onNewChat) {
        NubecitaIcon(
            name = NubecitaIconName.Edit, // or the existing compose/pencil icon in the icon set
            contentDescription = stringResource(R.string.new_chat_fab_content_description),
            filled = true,
        )
    }
},
```
(Confirm the icon name exists in `NubecitaIconName`; if there's no Edit/Compose glyph, reuse the composer's pencil icon name. Do NOT regenerate the icon font — pick an existing glyph.)

- [ ] **Step 3: Wire the entry provider**

In `ChatsNavigationModule.kt`: import `NewChat` + `NewChatScreen`, pass `onNewChat`, and register the picker entry:
```kotlin
entry<Chats> {
    val navState = LocalMainShellNavState.current
    ChatsScreen(
        onNavigateToChat = { did -> navState.add(Chat(otherUserDid = did)) },
        onNewChat = { navState.add(NewChat) },
    )
}
// … existing chatEntry { … } …
entry<NewChat> {
    val navState = LocalMainShellNavState.current
    NewChatScreen(onBack = { navState.removeLast() })
}
```
(`NewChatScreen` collects `OpenChat` and calls `navState.replaceTop(Chat(...))` itself — Task 5.)

- [ ] **Step 4: Update the Chats screenshot baseline (FAB now present)**

Run: `./gradlew :feature:chats:impl:updateDebugScreenshotTest` then `:feature:chats:impl:validateDebugScreenshotTest`.
Expected: validate PASS with the FAB in the `ChatsScreenContent` baselines.

- [ ] **Step 5: Build the app graph (Hilt + entry wiring)**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL (the `@MainShell` entry set compiles with the new `NewChat` provider).

- [ ] **Step 6: Commit**

```bash
./gradlew :feature:chats:impl:spotlessApply :feature:chats:impl:checkSortDependencies -q
git add feature/chats/impl/
git commit -m "$(printf 'feat(chats): New-Chat FAB + NewChat entry wiring\n\nRefs: nubecita-b6uv.5')"
```

---

## Task 7: Cross-cutting verification + close-out

- [ ] **Step 1: Full suite**

Run: `./gradlew :app:assembleDebug testDebugUnitTest spotlessCheck lint :app:checkSortDependencies`
Expected: BUILD SUCCESSFUL. Fix any `sortDependencies` ordering and re-commit if needed.

- [ ] **Step 2: Screenshot validation across touched modules**

Run: `./gradlew :feature:chats:impl:validateDebugScreenshotTest`
Expected: PASS (NewChatScreen states + Chats FAB baselines committed).

- [ ] **Step 3: On-device smoke (emulator-5554, signed in)**

Install + open Chats → tap FAB → picker opens with Recent (cache) → type a handle → live results → tap a result → lands in the chat thread; Back → Chats list (picker gone). Confirm no crash in `adb logcat` (Hilt/`MissingBinding`). Optionally confirm the `actors` cache still populates.

- [ ] **Step 4: Note `b6uv.7` covered + commit any fixups**

`bd` note on `nubecita-b6uv.7` that the existing `Chat(otherUserDid)` flow (via `getConvoForMembers`) already resolves existing-or-new convo, so the picker needs no separate resolution code; it will close with this PR. (Do not `bd close` until the PR merges.)
```bash
git add -A && git commit -m "$(printf 'chore(chats): formatting + sort after picker wiring\n\nRefs: nubecita-b6uv.6')" || true
```

---

## Done criteria
- New-Chat FAB on Chats → recipient picker (Recent-from-cache instantly, live typeahead, self excluded both paths) → select → existing chat thread; Back returns to Chats list (picker replaced, no flicker).
- `recentActors` (null-safe self-exclusion), `replaceTop`, `NewChat` key, `NewChatViewModel`/`Screen`/`RecipientRow` all in place with unit + DAO + nav + screenshot tests.
- Full gradle suite green; on-device smoke confirmed.
- PR opened with `Closes: nubecita-b6uv.5` and `Closes: nubecita-b6uv.6` (+ note `b6uv.7` covered).
