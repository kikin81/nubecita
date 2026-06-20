# Group chat Phase 3a (group details + roster) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a group-details screen (reachable from a ⋮ menu in the chat thread) showing the group name, facepile, accurate `N/50` member count, the full member roster (role badge + "Added by…" + Follow), and Leave/Mute/Report — plus fix the thread-header member count.

**Architecture:** New `ChatRepository.getConvoMembers(convoId, cursor)` wrapping the SDK's new `chat.bsky.convo.getConvoMembers`; a single `limit=100` call returns the whole roster (groups cap at 50). A dedicated `GroupDetailsViewModel`/`GroupDetailsScreen` behind an `adaptiveDialog()` `GroupDetails(convoId)` sub-route. Follow is extracted into a shared `:core:post-interactions` `FollowRepository` (adopted by `:feature:profile:impl`). Optimistic follow toggle + rollback (the reaction pattern).

**Tech Stack:** Kotlin 2.3, Compose M3 Expressive, atproto-kotlin `9.3.2-SNAPSHOT` (local; `getConvoMembers`), Hilt assisted-inject, JUnit 5, AGP screenshot tests.

**Spec:** `docs/superpowers/specs/2026-06-20-group-chat-phase3a-group-details-design.md` · **bd:** nubecita-hwix.4 · **branch:** `feat/nubecita-hwix.4-group-details`

**Confirmed SDK shapes (9.3.2-SNAPSHOT):**
- `ConvoService(client).getConvoMembers(GetConvoMembersRequest(convoId, limit, cursor)).{ members: List<chat.bsky.actor.ProfileViewBasic>, cursor }`.
- `chat.bsky.actor.ProfileViewBasic`: `did`/`handle` (value classes → `.raw`), `displayName?`, `avatar?` (`.raw`), `viewer: app.bsky.actor.ViewerState` (`.following: AtUri?`), `kind: ProfileViewBasicKindUnion`.
- `kind` group case: `chat.bsky.actor.GroupConvoMember(addedBy: ProfileViewBasic, role: String)`; role constants in `MemberRoleKt` (owner / standard). (Confirm exact strings via javap during Task 3.)

**Dependency caveat:** built against mavenLocal `9.3.2-SNAPSHOT`; the Nubecita PR can't merge until SDK #144 releases — swap the temp dep for the released version before merge.

---

## File Structure
- `feature/chats/api/.../Chats.kt` — `GroupDetails(convoId)` NavKey.
- `core/post-interactions/.../FollowRepository.kt` (+ Hilt module) — extracted `follow(did)`/`unfollow(followUri)`; `feature/profile/impl` migrates to it.
- `feature/chats/impl/.../data/ChatRepository.kt`(+Default, +5 fakes) — `getConvoMembers(convoId, cursor): Result<MemberPage>`.
- `feature/chats/impl/.../data/GroupMemberMapper.kt` — `ProfileViewBasic → GroupMemberUi` (the only file touching `chat.bsky.actor` member-kind types).
- `feature/chats/impl/.../GroupDetailsContract.kt`, `GroupDetailsViewModel.kt`, `GroupDetailsScreen.kt`, `GroupDetailsScreenContent.kt`, `ui/GroupMemberRow.kt`.
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — `entry<GroupDetails>(adaptiveDialog())`.
- `feature/chats/impl/.../ChatScreenContent.kt` + `ChatViewModel.kt`/`ChatContract.kt` — ⋮ overflow menu (group-only) → `NavigateTo(GroupDetails)`; accurate header count.
- Tests + screenshots.

---

