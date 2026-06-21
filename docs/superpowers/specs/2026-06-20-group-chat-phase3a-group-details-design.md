# Group chat — Phase 3a: group details + member roster (read)

**bd:** nubecita-hwix.4 (Phase 3) under epic nubecita-hwix (Group chat support)
**Date:** 2026-06-20
**Status:** Pending review

## Context

Phase 1 (view/send) and Phase 2 (reactions) shipped. The official Bluesky app
has a **Group chat settings** screen (facepile, name, "Members 28/50", a full
member list with role + "Added by…" + Follow, and Mute/Invite/Report/Leave).
Nubecita has no group-details surface, and the thread header shows the wrong
member count (`getConvo` returns only a partial preview roster, ~7).

The full member roster is now reachable via `chat.bsky.convo.getConvoMembers`,
added to the SDK in atproto-kotlin PR #144 (vendored overlay) and consumed here
via a local `9.3.2-SNAPSHOT` build until that PR releases.

## Scope (this slice = 3a)

The user wants **full group management** (read details, roster, Follow,
Leave/Mute/Report, **and** add/remove members, role/admin changes, join-request
approval, invite links). To stay reviewable, that's delivered as sequential
slices, all under nubecita-hwix.4:

- **3a (this spec):** the entry point + the read-only details screen + the full
  member roster + the actions we already have (Leave/Mute/Report) + per-member
  Follow + the corrected member count.
- **3b (later brief):** member management writes — add members, remove member,
  change role/admin, edit group name (`addMembers`/`removeMembers`/`editGroup`).
- **3c (later brief):** access control — join requests
  (`listJoinRequests`/approve/reject) and invite link
  (`createJoinLink`/enable/disable/edit + share).

## SDK shapes (verified against the 9.3.2-SNAPSHOT build)

- `ConvoService.getConvoMembers(GetConvoMembersRequest(convoId, limit? = 50, cursor?))`
  → `GetConvoMembersResponse(members: List<chat.bsky.actor.ProfileViewBasic>, cursor?)`.
  Pagination helpers also generated (`convoMembersFlow` / `convoMembersPageFlow`).
- `chat.bsky.actor.ProfileViewBasic`: `did`, `handle`, `displayName?`, `avatar?`,
  `viewer: app.bsky.actor.ViewerState`, and `kind: ProfileViewBasicKindUnion`.
- `kind` is a union; for a group member it is `GroupConvoMember(role: String,
  addedBy: ProfileViewBasic)` — `role` ∈ the lexicon's member roles (owner /
  standard; constants in `MemberRoleKt`). `addedBy` is the inviter's profile.
- `viewer: ViewerState` carries `following` (an at-uri when the viewer follows)
  — the follow **state** for the per-member Follow button.
- Authoritative count: there is **no count field**; the accurate member count is
  the size of the fully-paginated roster. The max is a client constant
  (`GROUP_MAX_MEMBERS = 50`, the current Bluesky group cap) → "N/50".

## Decisions

1. **Entry point = an overflow (⋮) menu in the thread `TopAppBar`.** Shown only
   when `state.header is ChatHeader.Group`. One item in 3a — "Group details" —
   emitting a `ChatEffect.NavigateTo(GroupDetails(convoId))` (the established
   effect→`LocalMainShellNavState.add` pattern). (3b/3c add more items.)
2. **`GroupDetails(convoId)` is a `@MainShell` sub-route tagged `adaptiveDialog()`**
   — full-screen on Compact, centered dialog on Medium/Expanded (the sanctioned
   scene-strategy pattern; mirrors `EditProfile`). NavKey lives in
   `:feature:chats:api`.
3. **Dedicated `GroupDetailsViewModel` (MVI) + `GroupDetailsScreen`.** It does NOT
   reuse `ChatViewModel`; it loads the convo header (name/facepile via the
   existing `getConvo`) + the paginated roster (`getConvoMembers`). Assisted-inject
   the `GroupDetails` NavKey (the `ChatViewModel` pattern).
