# Group chat Phase 3c-1 — group creation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a new group conversation from the inbox — an M3 Expressive FAB menu opens a single-screen recipient-chip picker + required name → `createGroup` → land in the new thread.

**Architecture:** A new `NewGroup` full-screen sub-route forks the Slice B recipient-chip picker and adds a group-name field; `ChatRepository.createGroup` wraps atproto 9.4.0 `chat.bsky.group.GroupService.createGroup` and returns the new convoId; the inbox FAB becomes an M3 Expressive `FloatingActionButtonMenu`. Success does `replaceTop(Chat(convoId))`.

**Tech Stack:** Kotlin 2.3, Jetpack Compose + M3 Expressive (material3 1.5.0-alpha22, `@ExperimentalMaterial3ExpressiveApi`), MVI (`MviViewModel`), Hilt, Navigation 3, `kotlinx.collections.immutable`, JUnit5 + Turbine + MockK, Compose screenshot tests.

**Spec:** `docs/superpowers/specs/2026-06-21-group-chat-phase3c1-group-creation-design.md`. **bd:** `nubecita-hwix.6`. **Branch:** `feat/nubecita-hwix.6-group-creation` (PR #564). All commits: lowercase-leading Conventional subjects + `Refs: nubecita-hwix.6`.

---

## Conventions for every task

- TDD for logic (repo, VM): failing test → watch fail → implement → watch pass → commit. UI tasks lead with composables + previews.
- NEVER `--no-verify`. commitlint rejects sentence/start/pascal-case subjects — lowercase-leading. Signing failure → `git config --local commit.gpgsign false` then retry. `pre-commit run --files <changed>` first, `git add -A`, commit, verify with `git log --oneline -1`.
- No `!!`. `ImmutableList` for list state. PII never logged remotely (repos log `javaClass.name` only).
- After a shared-interface change, update ALL implementors (the `ChatRepository` change touches 5).

---

## Task 1: `ChatRepository.createGroup` (+ all 5 implementors)

**Files:**
- Modify: `feature/chats/impl/.../data/ChatRepository.kt`, `data/DefaultChatRepository.kt`
- Modify fakes: `src/test/.../FakeChatRepository.kt`, `src/androidTest/.../FakeChatRepository.kt`, `src/bench/.../data/BenchFakeChatRepository.kt`, `src/test/.../store/ChatsUnreadPollingObserverTest.kt` (inline fake)
- Test: `feature/chats/impl/src/test/.../data/DefaultChatRepositoryCreateGroupTest.kt` (only if the repo has a JVM-testable seam; otherwise the VM tests in Task 4 exercise it via the fake — see note)

**Verified SDK:** `GroupService(xrpcClient).createGroup(CreateGroupRequest(members: List<Did>, name: String)): CreateGroupResponse`, and `CreateGroupResponse.convo.id` is the new convoId (`ConvoView.id`, same field `getConvo` reads). `members` maxLength=49.

- [ ] **Step 1: Interface** — add to `ChatRepository.kt` (near `addMembers`):

```kotlin
/** Create a group named [name] with [dids] as initial (pending) members. Returns the new convoId. */
suspend fun createGroup(name: String, dids: List<String>): Result<String>
```

- [ ] **Step 2: Implement in `DefaultChatRepository`.** The existing `groupMutation` returns `Result<Unit>`; add a value-returning sibling and the override:

```kotlin
import io.github.kikin81.atproto.chat.bsky.group.CreateGroupRequest
// (GroupService, Did already imported from Slice B)

override suspend fun createGroup(name: String, dids: List<String>): Result<String> =
    withContext(dispatcher) {
        runCatching {
            GroupService(xrpcClientProvider.authenticated())
                .createGroup(CreateGroupRequest(members = dids.map { Did(it) }, name = name))
                .convo.id
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            Timber.tag(TAG).w(throwable, "createGroup failed: %s", throwable.javaClass.name)
        }
    }
```
(Match the existing `groupMutation` shape — `withContext(dispatcher)`, `runCatching`, rethrow `CancellationException`, log `javaClass.name`. No PII.)

- [ ] **Step 3: Update all 5 implementors.** Test fake (`src/test`) gets a settable result + capture:

```kotlin
var createGroupResult: Result<String> = Result.success("convo:new")
val createGroupCalls = mutableListOf<Pair<String, List<String>>>()
override suspend fun createGroup(name: String, dids: List<String>): Result<String> {
    createGroupCalls += name to dids
    return createGroupResult
}
```
androidTest fake + inline observer-test fake: minimal `= Result.success("convo:new")` (match each file's override style). Bench fake: return a deterministic id, e.g. `Result.success("bench:new-group")`.

- [ ] **Step 4: Verify** — `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:testProductionDebugUnitTest :feature:chats:impl:compileBenchDebugKotlin spotlessCheck` → green (every implementor compiles).
  > Note: no dedicated repo unit test is required (the production impl is a thin SDK wrapper with no JVM-mockable seam; it's covered by the VM tests through the fake). Skip `DefaultChatRepositoryCreateGroupTest.kt` unless an existing repo-test harness already exists to mirror.

- [ ] **Step 5: Commit** — `feat(chats): repository createGroup via GroupService` / `Refs: nubecita-hwix.6`.

---

## Task 2: `NewGroup` NavKey

**Files:** Modify `feature/chats/api/.../Chats.kt`; Test `feature/chats/impl/src/test/.../NewGroupNavKeyTest.kt`.

- [ ] **Step 1: Failing test** (mirror `NewChat` — it's a `data object`):

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.feature.chats.api.NewGroup
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NewGroupNavKeyTest {
    @Test fun `NewGroup is a NavKey`() { assertTrue(NewGroup is NavKey) }
}
```

- [ ] **Step 2: Run → FAIL** (`:feature:chats:impl:testProductionDebugUnitTest --tests "*NewGroupNavKeyTest*"`).

- [ ] **Step 3: Add the NavKey** in `Chats.kt` (next to `NewChat`):

```kotlin
/**
 * Group-creation flow: a recipient-chip picker + group-name field that creates a new
 * group convo. A `@MainShell` sub-route pushed from the inbox FAB menu's "New group"
 * action. Plain full-screen (mirrors [NewChat]); on success it `replaceTop`s with the
 * new [Chat]. Carries no args.
 */
@Serializable
data object NewGroup : NavKey
```

- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `feat(chats): add the new-group NavKey` / `Refs: nubecita-hwix.6`.

---

## Task 3: Extract shared `RecipientChipsRow`

**Files:**
- Create: `feature/chats/impl/.../ui/RecipientChipsRow.kt`
- Modify: `feature/chats/impl/.../AddGroupMembersScreen.kt` (replace its inline `FlowRow` of `InputChip`s with the new component)

`RecipientUi` lives in `AddGroupMembersContract.kt` (package `net.kikin.nubecita.feature.chats.impl`).

- [ ] **Step 1: Create the component** (lift the exact chip rendering currently inline in `AddGroupMembersScreenContent`):

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.R
import net.kikin.nubecita.feature.chats.impl.RecipientUi

/**
 * A FlowRow of selected recipients as removable M3 [InputChip]s (avatar + label + ✕).
 * Shared by the add-members and new-group pickers. When [enabled] is false the chips
 * are non-interactive (used while a submit is in flight).
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun RecipientChipsRow(
    selected: ImmutableList<RecipientUi>,
    onRemove: (did: String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        selected.forEach { r ->
            InputChip(
                selected = true,
                enabled = enabled,
                onClick = { onRemove(r.did) },
                label = { Text(r.displayName ?: r.handle, maxLines = 1) },
                avatar = {
                    NubecitaAvatar(
                        model = r.avatarUrl,
                        contentDescription = null,
                        size = InputChipDefaults.AvatarSize,
                        fallback = avatarFallbackFor(did = r.did, handle = r.handle, displayName = r.displayName),
                    )
                },
                trailingIcon = {
                    NubecitaIcon(
                        name = NubecitaIconName.Close,
                        contentDescription = stringResource(R.string.add_members_remove_chip),
                    )
                },
            )
        }
    }
}
```
(Confirm against the current `AddGroupMembersScreen` chip block and copy its exact `InputChip` config; this adds an `enabled` param the new-group screen uses.)

- [ ] **Step 2: Switch `AddGroupMembersScreen`** — replace the inline `if (state.selected.isNotEmpty()) { FlowRow { … } }` with `if (state.selected.isNotEmpty()) { RecipientChipsRow(selected = state.selected, onRemove = { onEvent(AddMembersEvent.RecipientRemoved(it)) }, modifier = Modifier.padding(horizontal = 16.dp)) }`. Remove now-unused imports (`FlowRow`, `InputChip`, `InputChipDefaults`, etc.) from that file.

- [ ] **Step 3: Verify** — `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:compileProductionDebugUnitTestKotlin spotlessCheck` → green; the existing `AddGroupMembers` screenshot baselines must still validate (`./gradlew :feature:chats:impl:validateProductionDebugScreenshotTest` — the add-members chip visuals are unchanged, so its baselines should pass; ignore pre-existing unrelated drift).

- [ ] **Step 4: Commit** — `refactor(chats): extract shared RecipientChipsRow` / `Refs: nubecita-hwix.6`.

---

## Task 4: `NewGroupContract` + `NewGroupViewModel`

**Files:**
- Create: `feature/chats/impl/.../NewGroupContract.kt`, `NewGroupViewModel.kt`
- Test: `feature/chats/impl/src/test/.../NewGroupViewModelTest.kt`

Mirror `AddGroupMembersViewModel` (search/recent `combine` pipeline, `pickable`, capacity) but: plain `@Inject` (no route), a second `nameFieldState`, grapheme validation, `createGroup` submit, and input-lock. Read `AddGroupMembersViewModel.kt` first and reuse its pipeline verbatim.

**Contract:**
```kotlin
@Immutable
data class NewGroupViewState(
    val selected: ImmutableList<RecipientUi> = persistentListOf(),
    val atCapacity: Boolean = false,
    val nameGraphemeCount: Int = 0,
    val isNameValid: Boolean = false,
    val isSubmitting: Boolean = false,
    val status: NewGroupStatus = NewGroupStatus.Recent(persistentListOf()),
) : UiState {
    val canCreate: Boolean get() = isNameValid && selected.isNotEmpty() && !isSubmitting
}

sealed interface NewGroupStatus {
    data class Recent(val items: ImmutableList<ActorUi>) : NewGroupStatus
    data object Searching : NewGroupStatus
    data class Results(val items: ImmutableList<ActorUi>) : NewGroupStatus
    data object NoResults : NewGroupStatus
    data object Error : NewGroupStatus
}

sealed interface NewGroupEvent : UiEvent {
    data class RecipientToggled(val did: String) : NewGroupEvent
    data class RecipientRemoved(val did: String) : NewGroupEvent
    data object CreateTapped : NewGroupEvent
    data object RetryClicked : NewGroupEvent
}

sealed interface NewGroupEffect : UiEffect {
    data class ShowError(val error: ChatError) : NewGroupEffect
    data class GroupCreated(val convoId: String) : NewGroupEffect
}

internal const val GROUP_NAME_MAX_GRAPHEMES = 128
internal const val GROUP_NAME_COUNTER_THRESHOLD = 103 // 80% of 128
```
(`RecipientUi`, `ActorUi`, `ChatError`, `GROUP_MAX_MEMBERS` are reused. `canCreate` is a computed getter — keep it off the constructor so it never desyncs.)

**ViewModel** (`@HiltViewModel @Inject`, deps `ActorRepository`, `ChatRepository`, `SessionStateProvider`):
- `val nameFieldState = TextFieldState()` and `val queryFieldState = TextFieldState()` (both editor exceptions; KDoc them).
- `private val selfDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did`.
- Name validation collector:
```kotlin
snapshotFlow { nameFieldState.text.toString() }
    .onEach { text ->
        val count = GraphemeCounter.count(text.trim())
        setState { copy(nameGraphemeCount = count, isNameValid = count in 1..GROUP_NAME_MAX_GRAPHEMES) }
    }.launchIn(viewModelScope)
```
- Search pipeline: copy `AddGroupMembersViewModel`'s `combine(rawStatusFlow, uiState.map { it.selected }.distinctUntilChanged()) { … pickable(selectedDids) … }` verbatim, mapping into `NewGroupStatus`. `pickable(selectedDids) = filter { it.did != selfDid && it.did !in selectedDids }` (no existingDids — brand-new group).
- `recomputeCapacity()` → `setState { copy(atCapacity = selected.size >= GROUP_MAX_MEMBERS - 1) }`.
- `handleEvent`:
  - `RecipientToggled(did)` → **if `uiState.value.isSubmitting` return**; toggle (add the `ActorUi` from current `status` items as `RecipientUi`, gated `selected.size < GROUP_MAX_MEMBERS - 1`; or remove if already selected); `recomputeCapacity()`.
  - `RecipientRemoved(did)` → **if `isSubmitting` return**; remove; `recomputeCapacity()`.
  - `CreateTapped` → `submit()`.
  - `RetryClicked` → `retryTrigger.tryEmit(Unit)`.
- `submit()`:
```kotlin
if (!uiState.value.canCreate) return
val name = nameFieldState.text.toString().trim()
val dids = uiState.value.selected.map { it.did }
setState { copy(isSubmitting = true) }
viewModelScope.launch {
    chatRepository.createGroup(name, dids)
        .onSuccess { convoId -> sendEffect(NewGroupEffect.GroupCreated(convoId)) }
        .onFailure {
            setState { copy(isSubmitting = false) }
            sendEffect(NewGroupEffect.ShowError(it.toMemberMgmtError()))
        }
}
```
(`@OptIn(ExperimentalCoroutinesApi::class)` for `flatMapLatest`. Imports: `GraphemeCounter` from `net.kikin.nubecita.core.common.text`, `toMemberMgmtError` from `…impl.data`.)

- [ ] **Step 1: Failing VM tests** (`@ExtendWith(MainDispatcherExtension::class)`, real `FakeChatRepository` + MockK `ActorRepository` + `SessionStateProvider` SignedIn `did:self`; drive fields via `setTextAndPlaceCursorAtEnd` + `Snapshot.sendApplyNotifications()` + `runCurrent()`):
  - blank name → `isNameValid == false`, `canCreate == false`; 1-char name + ≥1 selected → `canCreate == true`.
  - name of exactly 128 graphemes → `isNameValid == true`; 129 → false. (`nameGraphemeCount` reflects the value; assert it crosses 103.)
  - blank query → Recent excludes self; `RecipientToggled` adds, toggling again removes; cap at `GROUP_MAX_MEMBERS - 1` (seed 48 selected, select one more → 49 + atCapacity true, a further toggle is dropped).
  - `CreateTapped` (valid) → `fakeChat.createGroupCalls.last() == (name to dids)` and effect `GroupCreated("convo:new")` (Turbine).
  - `createGroup` failure (`fakeChat.createGroupResult = Result.failure(XrpcError("MemberLimitReached","",400))`) → `ShowError(ChatError.GroupFull)`, `isSubmitting` back to false.
  - **input-lock:** gate `createGroupResult` behind a `CompletableDeferred` so `isSubmitting` stays true; assert `RecipientToggled` / `RecipientRemoved` / a 2nd `CreateTapped` are ignored (no extra `createGroupCalls`, `selected` unchanged) until the deferred completes.
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement** the contract + VM as above.
- [ ] **Step 4: Run → PASS** (`--tests "*NewGroupViewModelTest*"`); `compileProductionDebugKotlin spotlessCheck` green.
- [ ] **Step 5: Commit** — `feat(chats): new-group view model and contract` / `Refs: nubecita-hwix.6`.

---

## Task 5: `NewGroupScreen` (UI) + strings

**Files:**
- Create: `feature/chats/impl/.../NewGroupScreen.kt`
- Modify: `feature/chats/impl/src/main/res/values/strings.xml` (+ `values-b+es+419/`, `values-pt-rBR/`)

Read `AddGroupMembersScreen.kt` (the closest template) + `ChatScreen.kt` (effect collector with child-coroutine snackbar + `rememberUpdatedState`).

- [ ] **Step 1: Strings** (`<!-- New group (nubecita-hwix.6) -->`, all 3 locales):

| key | en | es | pt |
|---|---|---|---|
| new_group_title | New group | Grupo nuevo | Novo grupo |
| new_group_create | Create | Crear | Criar |
| new_group_name_placeholder | Group name | Nombre del grupo | Nome do grupo |
| new_group_name_counter | %1$d/%2$d | %1$d/%2$d | %1$d/%2$d |
| new_group_close | Close | Cerrar | Fechar |
| new_group_at_capacity | A group can have up to %1$d members. | Un grupo puede tener hasta %1$d miembros. | Um grupo pode ter até %1$d membros. |
| new_group_search_placeholder | Search for someone | Buscar a alguien | Buscar alguém |
| new_group_error_generic | Something went wrong. Try again. | Algo salió mal. Inténtalo de nuevo. | Algo deu errado. Tente de novo. |
| new_group_no_results | No people found | No se encontraron personas | Nenhuma pessoa encontrada |
| chats_new_message | New message | Mensaje nuevo | Nova mensagem |
| chats_new_group | New group | Grupo nuevo | Novo grupo |
| chats_fab_menu_open | New conversation | Nueva conversación | Nova conversa |
| chats_fab_menu_close | Close | Cerrar | Fechar |

(Reuse Slice B's `add_members_error_full` / `_follow_required` / `_permission` for the `ShowError` snackbar mapping — they already exist in all locales.)

- [ ] **Step 2: Stateless `NewGroupScreenContent(state, nameFieldState, queryFieldState, onEvent, onClose, modifier, snackbarHostState)`** — `@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)`:
  - `Scaffold(containerColor = surface, snackbarHost = …, topBar = { TopAppBar(title = { Text(new_group_title) }, navigationIcon = { IconButton(onClose){ NubecitaIcon(Close, new_group_close) } }, actions = { CreateAction(state, onEvent) }) })`.
  - `CreateAction`: a fixed-width `Box(contentAlignment = Center)` holding `TextButton(onClick = { onEvent(CreateTapped) }, enabled = state.canCreate) { Text(new_group_create, modifier = Modifier.alpha(if (state.isSubmitting) 0f else 1f)) }` and, when `state.isSubmitting`, a `CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)` overlaid. The `Text` keeps the Box width; the spinner sits centered over it.
  - Body: `Column(Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding))` containing an inner `Column(Modifier.fillMaxWidth().widthIn(max = 600.dp).align(Alignment.CenterHorizontally))` with, top→bottom:
    1. `OutlinedTextField(state = nameFieldState, lineLimits = TextFieldLineLimits.SingleLine, enabled = !state.isSubmitting, isError = state.nameGraphemeCount > GROUP_NAME_MAX_GRAPHEMES, placeholder = { Text(new_group_name_placeholder) }, supportingText = { if (state.nameGraphemeCount >= GROUP_NAME_COUNTER_THRESHOLD) Text(stringResource(R.string.new_group_name_counter, state.nameGraphemeCount, GROUP_NAME_MAX_GRAPHEMES), color = if (state.nameGraphemeCount > GROUP_NAME_MAX_GRAPHEMES) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))`.
    2. `if (state.selected.isNotEmpty()) RecipientChipsRow(selected = state.selected, onRemove = { onEvent(RecipientRemoved(it)) }, enabled = !state.isSubmitting, modifier = Modifier.padding(horizontal = 16.dp))`.
    3. `if (state.atCapacity) Text(stringResource(R.string.new_group_at_capacity, GROUP_MAX_MEMBERS), style = bodySmall, color = onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))`.
    4. `OutlinedTextField(state = queryFieldState, lineLimits = SingleLine, enabled = !state.isSubmitting, placeholder = { Text(new_group_search_placeholder) }, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp))`.
    5. results `when (val s = state.status)`: `Recent`/`Results` → `LazyColumn { items(s.items, key = { it.did }, contentType = { "recipient" }) { RecipientRow(actor = it, enabled = !state.atCapacity && !state.isSubmitting, respectCanMessage = false, onClick = { onEvent(RecipientToggled(it.did)) }, modifier = Modifier.fillMaxWidth()) } }`; `Searching` → centered spinner; `NoResults` → `new_group_no_results`; `Error` → text + retry `TextButton(onClick = { onEvent(RetryClicked) })`.
- [ ] **Step 3: Stateful `NewGroupScreen(viewModel, onCreated, onBack, modifier)`** — `@Suppress("ktlint:compose:parameter-naming")` for `onCreated` (past-tense, mirrors `AddGroupMembersScreen.onAdded`). Collect effects with `rememberUpdatedState(onCreated)`/`onBack`, pre-resolved error copy (`add_members_error_full`/`_follow_required`/`_permission`/`new_group_error_generic`), snackbar in a child coroutine; `GroupCreated(convoId)` → `currentOnCreated(convoId)`. Pass `viewModel.nameFieldState` + `viewModel.queryFieldState`.
- [ ] **Step 4: Previews** — `NubecitaCanvasPreviewTheme { NewGroupScreenContent(...) }`: empty; with name + 2 chips + results; at-capacity; name counter near limit (`nameGraphemeCount = 120`); `isSubmitting = true`. Plus one wider preview (`@Preview(widthDp = 840)`) to show the 600.dp body constraint. (Seed Task 7 screenshots; no screenshot tests here.)
- [ ] **Step 5: Verify** — `:feature:chats:impl:compileProductionDebugKotlin spotlessCheck :feature:chats:impl:lintProductionDebug` green (lint catches missing translations).
- [ ] **Step 6: Commit** — `feat(chats): new-group creation screen` / `Refs: nubecita-hwix.6`.

---

## Task 6: M3 Expressive FAB menu + new-group nav wiring

**Files:**
- Modify: `feature/chats/impl/.../ChatsScreenContent.kt`, `ChatsScreen.kt`, `di/ChatsNavigationModule.kt`

(Merged with nav wiring because adding `onNewGroup` to `ChatsScreen`/`ChatsScreenContent` breaks the nav-module call site until it's updated — the module must compile in one commit.)

- [ ] **Step 1: `ChatsScreenContent` FAB → `FloatingActionButtonMenu`.** Add `onNewGroup: () -> Unit` param. Where the FAB is rendered today (`FloatingActionButton(onClick = onNewChat) { NubecitaIcon(Edit, …) }`, gated by `!inSelection` + not-`NotEnrolled`), replace with the M3 Expressive menu:

```kotlin
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
// inside the same gating condition:
var fabMenuExpanded by rememberSaveable { mutableStateOf(false) }
FloatingActionButtonMenu(
    expanded = fabMenuExpanded,
    button = {
        ToggleFloatingActionButton(
            checked = fabMenuExpanded,
            onCheckedChange = { fabMenuExpanded = it },
        ) {
            NubecitaIcon(
                name = if (fabMenuExpanded) NubecitaIconName.Close else NubecitaIconName.Edit,
                contentDescription = stringResource(
                    if (fabMenuExpanded) R.string.chats_fab_menu_close else R.string.chats_fab_menu_open,
                ),
                filled = true,
            )
        }
    },
) {
    FloatingActionButtonMenuItem(
        onClick = { fabMenuExpanded = false; onNewChat() },
        icon = { NubecitaIcon(NubecitaIconName.Edit, contentDescription = null) },
        text = { Text(stringResource(R.string.chats_new_message)) },
    )
    FloatingActionButtonMenuItem(
        onClick = { fabMenuExpanded = false; onNewGroup() },
        icon = { NubecitaIcon(NubecitaIconName.PersonAdd, contentDescription = null) },
        text = { Text(stringResource(R.string.chats_new_group)) },
    )
}
```
Imports: `androidx.compose.material3.{FloatingActionButtonMenu, FloatingActionButtonMenuItem, ToggleFloatingActionButton, ExperimentalMaterial3ExpressiveApi}`, `rememberSaveable`. Confirm the exact param names/signatures against material3 1.5.0-alpha22 at build time (the toggle FAB content slot may receive an animation-progress lambda — adapt the icon swap accordingly; a simple `if (checked)` swap is acceptable). `NubecitaIconName.PersonAdd` is the closest existing glyph for "new group" — do NOT add a new font glyph.

- [ ] **Step 2: `ChatsScreen`** — add `onNewGroup: () -> Unit` param; pass it to `ChatsScreenContent(onNewGroup = onNewGroup, …)`.

- [ ] **Step 3: `ChatsNavigationModule`** — in the `entry<Chats>` block, pass `onNewGroup = { navState.add(NewGroup) }` to `ChatsScreen` (alongside the existing `onNewChat = { navState.add(NewChat) }`). Add `entry<NewGroup>`:

```kotlin
entry<NewGroup> {
    val navState = LocalMainShellNavState.current
    NewGroupScreen(
        viewModel = hiltViewModel(),
        onCreated = { convoId -> navState.replaceTop(Chat(convoId = convoId)) },
        onBack = { navState.removeLast() },
    )
}
```
Imports: `net.kikin.nubecita.feature.chats.api.NewGroup`, `net.kikin.nubecita.feature.chats.impl.NewGroupScreen`. (`hiltViewModel`, `Chat`, `LocalMainShellNavState` already imported.)

- [ ] **Step 4: Verify** — `./gradlew :feature:chats:impl:compileProductionDebugKotlin spotlessCheck :feature:chats:impl:lintProductionDebug :app:assembleDebug` → all green (DI graph + the new entry resolve; the FAB-menu API compiles).
- [ ] **Step 5: Commit** — `feat(chats): FAB menu with new-message and new-group actions` / `Refs: nubecita-hwix.6`.

---

## Task 7: Screenshots + full gate + compose-expert review

**Files:**
- Create: `feature/chats/impl/src/screenshotTest/.../NewGroupScreenContentScreenshotTest.kt`
- Possibly create/modify: a `ChatsScreenContent` screenshot showing the FAB menu expanded (add a baseline if a `ChatsScreenContentScreenshotTest` exists; otherwise rely on the NewGroup baselines + a preview).

- [ ] **Step 1: Screenshot tests** (mirror `AddGroupMembersScreenContentScreenshotTest` — `@PreviewTest` + light/dark `@Preview`, `NubecitaCanvasPreviewTheme`): NewGroup empty, name+chips+results, at-capacity, name-counter-near-limit, and `isSubmitting` (spinner + disabled inputs). If feasible, a `widthDp = 840` baseline for the 600.dp body constraint. For the FAB menu, add an expanded-state baseline of `ChatsScreenContent` if that test file exists; else cover it via a co-located preview only and note the gap.
- [ ] **Step 2: Generate + validate** — `./gradlew :feature:chats:impl:updateProductionDebugScreenshotTest` then `:validateProductionDebugScreenshotTest`. Stage ONLY the new PNGs; revert pre-existing Mac-vs-CI drift (`git checkout -- …`). The PR carries `update-baselines` so CI regenerates.
- [ ] **Step 3: Full gate** — `./gradlew spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug :feature:chats:impl:testProductionDebugUnitTest :feature:profile:impl:testProductionDebugUnitTest :app:assembleDebug` → green.
- [ ] **Step 4: compose-expert review** — UI added (FAB menu, NewGroupScreen, RecipientChipsRow). Run the compose-expert skill (Review Mode) over the new/changed composables; address findings.
- [ ] **Step 5: Commit** — `test(chats): new-group + FAB-menu screenshots` / `Refs: nubecita-hwix.6`. Then mark PR #564 ready for review.

---

## Self-Review

**Spec coverage:** createGroup repo + 5 fakes → T1; NewGroup NavKey → T2; RecipientChipsRow extraction → T3; NewGroup VM (name validation, counter threshold, combine/pickable, cap 49, canCreate, submit, input-lock) → T4; NewGroupScreen (600.dp body, jitter-free Create, proactive counter, enabled=!isSubmitting) → T5; M3 FAB menu + onNewGroup + entry<NewGroup> replaceTop → T6; strings → T5/T6; screenshots + gate + compose-expert → T7. Covered.

**Type consistency:** `NewGroupViewState`/`NewGroupStatus`/`NewGroupEvent`/`NewGroupEffect`, `GroupCreated(convoId)`, `createGroup(name, dids): Result<String>`, `RecipientChipsRow(selected, onRemove, modifier, enabled)`, `GROUP_NAME_MAX_GRAPHEMES`/`GROUP_NAME_COUNTER_THRESHOLD`, `GROUP_MAX_MEMBERS - 1` cap, `toMemberMgmtError`, `RecipientUi`/`ActorUi` — consistent across tasks and with the merged Slice B symbols.

**Verify-at-build notes:** the M3 Expressive `FloatingActionButtonMenu`/`ToggleFloatingActionButton`/`FloatingActionButtonMenuItem` exact param shapes (1.5.0-alpha22) — the toggle button's content lambda may expose animation progress; adapt the icon swap. `NubecitaIconName.PersonAdd` used for "new group" (no group glyph; don't add one).
