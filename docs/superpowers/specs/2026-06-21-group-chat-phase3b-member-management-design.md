# Group chat Phase 3b — member management (add + remove)

**Epic:** `nubecita-hwix` (Group chat support). **Slice B** of Phase 3, following Slice A
(group details + roster, #561, merged). **bd:** `nubecita-hwix.5` (to be created).

## Purpose

Let a group **owner** manage the group's membership from the existing group-details screen:
**add** people (via a recipient-chip picker) and **remove** existing members (with a
confirmation). Role/admin changes are explicitly out of scope — no endpoint exists for them
in atproto 9.4.0.

## SDK surface (atproto 9.4.0 — verified present in the published `models-jvm-9.4.0.jar`)

`io.github.kikin81.atproto.chat.bsky.group.GroupService`, constructed `GroupService(xrpcClient)`
(same pattern as `ConvoService` in `DefaultChatRepository`). No SDK bump or lexicon overlay needed.

- `addMembers(AddMembersRequest(convoId: String, members: List<Did>))` → `AddMembersResponse(convo)`.
  Adds members with **pending / `request`** status — the invitee must accept before they appear as
  an accepted roster member. Server-enforced errors include `InsufficientRole`, `MemberLimitReached`,
  `NotFollowedBySender`, `GroupInvitesDisabled`, `BlockedActor`, `AccountSuspended`, `RecipientNotFound`,
  `ConvoLocked`, `InvalidConvo`.
- `removeMembers(RemoveMembersRequest(convoId: String, members: List<Did>))` → `RemoveMembersResponse(convo)`.
  **Permanent** removal (not a status change). Server-enforced errors: `InsufficientRole`, `InvalidConvo`.
- **No role-change operation exists** (`updateMember` / `setRole` / etc. are absent from the lexicon).
  Member role stays read-only (`owner` / `standard`).

## Non-goals

- Role / admin changes (make-admin, demote) — no endpoint. Deferred until/if the lexicon adds one.
- Group creation, join requests, invite links (`createGroup`, `listJoinRequests`) — **Slice C**.
- Changing the existing **Leave** behavior (self-exit). Owner-leave/ownership-transfer is untouched.

## Architecture

### Repository

Extend the existing **`ChatRepository`** (member ops are convo-scoped and chats-internal; no
cross-feature reuse, so no `:core` extraction like `FollowRepository`):

```kotlin
/** Add [dids] to group [convoId]. Added with pending status; invitees must accept. */
suspend fun addMembers(convoId: String, dids: List<String>): Result<Unit>

/** Permanently remove [dids] from group [convoId]. */
suspend fun removeMembers(convoId: String, dids: List<String>): Result<Unit>
```

`DefaultChatRepository` wraps `GroupService(xrpcClientProvider.authenticated())`, maps wire DIDs via
`Did(...)`, runs on the IO dispatcher, rethrows `CancellationException`, logs only `javaClass.name`
(no DIDs/handles), and routes failures through `toChatError()`. Both return `Result<Unit>` — the VM
owns the optimistic UI; the returned `convo` is not needed by the UI.

All **five** `ChatRepository` fakes implement the two methods with settable results + call captures:
`src/test/…/FakeChatRepository.kt`, `src/androidTest/…/FakeChatRepository.kt`,
`src/bench/…/data/BenchFakeChatRepository.kt`, and any others discovered at implementation time
(grep `: ChatRepository`).

### Error taxonomy

Map the GroupService errors to user-facing copy where it changes the user's next action; the rest
fall through to a generic message. Extend `ChatError` (or the `toChatError` mapping) with:

| Wire error | Copy intent |
|---|---|
| `MemberLimitReached` | "This group is full." (50 max) |
| `NotFollowedBySender` | "You can only add people who follow you." |
| `InsufficientRole` | "You don't have permission to do that." |
| everything else | generic "Something went wrong. Try again." |

These surface as transient snackbars (add screen / group-details), never sticky banners.

## Feature 1 — Add members (new adaptive sub-route)

### Navigation

- New NavKey in `:feature:chats:api/Chats.kt`: `@Serializable data class AddGroupMembers(val convoId: String) : NavKey`.
- `entry<AddGroupMembers>(metadata = adaptiveDialog())` in `ChatsNavigationModule` (full-screen on
  Compact, centered Dialog on Medium/Expanded), assisted-injected VM (mirrors `Chat` / `GroupDetails`).
- Reached from group-details: the owner-only **"Add members"** action → `GroupDetailsEffect`
  navigates to `AddGroupMembers(convoId)`.

### `AddGroupMembersViewModel` + contract

Forks the New-Chat picker pipeline. Assisted-injected with the `AddGroupMembers` route; depends on
`ChatRepository` (for `getConvoMembers` to know exclusions/headroom + `addMembers`) and
`:core:actors` `ActorRepository` (`searchTypeahead`, `recentActors`).

- **Query:** a public `TextFieldState` (the sanctioned editor MVI exception), observed via
  `snapshotFlow`. Blank query → `recentActors`; typed → debounced (~250 ms) `searchTypeahead`.
- **Roster load on entry:** call `getConvoMembers(convoId)` once to obtain the set of existing
  member DIDs (for exclusion) and the current member count (for the cap).
- **Selection:** an `ImmutableList`/`Set` of selected `ActorUi` (did, handle, displayName, avatarUrl)
  held in `UiState`. Results exclude existing members; tapping a result toggles selection.
- **Cap:** selection disabled once `currentMemberCount + selected.size >= GROUP_MAX_MEMBERS` (50);
  show a notice at the cap.
- **State (`AddGroupMembersViewState`):** `selected: ImmutableList<RecipientUi>`,
  `atCapacity: Boolean`, `isSubmitting: Boolean`, and a `status: AddGroupMembersLoadStatus`
  sealed sum for the results pane (`Recent / Searching / Results / NoResults / Error`) mirroring
  New-Chat. Loading-the-roster failure → an initial error / retry state.
- **Events:** `QueryRetry`, `RecipientToggled(did)`, `RecipientRemoved(did)` (the chip ✕),
  `AddTapped`, `BackPressed`, `RetryClicked`.
- **Effects:** `ShowError(ChatError)`, `NavigateBack`. On `AddTapped`:
  set `isSubmitting`, call `addMembers(convoId, selectedDids)` → success: write a one-shot nav
  **result** (`MembersAddedResult(count)`) for the group-details consumer, then `NavigateBack`.
  The screen does **not** show its own success snackbar — it's about to be popped, so the snackbar
  would never render (see §Cross-screen result). Failure: clear `isSubmitting`, `ShowError`, stay.

### `AddGroupMembersScreen` (UI)

- `Scaffold(containerColor = surface)` + TopAppBar: close icon + title ("Add members") + a trailing
  **"Add"** text button, enabled when `selected.isNotEmpty() && !isSubmitting` (the cap disables
  further *selection*, not submission of an already-valid set).
- Below the bar: a search `TextField` bound to `vm.queryFieldState`.
- A **`FlowRow` of selected recipients as M3 `InputChip`s** — leading `NubecitaAvatar`, label =
  displayName ?: handle, trailing ✕ (`onClick` → `RecipientRemoved(did)`). Hidden when empty.
- A results `LazyColumn` reusing `RecipientRow` for `Recent`/`Results`, with `NoResults` / `Error`
  / `Searching` bodies mirroring New-Chat. Selected rows render a selected affordance; at-cap
  unselected rows are disabled.
- Stateful screen collects effects (snackbar for `ShowError`; `NavigateBack` → pop).

## Feature 2 — Remove member (on the existing roster)

### Owner-gating

Add `viewerRole: GroupRole? = null` to `GroupDetailsViewState`, derived in
`GroupDetailsViewModel.loadMembers()` from the `isViewer` member's `role`. Drives both:
- the action-row **"Add members"** button (shown only when `viewerRole == GroupRole.Owner`), and
- the per-row remove ⋮ (gated `viewerRole == Owner && member.role == Member && !member.isViewer`).

### Row affordance + confirm

`GroupMemberRow` gains an **owner-only trailing ⋮ overflow** (`MoreVert`) → `DropdownMenu` with a
single **"Remove from group"** item (the menu is future-proof for role changes later). The Follow
button stays inline as today. Selecting it raises a confirmation `AlertDialog`
("Remove @handle from the group?") whose state lives in the screen (Compose-local), so only a
`GroupDetailsEvent.RemoveMember(did)` reaches the VM on confirm.

### Optimistic removal

`GroupDetailsViewModel.onRemoveMember(did)` mirrors the follow-toggle shape:
- gate on `Loaded`; per-DID in-flight guard (`inFlightRemovals`); look up the target + its index;
- **optimistically** drop the member from the roster;
- `removeMembers(convoId, listOf(did))` in `viewModelScope.launch { try { … } finally { guard -= did } }`;
- on failure: re-insert the member at its prior index, `ShowError`.

**`memberCount` is always derived from `members.size`, never decremented as a standalone int.**
The `Loaded` reducer computes `memberCount = members.size` after every mutation (removal, rollback),
so rapid concurrent removes can't race on a stale counter — each `setState` recomputes the count
from the (atomic) list it just produced. (`memberCount` is retained as an explicit field only so the
"N / 50" header can render without re-reading the list; it is a pure projection of `members.size`.)

## Cross-screen result (success feedback + deliberate refresh)

The add screen must feed two things back to group-details on success: the **"Invitations sent"
snackbar** (shown on a host that won't be torn down) and a **single, deliberate roster refresh**
(not a network hit on every `ON_RESUME`). Both ride one mechanism.

### Nav3 one-shot result API on `MainShellNavState` (`:core:common:navigation`)

Nav3 has no `SavedStateHandle` result channel (that's a Nav2 idiom), and `MainShellNavState` has
none today. Add a minimal, general, **consume-once** result store — the Nav3 analog, scoped to the
shell's nav state (NOT a global event bus; it's a keyed value a returning consumer reads exactly
once):

```kotlin
// MainShellNavState
private val results = mutableStateMapOf<String, Any?>()       // snapshot-observed

/** Stash a one-shot result for a returning consumer, keyed by [key]. Overwrites any prior value. */
fun setResult(key: String, value: Any?) { results[key] = value }

/** Snapshot-observed read WITHOUT clearing — lets a consumer recompose when a result appears. */
fun peekResult(key: String): Any? = results[key]

/** Read and clear a one-shot result (returns null if none). */
fun consumeResult(key: String): Any? = results.remove(key)
```

Unit-tested in `MainShellNavStateTest` (set→peek→consume→peek-null; overwrite; independent keys).

### Wiring

- **Add success:** `navState.setResult("group_members_added:$convoId", count)` then `removeLast()`.
  (`AddGroupMembersScreen` calls `setResult` via the same nav-state it already holds for `onBack`;
  the VM emits a plain `NavigateBack` and the screen does the `setResult` + pop, keeping the VM free
  of the nav-state holder per the MVI boundary.)
- **Group-details consumes it reactively** — works for BOTH form factors: on Compact, group-details
  re-enters composition when the full-screen add route pops; on Medium/Expanded it stays composed
  under the dialog. A snapshot read makes both cases fire:
  ```kotlin
  val pending = navState.peekResult(key) as? Int       // recomposes when set
  LaunchedEffect(pending) {
      if (pending != null) {
          navState.consumeResult(key)                   // one-shot
          snackbarHostState.showSnackbar(invitesSentMsg) // correct host — group-details' Scaffold
          onRefresh()                                    // → GroupDetailsEvent.Refresh → loadMembers()
      }
  }
  ```
  The refresh is **deliberate** (only on a real add result), eliminating the `ON_RESUME`
  over-fetch.

### Ghost-invites note (verified)

`chat.bsky.actor.GroupConvoMember` exposes only `role` + `addedBy` — **no membership/status field**
— so newly-added members (created with `request` status) cannot be rendered with a distinct
"Pending" treatment, and typically won't appear in `getConvoMembers` until they accept. The
"Invitations sent" snackbar is therefore the **required** signal that the action succeeded, guarding
against an owner re-adding the same people (and hitting rate limits / `InsufficientRole`). The
deliberate post-add refresh stays (cheap, keeps the roster authoritative if the server does surface
any change), but is not the primary feedback.

## Files

**New**
- `feature/chats/impl/.../AddGroupMembersContract.kt`, `AddGroupMembersViewModel.kt`, `AddGroupMembersScreen.kt`
- (optional) `feature/chats/impl/.../ui/RecipientChipsRow.kt` if the `InputChip` `FlowRow` warrants extraction

**Modified**
- `core/common/.../navigation/MainShellNavState.kt` (+ `MainShellNavStateTest.kt`) — one-shot
  `setResult` / `peekResult` / `consumeResult` result API
- `feature/chats/api/.../Chats.kt` — `AddGroupMembers` NavKey
- `feature/chats/impl/.../data/ChatRepository.kt` (+ `DefaultChatRepository.kt`) — `addMembers` / `removeMembers`
- 5× `ChatRepository` fakes
- `feature/chats/impl/.../GroupDetailsContract.kt` — `viewerRole`; events `AddMembersTapped`, `RemoveMember(did)`, `Refresh` (exists); effect to navigate to `AddGroupMembers`
- `feature/chats/impl/.../GroupDetailsViewModel.kt` — derive `viewerRole`; add/remove handlers; `memberCount` from `members.size`
- `feature/chats/impl/.../GroupDetailsScreen.kt` — owner-only "Add members" button; remove confirm dialog; pass `viewerRole`; consume the add result (snackbar + refresh)
- `feature/chats/impl/.../ui/GroupMemberRow.kt` — owner-only ⋮ → "Remove from group"
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — `entry<AddGroupMembers>`
- `feature/chats/impl/.../data/GroupMemberMapper.kt` — no change expected (role/viewer already mapped)
- strings `values/`, `values-b+es+419/`, `values-pt-rBR/`

## Testing

- **Unit:** `MainShellNavState` — `setResult`/`peekResult`/`consumeResult` one-shot semantics
  (set→peek→consume→peek-null, overwrite, independent keys). `GroupDetailsViewModel` — `viewerRole`
  derivation, add-nav effect (owner only), optimistic remove + rollback + in-flight guard,
  `memberCount == members.size` after remove + rollback (incl. interleaved removes).
  `AddGroupMembersViewModel` — recent/search pipeline, exclusion of existing members, select/deselect,
  cap enforcement, add success → NavigateBack, add failure → ShowError. Repository fakes drive results.
- **Screenshot:** AddGroupMembersScreen (empty, with selected chips, results, at-cap); group-details
  **owner** view (Add button + a removable row's ⋮) and the remove confirmation dialog.
- **Gate:** `spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug
  :feature:chats:impl:testProductionDebugUnitTest :app:assembleDebug`, screenshot validate (+
  `update-baselines` label), and the compose-expert review (UI added).

## Conventions

MVI flat state + sealed load-status sums; `ImmutableList`; `@Immutable` models; surface tokens
(`Scaffold(containerColor = surface)`, member rows on the canvas, `InputChip`/dialog default
elevation); no `!!`; PII never logged remotely; optimistic mutate + in-flight guard + rollback;
lowercase-leading Conventional Commits referencing the bd id.
