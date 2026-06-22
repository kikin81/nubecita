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

### Entry point — M3 Expressive FAB menu (speed dial)

Replace the single inbox FAB with the **Material 3 Expressive `FloatingActionButtonMenu`**
(speed-dial), not a `DropdownMenu`: a primary `ToggleFloatingActionButton` that animates between the
compose/edit glyph and a close ("X") glyph and, when toggled, reveals a vertical stack of
pill-shaped `FloatingActionButtonMenuItem`s. These APIs are `@ExperimentalMaterial3ExpressiveApi` in
material3 **1.5.0-alpha22** (verified present in the resolved artifact).

In `ChatsScreenContent`, screen-local state drives it (no ViewModel involvement):
`var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }`. Shape:
```kotlin
FloatingActionButtonMenu(
    expanded = fabMenuExpanded,
    button = {
        ToggleFloatingActionButton(
            checked = fabMenuExpanded,
            onCheckedChange = { fabMenuExpanded = it },
        ) { /* animated edit ↔ close icon, driven by the toggle progress */ }
    },
) {
    // Primary action nearest the FAB.
    FloatingActionButtonMenuItem(
        onClick = { fabMenuExpanded = false; onNewChat() },
        icon = { NubecitaIcon(NubecitaIconName.Edit, …) },
        text = { Text(stringResource(R.string.chats_new_message)) },
    )
    FloatingActionButtonMenuItem(
        onClick = { fabMenuExpanded = false; onNewGroup() },
        icon = { NubecitaIcon(NubecitaIconName.Group /* or People */, …) },
        text = { Text(stringResource(R.string.chats_new_group)) },
    )
}
```
The menu stays gated by the existing FAB conditions (hidden in multi-select mode and when
`NotEnrolled`). `ChatsScreen` gains an `onNewGroup: () -> Unit` param alongside `onNewChat`; the
nav module wires `onNewChat = { navState.add(NewChat) }` and `onNewGroup = { navState.add(NewGroup) }`.
New strings: `chats_new_message`, `chats_new_group` (+ a toggle content-description that flips
between "New conversation" / "Close" by `fabMenuExpanded`). The expand/scrim/back-to-collapse
animation is owned by the component. `fabMenuExpanded` uses `rememberSaveable` so the menu collapses
across config changes. Confirm the exact `FloatingActionButtonMenu` / `ToggleFloatingActionButton` /
`FloatingActionButtonMenuItem` parameter names against 1.5.0-alpha22 at implementation time and add
the `@OptIn(ExperimentalMaterial3ExpressiveApi::class)`.

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
  - `nameGraphemeCount: Int`, `isNameValid: Boolean` (on the **trimmed** name: `count in 1..GROUP_NAME_MAX_GRAPHEMES` (128) **and** UTF-8 byte length `<= GROUP_NAME_MAX_BYTES` (1280) — the lexicon enforces both, and a sub-128-grapheme name can still bust the byte cap via multi-byte ZWJ emoji)
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
- **Input lock while submitting:** when `isSubmitting`, the VM ignores `RecipientToggled` /
  `RecipientRemoved` / a second `CreateTapped` (guarded at the top of each handler), and the UI
  disables both text fields + chip/row interactions (see the screen section), so neither the name
  nor the membership can change while `createGroup` is in flight. The screen also feeds
  `enabled = !state.isSubmitting` into the `OutlinedTextField`s, `RecipientChipsRow`, and
  `RecipientRow`s.

### `NewGroupScreen` (UI, single screen)

`Scaffold(containerColor = surface)` + TopAppBar:
- **close** nav icon, title "New group".
- a trailing **"Create"** action. To avoid width jitter when it toggles to a spinner, render a
  fixed-width `Box` holding BOTH the `Text("Create")` and a `CircularProgressIndicator`: the text at
  `alpha = if (state.isSubmitting) 0f else 1f` and the indicator shown only while `isSubmitting`, so
  the control keeps a constant width across states. `enabled = state.canCreate` (so it's disabled,
  not just opacity-dimmed, while submitting or invalid).

**Adaptive width (tablet/foldable).** The body is wrapped in a centered container constrained to
`Modifier.widthIn(max = 600.dp)` (e.g. a `Column(Modifier.fillMaxWidth().widthIn(max = 600.dp).align(CenterHorizontally))`,
or a centered wrapper). On Compact it fills the width; on Medium/Expanded the name field, chips, and
search list stay within one comfortable reading column instead of stretching edge-to-edge, per M3
large-screen guidance. (The route stays a single-pane sub-route; this is purely a body max-width
constraint — no list-detail metadata.) Body (top→bottom), all disabled when `state.isSubmitting`:

1. group-name `OutlinedTextField(state = nameFieldState, singleLine, enabled = !state.isSubmitting,
   isError = nameGraphemeCount > GROUP_NAME_MAX_GRAPHEMES)` with a **proactive grapheme counter** in
   `supportingText`: once `nameGraphemeCount >= GROUP_NAME_COUNTER_THRESHOLD` (80% of 128 = 103) show
   `"$nameGraphemeCount/128"`, coloured `colorScheme.error` when over 128; below the threshold the
   supporting text is empty. (Surfacing the counter as the user approaches the cap avoids the abrupt
   appear-only-on-overflow shift.)
2. selected members as `RecipientChipsRow` (the extracted shared chip FlowRow),
   `enabled = !state.isSubmitting`, hidden when empty.
3. an at-capacity hint when `atCapacity`.
4. member search `OutlinedTextField(state = queryFieldState, enabled = !state.isSubmitting)`.
5. results/recent `LazyColumn` reusing `RecipientRow(actor, enabled = !state.atCapacity && !state.isSubmitting, respectCanMessage = false, onClick = { RecipientToggled(it.did) })`; `Searching`/`NoResults`/`Error` bodies mirror the add picker.

Stateful screen collects effects: `ShowError` → snackbar (child coroutine, pre-resolved copy per
`ChatError` variant); `GroupCreated(convoId)` → `onCreated(convoId)`.

**Back-to-two-pane on tablets.** On Medium/Expanded the inbox is a list-detail layout; `onCreated`
does `replaceTop(Chat(convoId))`, which lands the new convo in the **detail pane** (the `Chat` entry
already carries `ListDetailSceneStrategy.detailPane()` metadata) with the inbox list restored in the
list pane — the same transition New-Chat's `replaceTop(Chat)` already produces, so there's no abrupt
full-screen→two-pane jump. Verify this transition on a tablet/expanded width during testing.

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
- New `GROUP_NAME_COUNTER_THRESHOLD = 103` (80% of 128) — above this the proactive name counter shows.
- New `GROUP_NAME_MAX_BYTES = 1280` — the lexicon's UTF-8 byte cap on the name, validated alongside the grapheme cap (on the trimmed text).

## Files

**New**
- `feature/chats/impl/.../NewGroupContract.kt`, `NewGroupViewModel.kt`, `NewGroupScreen.kt`
- `feature/chats/impl/.../ui/RecipientChipsRow.kt`

**Modified**
- `feature/chats/api/.../Chats.kt` — `NewGroup` NavKey
- `feature/chats/impl/.../data/ChatRepository.kt` (+ `DefaultChatRepository.kt`) — `createGroup` + 5 fakes
- `feature/chats/impl/.../ChatsScreen.kt` + `ChatsScreenContent.kt` — `onNewGroup` + M3 Expressive `FloatingActionButtonMenu`
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — `onNewGroup` wiring + `entry<NewGroup>`
- `feature/chats/impl/.../AddGroupMembersScreen.kt` — use the extracted `RecipientChipsRow`
- strings `values/`, `values-b+es+419/`, `values-pt-rBR/`

## Testing

- **Unit:** `NewGroupViewModel` — name grapheme validation incl. the 128 boundary + empty/blank →
  `canCreate` gating; the proactive counter threshold (`nameGraphemeCount` exposed; assert it crosses
  103/128 correctly); search/recent select/deselect; cap at `GROUP_MAX_MEMBERS - 1`; create success →
  `GroupCreated(convoId)` with `createGroupCalls` recording `(name, dids)`; failure → `ShowError`
  (e.g. `GroupFull` for `MemberLimitReached`); **input-lock**: while `isSubmitting`, `RecipientToggled`
  / `RecipientRemoved` / a second `CreateTapped` are ignored (assert no extra `createGroupCalls` and
  `selected` unchanged). `ChatRepository` fakes drive results (use a gated/suspending `createGroup`
  result to hold `isSubmitting` true for the lock assertions).
- **Screenshot:** the **`FloatingActionButtonMenu`** expanded over the inbox; `NewGroupScreenContent`
  empty, with name + chips + results, at-capacity, the name counter near/over the limit, and the
  `isSubmitting` state (spinner in the Create slot, inputs disabled). Add a Medium/Expanded-width
  preview to capture the 600.dp body constraint.
- **Gate:** `spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug
  :feature:chats:impl:testProductionDebugUnitTest :app:assembleDebug`, screenshot validate (+
  `update-baselines` label), compose-expert review (UI added).

## Conventions

MVI flat state + sealed status sum; editor-VM `TextFieldState` exceptions (name + query), derived
projections on `UiState`; `ImmutableList`; `@Immutable` models; surface tokens
(`Scaffold(containerColor = surface)`, chips/menu default elevation); no `!!`; PII never logged
remotely; optimistic patterns not needed here (create is a single one-shot); lowercase-leading
Conventional Commits referencing `nubecita-hwix.6`.
