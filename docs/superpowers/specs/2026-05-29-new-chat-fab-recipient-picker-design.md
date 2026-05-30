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

- `ActorDao`:
  ```kotlin
  @Query("SELECT * FROM actors ORDER BY last_seen_at DESC LIMIT :limit")
  fun recentActors(limit: Int): Flow<List<ActorEntity>>
  ```
- `ActorRepository`:
  ```kotlin
  /** Recently-seen actors (most recent first), from the local cache. */
  fun recentActors(limit: Int = 20): Flow<List<ActorUi>>
  ```
  `DefaultActorRepository.recentActors` = `actorDao.recentActors(limit).map { it.map(ActorEntity::asExternalModel) }`.

### `NewChat` NavKey — `:feature:chats:api`
```kotlin
/** Recipient picker for starting a new DM. A @MainShell sub-route. */
data object NewChat : NavKey
```

### New-Chat FAB — `:feature:chats:impl` (`b6uv.5`)
- `ChatsScreenContent`'s `Scaffold` gains a `floatingActionButton` (M3 `FloatingActionButton`, bottom-end via the default Scaffold FAB position) with an edit/compose icon.
- `ChatsScreen` takes a new `onNewChat: () -> Unit`.
- `ChatsNavigationModule`'s `entry<Chats>` wires `onNewChat = { navState.add(NewChat) }` — mirrors the existing `onNavigateToChat`.

### Recipient picker — `NewChatScreen` + `NewChatViewModel` (`:feature:chats:impl`, `b6uv.6`)

**ViewModel** (`MviViewModel<NewChatState, NewChatEvent, NewChatEffect>`), injects `ActorRepository` + `SessionStateProvider`:

- **Query input** uses the sanctioned editor exception: a public `val queryFieldState: TextFieldState` observed via `snapshotFlow { queryFieldState.text }`. The screen wires `OutlinedTextField(state = vm.queryFieldState)`.
- **Pipeline:** `snapshotFlow` → `debounce(250ms)` → `mapLatest`:
  - blank query → collect `actorRepository.recentActors()` → `Recent(items)`.
  - non-blank → `Searching` → `actorRepository.searchTypeahead(query)` → `Results(items)` / `NoResults` / `Error`.
  - (Mirror the debounce/`mapLatest` shape already used by `ComposerViewModel` / `SearchTypeaheadViewModel`.)
- **Self-exclusion:** read the current DID once — `(sessionStateProvider.state.value as? SessionState.SignedIn)?.did` (same precedent as `ProfileViewModel`) — and filter it out of BOTH recent and search result lists.
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
// pop the picker, then push the thread → Back returns to the Chats list (Gmail-style)
navState.removeLast()
navState.add(Chat(otherUserDid = did))
```
The existing `ChatViewModel` resolves DID→convo via `getConvoForMembers`; a brand-new convo materializes on first send. **No new resolution code** (this is why `b6uv.7` is covered).

**`RecipientRow`** (`:feature:chats:impl/ui`): avatar + display name + handle (falls back to handle when display name is null), styled to match `ConvoListItem`'s avatar. Chats-local — search's `ActorRow` is `internal` to `:feature:search:impl`; not promoting a shared component for a single reuse (house "per-feature UI" convention).

### Entry provider
`ChatsNavigationModule` registers `entry<NewChat> { NewChatScreen(...) }` under `@MainShell`. `:feature:chats:impl` adds `implementation(project(":core:actors"))`.

## Error & loading model
- Recent: cache `Flow`; effectively instant, no spinner needed (empty cache → empty Recent list, optionally a subtle "no recent" hint).
- Search: `Searching` shows a small progress affordance; failures → `Error` with retry; zero matches → `NoResults`.
- `getConvoForMembers` failures remain owned by the existing `ChatViewModel`/`ChatScreen` (unchanged).

## Testing
- `NewChatViewModel` unit tests (fake `ActorRepository` + fake/real `SessionStateProvider`): blank→Recent (with self filtered out), debounced non-blank→Results, NoResults, Error+retry, `RecipientSelected`→`OpenChat` effect, self-exclusion in both lists. Drive `TextFieldState` mutations via `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()` per the editor-exception testing rule.
- `ActorDaoTest`: add a `recentActors` ordering+limit case (instrumented).
- Screenshot tests for `NewChatScreen` states (Recent / Results / NoResults / Error) + the FAB on `ChatsScreen`.
- `:feature:chats:impl` unit + screenshot must stay green; lint/spotless/checkSortDependencies clean.

## Out of scope
- Group DMs (Bluesky is 1:1).
- Caching actors from non-search surfaces (convos/profiles) — future write-through points.
- Eviction/cap on the `actors` table.
- Reactions/typing/read receipts (later epics).