## Task 1: `GroupDetails` NavKey
**Files:** Modify `feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt`; Test `feature/chats/impl/.../GroupDetailsNavKeyTest.kt`.
- [ ] **Step 1: failing test** — `GroupDetails(convoId = "c1").convoId == "c1"` (and it's a `NavKey`). Run `:feature:chats:impl:compileProductionDebugUnitTestKotlin` → FAIL (unresolved).
- [ ] **Step 2: implement** in `Chats.kt`:
```kotlin
/** Group details / settings for a group conversation. A @MainShell sub-route tagged adaptiveDialog(). */
@Serializable
data class GroupDetails(val convoId: String) : NavKey
```
- [ ] **Step 3: run → PASS.** `:feature:chats:impl:testProductionDebugUnitTest --tests "*GroupDetailsNavKeyTest*"`.
- [ ] **Step 4: commit.** `feat(chats): GroupDetails NavKey`. Refs: nubecita-hwix.4.

---

## Task 2: Extract `FollowRepository` to `:core:post-interactions`
**Files:** Create `core/post-interactions/.../FollowRepository.kt` + Hilt module; migrate `feature/profile/impl` follow calls; Tests.

First READ how `:feature:profile:impl` currently follows (grep `follow` in `feature/profile/impl/src/main` — likely a repository calling `app.bsky.graph` createRecord(follow)/deleteRecord). Mirror that exact XRPC.

- [ ] **Step 1: interface + impl** in `:core:post-interactions`:
```kotlin
interface FollowRepository {
    /** Creates a follow of [did]; returns the new follow record's at-uri. */
    suspend fun follow(did: String): Result<String>
    /** Deletes the follow at [followUri] (the at-uri from a viewer state / a prior follow). */
    suspend fun unfollow(followUri: String): Result<Unit>
}
```
`DefaultFollowRepository` wraps the same `app.bsky.graph` create/delete the profile feature uses today (move that logic here). `@Module @InstallIn(SingletonComponent::class)` binds it. (Follow the like/repost primitive shape already in this module.)
- [ ] **Step 2: failing test** mirroring the existing follow test (if any) — follow returns the created uri; unfollow deletes by uri. Use the module's test harness (MockEngine / fake xrpc).
- [ ] **Step 3: implement; run → PASS.**
- [ ] **Step 4: migrate `:feature:profile:impl`** to inject `FollowRepository` instead of its inline follow logic; delete the now-dead inline code. Run `:feature:profile:impl:testProductionDebugUnitTest` + `:feature:profile:impl:assembleDebug` → PASS (profile follow still works).
- [ ] **Step 5: commit.** `refactor(interactions): extract shared FollowRepository`. Refs: nubecita-hwix.4.

---

## Task 3: `getConvoMembers` repository + `GroupMemberUi` mapper
**Files:** `data/ChatRepository.kt`(+Default, +5 fakes), new `data/GroupMemberMapper.kt`, `GroupDetailsContract.kt` (the UI models); Test `data/GroupMemberMapperTest.kt`.

- [ ] **Step 1: confirm role strings** — `javap` `chat.bsky.actor.MemberRoleKt` / `GroupConvoMember` in the SNAPSHOT jar; note the role values (owner/standard) for the mapper.
- [ ] **Step 2: models** in `GroupDetailsContract.kt`:
```kotlin
@Immutable data class GroupMemberUi(
    val did: String, val handle: String, val displayName: String?, val avatarUrl: String?,
    val role: GroupRole, val addedByName: String?, val isViewer: Boolean,
    val followState: FollowState, val followUri: String?,
)
enum class GroupRole { Owner, Admin, Member }
enum class FollowState { NotFollowing, Following, InFlight }
```
And `MemberPage(members: ImmutableList<GroupMemberUi>, cursor: String?)` in `ChatRepository.kt`.
- [ ] **Step 3: mapper (TDD)** `data/GroupMemberMapper.kt`: `fun ProfileViewBasic.toGroupMemberUi(viewerDid: String): GroupMemberUi` — `role` from `kind` (GroupConvoMember.role → Owner/Admin/Member; non-group/unknown → Member), `addedByName` from `kind.addedBy?.displayName ?: handle`, `followState`/`followUri` from `viewer.following` (non-null → Following+uri, else NotFollowing), `isViewer = did.raw == viewerDid`. Test: group-member-with-role, addedBy present/absent, following/not, self. (This is the only file touching `chat.bsky.actor` member-kind types.)
- [ ] **Step 4: repository** — `suspend fun getConvoMembers(convoId: String, cursor: String? = null): Result<MemberPage>`. Default impl: `ConvoService(client).getConvoMembers(GetConvoMembersRequest(convoId, limit = 100, cursor)).let { MemberPage(it.members.map { m -> m.toGroupMemberUi(viewerDid) }.toImmutableList(), it.cursor) }`; rethrow CancellationException; Timber.w javaClass only. Implement in all 5 fakes (test fake settable `getConvoMembersResult`).
- [ ] **Step 5: compile all source sets + run mapper tests → PASS.**
- [ ] **Step 6: commit.** `feat(chats): getConvoMembers repository + GroupMemberUi mapper`. Refs: nubecita-hwix.4.

---

## Task 4: Accurate thread-header member count
**Files:** `ChatContract.kt` (header count), `ChatViewModel.kt`, `ChatScreenContent.kt`.
- [ ] **Step 1: failing VM test** — opening a group thread calls `getConvoMembers(convoId)` once and exposes the count (e.g. `ChatHeader.Group.memberCount` or a state field). Direct threads don't call it.
- [ ] **Step 2: implement** — add `memberCount: Int?` to `ChatHeader.Group` (or a `groupMemberCount` state field). In `ChatViewModel.launchLoad`, when the header is a Group, fire `getConvoMembers(convoId, limit=100)` (fire-and-forget like markConvoRead) and `setState` the count from `members.size`; on failure leave it null (header falls back to facepile only). The TopAppBar "N members" reads the accurate count when present.
- [ ] **Step 3: run → PASS; assembleDebug.**
- [ ] **Step 4: commit.** `fix(chats): accurate group member count in the thread header`. Refs: nubecita-hwix.4.

---

## Task 5: `GroupDetailsViewModel` + contract
**Files:** `GroupDetailsContract.kt` (state/event/effect), `GroupDetailsViewModel.kt`; Test `GroupDetailsViewModelTest.kt`.
- [ ] **Step 1: contract** — `GroupDetailsViewState(name, members: ImmutableList<GroupMemberUi>, memberCount, maxMembers=50, muted, status: GroupDetailsLoadStatus)`; events `Refresh`, `RetryClicked`, `BackPressed`, `ToggleFollow(did)`, `LeaveTapped`, `ToggleMute`, `ReportTapped`, `MemberTapped(did)`; effects `ShowError`, `NavigateBack`, `NavigateTo(NavKey)` (profile/report).
- [ ] **Step 2: VM (TDD)** — `@HiltViewModel(assistedFactory=…)` injecting `GroupDetails` + `ChatRepository` + `FollowRepository`. On init: `getConvo(convoId)` (name + muted; guard non-group → InitialError) then `getConvoMembers(convoId)` → members + count; loop on non-null cursor (defensive). `ToggleFollow`: optimistic `InFlight`→follow/unfollow→reconcile/rollback+ShowError (in-flight guard per did, mirror ToggleReaction). `LeaveTapped`→`leaveConvo`→`NavigateBack`. `ToggleMute`→`setMuted`. `ReportTapped`/`MemberTapped`→`NavigateTo`. Tests: load (members+count+name), follow toggle add/remove/rollback, leave→back, non-group→error.
- [ ] **Step 3: run → PASS.**
- [ ] **Step 4: commit.** `feat(chats): GroupDetailsViewModel`. Refs: nubecita-hwix.4.

---

## Task 6: `GroupDetailsScreen` + `GroupMemberRow`
**Files:** `GroupDetailsScreen.kt`, `GroupDetailsScreenContent.kt`, `ui/GroupMemberRow.kt`.
- [ ] **Step 1:** stateless `GroupDetailsScreenContent(state, onEvent)` — `Scaffold(containerColor = surface)` + TopAppBar (back + name). Body: header block (`AvatarGroup(members)` facepile + name + "N/50" via a plurals/format string) + action row (Mute toggle, Report, Leave) + a `LazyColumn` of `GroupMemberRow`. `GroupMemberRow`: `NubecitaAvatar`(member) + displayName/handle + role badge (Owner/Admin via a small `AssistChip`/`Badge`; Member = none) + "Added by …" supporting line + a Follow `Button`/`OutlinedButton` (hidden for `isViewer`; shows Following/Follow/spinner from `followState`) → `onEvent(ToggleFollow(did))`; row tap → `MemberTapped(did)`. `GroupDetailsScreen` (stateful) collects effects (ShowError snackbar; NavigateBack/NavigateTo via callbacks).
- [ ] **Step 2: strings** (`chat_group_member_count` plural already exists? add `group_details_*` strings in en/es/pt).
- [ ] **Step 3: compile + screenshotTest compile + assembleDebug → PASS.**
- [ ] **Step 4: commit.** `feat(chats): group details screen + member rows`. Refs: nubecita-hwix.4.

---

## Task 7: Overflow menu entry point + nav wiring
**Files:** `ChatScreenContent.kt` (⋮ menu), `di/ChatsNavigationModule.kt`, `ChatScreen.kt`/`ChatContract.kt` (effect).
- [ ] **Step 1:** add a `ChatEvent.GroupDetailsTapped` + `ChatEffect.NavigateTo(NavKey)` (if not present); VM emits `NavigateTo(GroupDetails(convoId))`. In the thread TopAppBar `actions = {}`, when `state.header is ChatHeader.Group`, show a `MoreVert` `IconButton` → `DropdownMenu` with "Group details" → `onEvent(GroupDetailsTapped)`.
- [ ] **Step 2: nav module** — `entry<GroupDetails>(metadata = adaptiveDialog()) { route -> val navState = LocalMainShellNavState.current; GroupDetailsScreen(viewModel = hiltViewModel<GroupDetailsViewModel, …>(creationCallback = { it.create(route) }), onBack = { navState.removeLast() }, onNavigateTo = { navState.add(it) }) }`. The chat screen's `onNavigateTo` already forwards `NavigateTo` effects to `navState.add`.
- [ ] **Step 3: assembleDebug → PASS.**
- [ ] **Step 4: commit.** `feat(chats): group details overflow entry + nav`. Refs: nubecita-hwix.4.

---

## Task 8: Screenshots + verification
- [ ] **Step 1:** screenshot fixtures (light+dark): GroupDetailsScreenContent with a roster (owner badge, admin badge, member, a Following vs Follow member, self row), and the member-count header. Mirror existing screenshot conventions; stage only new baselines (revert pre-existing drift); PR needs `update-baselines`.
- [ ] **Step 2: full gate** — `spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug :feature:chats:impl:testProductionDebugUnitTest :feature:profile:impl:testProductionDebugUnitTest :app:assembleDebug` green.
- [ ] **Step 3: compose-expert review** (UI added) + commit. `test(chats): group details screenshots`. Refs: nubecita-hwix.4.

---

## Self-Review
**Spec coverage:** entry point (T7), GroupDetails route adaptiveDialog (T1/T7), roster via getConvoMembers (T3), role/addedBy/Follow (T3/T5/T6), accurate count + thread-header fix (T3/T4), FollowRepository extraction (T2), Leave/Mute/Report reuse (T5/T6), tests+screenshots (all + T8). Covered.
**Placeholder scan:** role-string values + the existing-follow-logic location are flagged "confirm via javap / grep in-task" — concrete verifications, not deferred work.
**Type consistency:** `GroupMemberUi`/`GroupRole`/`FollowState`, `MemberPage`, `getConvoMembers(convoId,cursor)`, `toGroupMemberUi(viewerDid)`, `FollowRepository.follow/unfollow`, `GroupDetails(convoId)`, `GroupDetailsViewModel` — consistent across tasks.
