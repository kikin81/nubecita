# New-Chat FAB + recipient picker

- **bd:** `nubecita-b6uv.5` (FAB + `NewChat` key) + `nubecita-b6uv.6` (recipient picker). Covers `nubecita-b6uv.7` (existing-or-new convo resolution) via the existing Chat flow — to be noted/closed on that issue, not separately built.
- **Date:** 2026-05-29
- **Status:** Approved (design); pending implementation plan
- **Builds on:** PR1 `:core:actors` (`nubecita-26a6`).

## Summary

Let users start a new DM: a New-Chat FAB on the Chats tab opens a recipient picker that searches actors (live, via `:core:actors`) and shows recently-seen actors instantly from the cache when empty. Selecting a recipient navigates into the existing per-DID chat thread (which already resolves/creates the convo), popping the picker so Back returns to the Chats list — Gmail-style.

No user-facing behavior change to existing screens beyond the added FAB.

## Architecture

### Cache read-side — `:core:database` + `:core:actors`
The read-side PR1 deferred (no schema change; reuses the `actors` table + `last_seen_at`):

- `ActorDao` — self-exclusion happens in SQL so a `LIMIT n` always yields up to `n` rows (filtering after the query could drop one to `n-1`). The `:selfDid IS NULL OR …` guard is **required**: `did <> NULL` evaluates to `NULL` in SQL and would return zero rows if `selfDid` were ever null.
  ```kotlin
  @Query(
      "SELECT * FROM actors WHERE :selfDid IS NULL OR did <> :selfDid " +
          "ORDER BY last_seen_at DESC LIMIT :limit",
  )
  fun recentActors(selfDid: String?, limit: Int): Flow<List<ActorEntity>>
  ```
- `ActorRepository`:
  ```kotlin
  /** Recently-seen actors (most recent first) from the cache, excluding [selfDid]. */
  fun recentActors(selfDid: String?, limit: Int = 20): Flow<List<ActorUi>>
  ```
  `DefaultActorRepository.recentActors` = `actorDao.recentActors(selfDid, limit).map { it.map(ActorEntity::asExternalModel) }`.

### `NewChat` NavKey — `:feature:chats:api`
```kotlin
/** Recipient picker for starting a new DM. A @MainShell sub-route. */
data object NewChat : NavKey
```

### `replaceTop` on `MainShellNavState` — `:core:common`
The picker needs to swap itself for the chat thread (push thread, drop the picker), not just push. `MainShellNavState` today exposes only `add` / `removeLast`. Add:
```kotlin
/**
 * Replace the current top of the active tab's back stack with [key]:
 * push [key], then remove the entry beneath it — in one snapshot block,
 * so NavDisplay sees a single forward transition (no intermediate frame,
 * no flicker) and Back skips the replaced route.
 */
fun replaceTop(key: NavKey)
```
Implemented on the active `NavBackStack`: `add(key)` then `removeAt(lastIndex - 1)` (guarded for a single-element stack). Covered by a `MainShellNavStateTest` case. This is the clean fix for the picker→thread transition (vs. `removeLast()` + `add()`, which relies on snapshot-batching and lets Nav3 guess the animation direction).

### New-Chat FAB — `:feature:chats:impl` (`b6uv.5`)
- `ChatsScreenContent`'s `Scaffold` gains a `floatingActionButton` (M3 `FloatingActionButton`, bottom-end via the default Scaffold FAB position) with an edit/compose icon. Static (no hide-on-scroll — DM lists are short; deferred unless the list grows).
- `ChatsScreen` takes a new `onNewChat: () -> Unit`.
- `ChatsNavigationModule`'s `entry<Chats>` wires `onNewChat = { navState.add(NewChat) }` — mirrors the existing `onNavigateToChat`.

### Recipient picker — `NewChatScreen` + `NewChatViewModel` (`:feature:chats:impl`, `b6uv.6`)

**ViewModel** (`MviViewModel<NewChatState, NewChatEvent, NewChatEffect>`), injects `ActorRepository` + `SessionStateProvider`:

- **Query input** uses the sanctioned editor exception: a public `val queryFieldState: TextFieldState` observed via `snapshotFlow { queryFieldState.text }`. The screen wires `OutlinedTextField(state = vm.queryFieldState)`.
- **Pipeline:** source = `merge(snapshotFlow { queryFieldState.text.toString() }, retryTrigger.map { queryFieldState.text.toString() })` (a `retryTrigger: MutableSharedFlow<Unit>` so `RetryClicked` re-runs the *current* query — text alone wouldn't change). Then `distinctUntilChanged()` → `flatMapLatest { q -> … }`:
  - blank `q` → **immediately** `actorRepository.recentActors(selfDid)` → `Recent(items)`. No delay — clearing/backspacing-to-empty returns to Recent instantly.
  - non-blank `q` → emit `Searching`, then `delay(250)` (debounce **inside** the branch, so it never delays the blank path), then `actorRepository.searchTypeahead(q)` → `Results` / `NoResults` / `Error`. This is the exact shape `ComposerViewModel`'s mention typeahead uses (delay-in-branch, not an upstream `.debounce()` operator).
- **Self-exclusion:** the current DID is read once — `(sessionStateProvider.state.value as? SessionState.SignedIn)?.did` (same synchronous precedent as `ProfileViewModel`). This is safe because `NewChat` is only reachable inside `MainShell`, which `MainActivity` mounts **only** on `SessionState.SignedIn` (a stable DID for the session; background token refresh keeps `SignedIn` and never changes the DID). The DID is passed to `recentActors(selfDid, …)` (SQL-level exclusion) and used to filter network `searchTypeahead` results in the VM. If the DID were ever null, both paths degrade to "don't filter" (the SQL guard handles null) rather than crashing — no dynamic session collection needed.
- **State:**
  ```kotlin
  data class NewChatState(val status: NewChatStatus = NewChatStatus.Recent(persistentListOf())) : UiState
  sealed interface NewChatStatus {
      data class Recent(val items: ImmutableList<ActorUi>) : NewChatStatus   // blank query
      data object Searching : NewChatStatus
      data class Results(val items: ImmutableList<ActorUi>) : NewChatStatus
      data object NoResults : NewChatStatus
      data class Error(val message: String) : NewChatStatus                  // retryable
  }
  ```
  Note: unlike the composer's "hide dropdown on failure," a full-screen picker surfaces an explicit retryable `Error` (the gap Copilot flagged in PR1's typeahead failure model).
- **Events:** `QueryCleared`/`RetryClicked` (re-run current query); `RecipientSelected(did)`.
- **Effects:** `NewChatEffect.OpenChat(otherUserDid: String)`.

**Screen** (`NewChatScreen`): `Scaffold` (sub-route insets per the MainShell IME convention — `contentWindowInsets = WindowInsets.safeDrawing`), top search field, a `LazyColumn` of `RecipientRow`s with a header ("Recent" vs results), and Searching/NoResults/Error bodies. Collects `OpenChat` in a single `LaunchedEffect`:
```kotlin
val navState = LocalMainShellNavState.current
val focusManager = LocalFocusManager.current
// effect collector:
focusManager.clearFocus()                          // dismiss the IME before the route change
navState.replaceTop(Chat(otherUserDid = did))      // push thread, drop the picker (one snapshot, no flicker)
```
Back from the thread returns to the Chats list (Gmail-style). The existing `ChatViewModel` resolves DID→convo via `getConvoForMembers`; a brand-new convo materializes on first send. **No new resolution code** (this is why `b6uv.7` is covered).

**`RecipientRow`** (`:feature:chats:impl/ui`): avatar + display name + handle (falls back to handle when display name is null), styled to match `ConvoListItem`'s avatar. Chats-local — search's `ActorRow` is `internal` to `:feature:search:impl`; not promoting a shared component for a single reuse (house "per-feature UI" convention).

### Entry provider
`ChatsNavigationModule` registers `entry<NewChat> { NewChatScreen(...) }` under `@MainShell`. `:feature:chats:impl` adds `implementation(project(":core:actors"))`.

## Error & loading model
- Recent: cache `Flow`; effectively instant, no spinner needed (empty cache → empty Recent list, optionally a subtle "no recent" hint).
- Search: `Searching` shows a small progress affordance; failures → `Error` with retry; zero matches → `NoResults`.
- `getConvoForMembers` failures remain owned by the existing `ChatViewModel`/`ChatScreen` (unchanged).

## Testing
- `NewChatViewModel` unit tests (fake `ActorRepository` + fake `SessionStateProvider`): blank→Recent (self filtered out); **clearing a non-blank query returns to Recent immediately** (no 250ms lag — assert via virtual time); debounced non-blank→Results; NoResults; Error then **`RetryClicked` re-runs the same query** (assert the repo is hit again though the text is unchanged); `RecipientSelected`→`OpenChat` effect; self-exclusion in both recent and search lists. Drive `TextFieldState` mutations via `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()` per the editor-exception testing rule.
- `ActorDaoTest`: `recentActors` ordering + limit; **excludes `selfDid`**; **`selfDid = null` returns all rows** (the SQL null-guard) (instrumented).
- `MainShellNavStateTest`: `replaceTop` swaps the active tab's top entry (push + drop-beneath), Back then skips the replaced route; single-element-stack guard behaves.
- Screenshot tests for `NewChatScreen` states (Recent / Results / NoResults / Error) + the FAB on `ChatsScreen`.
- `:feature:chats:impl` / `:core:common` / `:core:actors` / `:core:database` unit + screenshot stay green; lint/spotless/checkSortDependencies clean.

## Out of scope
- Group DMs (Bluesky is 1:1).
- Caching actors from non-search surfaces (convos/profiles) — future write-through points.
- Eviction/cap on the `actors` table.
- Reactions/typing/read receipts (later epics).
