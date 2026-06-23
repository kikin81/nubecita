# Group chat Phase 3c-2 — join requests (owner side) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A group owner reviews pending join requests and approves/rejects each, from a dedicated Paging-3 list reachable off the group-details screen.

**Architecture:** A new `GroupJoinRequests` adaptive sub-route backed by Paging 3 (network `PagingSource` over `chat.bsky.group.listJoinRequests`); approve/reject (`approveJoinRequest`/`rejectJoinRequest`) remove the row optimistically via a `removedDids` overlay combined into the cached `PagingData` flow; a successful approve signals group-details to refresh via the one-shot `MainShellNavState` result API.

**Tech Stack:** Kotlin 2.3, Jetpack Compose + M3 Expressive, MVI (`MviViewModel`), Hilt assisted injection, Navigation 3, **Paging 3** (`paging-runtime`/`paging-compose`/`paging-testing`), `kotlinx.collections.immutable`, JUnit5 + Turbine + MockK, Compose screenshot tests.

**Spec:** `docs/superpowers/specs/2026-06-22-group-chat-phase3c2-join-requests-design.md`. **bd:** `nubecita-hwix.7`. **Branch:** `feat/nubecita-hwix.7-join-requests` (rebased onto main w/ #563; PR #565). All commits lowercase-leading + `Refs: nubecita-hwix.7`.

---

## Conventions for every task

- TDD for logic (repo/mapper/pagingsource/VM): failing test → watch fail → implement → pass → commit. UI tasks lead with composables + previews.
- NEVER `--no-verify`. commitlint rejects sentence/start/pascal-case subjects — lowercase-leading. Signing failure → `git config --local commit.gpgsign false` then retry. `pre-commit run --files <changed>` first (the **brand-progress-indicator guard** from #563 now runs here), `git add -A`, commit, verify `git log --oneline -1`.
- No `!!`. `ImmutableList`/`ImmutableSet`. PII never logged remotely (repos log `javaClass.name`).
- **Spinners:** standalone "content loading" → `NubecitaWavyProgressIndicator`; the in-button micro-spinner stays a raw `CircularProgressIndicator` with `// nubecita-allow-raw-progress: in-button micro-spinner` on the line above (the #563 guard will reject an unmarked raw spinner).
- After the `ChatRepository` change, update ALL 5 implementors.

---

## Task 1: Paging 3 dependencies

**Files:** `gradle/libs.versions.toml`, `feature/chats/impl/build.gradle.kts`.

The catalog already has `androidx-paging-runtime`, `androidx-paging-testing`, `paging = "3.5.0"` — but NOT `paging-compose`.

- [ ] **Step 1:** add to `gradle/libs.versions.toml` under the paging entries:

```toml
androidx-paging-compose = { module = "androidx.paging:paging-compose", version.ref = "paging" }
```

- [ ] **Step 2:** add to `feature/chats/impl/build.gradle.kts` `dependencies { }` (alphabetical within the implementation group; the module uses `:app:checkSortDependencies`, so place correctly):

```kotlin
implementation(libs.androidx.paging.runtime)
implementation(libs.androidx.paging.compose)
testImplementation(libs.androidx.paging.testing)
```

- [ ] **Step 3: Verify** — `./gradlew :app:checkSortDependencies :feature:chats:impl:compileProductionDebugKotlin` → PASS.
- [ ] **Step 4: Commit** — `build(chats): add Paging 3 deps for join requests` / `Refs: nubecita-hwix.7`.

---

## Task 2: `JoinRequestUi` model + mapper

**Files:**
- Modify: `feature/chats/impl/.../GroupJoinRequestsContract.kt` (create it here with the model; the rest of the contract lands in Task 5) — OR put the model in a small shared spot. Put `JoinRequestUi` in a new `GroupJoinRequestsContract.kt` now.
- Create: `feature/chats/impl/.../data/JoinRequestMapper.kt`
- Test: `feature/chats/impl/src/test/.../data/JoinRequestMapperTest.kt`

`JoinRequestView` (atproto): `requestedBy: ProfileViewBasic`, `requestedAt: <Datetime value class>` (`.raw` → ISO string), `convoId`. `ProfileViewBasic`: `did.raw`, `handle.raw`, `displayName: String?`, `avatar: Uri?` (`.raw`). Convert the timestamp with `Instant.parse(requestedAt.raw)` (mirrors `ConvoMapper.sentAt()` → `Instant.parse(sentAt.raw)`).

- [ ] **Step 1: model** in `GroupJoinRequestsContract.kt`:

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlin.time.Instant

@Immutable
data class JoinRequestUi(
    val did: String,
    val handle: String,
    val displayName: String?,
    val avatarUrl: String?,
    val requestedAt: Instant,
)
```

- [ ] **Step 2: Failing mapper test** (`JoinRequestMapperTest.kt`):

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import io.github.kikin81.atproto.chat.bsky.group.JoinRequestView
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import io.github.kikin81.atproto.runtime.Handle
import io.github.kikin81.atproto.runtime.Uri
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.time.Instant

class JoinRequestMapperTest {
    @Test
    fun `maps requestedBy + requestedAt`() {
        val view = JoinRequestView(
            convoId = "c1",
            requestedBy = ProfileViewBasic(
                did = Did("did:plc:a"),
                handle = Handle("alice.bsky.social"),
                displayName = "Alice",
                avatar = Uri("https://cdn/a.jpg"),
            ),
            requestedAt = Datetime("2026-06-22T10:00:00Z"),
        )
        val ui = view.toJoinRequestUi()
        assertEquals("did:plc:a", ui.did)
        assertEquals("alice.bsky.social", ui.handle)
        assertEquals("Alice", ui.displayName)
        assertEquals("https://cdn/a.jpg", ui.avatarUrl)
        assertEquals(Instant.parse("2026-06-22T10:00:00Z"), ui.requestedAt)
    }
}
```
> Confirm `JoinRequestView` / `ProfileViewBasic` / `Datetime` / `Handle` / `Uri` constructor params + the `requestedAt` value-class name at implementation time (decompile or check generated sources). Adjust the fixture to match (the `requestedAt` setter name is mangled in bytecode but is plain `requestedAt = Datetime(...)` in Kotlin source). `displayName?.takeUnless { it.isBlank() }` to drop blank names.

- [ ] **Step 3: Run → FAIL.**
- [ ] **Step 4: Implement** `data/JoinRequestMapper.kt`:

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.group.JoinRequestView
import net.kikin.nubecita.feature.chats.impl.JoinRequestUi
import kotlin.time.Instant

/** `chat.bsky.group.JoinRequestView` → UI [JoinRequestUi] (requester profile + requested time). */
internal fun JoinRequestView.toJoinRequestUi(): JoinRequestUi =
    JoinRequestUi(
        did = requestedBy.did.raw,
        handle = requestedBy.handle.raw,
        displayName = requestedBy.displayName?.takeUnless { it.isBlank() },
        avatarUrl = requestedBy.avatar?.raw,
        requestedAt = Instant.parse(requestedAt.raw),
    )
```

- [ ] **Step 5: Run → PASS** (`:feature:chats:impl:testProductionDebugUnitTest --tests "*JoinRequestMapperTest*"`); `spotlessCheck` green.
- [ ] **Step 6: Commit** — `feat(chats): JoinRequestUi model and mapper` / `Refs: nubecita-hwix.7`.

---

## Task 3: `ChatRepository` join-request methods (+ 5 implementors)

**Files:** `data/ChatRepository.kt`, `data/DefaultChatRepository.kt`, 5 fakes (`src/test/.../FakeChatRepository.kt`, `src/androidTest/.../FakeChatRepository.kt`, `src/bench/.../data/BenchFakeChatRepository.kt`, inline fake in `src/test/.../store/ChatsUnreadPollingObserverTest.kt`).

**SDK:** `GroupService(xrpcClient).listJoinRequests(ListJoinRequestsRequest(convoId, limit = …, cursor))` → `ListJoinRequestsResponse(requests, cursor)`; `approveJoinRequest(ApproveJoinRequestRequest(convoId, member = Did(did)))`; `rejectJoinRequest(RejectJoinRequestRequest(convoId, member = Did(did)))`.

- [ ] **Step 1: Interface** in `ChatRepository.kt`:

```kotlin
/** Pending join requests for group [convoId]. */
suspend fun getJoinRequests(convoId: String, cursor: String? = null): Result<JoinRequestPage>

/** Approve the pending request from [did] (they become a member). */
suspend fun approveJoinRequest(convoId: String, did: String): Result<Unit>

/** Reject the pending request from [did]. */
suspend fun rejectJoinRequest(convoId: String, did: String): Result<Unit>
```
And the page type (near `MemberPage`):
```kotlin
data class JoinRequestPage(
    val requests: ImmutableList<JoinRequestUi> = persistentListOf(),
    val cursor: String? = null,
)
```

- [ ] **Step 2: Implement in `DefaultChatRepository`** (mirror `getConvoMembers` for the list + `groupMutation` for the two mutations). Imports: `io.github.kikin81.atproto.chat.bsky.group.{ListJoinRequestsRequest, ApproveJoinRequestRequest, RejectJoinRequestRequest}` (GroupService + Did already imported).

```kotlin
internal const val JOIN_REQUESTS_PAGE_LIMIT = 50

override suspend fun getJoinRequests(convoId: String, cursor: String?): Result<JoinRequestPage> =
    withContext(dispatcher) {
        runCatching {
            val response = GroupService(xrpcClientProvider.authenticated())
                .listJoinRequests(
                    ListJoinRequestsRequest(convoId = convoId, limit = JOIN_REQUESTS_PAGE_LIMIT.toLong(), cursor = cursor),
                )
            JoinRequestPage(
                requests = response.requests.map { it.toJoinRequestUi() }.toImmutableList(),
                cursor = response.cursor,
            )
        }.onFailure {
            if (it is CancellationException) throw it
            Timber.tag(TAG).w(it, "getJoinRequests failed: %s", it.javaClass.name)
        }
    }

override suspend fun approveJoinRequest(convoId: String, did: String): Result<Unit> =
    groupMutation("approveJoinRequest") { service ->
        service.approveJoinRequest(ApproveJoinRequestRequest(convoId = convoId, member = Did(did)))
    }

override suspend fun rejectJoinRequest(convoId: String, did: String): Result<Unit> =
    groupMutation("rejectJoinRequest") { service ->
        service.rejectJoinRequest(RejectJoinRequestRequest(convoId = convoId, member = Did(did)))
    }
```
(Confirm the `groupMutation` helper exists from Slice B; if it returns `Result<Unit>` and runs the block on a `GroupService`, reuse it verbatim.)

- [ ] **Step 3: Update all 5 implementors.** Test fake — settable results + captures:
```kotlin
var getJoinRequestsResult: Result<JoinRequestPage> = Result.success(JoinRequestPage())
var approveJoinRequestResult: Result<Unit> = Result.success(Unit)
var rejectJoinRequestResult: Result<Unit> = Result.success(Unit)
val approveJoinRequestCalls = mutableListOf<Pair<String, String>>()
val rejectJoinRequestCalls = mutableListOf<Pair<String, String>>()
var approveJoinRequestGate: CompletableDeferred<Unit>? = null   // for the in-flight-guard test

override suspend fun getJoinRequests(convoId: String, cursor: String?): Result<JoinRequestPage> = getJoinRequestsResult
override suspend fun approveJoinRequest(convoId: String, did: String): Result<Unit> {
    approveJoinRequestCalls += convoId to did
    approveJoinRequestGate?.await()
    return approveJoinRequestResult
}
override suspend fun rejectJoinRequest(convoId: String, did: String): Result<Unit> {
    rejectJoinRequestCalls += convoId to did
    return rejectJoinRequestResult
}
```
androidTest / bench / inline fakes: minimal `= Result.success(JoinRequestPage())` / `= Result.success(Unit)` (match each file's override style).

- [ ] **Step 4: Verify** — `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:testProductionDebugUnitTest :feature:chats:impl:compileBenchDebugKotlin :feature:chats:impl:compileProductionDebugAndroidTestKotlin spotlessCheck` → green.
- [ ] **Step 5: Commit** — `feat(chats): repository join-request list/approve/reject` / `Refs: nubecita-hwix.7`.

---

## Task 4: `GroupJoinRequests` NavKey

**Files:** `feature/chats/api/.../Chats.kt`; Test `feature/chats/impl/src/test/.../GroupJoinRequestsNavKeyTest.kt`.

- [ ] **Step 1: Failing test** (mirror `GroupDetailsNavKeyTest`):
```kotlin
class GroupJoinRequestsNavKeyTest {
    @Test fun `carries convoId and is a NavKey`() {
        val key = GroupJoinRequests(convoId = "c1")
        assertEquals("c1", key.convoId)
        assertTrue(key is NavKey)
    }
}
```
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Add** in `Chats.kt` (next to `GroupDetails`):
```kotlin
/**
 * Owner-side join-requests list for a group convo. A `@MainShell` sub-route pushed from the
 * group-details "Join requests" row; tagged `adaptiveDialog()` (full-screen Compact / Dialog
 * Medium-Expanded). Carries the [convoId] whose pending requests it shows.
 */
@Serializable
data class GroupJoinRequests(val convoId: String) : NavKey
```
- [ ] **Step 4: Run → PASS.**
- [ ] **Step 5: Commit** — `feat(chats): add the group-join-requests NavKey` / `Refs: nubecita-hwix.7`.

---

## Task 5: `JoinRequestPagingSource`

**Files:** Create `feature/chats/impl/.../JoinRequestPagingSource.kt`; Test `feature/chats/impl/src/test/.../JoinRequestPagingSourceTest.kt` (uses `androidx.paging:paging-testing` `TestPager`).

- [ ] **Step 1: Failing test:**
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
class JoinRequestPagingSourceTest {
    private val repo = FakeChatRepository()

    @Test fun `first load returns page with nextKey from cursor`() = runTest {
        repo.getJoinRequestsResult = Result.success(
            JoinRequestPage(requests = persistentListOf(joinRequest("did:a")), cursor = "next"),
        )
        val source = JoinRequestPagingSource(convoId = "c1", repository = repo)
        val pager = TestPager(PagingConfig(pageSize = 50), source)
        val result = pager.refresh() as PagingSource.LoadResult.Page
        assertEquals(listOf("did:a"), result.data.map { it.did })
        assertEquals("next", result.nextKey)
        assertNull(result.prevKey)
    }

    @Test fun `failure surfaces as LoadResult Error`() = runTest {
        repo.getJoinRequestsResult = Result.failure(IOException("down"))
        val source = JoinRequestPagingSource("c1", repo)
        val result = TestPager(PagingConfig(pageSize = 50), source).refresh()
        assertTrue(result is PagingSource.LoadResult.Error)
    }
    // joinRequest(did) builds a JoinRequestUi fixture.
}
```
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement:**
```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.paging.PagingSource
import androidx.paging.PagingState
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository

/** Network cursor [PagingSource] over `getJoinRequests`. Key = cursor string. */
internal class JoinRequestPagingSource(
    private val convoId: String,
    private val repository: ChatRepository,
) : PagingSource<String, JoinRequestUi>() {
    override suspend fun load(params: LoadParams<String>): LoadResult<String, JoinRequestUi> =
        repository.getJoinRequests(convoId, params.key).fold(
            onSuccess = { page -> LoadResult.Page(data = page.requests, prevKey = null, nextKey = page.cursor) },
            onFailure = { LoadResult.Error(it) },
        )

    // Cursor sources can't anchor a refresh key; restart from the head.
    override fun getRefreshKey(state: PagingState<String, JoinRequestUi>): String? = null
}
```
- [ ] **Step 4: Run → PASS** (`--tests "*JoinRequestPagingSourceTest*"`).
- [ ] **Step 5: Commit** — `feat(chats): join-request paging source` / `Refs: nubecita-hwix.7`.

---

## Task 6: `GroupJoinRequestsContract` + `GroupJoinRequestsViewModel`

**Files:** `GroupJoinRequestsContract.kt` (extend with state/events/effects), `GroupJoinRequestsViewModel.kt`; Test `GroupJoinRequestsViewModelTest.kt`.

**Contract** (append to the file holding `JoinRequestUi`):
```kotlin
@Immutable
data class GroupJoinRequestsViewState(
    val inFlightDids: ImmutableSet<String> = persistentSetOf(),
) : UiState

sealed interface GroupJoinRequestsEvent : UiEvent {
    data class ApproveTapped(val did: String) : GroupJoinRequestsEvent
    data class RejectTapped(val did: String) : GroupJoinRequestsEvent
}

sealed interface GroupJoinRequestsEffect : UiEffect {
    data class ShowError(val error: ChatError) : GroupJoinRequestsEffect
    data object RosterChanged : GroupJoinRequestsEffect
}
```

**ViewModel:**
```kotlin
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = GroupJoinRequestsViewModel.Factory::class)
class GroupJoinRequestsViewModel @AssistedInject constructor(
    @Assisted private val route: GroupJoinRequests,
    private val repository: ChatRepository,
) : MviViewModel<GroupJoinRequestsViewState, GroupJoinRequestsEvent, GroupJoinRequestsEffect>(GroupJoinRequestsViewState()) {

    @AssistedFactory interface Factory { fun create(route: GroupJoinRequests): GroupJoinRequestsViewModel }

    private val convoId = route.convoId
    private val removedDids = MutableStateFlow<Set<String>>(emptySet())

    val joinRequests: Flow<PagingData<JoinRequestUi>> =
        Pager(PagingConfig(pageSize = JOIN_REQUESTS_PAGE_LIMIT)) {
            JoinRequestPagingSource(convoId, repository)
        }.flow
            .cachedIn(viewModelScope)
            .combine(removedDids) { data, removed -> data.filter { it.did !in removed } }

    override fun handleEvent(event: GroupJoinRequestsEvent) {
        when (event) {
            is GroupJoinRequestsEvent.ApproveTapped -> act(event.did, approve = true)
            is GroupJoinRequestsEvent.RejectTapped -> act(event.did, approve = false)
        }
    }

    private fun act(did: String, approve: Boolean) {
        if (did in uiState.value.inFlightDids) return
        setState { copy(inFlightDids = (inFlightDids + did).toPersistentSet()) }
        removedDids.update { it + did }                 // optimistic remove
        viewModelScope.launch {
            try {
                val result = if (approve) repository.approveJoinRequest(convoId, did)
                             else repository.rejectJoinRequest(convoId, did)
                result
                    .onSuccess { if (approve) sendEffect(GroupJoinRequestsEffect.RosterChanged) }
                    .onFailure {
                        removedDids.update { it - did }  // rollback — the row reappears
                        sendEffect(GroupJoinRequestsEffect.ShowError(it.toMemberMgmtError()))
                    }
            } finally {
                setState { copy(inFlightDids = (inFlightDids - did).toPersistentSet()) }
            }
        }
    }
}
```
(`JOIN_REQUESTS_PAGE_LIMIT` from Task 3 is `internal`; if it's in `data`, import it. Imports: `androidx.paging.{Pager, PagingConfig, PagingData, cachedIn, filter}`, `kotlinx.coroutines.flow.{Flow, MutableStateFlow, combine, update}`, `kotlinx.collections.immutable.{ImmutableSet, persistentSetOf, toPersistentSet}`, `toMemberMgmtError`.)

- [ ] **Step 1: Failing VM tests** (`@ExtendWith(MainDispatcherExtension::class)`, real `FakeChatRepository`):
  - `approve removes the row + records the call + emits RosterChanged`: seed `getJoinRequestsResult` with `[did:a, did:b]`; collect `vm.joinRequests` via `asSnapshot { }` to confirm both present; `vm.handleEvent(ApproveTapped("did:a"))`; `advanceUntilIdle()`; assert `asSnapshot` now excludes `did:a`, `repo.approveJoinRequestCalls.last() == ("c1" to "did:a")`, and (Turbine) a `RosterChanged` effect.
  - `reject removes the row but does NOT emit RosterChanged`.
  - `approve failure re-adds the row + ShowError`: `repo.approveJoinRequestResult = Result.failure(XrpcError("InsufficientRole","",400))` → after `advanceUntilIdle()`, `asSnapshot` includes `did:a` again + a `ShowError(ChatError.InsufficientPermission)` effect; `inFlightDids` empty.
  - `in-flight guard`: gate via `repo.approveJoinRequestGate = CompletableDeferred()`; two `ApproveTapped("did:a")` before completing the gate → `repo.approveJoinRequestCalls` has exactly one `did:a`; then complete the gate.
  > `asSnapshot { }` is from `androidx.paging:paging-testing` (collect `vm.joinRequests`). Build a `joinRequest(did)` fixture.
- [ ] **Step 2: Run → FAIL.**
- [ ] **Step 3: Implement** the contract + VM.
- [ ] **Step 4: Run → PASS**; `compileProductionDebugKotlin spotlessCheck` green.
- [ ] **Step 5: Commit** — `feat(chats): group-join-requests view model and contract` / `Refs: nubecita-hwix.7`.

---

## Task 7: `EmptyStateContent` + `JoinRequestRow`

**Files:** Create `feature/chats/impl/.../ui/EmptyStateContent.kt`, `feature/chats/impl/.../ui/JoinRequestRow.kt`. Add strings (en/es/pt) for the join-request row + empty state (see Task 8's string table — add the row/empty keys here since these composables reference them).

- [ ] **Step 1: `EmptyStateContent`** — a reusable empty body:
```kotlin
@Composable
internal fun EmptyStateContent(
    icon: NubecitaIconName,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        NubecitaIcon(name = icon, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 12.dp))
    }
}
```
(Confirm `NubecitaIcon` supports a `tint`/`modifier`; if `tint` isn't a param, wrap or use the default. Pick an existing `NubecitaIconName` for the empty icon — e.g. `PersonAdd` or `ChatBubble` — do NOT add a font glyph.)

- [ ] **Step 2: `JoinRequestRow`:**
```kotlin
@Composable
internal fun JoinRequestRow(
    request: JoinRequestUi,
    inFlight: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relative by rememberChatRelativeTimeText(then = request.requestedAt)
    Row(
        modifier = modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NubecitaAvatar(
            model = request.avatarUrl, contentDescription = null,
            fallback = avatarFallbackFor(did = request.did, handle = request.handle, displayName = request.displayName),
        )
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(request.displayName ?: request.handle, style = MaterialTheme.typography.titleSmall, maxLines = 1)
            Text("@${request.handle}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            Text(stringResource(R.string.join_requests_requested_relative, relative), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        TextButton(onClick = onReject, enabled = !inFlight) { Text(stringResource(R.string.join_requests_reject)) }
        FilledTonalButton(onClick = onApprove, enabled = !inFlight) {
            if (inFlight) {
                // nubecita-allow-raw-progress: in-button micro-spinner with a tuned strokeWidth
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.join_requests_approve))
            }
        }
    }
}
```
(`@${request.handle}` — reuse a `group_details_handle`-style string if one exists; else inline. `rememberChatRelativeTimeText` from `net.kikin.nubecita.core.common.time`; `NubecitaAvatar`/`avatarFallbackFor` from `:designsystem`.)

- [ ] **Step 3: Verify** — `:feature:chats:impl:compileProductionDebugKotlin spotlessCheck` + the **guard** (`bash scripts/check_progress_indicators.sh`) → PASS (the in-button marker satisfies it).
- [ ] **Step 4: Commit** — `feat(chats): join-request row and empty-state component` / `Refs: nubecita-hwix.7`.

---

## Task 8: `GroupJoinRequestsScreen` + strings

**Files:** Create `GroupJoinRequestsScreen.kt`; modify `values/`, `values-b+es+419/`, `values-pt-rBR/` strings.

- [ ] **Step 1: Strings** (`<!-- Join requests (nubecita-hwix.7) -->`, all 3 locales):

| key | en | es | pt |
|---|---|---|---|
| join_requests_title | Join requests | Solicitudes para unirse | Solicitações para entrar |
| join_requests_close | Close | Cerrar | Fechar |
| join_requests_approve | Approve | Aprobar | Aprovar |
| join_requests_reject | Reject | Rechazar | Recusar |
| join_requests_requested_relative | Requested %1$s | Solicitó %1$s | Solicitou %1$s |
| join_requests_empty | No pending requests | No hay solicitudes pendientes | Nenhuma solicitação pendente |
| join_requests_error | Couldn\'t load join requests. | No se pudieron cargar las solicitudes. | Não foi possível carregar as solicitações. |
| join_requests_retry | Try again | Reintentar | Tentar de novo |
| group_details_join_requests | Join requests | Solicitudes para unirse | Solicitações para entrar |
| group_details_invites_sent_dummy | (unused) | — | — |

(Drop the dummy row; reuse Slice B's `add_members_error_*` for the approve/reject `ShowError` snackbar copy. `group_details_join_requests` is the GroupDetails entry label — Task 9.)

- [ ] **Step 2: Stateless `GroupJoinRequestsScreenContent(lazyItems, inFlightDids, onApprove, onReject, onClose, modifier, snackbarHostState)`** — `@OptIn(ExperimentalMaterial3Api::class)`:
  - `Scaffold(containerColor = surface, snackbarHost, topBar = TopAppBar(title = join_requests_title, nav close icon))`.
  - body: `Box(Modifier.fillMaxSize().padding(innerPadding))` containing a width-constrained content `Box(Modifier.fillMaxSize().widthIn(max = 600.dp).align(Alignment.TopCenter))` (or `Modifier.align` within a Box) → branch on `lazyItems.loadState.refresh`:
    - `LoadState.Loading` → centered `NubecitaWavyProgressIndicator()`.
    - `LoadState.Error` → centered `Column` with `join_requests_error` + a `TextButton(onClick = { lazyItems.retry() }) { Text(join_requests_retry) }`.
    - `LoadState.NotLoading` && `lazyItems.itemCount == 0` → `EmptyStateContent(icon = NubecitaIconName.PersonAdd, message = stringResource(R.string.join_requests_empty))`.
    - else `LazyColumn(Modifier.fillMaxSize())`: `items(count = lazyItems.itemCount, key = lazyItems.itemKey { it.did }, contentType = lazyItems.itemContentType { "request" }) { i -> lazyItems[i]?.let { r -> JoinRequestRow(request = r, inFlight = r.did in inFlightDids, onApprove = { onApprove(r.did) }, onReject = { onReject(r.did) }, modifier = Modifier.fillMaxWidth()) } }`, plus an append footer: `when (lazyItems.loadState.append) { is LoadState.Loading -> item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { NubecitaWavyProgressIndicator() } }; is LoadState.Error -> item { /* retry row → lazyItems.retry() */ }; else -> {} }`.
- [ ] **Step 3: Stateful `GroupJoinRequestsScreen(viewModel, onRosterChanged, onBack, modifier)`** — `@Suppress("ktlint:compose:parameter-naming")` for `onRosterChanged` (past-tense). `val lazyItems = viewModel.joinRequests.collectAsLazyPagingItems()`; `val state by viewModel.uiState.collectAsStateWithLifecycle()`. Effect collector (child-coroutine snackbar, pre-resolved `add_members_error_*`/`new_group_error_generic`-style copy via the same `ChatError` mapping; `rememberUpdatedState` for callbacks): `ShowError` → snackbar; `RosterChanged` → `currentOnRosterChanged()`. Pass `inFlightDids = state.inFlightDids`, `onApprove = { viewModel.handleEvent(ApproveTapped(it)) }`, `onReject = { viewModel.handleEvent(RejectTapped(it)) }`, `onClose = onBack`.
- [ ] **Step 4: Previews** — drive `collectAsLazyPagingItems` from a fixed `MutableStateFlow(PagingData.from(list))`: a list of 3 requests (one in-flight), the empty state (`PagingData.empty()`), and a refresh-error (a flow emitting `PagingData` with a `LoadStates` error — or just preview the list + empty for screenshots; the error state can be a Task-10 screenshot via a fake). Wrap in `NubecitaCanvasPreviewTheme`.
- [ ] **Step 5: Verify** — `:feature:chats:impl:compileProductionDebugKotlin spotlessCheck :feature:chats:impl:lintProductionDebug` + the guard → green.
- [ ] **Step 6: Commit** — `feat(chats): group-join-requests screen` / `Refs: nubecita-hwix.7`.

---

## Task 9: GroupDetails entry row + roster-refresh consumer

**Files:** `GroupDetailsContract.kt`, `GroupDetailsScreen.kt` (+ strings already added in Task 8).

- [ ] **Step 1: Contract** — add `data object JoinRequestsTapped : GroupDetailsEvent`.
- [ ] **Step 2: VM** (`GroupDetailsViewModel`) — `handleEvent`: `GroupDetailsEvent.JoinRequestsTapped -> sendEffect(GroupDetailsEffect.NavigateTo(GroupJoinRequests(convoId)))`. (Failing test: `JoinRequestsTapped` → `NavigateTo(GroupJoinRequests(convoId))`, in `GroupDetailsViewModelTest`.)
- [ ] **Step 3: Screen** — in `LoadedBody`, render an **owner-only** "Join requests" row below the action row when `state.viewerRole == GroupRole.Owner`: a `Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onEvent(GroupDetailsEvent.JoinRequestsTapped) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = CenterVertically)` with a leading `NubecitaIcon(PersonAdd)`, `Text(stringResource(R.string.group_details_join_requests), Modifier.weight(1f))`, and a trailing chevron icon (an existing `NubecitaIconName` — e.g. `ExpandMore`/an arrow; reuse what's available, no new glyph).
- [ ] **Step 4: Roster-refresh consumer** — in the stateful `GroupDetailsScreen`, add a SECOND one-shot consumer alongside the existing `group_members_added` one:
```kotlin
val rosterRefreshKey = "group_roster_refresh:$convoId"
val pendingRosterRefresh = navState.peekResult(rosterRefreshKey)
LaunchedEffect(Unit) {
    snapshotFlow { navState.peekResult(rosterRefreshKey) }
        .collect { v ->
            if (v != null) {
                navState.consumeResult(rosterRefreshKey)
                viewModel.handleEvent(GroupDetailsEvent.Refresh) // no snackbar
            }
        }
}
```
> Match whatever pattern the existing `group_members_added` consumer uses (it may already be a `snapshotFlow`-based collector keyed on `convoId`); add a parallel collector for `rosterRefreshKey`. Don't show a snackbar.
- [ ] **Step 5: Verify** — `:feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:testProductionDebugUnitTest spotlessCheck` → green.
- [ ] **Step 6: Commit** — `feat(chats): group-details join-requests entry + roster-refresh consumer` / `Refs: nubecita-hwix.7`.

---

## Task 10: Navigation wiring

**Files:** `feature/chats/impl/.../di/ChatsNavigationModule.kt`.

- [ ] **Step 1:** add the entry (assisted VM, adaptiveDialog), wiring `onRosterChanged` → the result set:
```kotlin
entry<GroupJoinRequests>(metadata = adaptiveDialog()) { route ->
    val navState = LocalMainShellNavState.current
    val viewModel = hiltViewModel<GroupJoinRequestsViewModel, GroupJoinRequestsViewModel.Factory>(
        creationCallback = { it.create(route) },
    )
    GroupJoinRequestsScreen(
        viewModel = viewModel,
        onRosterChanged = { navState.setResult("group_roster_refresh:${route.convoId}", true) },
        onBack = { navState.removeLast() },
    )
}
```
(The `GroupDetails` entry's `onNavigateTo` already forwards `NavigateTo(GroupJoinRequests)` via `navState.add(key)` — confirm; no change needed there. Imports: `net.kikin.nubecita.feature.chats.api.GroupJoinRequests`, `net.kikin.nubecita.feature.chats.impl.GroupJoinRequestsScreen`, `GroupJoinRequestsViewModel`.)
- [ ] **Step 2: Verify** — `./gradlew :app:assembleDebug` → PASS (DI graph + entry resolve).
- [ ] **Step 3: Commit** — `feat(chats): wire group-join-requests route` / `Refs: nubecita-hwix.7`.

---

## Task 11: Screenshots + full gate + compose-expert review

**Files:** Create `feature/chats/impl/src/screenshotTest/.../GroupJoinRequestsScreenContentScreenshotTest.kt`; extend the GroupDetails screenshot test with the owner "Join requests" row (already covered if the owner view shows it — add a fixture if needed).

- [ ] **Step 1: Screenshot tests** (mirror existing `@PreviewTest` + light/dark): drive `GroupJoinRequestsScreenContent` with `collectAsLazyPagingItems` over a `MutableStateFlow(PagingData.from(...))` — a 3-request list (one `inFlightDids` member), the empty state (`PagingData.empty()`), and (if feasible) a refresh-error via a `PagingData` with error `LoadStates`. Add a `widthDp = 840` baseline for the 600.dp constraint.
- [ ] **Step 2: Generate + validate** — `:feature:chats:impl:updateProductionDebugScreenshotTest` then `:validateProductionDebugScreenshotTest`; stage only the new PNGs; revert unrelated drift.
- [ ] **Step 3: Full gate** — `./gradlew spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug :feature:chats:impl:testProductionDebugUnitTest :feature:profile:impl:testProductionDebugUnitTest :app:assembleDebug` + `bash scripts/check_progress_indicators.sh` → all green.
- [ ] **Step 4: compose-expert review** — UI added (screen, JoinRequestRow, EmptyStateContent, GroupDetails row). Run the compose-expert skill (Review Mode); address findings. Pay attention to the Paging `loadState` branching, `collectAsLazyPagingItems` recomposition, `items(key/contentType)`, and the `removedDids.combine` filter.
- [ ] **Step 5: Commit** — `test(chats): join-requests screenshots` / `Refs: nubecita-hwix.7`. Ensure PR #565 carries the `update-baselines` label (it should from earlier); then mark PR ready for review.

---

## Self-Review

**Spec coverage:** paging deps → T1; JoinRequestUi+mapper → T2; repo list/approve/reject + 5 fakes → T3; NavKey → T4; PagingSource → T5; contract+VM (paging flow + removedDids + inFlightDids + optimistic + RosterChanged approve-only + rollback + guard) → T6; EmptyStateContent + JoinRequestRow (FilledTonalButton/TextButton, 18dp marked in-button spinner) → T7; screen (loadState branching, NubecitaWavyProgressIndicator, 600dp, snackbar) + strings → T8; GroupDetails owner row (≥48dp) + JoinRequestsTapped + roster-refresh consumer → T9; entry<GroupJoinRequests> adaptiveDialog + onRosterChanged→setResult → T10; screenshots + gate + compose-expert → T11. Covered.

**Type consistency:** `JoinRequestUi`, `JoinRequestPage`, `toJoinRequestUi()`, `getJoinRequests/approveJoinRequest/rejectJoinRequest`, `JoinRequestPagingSource`, `GroupJoinRequestsViewState.inFlightDids`, `GroupJoinRequestsEvent.{ApproveTapped,RejectTapped}`, `GroupJoinRequestsEffect.{ShowError,RosterChanged}`, `GroupJoinRequests(convoId)`, `JOIN_REQUESTS_PAGE_LIMIT`, `group_roster_refresh:$convoId`, `toMemberMgmtError`, `rememberChatRelativeTimeText` — consistent across tasks and with merged A/B/C1 symbols.

**Verify-at-build notes:** `JoinRequestView`/`ProfileViewBasic`/`Datetime`/`Handle`/`Uri` constructor params for the mapper test fixture; the `requestedAt` value-class `.raw` accessor; `groupMutation` reuse; `NubecitaIcon` `tint` param; a chevron `NubecitaIconName` (no new glyph); `toPersistentSet()` import. The standalone loaders use `NubecitaWavyProgressIndicator`; the in-button approve spinner carries the `nubecita-allow-raw-progress` marker (the #563 guard runs in pre-commit + CI).
