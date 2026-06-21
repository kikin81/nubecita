# Group chat Phase 3c-1 — group creation

**Epic:** `nubecita-hwix` (Group chat support). **Sub-slice C1** of Phase 3, after Slice A
(group details, #561) and Slice B (member management, #562). **bd:** `nubecita-hwix.6`.

## Purpose

Let the user **create a new group conversation** from the inbox: pick members (recipient-chip
picker), name the group, and create it via atproto 9.4.0 `chat.bsky.group.createGroup` — landing
directly in the new thread.

## Scope decomposition

"Slice C" is three independent surfaces; this spec covers **C1 only**:

- **C1 — group creation (this spec).**
- C2 — join requests (owner-side `listJoinRequests` + approve/reject). Later.
- C3 — invite links (`createJoinLink`/edit/enable/disable + `getGroupPublicInfo` preview →
  `requestJoin`, deep-link handling). Later.

## SDK surface (atproto 9.4.0 — verified in `models-jvm-9.4.0.jar`)

`io.github.kikin81.atproto.chat.bsky.group.GroupService`, constructed `GroupService(xrpcClient)`
(same as the Slice B member ops). No SDK bump or overlay needed.

- `createGroup(CreateGroupRequest(members: List<Did>, name: String))` → `CreateGroupResponse(convo: ConvoView)`.
  `members` lexicon `maxLength = 49` (so up to 49 others + the creator = 50); `name`
  `minLength = 1`, `maxGraphemes = 128`, `maxLength = 1280` bytes. The creator joins `accepted`;
  added members are `pending`/`request` until they accept. The new convo id is `response.convo.id`
  (same `ConvoView.id` field `getConvo` reads).

## Non-goals

- Join requests / invite links (C2, C3).
- `editGroup` (rename / re-add): name + members are set at creation only in C1.
- Adding members who are pending acceptance is the server's behavior; C1 surfaces the create result,
  not invite tracking.

## Architecture

### Entry point — FAB choice menu

The inbox FAB in `ChatsScreenContent` currently calls `onNewChat`. Replace its `onClick` with a
small **`DropdownMenu` anchored to the FAB** (screen-local `var menuExpanded by remember { mutableStateOf(false) }`,
mirroring the existing overflow menus — no ViewModel involvement):

- **New message** → `onNewChat()` (unchanged → nav module `navState.add(NewChat)`).
- **New group** → `onNewGroup()` (new callback → nav module `navState.add(NewGroup)`).

`ChatsScreen` gains an `onNewGroup: () -> Unit` param alongside `onNewChat`. The DropdownMenu items
use new strings; the FAB content description is unchanged.

### `NewGroup` route + screen

- New `@Serializable data object NewGroup : NavKey` in `:feature:chats:api/Chats.kt`.
- Registered as a **plain full-screen sub-route** (`entry<NewGroup> { … }`, NO `adaptiveDialog`) —
  it mirrors `NewChat`, its new-conversation sibling.
- `NewGroupScreen` (stateful + stateless content + previews) + `NewGroupViewModel` (plain
  `@HiltViewModel @Inject` — `NewGroup` carries no args) + `NewGroupContract`.

### `NewGroupViewModel` + contract

Plain `@Inject` VM depending on `ActorRepository`, `ChatRepository`, `SessionStateProvider`. Reuses
`RecipientUi` (from `AddGroupMembersContract`), the search/recent pipeline shape, and
`GraphemeCounter` (`:core:common:text`).

- **Editor exceptions (two `TextFieldState` public vals):** `nameFieldState` (group name) and
  `queryFieldState` (member search), each observed via `snapshotFlow`.
- **State (`NewGroupViewState`):**
  - `selected: ImmutableList<RecipientUi>`
  - `atCapacity: Boolean` — `selected.size >= GROUP_MAX_MEMBERS - 1` (49 others + creator = 50)
  - `nameGraphemeCount: Int`, `isNameValid: Boolean` (`count in 1..GROUP_NAME_MAX_GRAPHEMES`, 128)
  - `isSubmitting: Boolean`
  - `status: NewGroupStatus` — `Recent / Searching / Results / NoResults / Error` (mirrors New-Chat)
  - derived `canCreate: Boolean = isNameValid && selected.isNotEmpty() && !isSubmitting`
- **Events:** `RecipientToggled(did)`, `RecipientRemoved(did)`, `CreateTapped`, `RetryClicked`.
- **Effects:** `ShowError(ChatError)`, `GroupCreated(convoId: String)`.
- **Name validation:** a `snapshotFlow { nameFieldState.text }` collector updates `nameGraphemeCount`
  + `isNameValid` (derived projections stay on `UiState`, per the editor-MVI rule).
- **Search pipeline:** the same `combine(rawStatusFlow, uiState.map { it.selected }.distinctUntilChanged())`
  shape as `AddGroupMembersViewModel`, with `pickable(selectedDids)` excluding `selfDid` + selected
  (no existing-member exclusion — brand-new group).
- **Submit:** `CreateTapped` → guard `canCreate` → `isSubmitting = true` →
  `chatRepository.createGroup(name, selectedDids)` → success: `GroupCreated(convoId)`; failure:
  `isSubmitting = false` + `ShowError(it.toMemberMgmtError())`.

### `NewGroupScreen` (UI, single screen)

`Scaffold(containerColor = surface)` + TopAppBar (close icon + a trailing **"Create"** `TextButton`,
`enabled = state.canCreate`, showing a `CircularProgressIndicator` while `isSubmitting`). Body
(top→bottom):
1. group-name `OutlinedTextField(state = nameFieldState, singleLine)` with an over-limit supporting
   text when `nameGraphemeCount > 128`.
2. selected members as `RecipientChipsRow` (the extracted shared chip FlowRow), hidden when empty.
3. an at-capacity hint when `atCapacity`.
4. member search `OutlinedTextField(state = queryFieldState)`.
5. results/recent `LazyColumn` reusing `RecipientRow(actor, enabled = !state.atCapacity, respectCanMessage = false, onClick = { RecipientToggled(it.did) })`; `Searching`/`NoResults`/`Error` bodies mirror the add picker.

Stateful screen collects effects: `ShowError` → snackbar (child coroutine, pre-resolved copy per
`ChatError` variant); `GroupCreated(convoId)` → `onCreated(convoId)`.

### Navigation

- Nav module: `onNewGroup = { navState.add(NewGroup) }`, and
  ```kotlin
  entry<NewGroup> {
      val navState = LocalMainShellNavState.current
      NewGroupScreen(
          viewModel = hiltViewModel(),
          onBack = { navState.removeLast() },
          onCreated = { convoId -> navState.replaceTop(Chat(convoId = convoId)) },
      )
  }
  ```
- `replaceTop(Chat(convoId))` opens the new thread and makes Back return to the inbox (the create
  screen is dropped) — mirrors New-Chat's `replaceTop(Chat)`. The inbox convo list shows the new
  group on its next refresh; **no nav-result plumbing** is needed (unlike Slice B's add flow, which
  returned to an existing screen).

### Repository

```kotlin
/** Create a group named [name] with [dids] as initial (pending) members. Returns the new convoId. */
suspend fun createGroup(name: String, dids: List<String>): Result<String>
```
`DefaultChatRepository` implements it over `GroupService(xrpcClientProvider.authenticated())`,
returning `response.convo.id`, on the IO dispatcher, rethrowing `CancellationException`, logging only
`javaClass.name`. Reuses the `groupMutation` pattern (a value-returning variant, since `createGroup`
yields a convoId rather than `Unit`). Added to all **5** `ChatRepository` implementors (Default +
test fake with settable result/capture + androidTest fake + bench fake + the inline observer-test
fake).

### Shared-chip refactor (DRY)

Extract the InputChip `FlowRow` (currently inline in `AddGroupMembersScreen`) into a shared
`feature/chats/impl/.../ui/RecipientChipsRow.kt`:
`RecipientChipsRow(selected: ImmutableList<RecipientUi>, onRemove: (did: String) -> Unit, modifier)`.
Switch `AddGroupMembersScreen` to use it; `NewGroupScreen` uses it too (2 call sites justify the
extraction).

## Constants

- `GROUP_MAX_MEMBERS = 50` (existing, `GroupDetailsContract`) — cap on others = `GROUP_MAX_MEMBERS - 1`.
- New `GROUP_NAME_MAX_GRAPHEMES = 128`.

## Files

**New**
- `feature/chats/impl/.../NewGroupContract.kt`, `NewGroupViewModel.kt`, `NewGroupScreen.kt`
- `feature/chats/impl/.../ui/RecipientChipsRow.kt`

**Modified**
- `feature/chats/api/.../Chats.kt` — `NewGroup` NavKey
- `feature/chats/impl/.../data/ChatRepository.kt` (+ `DefaultChatRepository.kt`) — `createGroup` + 5 fakes
- `feature/chats/impl/.../ChatsScreen.kt` + `ChatsScreenContent.kt` — `onNewGroup` + FAB DropdownMenu
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — `onNewGroup` wiring + `entry<NewGroup>`
- `feature/chats/impl/.../AddGroupMembersScreen.kt` — use the extracted `RecipientChipsRow`
- strings `values/`, `values-b+es+419/`, `values-pt-rBR/`

## Testing

- **Unit:** `NewGroupViewModel` — name grapheme validation incl. the 128 boundary + empty/blank →
  `canCreate` gating; search/recent select/deselect; cap at `GROUP_MAX_MEMBERS - 1`; create success →
  `GroupCreated(convoId)` with `createGroupCalls` recording `(name, dids)`; failure → `ShowError`
  (e.g. `GroupFull` for `MemberLimitReached`). `ChatRepository` fakes drive results.
- **Screenshot:** the FAB DropdownMenu open; `NewGroupScreenContent` empty, with name + chips +
  results, and at-capacity.
- **Gate:** `spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug
  :feature:chats:impl:testProductionDebugUnitTest :app:assembleDebug`, screenshot validate (+
  `update-baselines` label), compose-expert review (UI added).

## Conventions

MVI flat state + sealed status sum; editor-VM `TextFieldState` exceptions (name + query), derived
projections on `UiState`; `ImmutableList`; `@Immutable` models; surface tokens
(`Scaffold(containerColor = surface)`, chips/menu default elevation); no `!!`; PII never logged
remotely; optimistic patterns not needed here (create is a single one-shot); lowercase-leading
Conventional Commits referencing `nubecita-hwix.6`.
