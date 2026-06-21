# Group chat Phase 3b — member management (add + remove) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a group **owner** add members (recipient-chip picker) and remove members (confirm + optimistic) from the existing group-details screen.

**Architecture:** Extend `ChatRepository` with `addMembers`/`removeMembers` over `chat.bsky.group.GroupService` (atproto 9.4.0, already on `main`). A new adaptive `AddGroupMembers` sub-route forks the New-Chat search/recent pipeline into a multi-select chip picker. Group-details gains `viewerRole` gating and an owner-only remove ⋮. Cross-screen success flows through a new one-shot result API on `MainShellNavState` (the Nav3 analog of `SavedStateHandle` results).

**Tech Stack:** Kotlin 2.3, Jetpack Compose + M3 Expressive, MVI (`MviViewModel`), Hilt assisted injection, Navigation 3, `kotlinx.collections.immutable`, JUnit5 + Turbine + MockK, Compose screenshot tests.

**Spec:** `docs/superpowers/specs/2026-06-21-group-chat-phase3b-member-management-design.md`. **bd:** `nubecita-hwix.5`. **Branch:** `feat/nubecita-hwix.5-group-member-management` (PR #562). All commits use lowercase-leading Conventional subjects + `Refs: nubecita-hwix.5`.

---

## Conventions for every task

- TDD where there's logic (repository, VMs, nav state): write the failing test first, watch it fail, implement, watch it pass, commit. UI tasks lead with the composable + previews/screenshots.
- NEVER `--no-verify`. commitlint rejects sentence/start/pascal-case subjects — use lowercase-leading. If `git commit` fails on signing, `git config --local commit.gpgsign false` then retry. Run `pre-commit run --files <changed>` first, `git add -A`, then commit; verify with `git log --oneline -1`.
- No `!!`. `ImmutableList` for list state. PII (DIDs/handles) never logged remotely — repos log `javaClass.name` only.
- After editing a shared interface, grep all implementors and update every fake (the `ChatRepository` change touches 5 fakes).

---

## Task 1: One-shot nav result API on `MainShellNavState`

**Files:**
- Modify: `core/common/src/main/kotlin/net/kikin/nubecita/core/common/navigation/MainShellNavState.kt`
- Test: `core/common/src/test/kotlin/net/kikin/nubecita/core/common/navigation/MainShellNavStateTest.kt`

- [ ] **Step 1: Write the failing tests** (append to `MainShellNavStateTest`):

```kotlin
@Test
fun `setResult then consumeResult returns the value once`() {
    val state = newState() // existing test helper that builds a MainShellNavState
    state.setResult("k", 3)
    assertEquals(3, state.consumeResult("k"))
    assertNull(state.consumeResult("k")) // one-shot: gone after first consume
}

@Test
fun `peekResult reads without clearing`() {
    val state = newState()
    state.setResult("k", 7)
    assertEquals(7, state.peekResult("k"))
    assertEquals(7, state.peekResult("k")) // still there
    assertEquals(7, state.consumeResult("k"))
}

@Test
fun `setResult overwrites and keys are independent`() {
    val state = newState()
    state.setResult("a", 1)
    state.setResult("a", 2)
    state.setResult("b", 9)
    assertEquals(2, state.consumeResult("a"))
    assertEquals(9, state.consumeResult("b"))
}
```

(Reuse the existing test's construction pattern — `MainShellNavStateTest` already builds instances directly via the public constructor with `mutableStateOf(startRoute)` + `backStacks`. If there's no `newState()` helper, inline that construction as the other tests do.)

- [ ] **Step 2: Run to verify they fail** — `./gradlew :core:common:testDebugUnitTest --tests "*MainShellNavStateTest*"` → FAIL (unresolved `setResult`).

- [ ] **Step 3: Implement the result store** in `MainShellNavState`:

```kotlin
import androidx.compose.runtime.mutableStateMapOf
// ...
// One-shot results handed from a popped sub-route back to the destination beneath it
// (the Nav3 analog of Nav2's SavedStateHandle results). Snapshot-backed so a consumer
// that reads via peekResult recomposes when a value appears. NOT an event bus: a value
// is keyed for a specific consumer and read exactly once.
private val results = mutableStateMapOf<String, Any?>()

/** Stash a one-shot [value] for a returning consumer keyed by [key]; overwrites any prior. */
fun setResult(key: String, value: Any?) { results[key] = value }

/** Snapshot-observed read WITHOUT clearing — lets a consumer recompose when a result appears. */
fun peekResult(key: String): Any? = results[key]

/** Read and clear a one-shot result; null if none. */
fun consumeResult(key: String): Any? = results.remove(key)
```

- [ ] **Step 4: Run to verify pass** — same command → PASS.

- [ ] **Step 5: Commit**

```bash
git add core/common/src/main/kotlin/net/kikin/nubecita/core/common/navigation/MainShellNavState.kt \
        core/common/src/test/kotlin/net/kikin/nubecita/core/common/navigation/MainShellNavStateTest.kt
git commit -m "feat(navigation): one-shot result channel on MainShellNavState

Refs: nubecita-hwix.5"
```

---

## Task 2: Member-management error variants + mapper

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatContract.kt` (the `ChatError` sealed interface)
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMapping.kt`
- Test: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/MemberMgmtErrorMappingTest.kt`

**Why a dedicated mapper:** the existing `toChatError()` already maps the `notfollowedbysender` marker to `MessagesDisabled` (the DM-start meaning). In the add-member context that same wire code means "you can only add people who follow you" — a different message — so member ops need their own marker pass that runs *before* delegating to `toChatError()`.

- [ ] **Step 1: Write the failing test:**

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.feature.chats.impl.ChatError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MemberMgmtErrorMappingTest {
    private fun xrpc(msg: String) = XrpcError(error = "X", message = msg)

    @Test fun `member limit reached maps to GroupFull`() =
        assertEquals(ChatError.GroupFull, xrpc("MemberLimitReached: too many").toMemberMgmtError())

    @Test fun `not followed by sender maps to FollowRequiredToAdd`() =
        assertEquals(ChatError.FollowRequiredToAdd, xrpc("NotFollowedBySender").toMemberMgmtError())

    @Test fun `insufficient role maps to InsufficientPermission`() =
        assertEquals(ChatError.InsufficientPermission, xrpc("InsufficientRole: nope").toMemberMgmtError())

    @Test fun `unrecognised falls through to toChatError Unknown`() {
        val result = xrpc("SomethingElse").toMemberMgmtError()
        assert(result is ChatError.Unknown)
    }
}
```

> Confirm `XrpcError`'s constructor params at implementation time (the existing `ChatsErrorMapping` tests, if any, show the shape; otherwise check the SDK). Adjust the `xrpc(...)` helper to match.

- [ ] **Step 2: Run → FAIL** (unresolved `GroupFull` / `toMemberMgmtError`): `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*MemberMgmtErrorMappingTest*"`.

- [ ] **Step 3a: Add `ChatError` variants** in `ChatContract.kt` (inside `sealed interface ChatError`):

```kotlin
    /** Group is at the 50-member cap. */
    data object GroupFull : ChatError

    /** Can only add people who follow the actor (NotFollowedBySender, add-member context). */
    data object FollowRequiredToAdd : ChatError

    /** Viewer lacks the role to manage members (InsufficientRole). */
    data object InsufficientPermission : ChatError
```

- [ ] **Step 3b: Add the mapper** in `ChatsErrorMapping.kt`:

```kotlin
private const val MEMBER_LIMIT_MARKER = "memberlimitreached"
private const val INSUFFICIENT_ROLE_MARKER = "insufficientrole"

/**
 * Map a `chat.bsky.group.addMembers` / `removeMembers` failure to a [ChatError].
 * Recognises the member-management wire markers (which carry different UX than the
 * DM-start markers) first, then delegates everything else to [toChatError].
 * Note `notfollowedbysender` means "must follow you to be added" here, NOT MessagesDisabled.
 */
fun Throwable.toMemberMgmtError(): ChatError {
    if (this is XrpcError) {
        val msg = message.orEmpty().lowercase(Locale.ROOT)
        when {
            MEMBER_LIMIT_MARKER in msg -> return ChatError.GroupFull
            NOT_FOLLOWED_BY_SENDER_MARKER in msg -> return ChatError.FollowRequiredToAdd
            INSUFFICIENT_ROLE_MARKER in msg -> return ChatError.InsufficientPermission
        }
    }
    return toChatError()
}
```

- [ ] **Step 4: Run → PASS.**

- [ ] **Step 5: Commit** — `feat(chats): member-management error variants and mapper` / `Refs: nubecita-hwix.5`.

---

## Task 3: `ChatRepository.addMembers` / `removeMembers` (+ all 5 fakes)

**Files:**
- Modify: `feature/chats/impl/.../data/ChatRepository.kt` (interface)
- Modify: `feature/chats/impl/.../data/DefaultChatRepository.kt`
- Modify fakes: `src/test/.../FakeChatRepository.kt`, `src/androidTest/.../FakeChatRepository.kt`, `src/bench/.../data/BenchFakeChatRepository.kt`, and any others from `grep -rl ": ChatRepository" feature/chats/impl/src` (Phase 3a noted 5 sites).
- Test: `feature/chats/impl/src/test/.../data/DefaultChatRepositoryMemberMgmtTest.kt` (if the repo has an existing unit test harness; otherwise drive via the VM tests in later tasks and assert through the fake).

- [ ] **Step 1: Add interface methods** in `ChatRepository.kt`:

```kotlin
/** Add [dids] to group [convoId]. Added with pending status; invitees must accept. */
suspend fun addMembers(convoId: String, dids: List<String>): Result<Unit>

/** Permanently remove [dids] from group [convoId]. */
suspend fun removeMembers(convoId: String, dids: List<String>): Result<Unit>
```

- [ ] **Step 2: Implement in `DefaultChatRepository`** (mirror `leaveConvo`'s `convoMutation` shape, but the call goes on `GroupService`, and failures map via `toMemberMgmtError` — so use a small local `runCatching`/`withContext` rather than the `ConvoService`-typed `convoMutation`):

```kotlin
import io.github.kikin81.atproto.chat.bsky.group.AddMembersRequest
import io.github.kikin81.atproto.chat.bsky.group.GroupService
import io.github.kikin81.atproto.chat.bsky.group.RemoveMembersRequest
// Did is already imported.

override suspend fun addMembers(convoId: String, dids: List<String>): Result<Unit> =
    groupMutation("addMembers") { service ->
        service.addMembers(AddMembersRequest(convoId = convoId, members = dids.map { Did(it) }))
    }

override suspend fun removeMembers(convoId: String, dids: List<String>): Result<Unit> =
    groupMutation("removeMembers") { service ->
        service.removeMembers(RemoveMembersRequest(convoId = convoId, members = dids.map { Did(it) }))
    }

private suspend inline fun groupMutation(
    op: String,
    crossinline block: suspend (GroupService) -> Unit,
): Result<Unit> =
    withContext(dispatcher) {
        runCatching {
            block(GroupService(xrpcClientProvider.authenticated()))
        }.onFailure { throwable ->
            if (throwable is CancellationException) throw throwable
            Timber.tag(TAG).w(throwable, "%s failed: %s", op, throwable.javaClass.name)
        }
    }
```

> Note: the repository returns the raw `Result<Unit>` (mapping to `ChatError` happens in the VM via `toMemberMgmtError`, matching how the VMs already call `.toChatError()` on failures). Confirm `AddMembersRequest`/`RemoveMembersRequest` field names (`convoId`, `members`) and that `addMembers`/`removeMembers` are `suspend` on `GroupService` — verified present in `models-jvm-9.4.0.jar`.

- [ ] **Step 3: Update all fakes.** Each `FakeChatRepository` gets settable results + call capture:

```kotlin
var addMembersResult: Result<Unit> = Result.success(Unit)
var removeMembersResult: Result<Unit> = Result.success(Unit)
val addMembersCalls = mutableListOf<Pair<String, List<String>>>()
val removeMembersCalls = mutableListOf<Pair<String, List<String>>>()

override suspend fun addMembers(convoId: String, dids: List<String>): Result<Unit> {
    addMembersCalls += convoId to dids
    return addMembersResult
}
override suspend fun removeMembers(convoId: String, dids: List<String>): Result<Unit> {
    removeMembersCalls += convoId to dids
    return removeMembersResult
}
```

For `BenchFakeChatRepository`, return `Result.success(Unit)` (no-op; bench is offline). For the androidTest fake, mirror the unit-test fake (no call capture needed unless a test asserts it).

- [ ] **Step 4: Verify compile + existing tests** — `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:testProductionDebugUnitTest` → PASS (no implementor left unimplemented).

- [ ] **Step 5: Commit** — `feat(chats): repository addMembers/removeMembers via GroupService` / `Refs: nubecita-hwix.5`.

---

## Task 4: `AddGroupMembers` NavKey

**Files:**
- Modify: `feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt`
- Test: `feature/chats/impl/src/test/.../AddGroupMembersNavKeyTest.kt`

- [ ] **Step 1: Failing test** (mirror `GroupDetailsNavKeyTest`):

```kotlin
class AddGroupMembersNavKeyTest {
    @Test fun `carries convoId and is a NavKey`() {
        val key = AddGroupMembers(convoId = "c1")
        assertEquals("c1", key.convoId)
        assertTrue(key is NavKey)
    }
}
```

- [ ] **Step 2: Run → FAIL** (unresolved `AddGroupMembers`).

- [ ] **Step 3: Add the NavKey** in `Chats.kt`:

```kotlin
/**
 * Recipient picker for adding members to a group convo. A `@MainShell` sub-route
 * pushed from the group-details "Add members" action; tagged `adaptiveDialog()`
 * (full-screen Compact / centered Dialog Medium-Expanded). Carries the [convoId]
 * whose membership it extends.
 */
@Serializable
data class AddGroupMembers(val convoId: String) : NavKey
```

- [ ] **Step 4: Run → PASS** (`:feature:chats:impl:testProductionDebugUnitTest --tests "*AddGroupMembersNavKeyTest*"`).

- [ ] **Step 5: Commit** — `feat(chats): add the add-group-members NavKey` / `Refs: nubecita-hwix.5`.

---

## Task 5: `AddGroupMembersContract` + `AddGroupMembersViewModel`

**Files:**
- Create: `feature/chats/impl/.../AddGroupMembersContract.kt`
- Create: `feature/chats/impl/.../AddGroupMembersViewModel.kt`
- Test: `feature/chats/impl/src/test/.../AddGroupMembersViewModelTest.kt`

**Contract** (forks `NewChatContract`; adds selection + submission):

```kotlin
@Immutable
data class RecipientUi(val did: String, val handle: String, val displayName: String?, val avatarUrl: String?)

@Immutable
data class AddGroupMembersViewState(
    val selected: ImmutableList<RecipientUi> = persistentListOf(),
    val atCapacity: Boolean = false,
    val isSubmitting: Boolean = false,
    val status: AddMembersStatus = AddMembersStatus.Recent(persistentListOf()),
) : UiState

sealed interface AddMembersStatus {
    data class Recent(val items: ImmutableList<ActorUi>) : AddMembersStatus
    data object Searching : AddMembersStatus
    data class Results(val items: ImmutableList<ActorUi>) : AddMembersStatus
    data object NoResults : AddMembersStatus
    data object Error : AddMembersStatus
}

sealed interface AddMembersEvent : UiEvent {
    data class RecipientToggled(val did: String) : AddMembersEvent   // tap a result row
    data class RecipientRemoved(val did: String) : AddMembersEvent   // chip ✕
    data object AddTapped : AddMembersEvent
    data object RetryClicked : AddMembersEvent
}

sealed interface AddMembersEffect : UiEffect {
    data class ShowError(val error: ChatError) : AddMembersEffect
    data object MembersAdded : AddMembersEffect   // screen does setResult + pop
}
```

**ViewModel** (assisted-injected with the route; reuses the New-Chat merge/debounce pipeline):

```kotlin
@HiltViewModel(assistedFactory = AddGroupMembersViewModel.Factory::class)
class AddGroupMembersViewModel @AssistedInject constructor(
    @Assisted private val route: AddGroupMembers,
    private val actorRepository: ActorRepository,
    private val chatRepository: ChatRepository,
    sessionStateProvider: SessionStateProvider,
) : MviViewModel<AddGroupMembersViewState, AddMembersEvent, AddMembersEffect>(AddGroupMembersViewState()) {

    @AssistedFactory interface Factory { fun create(route: AddGroupMembers): AddGroupMembersViewModel }

    val queryFieldState: TextFieldState = TextFieldState()
    private val convoId = route.convoId
    private val selfDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
    private var existingDids: Set<String> = emptySet()
    private var memberCount = 0
    private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    init {
        viewModelScope.launch {
            // Roster load for exclusion + cap headroom (best-effort; on failure the picker
            // still works, just without exclusion).
            chatRepository.getConvoMembers(convoId).onSuccess { page ->
                existingDids = page.members.map { it.did }.toSet()
                memberCount = page.members.size
                recomputeCapacity()
            }
        }
        // search/recent pipeline identical to NewChatViewModel, but results are filtered to
        // exclude self AND existing members, and mapped through the same status sum.
        merge(
            snapshotFlow { queryFieldState.text.toString() },
            retryTrigger.map { queryFieldState.text.toString() },
        ).flatMapLatest { raw -> /* see NewChatViewModel; reuse verbatim with the added exclusion */ }
            .onEach { status -> setState { copy(status = status.excludeExisting()) } }
            .launchIn(viewModelScope)
    }

    override fun handleEvent(event: AddMembersEvent) {
        when (event) {
            is AddMembersEvent.RecipientToggled -> toggle(event.did)
            is AddMembersEvent.RecipientRemoved -> deselect(event.did)
            AddMembersEvent.AddTapped -> submit()
            AddMembersEvent.RetryClicked -> retryTrigger.tryEmit(Unit)
        }
    }

    private fun submit() {
        val dids = uiState.value.selected.map { it.did }
        if (dids.isEmpty() || uiState.value.isSubmitting) return
        setState { copy(isSubmitting = true) }
        viewModelScope.launch {
            chatRepository.addMembers(convoId, dids)
                .onSuccess { sendEffect(AddMembersEffect.MembersAdded) }
                .onFailure {
                    setState { copy(isSubmitting = false) }
                    sendEffect(AddMembersEffect.ShowError(it.toMemberMgmtError()))
                }
        }
    }
    // toggle/deselect update `selected` + recomputeCapacity(); capacity = memberCount + selected.size >= GROUP_MAX_MEMBERS
    // (GROUP_MAX_MEMBERS is the existing const from GroupDetailsContract).
}
```

- [ ] **Step 1: Write failing VM tests** (use `@ExtendWith(MainDispatcherExtension::class)`, a `FakeChatRepository`, and a fake/mock `ActorRepository`; drive `queryFieldState` + `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()` per the editor-VM testing rule):
  - blank query → Recent excludes self + existing members
  - `RecipientToggled` adds to `selected`; toggling again removes it
  - `RecipientRemoved` removes the chip
  - at `memberCount + selected.size == GROUP_MAX_MEMBERS` → `atCapacity == true`
  - `AddTapped` with selection → `chatRepository.addMembersCalls` records `(convoId, dids)`, emits `MembersAdded`
  - `addMembers` failure → `ShowError(GroupFull)` for a `MemberLimitReached` XrpcError, `isSubmitting` back to false

- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement the contract + VM** as above (lift the `flatMapLatest` body from `NewChatViewModel`, adding the `excludeExisting()` filter that drops `selfDid` and `existingDids`).
- [ ] **Step 4: Run → PASS** (`--tests "*AddGroupMembersViewModelTest*"`).
- [ ] **Step 5: Commit** — `feat(chats): add-group-members view model and contract` / `Refs: nubecita-hwix.5`.

---

## Task 6: `AddGroupMembersScreen` (recipient-chip picker UI) + strings

**Files:**
- Create: `feature/chats/impl/.../AddGroupMembersScreen.kt` (stateful + stateless content + previews)
- Modify: `feature/chats/impl/src/main/res/values/strings.xml` (+ `values-b+es+419/`, `values-pt-rBR/`)
- Reuse: `ui/RecipientRow.kt` (results rows), `NubecitaAvatar`, `AvatarGroup` not needed here.

- [ ] **Step 1: Add strings** (en, then es/pt) under an `<!-- Add group members (nubecita-hwix.5) -->` block:

| key | en | es | pt |
|---|---|---|---|
| `add_members_title` | Add members | Agregar miembros | Adicionar membros |
| `add_members_action` | Add | Agregar | Adicionar |
| `add_members_search_placeholder` | Search for someone | Buscar a alguien | Buscar alguém |
| `add_members_remove_chip` | Remove | Quitar | Remover |
| `add_members_at_capacity` | This group is full (50 max). | El grupo está lleno (máx. 50). | O grupo está cheio (máx. 50). |
| `add_members_error_full` | This group is full. | El grupo está lleno. | O grupo está cheio. |
| `add_members_error_follow_required` | You can only add people who follow you. | Solo puedes agregar a personas que te siguen. | Você só pode adicionar pessoas que te seguem. |
| `add_members_error_permission` | You don't have permission to do that. | No tienes permiso para hacer eso. | Você não tem permissão para isso. |
| `add_members_error_generic` | Something went wrong. Try again. | Algo salió mal. Inténtalo de nuevo. | Algo deu errado. Tente de novo. |
| `add_members_no_results` | No people found | No se encontraron personas | Nenhuma pessoa encontrada |
| `add_members_close` | Close | Cerrar | Fechar |

- [ ] **Step 2: Stateless `AddGroupMembersScreenContent(state, queryFieldState, onEvent, onClose, snackbarHostState)`** — mirror `ChatSettingsScreenContent` chrome:
  - `Scaffold(containerColor = surface)` + `TopAppBar` (close icon = `NubecitaIconName.Close`, title `add_members_title`, `actions = { TextButton(onClick = { onEvent(AddTapped) }, enabled = state.selected.isNotEmpty() && !state.isSubmitting) { Text(add_members_action) } }`).
  - Body `Column(Modifier.padding(innerPadding))`:
    - search `OutlinedTextField`/`TextField` bound to `queryFieldState` (use the `state =` overload).
    - a `FlowRow` (`androidx.compose.foundation.layout.FlowRow`) of selected chips — for each `RecipientUi`, an `InputChip(selected = true, onClick = { onEvent(RecipientRemoved(did)) }, label = { Text(displayName ?: handle) }, avatar = { NubecitaAvatar(model = avatarUrl, contentDescription = null, size = InputChipDefaults.AvatarSize, fallback = avatarFallbackFor(did, handle, displayName)) }, trailingIcon = { NubecitaIcon(NubecitaIconName.Close, contentDescription = stringResource(R.string.add_members_remove_chip)) })`. Hidden when `selected.isEmpty()`.
    - an at-capacity notice `Text(add_members_at_capacity)` when `state.atCapacity`.
    - results `LazyColumn` over `state.status` (`Recent`/`Results` → `items(items, key = { it.did }) { RecipientRow(actor = it, onClick = { onEvent(RecipientToggled(it.did)) }) }`; `Searching` → centered spinner; `NoResults` → `add_members_no_results`; `Error` → retry body calling `RetryClicked`). At capacity, unselected rows are disabled (wrap `RecipientRow` enable on `atCapacity`).
- [ ] **Step 3: Stateful `AddGroupMembersScreen(viewModel, onAdded, onBack)`** — collects effects: `ShowError` → snackbar (pre-resolve copy per `ChatError` variant: `GroupFull`→`add_members_error_full`, `FollowRequiredToAdd`→`add_members_error_follow_required`, `InsufficientPermission`→`add_members_error_permission`, else `add_members_error_generic`); `MembersAdded` → `onAdded(selected.size)`. Passes `viewModel.queryFieldState`.
- [ ] **Step 4: Previews** — `NubecitaCanvasPreviewTheme { AddGroupMembersScreenContent(...) }` for: empty/recent, with 2 selected chips + results, at-capacity. (Seed Task 11 screenshots.)
- [ ] **Step 5: Verify** — `./gradlew :feature:chats:impl:compileProductionDebugKotlin spotlessCheck :feature:chats:impl:lintProductionDebug` → PASS.
- [ ] **Step 6: Commit** — `feat(chats): add-group-members recipient-chip picker screen` / `Refs: nubecita-hwix.5`.

---

## Task 7: Group-details owner-gating + add/remove events (contract + VM)

**Files:**
- Modify: `feature/chats/impl/.../GroupDetailsContract.kt`
- Modify: `feature/chats/impl/.../GroupDetailsViewModel.kt`
- Test: `feature/chats/impl/src/test/.../GroupDetailsViewModelTest.kt` (extend existing)

- [ ] **Step 1: Failing VM tests:**
  - after load, `state.viewerRole == GroupRole.Owner` when the `isViewer` member is owner; `Member` otherwise
  - `AddMembersTapped` → `NavigateTo(AddGroupMembers(convoId))` effect
  - `RemoveMember(did)` → optimistic: member gone from `Loaded.members`, `memberCount == members.size`; `removeMembersCalls` records `(convoId, [did])`
  - `removeMembers` failure → member re-inserted at prior index, `memberCount` restored, `ShowError`
  - two interleaved `RemoveMember` calls → final `memberCount == members.size` (no stale-counter drift); second call for an in-flight DID is dropped

- [ ] **Step 2: Run → FAIL.**

- [ ] **Step 3: Contract** — add to `GroupDetailsViewState`: `val viewerRole: GroupRole? = null`. Add events:

```kotlin
data object AddMembersTapped : GroupDetailsEvent
data class RemoveMember(val did: String) : GroupDetailsEvent
```

(`GroupDetailsEffect.NavigateTo(key: NavKey)` already exists — reuse it for the add route.)

- [ ] **Step 4: VM** — in `loadMembers()`, derive role and recompute count from the list:

```kotlin
val members = accumulated.toImmutableList()
val viewerRole = members.firstOrNull { it.isViewer }?.role
setState {
    copy(
        viewerRole = viewerRole,
        status = GroupDetailsLoadStatus.Loaded(members = members, memberCount = members.size),
    )
}
```

Add handlers (mirror `onToggleFollow`'s guard/rollback shape):

```kotlin
private val inFlightRemovals = mutableSetOf<String>()

private fun onAddMembersTapped() {
    sendEffect(GroupDetailsEffect.NavigateTo(AddGroupMembers(convoId)))
}

private fun onRemoveMember(did: String) {
    val loaded = uiState.value.status as? GroupDetailsLoadStatus.Loaded ?: return
    if (did in inFlightRemovals) return
    val index = loaded.members.indexOfFirst { it.did == did }
    if (index < 0) return
    val removed = loaded.members[index]
    inFlightRemovals += did
    setState { withMembers(loaded.members.toMutableList().apply { removeAt(index) }) }
    viewModelScope.launch {
        try {
            repository.removeMembers(convoId, listOf(did))
                .onFailure {
                    setState { withMembers(currentMembers().toMutableList().apply { add(index.coerceAtMost(size), removed) }) }
                    sendEffect(GroupDetailsEffect.ShowError(it.toMemberMgmtError()))
                }
        } finally {
            inFlightRemovals -= did
        }
    }
}

// helper: rebuild Loaded with memberCount derived from the list size (never a separate decrement).
private fun GroupDetailsViewState.withMembers(newMembers: List<GroupMemberUi>): GroupDetailsViewState {
    val l = status as? GroupDetailsLoadStatus.Loaded ?: return this
    val imm = newMembers.toImmutableList()
    return copy(status = l.copy(members = imm, memberCount = imm.size))
}
```

Wire both into `handleEvent`. Import `AddGroupMembers` + `toMemberMgmtError`.

- [ ] **Step 5: Run → PASS.**
- [ ] **Step 6: Commit** — `feat(chats): group-details owner gating, add-nav and optimistic remove` / `Refs: nubecita-hwix.5`.

---

## Task 8: Group-details UI — Add button, remove ⋮ + confirm, consume add result

**Files:**
- Modify: `feature/chats/impl/.../GroupDetailsScreen.kt`
- Modify: `feature/chats/impl/.../ui/GroupMemberRow.kt`
- Modify: strings `values/`, `values-b+es+419/`, `values-pt-rBR/`

- [ ] **Step 1: Strings** (`<!-- Group member management (nubecita-hwix.5) -->`):

| key | en | es | pt |
|---|---|---|---|
| `group_details_add_members` | Add members | Agregar miembros | Adicionar membros |
| `group_details_remove_member` | Remove from group | Quitar del grupo | Remover do grupo |
| `group_details_remove_confirm_title` | Remove member? | ¿Quitar miembro? | Remover membro? |
| `group_details_remove_confirm_body` | Remove %1$s from this group? | ¿Quitar a %1$s de este grupo? | Remover %1$s deste grupo? |
| `group_details_remove_confirm_action` | Remove | Quitar | Remover |
| `group_details_cancel` | Cancel | Cancelar | Cancelar |
| `group_details_member_options` | Member options | Opciones del miembro | Opções do membro |
| `group_details_invites_sent` | Invitations sent | Invitaciones enviadas | Convites enviados |
| `group_details_remove_error` | Couldn't remove that member. Try again. | No se pudo quitar a ese miembro. Inténtalo de nuevo. | Não foi possível remover esse membro. Tente de novo. |

- [ ] **Step 2: `GroupActionRow`** — add an owner-only "Add members" `OutlinedButton` (3rd button or a leading row) gated on `state.viewerRole == GroupRole.Owner`, `onClick = { onEvent(GroupDetailsEvent.AddMembersTapped) }`.
- [ ] **Step 3: `GroupMemberRow`** — add a `viewerRole: GroupRole?` param. When `viewerRole == GroupRole.Owner && member.role == GroupRole.Member && !member.isViewer`, render a trailing `IconButton(MoreVert)` + `DropdownMenu` with one `DropdownMenuItem(group_details_remove_member)` → a new `onRemove: () -> Unit` callback. Keep the Follow affordance inline. `LoadedBody` passes `state.viewerRole` + `onRemove = { onEvent(GroupDetailsEvent.RemoveMember(member.did)) }` into the row, but routes it through a confirm dialog (next step) rather than firing immediately.
- [ ] **Step 4: Remove confirm dialog** — in `LoadedBody`/screen, hold `var pendingRemoval by remember { mutableStateOf<GroupMemberUi?>(null) }`. The row ⋮ "Remove" sets `pendingRemoval = member`. Render an `AlertDialog` when non-null: title `group_details_remove_confirm_title`, text `stringResource(group_details_remove_confirm_body, member.displayName ?: member.handle)`, confirm → `onEvent(RemoveMember(did)); pendingRemoval = null`, dismiss → `pendingRemoval = null`.
- [ ] **Step 5: Consume the add result** in the stateful `GroupDetailsScreen`. It needs the nav state — pass it in (the nav module already has `LocalMainShellNavState`); add a `navState: MainShellNavState` param (or read `LocalMainShellNavState.current` in the screen). Then:

```kotlin
val key = "group_members_added:${/* convoId */}"
val pendingAdd = navState.peekResult(key) as? Int
val invitesSentMsg = stringResource(R.string.group_details_invites_sent)
LaunchedEffect(pendingAdd) {
    if (pendingAdd != null) {
        navState.consumeResult(key)
        snackbarHostState.showSnackbar(invitesSentMsg)
        viewModel.handleEvent(GroupDetailsEvent.Refresh)   // deliberate refresh
    }
}
```

> The screen needs the `convoId` for the key — the `GroupDetails` route carries it; thread it into the screen (the nav module has `route.convoId`). Pass `convoId: String` to `GroupDetailsScreen`.

- [ ] **Step 6: Verify** — `:feature:chats:impl:compileProductionDebugKotlin spotlessCheck :feature:chats:impl:lintProductionDebug :feature:chats:impl:testProductionDebugUnitTest` → PASS.
- [ ] **Step 7: Commit** — `feat(chats): group-details add button, remove menu and add-result snackbar` / `Refs: nubecita-hwix.5`.

---

## Task 9: Navigation wiring (`entry<AddGroupMembers>` + setResult)

**Files:**
- Modify: `feature/chats/impl/.../di/ChatsNavigationModule.kt`

- [ ] **Step 1: Register the add route** (assisted VM, adaptiveDialog), and wire its `onAdded` to `setResult` + pop:

```kotlin
entry<AddGroupMembers>(metadata = adaptiveDialog()) { route ->
    val navState = LocalMainShellNavState.current
    val viewModel = hiltViewModel<AddGroupMembersViewModel, AddGroupMembersViewModel.Factory>(
        creationCallback = { it.create(route) },
    )
    AddGroupMembersScreen(
        viewModel = viewModel,
        onAdded = { count ->
            navState.setResult("group_members_added:${route.convoId}", count)
            navState.removeLast()
        },
        onBack = { navState.removeLast() },
    )
}
```

- [ ] **Step 2: Update the `GroupDetails` entry** to pass `convoId` + `navState` into `GroupDetailsScreen` (per Task 8 Step 5), so it can consume the result. Existing `onBack`/`onNavigateTo` stay.
- [ ] **Step 3: Verify** — `./gradlew :app:assembleDebug` → PASS (DI graph wires; both entries resolve).
- [ ] **Step 4: Commit** — `feat(chats): wire add-group-members route and add-result plumbing` / `Refs: nubecita-hwix.5`.

---

## Task 10: Bench fixture (optional offline coverage)

**Files:**
- Modify: `feature/chats/impl/src/bench/.../data/BenchFakeChatRepository.kt`

- [ ] **Step 1:** Ensure the bench fake's `getConvoMembers` returns a roster where the viewer is the **owner** (so the Fastlane/bench build shows the Add button + remove ⋮ for screenshots), and `addMembers`/`removeMembers` are no-op `Result.success(Unit)`. If the bench roster fixture already marks an owner, no change beyond the Task 3 stubs.
- [ ] **Step 2: Verify** — `./gradlew :feature:chats:impl:assembleBenchDebug` (or `:app:assembleBenchDebug`) → PASS.
- [ ] **Step 3: Commit** — `chore(chats): bench owner roster for member-management capture` / `Refs: nubecita-hwix.5`. *(Skip the task + commit if the bench fixture already yields an owner viewer.)*

---

## Task 11: Screenshots + full gate + compose-expert review

**Files:**
- Create: `feature/chats/impl/src/screenshotTest/.../AddGroupMembersScreenContentScreenshotTest.kt`
- Modify: `feature/chats/impl/src/screenshotTest/.../GroupDetailsScreenContentScreenshotTest.kt` (add an owner-view baseline)

- [ ] **Step 1: Screenshot tests** (mirror `ChatSettingsScreenContentScreenshotTest` — `@PreviewTest` + light/dark `@Preview`, `NubecitaCanvasPreviewTheme`):
  - AddGroupMembers: recent/empty, with selected chips + results, at-capacity.
  - GroupDetails **owner** view: Add button visible + a member row showing the ⋮ (and, if feasible as a separate fixture, the remove `AlertDialog`).
- [ ] **Step 2: Generate + validate baselines** — `./gradlew :feature:chats:impl:updateProductionDebugScreenshotTest` then `:validateProductionDebugScreenshotTest`. Stage ONLY the new PNGs; revert any pre-existing Mac-vs-CI drift (`git checkout -- <unrelated baselines>`). The PR already carries the `update-baselines` label so CI regenerates.
- [ ] **Step 3: Full gate** — `./gradlew spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug :feature:chats:impl:testProductionDebugUnitTest :feature:profile:impl:testProductionDebugUnitTest :app:assembleDebug` → all green.
- [ ] **Step 4: compose-expert review** — UI was added (AddGroupMembersScreen, GroupMemberRow ⋮, dialog). Run the compose-expert skill (Review Mode) over the diff of the new/changed composables; address findings.
- [ ] **Step 5: Commit** — `test(chats): member-management screenshots` / `Refs: nubecita-hwix.5`. Then mark PR #562 ready for review.

---

## Self-Review

**Spec coverage:** (1) nav result API → Task 1; (2) repo add/remove + mapper + 5 fakes → Tasks 2–3; (3) NavKey → Task 4; (4) add picker VM/screen → Tasks 5–6; (5) owner-gating + add-nav → Tasks 7–8; (6) remove ⋮ + confirm + optimistic + count-from-size → Tasks 7–8; (7) entry<AddGroupMembers> adaptiveDialog → Task 9; (8) consume add result (snackbar + deliberate refresh) → Task 8; (9) strings en/es/pt → Tasks 6, 8; (10) screenshots + gate + compose-expert → Task 11; ghost-invites (no status field) → handled by the success snackbar in Task 8. Covered.

**Type consistency:** `ChatError.{GroupFull,FollowRequiredToAdd,InsufficientPermission}` (Task 2) used in Tasks 5/7/8; `toMemberMgmtError()` (Task 2) used in Tasks 5/7; `addMembers`/`removeMembers(convoId, dids: List<String>)` (Task 3) called in Tasks 5/7; `AddGroupMembers(convoId)` (Task 4) used in Tasks 5/7/9; `setResult/peekResult/consumeResult` (Task 1) used in Tasks 8/9 with the shared key `"group_members_added:$convoId"`; `viewerRole`/`RemoveMember`/`AddMembersTapped` (Task 7) used in Task 8; `GROUP_MAX_MEMBERS` (existing) used in Task 5. Consistent.

**Notes:** Verify at implementation time — `XrpcError` constructor shape (Task 2 test helper), `InputChipDefaults.AvatarSize` import, and whether the bench roster already yields an owner viewer (Task 10 may be a no-op).