4. **Accurate count from a single roster call.** Bluesky groups cap at
   `GROUP_MAX_MEMBERS = 50` and `getConvoMembers.limit` allows up to 100, so ONE
   `getConvoMembers(convoId, limit = 100)` call returns the **entire** roster for
   any group (`cursor` is null in practice). The accurate count is therefore just
   `members.size` from that one call — no multi-page exhaustion. (The VM still
   loops on a non-null `cursor` defensively, but it's a single page in practice.)
   This same single call powers both the details roster and the **thread-header
   count fix**: on opening a group thread, `ChatViewModel` fetches the roster once
   and renders "N/50" instead of the misleading partial `getConvo().members.size`.
   One extra foreground XRPC per group-thread open (user-initiated, alongside the
   existing getConvo/getMessages) — within the battery rule.
5. **Per-member Follow.** The member's `viewer.following` gives the initial state;
   the follow/unfollow **action** uses `app.bsky.graph` follow create/delete.
   There is no shared follow primitive today (follow logic lives in
   `:feature:profile:impl`). 3a extracts a minimal `FollowRepository`
   (`follow(did)` / `unfollow(followUri)`) into `:core:post-interactions` (its
   stated home for like/repost/follow primitives) and has `:feature:profile:impl`
   adopt it, so group-details and profile share one implementation. Optimistic
   toggle + rollback, mirroring the reaction pattern.
6. **Leave / Mute / Report reuse existing repo methods** (`leaveConvo`,
   `setMuted`, the moderation `Report` NavKey). Leave navigates back to the inbox
   on success.

## Models

```kotlin
// :feature:chats:api
@Serializable data class GroupDetails(val convoId: String) : NavKey

// :feature:chats:impl — GroupDetailsContract
@Immutable data class GroupMemberUi(
    val did: String,
    val handle: String,
    val displayName: String?,     // null → render handle
    val avatarUrl: String?,
    val role: GroupRole,          // Owner / Admin / Member (mapped from the lexicon role string)
    val addedByName: String?,     // addedBy.displayName ?: handle; null when absent
    val isViewer: Boolean,        // the signed-in user — no Follow button on self
    val followState: FollowState, // NotFollowing / Following / InFlight
    val followUri: String?,       // viewer.following at-uri, for unfollow
)
enum class GroupRole { Owner, Admin, Member }
enum class FollowState { NotFollowing, Following, InFlight }

@Immutable data class GroupDetailsViewState(
    val name: String = "",
    val members: ImmutableList<GroupMemberUi> = persistentListOf(),
    val memberCount: Int = 0,         // accurate (fully paginated)
    val maxMembers: Int = 50,
    val muted: Boolean = false,
    val status: GroupDetailsLoadStatus = GroupDetailsLoadStatus.Loading,
)
sealed interface GroupDetailsLoadStatus { Loading; data class Loaded(...) ; data class InitialError(error) }
```

## Data flow

- **Load:** VM resolves `convoId` → `getConvo(convoId)` for the name + muted +
  (Direct guard: if not a group, error/close). Then pages `getConvoMembers` to
  exhaustion, mapping each `ProfileViewBasic` → `GroupMemberUi`
  (role from `kind#groupConvoMember.role`; `addedByName` from `kind.addedBy`;
  `followState`/`followUri` from `viewer.following`; `isViewer` from did == viewer).
  `memberCount = members.size`. Errors route through a `ShowError` effect; initial
  failure → `InitialError`.
- **Follow toggle:** optimistic `InFlight` → `FollowRepository.follow/unfollow` →
  reconcile (`Following` + new uri / `NotFollowing`) or rollback + snackbar.
- **Mute / Leave:** call the repo; Leave → `NavigateBack` (pops to inbox).
- **Thread header count fix:** `getConvo` stays the header source for name +
  facepile. On opening a GROUP thread, `ChatViewModel` additionally calls
  `getConvoMembers(convoId, limit = 100)` once and sets the header's member count
  from `members.size` (the full roster — groups ≤ 50). The "N members" line then
  shows the accurate count (e.g. "28 members"); on the details screen it's "N/50".
  If that call fails, the header falls back to the partial facepile count (no hard
  error — the thread still works). One foreground call per group-thread open.

## Files (3a)

- `:feature:chats:api` — `GroupDetails` NavKey.
- `:core:post-interactions` — extract `FollowRepository` (+ Hilt binding); migrate `:feature:profile:impl` to it.
- `:feature:chats:impl`:
  - `data/ChatRepository`(+Default) — `getConvoMembers(convoId, cursor): Result<MemberPage>`; `MemberPage(members, cursor)`. Member mapping in a new `data/GroupMemberMapper.kt` (the only file touching `chat.bsky.actor` member-kind types). +5 fakes.
  - `GroupDetailsContract.kt`, `GroupDetailsViewModel.kt`, `GroupDetailsScreen(Content).kt`, `ui/GroupMemberRow.kt`.
  - `di/ChatsNavigationModule.kt` — `entry<GroupDetails>(adaptiveDialog())`.
  - `ChatScreenContent.kt` — overflow (⋮) menu (group-only) → `NavigateTo(GroupDetails)`; thread-header count tweak.
- Tests: member mapper (role/addedBy/viewer/self), VM (paginated load + count, follow toggle + rollback, leave→back), screenshots (details + roster, role badges, follow states).

## Error handling

Failures route through a `GroupDetailsEffect.ShowError` snackbar; `CancellationException`
rethrown in the repo (established pattern). A direct (non-group) convoId → friendly error.

## Non-goals (3a)

Member management writes (3b), join requests + invite link (3c), the "Created …"
date (not exposed by any chat lexicon we have).

## Dependency note

3a is built against the local atproto `9.3.2-SNAPSHOT` (mavenLocal). The Nubecita
PR for 3a **cannot merge until SDK PR #144 releases** a real version; the temp
`mavenLocal()` + SNAPSHOT pin is reverted to the released version before merge.
