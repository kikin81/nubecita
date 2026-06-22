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
  on the sub-screen; avoids an extra `listJoinRequests` call on every group-details open).
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

### ViewModel + contract

`GroupJoinRequestsViewModel` — `@HiltViewModel(assistedFactory = Factory::class)`, `@AssistedInject`
with `@Assisted route: GroupJoinRequests` + `ChatRepository`. Auto-loads on construction.

- **State (`GroupJoinRequestsViewState`):** one sealed `status: GroupJoinRequestsLoadStatus`:
  - `Loading`
  - `Loaded(requests: ImmutableList<JoinRequestUi>)` (empty list → the screen renders an empty body)
  - `InitialError(error: ChatError)`
- **Events:** `Refresh`, `RetryClicked`, `BackPressed`, `ApproveTapped(did)`, `RejectTapped(did)`.
- **Effects:** `ShowError(ChatError)`, `NavigateBack`, `RosterChanged` (emitted after a successful
  approve so the screen can signal group-details — see below).
- **Load:** `getJoinRequests(convoId)` with a defensive cursor loop (accumulate pages) →
  `Loaded(requests)`; failure → `InitialError(toMemberMgmtError())`.
- **Approve/Reject (optimistic):** per-DID in-flight guard (`inFlightRequests`); on tap,
  **optimistically remove** the request from the `Loaded` list, then call
  `approveJoinRequest`/`rejectJoinRequest` in `viewModelScope.launch { try { … } finally { guard -= did } }`;
  on failure re-insert at the prior index + `ShowError`. On a **successful approve only**, emit
  `RosterChanged` (reject doesn't change the roster). Mirrors the Slice B remove-member shape.

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

- `GroupJoinRequestsScreen` (stateful: VM + effect collector with child-coroutine snackbar +
  `rememberUpdatedState`; `BackPressed`/`NavigateBack` → `onBack`; `RosterChanged` →
  `onRosterChanged()` which the nav module wires to `setResult`) + stateless
  `GroupJoinRequestsScreenContent` (`Scaffold(containerColor = surface)` + TopAppBar with close +
  title; `when (status)` → Loading spinner / `Loaded` `LazyColumn(weight)` of `JoinRequestRow` (or an
  empty body when `requests.isEmpty()`) / `InitialError` retry body).
- `ui/JoinRequestRow.kt`: `NubecitaAvatar` + displayName/handle + a "requested {relative}" supporting
  line (`rememberChatRelativeTimeText(requestedAt)`) + an **Approve** filled `Button` and a **Reject**
  `OutlinedButton`; while that DID is in-flight both show disabled and Approve hosts a small
  `CircularProgressIndicator` (mirror the follow-affordance in-flight treatment).
  `items(key = { it.did }, contentType)`.

## Files

**New**
- `feature/chats/impl/.../GroupJoinRequestsContract.kt`, `GroupJoinRequestsViewModel.kt`, `GroupJoinRequestsScreen.kt`
- `feature/chats/impl/.../data/JoinRequestMapper.kt`
- `feature/chats/impl/.../ui/JoinRequestRow.kt`

**Modified**
- `feature/chats/api/.../Chats.kt` — `GroupJoinRequests` NavKey
- `feature/chats/impl/.../data/ChatRepository.kt` (+ `DefaultChatRepository.kt`) — `getJoinRequests`/`approveJoinRequest`/`rejectJoinRequest` + `JoinRequestPage`; + 5 fakes
- `feature/chats/impl/.../GroupDetailsContract.kt` — event `JoinRequestsTapped`
- `feature/chats/impl/.../GroupDetailsScreen.kt` — owner-only "Join requests" row; the `group_roster_refresh` consumer
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — `entry<GroupJoinRequests>` + the `JoinRequestsTapped`→nav and the requests screen's `onRosterChanged` → `setResult`
- strings `values/`, `values-b+es+419/`, `values-pt-rBR/`

## Testing

- **Unit:** `GroupJoinRequestsViewModel` — load (+ cursor loop), approve → optimistic remove +
  `approveJoinRequestCalls` records `(convoId, did)` + `RosterChanged` emitted; reject → remove, NO
  `RosterChanged`; approve/reject failure → re-insert at prior index + `ShowError`; per-DID in-flight
  guard dedupe (gated fake); empty `Loaded` and `InitialError`. `ChatRepository` fakes drive results.
  `JoinRequestMapper` mapping. `GroupDetailsViewModel` — `JoinRequestsTapped` → `NavigateTo(GroupJoinRequests)`.
- **Screenshot:** `GroupJoinRequestsScreenContent` — list with a few requests, an in-flight row,
  empty, and error; group-details owner view showing the "Join requests" row.
- **Gate:** `spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug
  :feature:chats:impl:testProductionDebugUnitTest :feature:profile:impl:testProductionDebugUnitTest
  :app:assembleDebug`, screenshot validate (+ `update-baselines` label), compose-expert review (UI added).

## Conventions

MVI flat state + sealed status sum; `ImmutableList`; `@Immutable` models; surface tokens
(`Scaffold(containerColor = surface)`, rows on the canvas); no `!!`; PII never logged remotely;
optimistic mutate + per-key in-flight guard + rollback; reducers read current state (never a captured
list); lowercase-leading Conventional Commits referencing `nubecita-hwix.7`.
