# Group chat Phase 3c-2 — join requests (owner side)

**Epic:** `nubecita-hwix` (Group chat support). **Sub-slice C2** of Phase 3, after Slices A
(group details, #561), B (member management, #562), C1 (group creation, #564). **bd:**
`nubecita-hwix.7`.

## Purpose

Let a group **owner** review pending join requests and **approve** or **reject** each, via atproto
9.4.0 `chat.bsky.group` — from a dedicated screen reachable off the group-details screen.

## Scope

C2 only: the **owner-side** of join requests (list + approve/reject). Out of scope: C3 — the join
*side* (invite links: `getGroupPublicInfo` preview → `requestJoin`, `createJoinLink`/edit/enable/
disable, deep-link handling). No bulk approve/reject (per-row only). No `editGroup`.

## SDK surface (atproto 9.4.0 — verified in `models-jvm-9.4.0.jar`)

`io.github.kikin81.atproto.chat.bsky.group.GroupService`, constructed `GroupService(xrpcClient)`
(same as the Slice B/C1 group ops). No SDK bump or overlay needed.

- `listJoinRequests(ListJoinRequestsRequest(convoId: String, limit: Long? = null, cursor: String? = null))`
  → `ListJoinRequestsResponse(requests: List<JoinRequestView>, cursor: String?)`.
- `JoinRequestView`: `requestedBy: ProfileViewBasic` (avatar/handle/displayName), `requestedAt`
  (wire timestamp), `convoId`.
- `approveJoinRequest(ApproveJoinRequestRequest(convoId: String, member: Did))` →
  `ApproveJoinRequestResponse(convo: ConvoView)` (the approved user is now a member).
- `rejectJoinRequest(RejectJoinRequestRequest(convoId: String, member: Did))` →
  `RejectJoinRequestResponse` (no payload).

## Architecture

### Entry point + route

- An **owner-only "Join requests" row** in `GroupDetailsScreen`'s `LoadedBody` — a tappable row
  (leading icon + label + trailing chevron) gated on `state.viewerRole == GroupRole.Owner`, placed
  below the action row / above the member list. Tapping emits `GroupDetailsEvent.JoinRequestsTapped`
  → `GroupDetailsEffect.NavigateTo(GroupJoinRequests(convoId))`. **No count badge** (the list loads
  on the sub-screen; avoids an extra `listJoinRequests` call on every group-details open). The row
  meets M3 touch-target standards (≥ 48dp height — a `ListItem`, or `Row(Modifier.heightIn(min = 48.dp).clickable…)`).
- New NavKey in `:feature:chats:api/Chats.kt`:
  `@Serializable data class GroupJoinRequests(val convoId: String) : NavKey`.
- Registered `entry<GroupJoinRequests>(metadata = adaptiveDialog())` — full-screen on Compact, a
  centered Dialog on Medium/Expanded (consistent with `GroupDetails` / `AddGroupMembers`).

### Repository (`ChatRepository` + all 5 implementors)

```kotlin
/** Pending join requests for group [convoId]. */
suspend fun getJoinRequests(convoId: String, cursor: String? = null): Result<JoinRequestPage>

/** Approve the pending request from [did] (they become a member). */
suspend fun approveJoinRequest(convoId: String, did: String): Result<Unit>

/** Reject the pending request from [did]. */
suspend fun rejectJoinRequest(convoId: String, did: String): Result<Unit>
```
`data class JoinRequestPage(requests: ImmutableList<JoinRequestUi>, cursor: String?)`.
`DefaultChatRepository` wraps `GroupService(...)` (`ListJoinRequestsRequest(convoId, limit = …, cursor)`,
`ApproveJoinRequestRequest(convoId, member = Did(did))`, `RejectJoinRequestRequest(convoId, member = Did(did))`),
on the IO dispatcher, rethrowing `CancellationException`, logging only `javaClass.name`. The approve/
reject use the value-returning-or-Unit `groupMutation` shape. All **5** `ChatRepository` fakes get the
three methods (test fake with settable results + call captures; bench/androidTest/inline minimal).

### Model + mapper

`@Immutable data class JoinRequestUi(did: String, handle: String, displayName: String?, avatarUrl: String?, requestedAt: Instant)`.
New `feature/chats/impl/.../data/JoinRequestMapper.kt`: `internal fun JoinRequestView.toJoinRequestUi(): JoinRequestUi`
— maps `requestedBy: ProfileViewBasic` (`did.raw`, `handle.raw`, `displayName`, `avatar?.raw`) and
converts `requestedAt` → `kotlin.time.Instant` (mirroring how `ConvoMapper` converts the wire
timestamp it keeps as a raw `Instant`). Raw `Instant` is kept on the model; the row formats it via
`rememberChatRelativeTimeText` (same as `ConvoListItem`).

### Pagination: Paging 3 (network cursor source)

The list paginates lazily with **Paging 3** (first page renders immediately; subsequent pages load on
scroll). This is the first *network* `PagingSource` in the app (Paging 3 was previously Room-only),
so it adds the paging deps to `:feature:chats:impl` (`androidx-paging-runtime` + a new
`androidx-paging-compose` catalog entry; `androidx-paging-testing` for tests).

- `JoinRequestPagingSource(convoId, repository) : PagingSource<String, JoinRequestUi>` — key = cursor.
  `load()` calls `repository.getJoinRequests(convoId, params.key)` →
  `LoadResult.Page(data = page.requests, prevKey = null, nextKey = page.cursor)`; a failure →
  `LoadResult.Error(e)` (the UI maps `loadState` errors to copy). `getRefreshKey` returns null
  (cursor sources restart from the head on refresh).

### ViewModel + contract

`GroupJoinRequestsViewModel` — `@HiltViewModel(assistedFactory = Factory::class)`, `@AssistedInject`
with `@Assisted route: GroupJoinRequests` + `ChatRepository`, extending
`MviViewModel<GroupJoinRequestsViewState, GroupJoinRequestsEvent, GroupJoinRequestsEffect>`. Uses the
**dual-flow** Paging-3-in-MVI shape: the `PagingData` stream is the one **separate public val** (the
sanctioned paging exception — `PagingData` is not a `UiState` field, analogous to the editor
`TextFieldState` exception); everything else stays in `UiState`/effects.

- **`GroupJoinRequestsViewState(inFlightDids: ImmutableSet<String> = persistentSetOf())`** — the only
  UiState field: the set of DIDs with an approve/reject in flight (drives each row's disabled/spinner
  state). Read in the screen via `collectAsStateWithLifecycle`.

- **Paging flow** — optimistic removal via a `removedDids` overlay (the canonical way to mutate an
  otherwise-immutable `PagingData`):
  ```kotlin
  private val removedDids = MutableStateFlow<Set<String>>(emptySet())
  val joinRequests: Flow<PagingData<JoinRequestUi>> =
      Pager(PagingConfig(pageSize = JOIN_REQUESTS_PAGE_SIZE)) {
          JoinRequestPagingSource(convoId, repository)
      }.flow
          .cachedIn(viewModelScope)                       // cache BEFORE the overlay
          .combine(removedDids) { data, removed -> data.filter { it.did !in removed } }
  ```
- Per-row in-flight is the `UiState.inFlightDids` field above (updated via `setState`).
- **Events:** `ApproveTapped(did)`, `RejectTapped(did)`. (Refresh/retry are driven by the UI's
  `LazyPagingItems.refresh()`/`retry()` on the `loadState`, not VM events; back is handled by the
  screen.)
- **Effects:** `ShowError(ChatError)`, `RosterChanged` (after a successful approve, so the screen can
  signal group-details — see below).
- **Approve/Reject (optimistic):** guard `if (did in uiState.value.inFlightDids) return`; `setState`
  to add `did` to `inFlightDids` AND add it to `removedDids` (the row disappears immediately); call
  `approveJoinRequest`/`rejectJoinRequest` in
  `viewModelScope.launch { try { … } finally { setState { copy(inFlightDids = inFlightDids - did) } } }`;
  on **success** keep it removed, and for **approve only** emit `RosterChanged`; on **failure** remove
  `did` from `removedDids` (the row re-appears) + `ShowError(it.toMemberMgmtError())`.
  `getJoinRequests`/the `PagingSource` errors surface through `loadState`, not these handlers.

### GroupDetails roster refresh (reuse the one-shot result API)

- The requests screen, on the `RosterChanged` effect, calls
  `navState.setResult("group_roster_refresh:$convoId", true)` (the screen owns the nav-state, not the
  VM — same boundary as Slice B's add flow).
- `GroupDetailsScreen` gains a **second** one-shot consumer (alongside the existing
  `group_members_added` one): `snapshotFlow { navState.peekResult("group_roster_refresh:$convoId") }`
  → on a non-null value, `consumeResult` + fire `GroupDetailsEvent.Refresh`. **No snackbar** —
  returning to a refreshed roster is the feedback. Reuses the existing `MainShellNavState`
  `setResult`/`peekResult`/`consumeResult` API from Slice B.

### UI

- **Stateful `GroupJoinRequestsScreen`** — owns the VM; `val lazyItems = viewModel.joinRequests.collectAsLazyPagingItems()`;
  `val inFlight by viewModel.inFlightDids.collectAsStateWithLifecycle()`. Effect collector (child-coroutine
  snackbar + `rememberUpdatedState`): `ShowError` → snackbar; `RosterChanged` → `onRosterChanged()`
  (the nav module wires this to `navState.setResult("group_roster_refresh:$convoId", true)`). Back is
  the TopAppBar close → `onBack`.
- **Stateless `GroupJoinRequestsScreenContent(lazyItems, inFlight, onApprove, onReject, onClose, …)`** —
  `Scaffold(containerColor = surface)` + TopAppBar (close + title). Body branches on Paging
  `loadState`, NOT a sealed status sum:
  - `loadState.refresh is LoadState.Loading` → a centered **`NubecitaWavyProgressIndicator`** (the
    brand standalone loader; the `check_progress_indicators.sh` guard from #563 forbids a raw
    `CircularProgressIndicator` for standalone "content loading" spinners).
  - `loadState.refresh is LoadState.Error` → retry body (`lazyItems.retry()`).
  - `loadState.refresh is NotLoading && lazyItems.itemCount == 0` → **`EmptyStateContent`** (a dedicated
    component: a subdued M3 icon — e.g. `NubecitaIconName.Group`/inbox — + a friendly "No pending
    requests" message; not a blank screen).
  - otherwise a `LazyColumn` of rows + an append footer (`loadState.append` Loading → a row spinner;
    Error → a retry row).
- **Adaptive width:** the list container is constrained to `Modifier.widthIn(max = 600.dp)` (centered),
  so on Medium/Expanded (the dialog presentation) rows don't stretch uncomfortably wide — same
  reading-width treatment as the C1 create screen.
- `items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.did }, contentType = lazyItems.itemContentType { "request" })`,
  reading each via `lazyItems[index]`.
- **`ui/JoinRequestRow.kt`:** `NubecitaAvatar` + displayName/handle + a "requested {relative}"
  supporting line (`rememberChatRelativeTimeText(requestedAt)`); trailing actions use a clear M3
  hierarchy — **Approve = `FilledTonalButton`** (primary positive action), **Reject = `TextButton`**
  (not an `OutlinedButton`, to cut vertical-list visual noise). While the row's DID is in-flight, both
  are `enabled = false` and Approve hosts a small spinner in place of its label (explicit size so the
  button height doesn't jump). This is the **sanctioned in-button micro-spinner** exception in #563's
  guard, so it stays a raw `CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)` with
  the opt-out marker on the line above:
  `// nubecita-allow-raw-progress: in-button micro-spinner`. Min row height ≥ 48dp.

> **Brand-loader alignment (PR #563).** `NubecitaWavyProgressIndicator` is already on `main`; #563
> adds the `check_progress_indicators.sh` pre-commit guard + migrates the *remaining* raw spinners.
> C2 aligns up-front: standalone loaders use the wrapper, the in-button spinner carries the opt-out
> marker. Note #563 currently **conflicts** (opened before C1 merged) and does **not** migrate
> `NewGroupScreen`'s standalone "Searching" spinner (C1 landed after #563 was cut) — that's a #563
> rebase/gap to handle separately, out of scope for C2.

## Files

**New**
- `feature/chats/impl/.../GroupJoinRequestsContract.kt`, `GroupJoinRequestsViewModel.kt`, `GroupJoinRequestsScreen.kt`
- `feature/chats/impl/.../data/JoinRequestPagingSource.kt`, `data/JoinRequestMapper.kt`
- `feature/chats/impl/.../ui/JoinRequestRow.kt`
- `feature/chats/impl/.../ui/EmptyStateContent.kt` (subdued M3 icon + message; reusable empty body)

**Modified**
- `gradle/libs.versions.toml` — add `androidx-paging-compose` (version ref `paging`); `feature/chats/impl/build.gradle.kts` — add `androidx-paging-runtime` + `androidx-paging-compose` (+ `androidx-paging-testing` for tests)
- `feature/chats/api/.../Chats.kt` — `GroupJoinRequests` NavKey
- `feature/chats/impl/.../data/ChatRepository.kt` (+ `DefaultChatRepository.kt`) — `getJoinRequests`/`approveJoinRequest`/`rejectJoinRequest` + `JoinRequestPage`; + 5 fakes
- `feature/chats/impl/.../GroupDetailsContract.kt` — event `JoinRequestsTapped`
- `feature/chats/impl/.../GroupDetailsScreen.kt` — owner-only "Join requests" row; the `group_roster_refresh` consumer
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — `entry<GroupJoinRequests>` + the `JoinRequestsTapped`→nav and the requests screen's `onRosterChanged` → `setResult`
- strings `values/`, `values-b+es+419/`, `values-pt-rBR/`

## Testing

- **Unit:** `JoinRequestPagingSource` via `androidx.paging:paging-testing` (`TestPager`: first
  `LoadResult.Page` has the right data + `nextKey`; an error page → `LoadResult.Error`). The paged
  flow via `asSnapshot { }` (asserts the first page's items, and that a `removedDids` entry filters a
  row out). `GroupJoinRequestsViewModel` — approve → `did` enters `removedDids` (row filtered from
  `asSnapshot`) + `approveJoinRequestCalls` records `(convoId, did)` + `RosterChanged` emitted; reject
  → removed, NO `RosterChanged`; failure → `did` leaves `removedDids` (row reappears) + `ShowError`;
  per-DID in-flight guard dedupe via `inFlightDids` (gated fake). `JoinRequestMapper` mapping.
  `GroupDetailsViewModel` — `JoinRequestsTapped` → `NavigateTo(GroupJoinRequests)`.
- **Screenshot:** `GroupJoinRequestsScreenContent` driven by fixed `PagingData` (`PagingData.from(list)`
  collected via a `MutableStateFlow` → `collectAsLazyPagingItems`) — list with a few requests, an
  in-flight row, the empty state (`PagingData.empty()` + NotLoading), and a refresh-error body;
  group-details owner view showing the "Join requests" row.
- **Gate:** `spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug
  :feature:chats:impl:testProductionDebugUnitTest :feature:profile:impl:testProductionDebugUnitTest
  :app:assembleDebug`, screenshot validate (+ `update-baselines` label), compose-expert review (UI added).

## Conventions

MVI flat state + sealed status sum; `ImmutableList`; `@Immutable` models; surface tokens
(`Scaffold(containerColor = surface)`, rows on the canvas); no `!!`; PII never logged remotely;
optimistic mutate + per-key in-flight guard + rollback; reducers read current state (never a captured
list); lowercase-leading Conventional Commits referencing `nubecita-hwix.7`.
