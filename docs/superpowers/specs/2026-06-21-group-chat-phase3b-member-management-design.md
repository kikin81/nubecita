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
  set `isSubmitting`, call `addMembers(convoId, selectedDids)` → success: `NavigateBack` (no
  cross-screen success snackbar — that would require bus/result plumbing; the pop + group-details'
  resume-refresh is the feedback); failure: clear `isSubmitting`, `ShowError` and stay.

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
- gate on `Loaded`; per-DID in-flight guard (`inFlightRemovals`); look up the target;
- **optimistically** drop the member from the roster and decrement `memberCount`;
- `removeMembers(convoId, listOf(did))` in `viewModelScope.launch { try { … } finally { guard -= did } }`;
- on failure: re-insert the member at its prior position, restore the count, `ShowError`.

## Cross-screen refresh (no event bus)

After a successful add, group-details should reflect the change. Per the no-event-bus preference,
group-details **re-loads its roster on resume** (a lifecycle-`RESUMED` `Refresh`, deduped against the
initial-composition load) rather than via a shared flow/bus or nav result. This is best-effort:
added members are pending until they accept, so they may not appear as roster members immediately —
the authoritative success feedback is the add screen's snackbar.

## Files

**New**
- `feature/chats/impl/.../AddGroupMembersContract.kt`, `AddGroupMembersViewModel.kt`, `AddGroupMembersScreen.kt`
- (optional) `feature/chats/impl/.../ui/RecipientChipsRow.kt` if the `InputChip` `FlowRow` warrants extraction

**Modified**
- `feature/chats/api/.../Chats.kt` — `AddGroupMembers` NavKey
- `feature/chats/impl/.../data/ChatRepository.kt` (+ `DefaultChatRepository.kt`) — `addMembers` / `removeMembers`
- 5× `ChatRepository` fakes
- `feature/chats/impl/.../GroupDetailsContract.kt` — `viewerRole`; events `AddMembersTapped`, `RemoveMember(did)`; effect to navigate to `AddGroupMembers`
- `feature/chats/impl/.../GroupDetailsViewModel.kt` — derive `viewerRole`; add/remove handlers; resume-refresh
- `feature/chats/impl/.../GroupDetailsScreen.kt` — owner-only "Add members" button; remove confirm dialog; pass `viewerRole`
- `feature/chats/impl/.../ui/GroupMemberRow.kt` — owner-only ⋮ → "Remove from group"
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — `entry<AddGroupMembers>`
- `feature/chats/impl/.../data/GroupMemberMapper.kt` — no change expected (role/viewer already mapped)
- strings `values/`, `values-b+es+419/`, `values-pt-rBR/`

## Testing

- **Unit:** `GroupDetailsViewModel` — `viewerRole` derivation, add-nav effect (owner only), optimistic
  remove + rollback + in-flight guard, count decrement/restore. `AddGroupMembersViewModel` —
  recent/search pipeline, exclusion of existing members, select/deselect, cap enforcement, add
  success → NavigateBack, add failure → ShowError. Repository fakes drive results.
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
