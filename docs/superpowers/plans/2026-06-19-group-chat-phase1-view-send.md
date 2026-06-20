# Group chat Phase 1 (view + send) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make tapping a Bluesky group conversation open a working thread you can read and send to (today it errors/opens a synthetic 1:1).

**Architecture:** Re-key the chat path to `convoId` internally while the `Chat` NavKey accepts `convoId` OR `otherUserDid` (VM normalizes — convoId loads via `getConvo`, otherUserDid resolves via `getConvoForMembers` as today). Discriminate `ConvoView.kind` (`DirectConvo`/`GroupConvo`) in the mappers, model the convo row as a sealed `ConvoRowUi {Direct|Group}` and the thread header as a sealed `ChatHeader {Direct|Group}`, render group identity via the shipped `AvatarGroup`, attribute incoming messages with avatar+name (reusing the existing `showAvatar` run-collapsing), and gate the composer on the loaded convo's `lockStatus`/membership.

**Tech Stack:** Kotlin 2.3, Jetpack Compose + M3 Expressive, `atproto-kotlin` 9.3.1 (`chat.bsky.convo`), Hilt assisted-inject, `kotlinx.collections.immutable`, JUnit 5, AGP screenshot tests.

**Spec:** `docs/superpowers/specs/2026-06-19-group-chat-phase1-view-send-design.md` · **bd:** nubecita-r3g1 (epic nubecita-hwix) · **branch:** `feat/nubecita-r3g1-group-chat-impl`

**Confirmed SDK shapes (9.3.1):**
- `ConvoView(id, kind: ConvoViewKindUnion, lastMessage, lastReaction, members: List<ProfileViewBasic>, muted, rev, status, unreadCount)`
- `ConvoViewKindUnion` (interface) → `DirectConvo` (no fields) | `GroupConvo(joinLink: JoinLinkView, lockStatus: String, name: String)`
- `ConvoService(client).getConvo(GetConvoRequest(convoId)).convo`
- `ProfileViewBasic(did: Did, handle: Handle, displayName: String?, avatar: AtUri?, …)`
- `GetConvoAvailabilityResponse(canChat, convo)` (not needed P1 — gate from loaded ConvoView)

**Out of scope:** reactions (Phase 2), group creation / invite links / member management (Phase 3).

---

## File Structure

- `feature/chats/api/.../Chats.kt` — `Chat` NavKey gains optional `convoId`.
- `feature/chats/impl/.../ChatsContract.kt` — `ConvoListItemUi` → sealed `ConvoRowUi {Direct|Group}` (shared fields as interface props); `ConvoTapped`/effect carry `convoId`.
- `feature/chats/impl/.../ChatContract.kt` — add sealed `ChatHeader {Direct|Group}` + `canPost` to `ChatScreenViewState`; `MessageUi` gains `senderDisplayName`/`senderHandle`/`senderAvatarUrl` (sender identity for attribution).
- `feature/chats/impl/.../data/ConvoMapper.kt` — `when (kind)` → `ConvoRowUi.Direct`/`Group`.
- `feature/chats/impl/.../data/ConvoMember.kt` (new) — `ProfileViewBasic.toAuthorUi()` helper.
- `feature/chats/impl/.../data/ChatRepository.kt` + `DefaultChatRepository.kt` — add `getConvo(convoId): Result<ChatConvo>` (header + kind + canPost); cache type → `ConvoRowUi`.
- `feature/chats/impl/.../data/ThreadItemMapper.kt` — `showAvatar` already computed; no logic change (reused).
- `feature/chats/impl/.../ChatViewModel.kt` — normalize NavKey → convoId; load header via `getConvo`; compute `canPost`.
- `feature/chats/impl/.../ChatsViewModel.kt` — `ConvoTapped(convoId)` → `NavigateToChat(convoId)`.
- `feature/chats/impl/.../di/ChatsNavigationModule.kt` — push `Chat(convoId=…)` from list; `Chat(otherUserDid=…)` unchanged from profile/search.
- `feature/chats/impl/.../ui/ConvoListItem.kt` — `when` on row variant (Group → `AvatarGroup` + name).
- `feature/chats/impl/.../ChatScreenContent.kt` — group header (name + `AvatarGroup`), per-message sender attribution, gated composer.
- Fakes/tests/fixtures: `test`/`androidTest`/`bench` `FakeChatRepository`, `BenchChatsMapper`, `BenchFakeChatRepository`, `ConvoMapperTest`, `ChatsViewModelTest`, `ChatViewModelTest`, `ConvoCachePatch(Test)`, unread store tests, `DmPollRunnerTest`, screenshot tests.

---

## Task 1: `Chat` NavKey accepts `convoId` or `otherUserDid`

**Files:** Modify `feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt`; Test `feature/chats/api/src/test/.../ChatNavKeyTest.kt` (create if no api test exists — else put the test in `:impl`).

- [ ] **Step 1: failing test for the require()**

```kotlin
// ChatNavKeyTest.kt
class ChatNavKeyTest {
    @org.junit.jupiter.api.Test
    fun `requires at least one identifier`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) { Chat() }
        Chat(convoId = "c1")          // ok
        Chat(otherUserDid = "did:x")  // ok
    }
}
```

- [ ] **Step 2: run → FAIL** (`Chat()` compiles today: `otherUserDid` is required). `./gradlew :feature:chats:api:testDebugUnitTest` (or `:impl` if api has no unit-test setup) → FAIL.

- [ ] **Step 3: implement**

```kotlin
@Serializable
data class Chat(
    val otherUserDid: String? = null,
    val convoId: String? = null,
) : NavKey {
    init { require(otherUserDid != null || convoId != null) { "Chat needs convoId or otherUserDid" } }
}
```

- [ ] **Step 4: run → PASS.**

- [ ] **Step 5: commit** `git commit -m "feat(chats): Chat NavKey accepts convoId or otherUserDid\n\nRefs: nubecita-r3g1"` (don't break callers yet — they pass otherUserDid by name, still valid).

---

## Task 2: `getConvo` on the repository (header + kind + canPost)

**Files:** Modify `data/ChatRepository.kt` (interface), `data/DefaultChatRepository.kt`; new `data/ConvoMember.kt`; Test `test/.../data/DefaultChatRepositoryTest.kt` (or a new focused test with a MockEngine/fake XrpcClient mirroring existing repo tests).

- [ ] **Step 1: define the return model + member mapper.** In a new `data/ConvoMember.kt`:

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.actor.ProfileViewBasic
import net.kikin.nubecita.data.models.AuthorUi

internal fun ProfileViewBasic.toAuthorUi(): AuthorUi =
    AuthorUi(
        did = did.raw,
        handle = handle.raw,
        displayName = displayName?.takeUnless { it.isBlank() } ?: handle.raw,
        avatarUrl = avatar?.raw,
    )
```

`ChatConvo` (repository-facing convo header) in `ChatRepository.kt`:

```kotlin
data class ChatConvo(
    val convoId: String,
    val header: ChatHeader,   // sealed, defined in ChatContract (Task 5)
    val canPost: Boolean,
)
```

> Note: `ChatHeader` is introduced in Task 5; if implementing strictly in order, temporarily return the raw pieces (convoId, isGroup, name/members or direct fields, canPost) and fold into `ChatHeader` in Task 5. Prefer doing Task 5's `ChatHeader` definition first if the tasks are run out of order.

- [ ] **Step 2: failing test** — `getConvo(convoId)` maps a `GroupConvo` ConvoView to a group header and a `DirectConvo` to a direct header; `canPost` false when `lockStatus` indicates locked / viewer not a member. Use the existing repo test harness (MockEngine returns a `getConvo` fixture).

- [ ] **Step 3: implement in `DefaultChatRepository`:**

```kotlin
override suspend fun getConvo(convoId: String): Result<ChatConvo> =
    withContext(dispatcher) {
        runCatching {
            val viewerDid = currentViewerDid()
            val client = xrpcClientProvider.authenticated()
            val convo = ConvoService(client).getConvo(GetConvoRequest(convoId = convoId)).convo
            ChatConvo(
                convoId = convo.id,
                header = convo.toChatHeader(viewerDid),
                canPost = convo.canViewerPost(viewerDid),
            )
        }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            Timber.tag(TAG).w(it, "getConvo failed: %s", it.javaClass.name)
        }
    }
```

with helpers (in `ConvoMapper.kt`):

```kotlin
fun ConvoView.toChatHeader(viewerDid: String): ChatHeader =
    when (kind) {
        is GroupConvo -> ChatHeader.Group(
            name = (kind as GroupConvo).name,
            members = members.map { it.toAuthorUi() }.toImmutableList(),
        )
        else -> {
            val other = members.firstOrNull { it.did.raw != viewerDid } ?: members.first()
            ChatHeader.Direct(
                did = other.did.raw,
                handle = other.handle.raw,
                displayName = other.displayName?.takeUnless { it.isBlank() },
                avatarUrl = other.avatar?.raw,
            )
        }
    }

// Lightweight gating: not a member, or group lockStatus says locked → can't post.
// lockStatus values come from the lexicon; treat any non-"unlocked" as locked.
fun ConvoView.canViewerPost(viewerDid: String): Boolean {
    val isMember = members.any { it.did.raw == viewerDid }
    val locked = (kind as? GroupConvo)?.lockStatus?.let { it != "unlocked" } ?: false
    return isMember && !locked
}
```

> Implementer: confirm the exact `lockStatus` "unlocked" sentinel by reading the SDK/lexicon enum (`GroupConvo.lockStatus` is a String); adjust the comparison if the open value differs. If membership can't be determined from `members` for the viewer, default `isMember = true` (fail-open to the send-error path rather than wrongly disabling).

- [ ] **Step 4: run repo test → PASS.** `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*DefaultChatRepositoryTest*"`

- [ ] **Step 5: commit.** `feat(chats): repository getConvo with kind→header + canPost`. Refs: nubecita-r3g1.

---

## Task 3: Sealed `ConvoRowUi {Direct|Group}` (the list-model migration)

**Files:** Modify `ChatsContract.kt` (the model), `data/ConvoMapper.kt`, `data/ChatRepository.kt` + `DefaultChatRepository.kt` (cache types + patch fns), `data/ConvoCachePatch.kt`, `ui/ConvoListItem.kt`, `ChatsViewModel.kt`, unread store; update every construction site. Tests as listed.

- [ ] **Step 1: define the sealed model** in `ChatsContract.kt` (shared fields as interface props so `convoId`/`unreadCount`/`muted`/`sentAt`/`lastMessage*` stay polymorphic for multi-select/unread/cache-patch):

```kotlin
@Immutable
sealed interface ConvoRowUi {
    val convoId: String
    val lastMessageSnippet: String?
    val lastMessageFromViewer: Boolean
    val lastMessageIsAttachment: Boolean
    val sentAt: Instant?
    val unreadCount: Int
    val muted: Boolean

    @Immutable
    data class Direct(
        override val convoId: String,
        val otherUserDid: String,
        val otherUserHandle: String,
        val displayName: String?,
        val avatarUrl: String?,
        override val lastMessageSnippet: String?,
        override val lastMessageFromViewer: Boolean,
        override val lastMessageIsAttachment: Boolean,
        override val sentAt: Instant?,
        override val unreadCount: Int = 0,
        override val muted: Boolean = false,
    ) : ConvoRowUi

    @Immutable
    data class Group(
        override val convoId: String,
        val name: String,
        val members: ImmutableList<AuthorUi>,
        override val lastMessageSnippet: String?,
        override val lastMessageFromViewer: Boolean,
        override val lastMessageIsAttachment: Boolean,
        override val sentAt: Instant?,
        override val unreadCount: Int = 0,
        override val muted: Boolean = false,
    ) : ConvoRowUi
}
```

- [ ] **Step 2: failing mapper test** in `ConvoMapperTest.kt`: a `GroupConvo` ConvoView maps to `ConvoRowUi.Group(name, members=all)`; a `DirectConvo` maps to `ConvoRowUi.Direct(otherUserDid=the-other-member)`. (The existing direct cases become `.Direct` assertions.)

- [ ] **Step 3: implement `ConvoMapper.toConvoRowUi`:**

```kotlin
fun ConvoView.toConvoRowUi(viewerDid: String): ConvoRowUi {
    val snippet = lastMessage?.snippet()
    val fromViewer = lastMessage?.senderDid() == viewerDid
    val attachment = lastMessage.isAttachmentOnly()
    val at = lastMessage?.sentAt()
    val unread = unreadCount.toInt().coerceAtLeast(0)
    return when (kind) {
        is GroupConvo -> ConvoRowUi.Group(
            convoId = id, name = (kind as GroupConvo).name,
            members = members.map { it.toAuthorUi() }.toImmutableList(),
            lastMessageSnippet = snippet, lastMessageFromViewer = fromViewer,
            lastMessageIsAttachment = attachment, sentAt = at, unreadCount = unread, muted = muted,
        )
        else -> {
            val other = members.firstOrNull { it.did.raw != viewerDid } ?: members.first()
            ConvoRowUi.Direct(
                convoId = id, otherUserDid = other.did.raw, otherUserHandle = other.handle.raw,
                displayName = other.displayName?.takeUnless { it.isBlank() }, avatarUrl = other.avatar?.raw,
                lastMessageSnippet = snippet, lastMessageFromViewer = fromViewer,
                lastMessageIsAttachment = attachment, sentAt = at, unreadCount = unread, muted = muted,
            )
        }
    }
}
```

Delete the old `toConvoListItemUi`.

- [ ] **Step 4: migrate consumers** (compile-driven — `git grep -n 'ConvoListItemUi'`):
  - `DefaultChatRepository`: cache types `MutableStateFlow<ImmutableList<ConvoRowUi>?>`; `patchConvosOnLeave/Read/Prepend` operate on `convoId`/`unreadCount`/`muted` via the interface (use `.copyShared(...)` pattern — since data-class `copy` isn't on the interface, the read/mute patch must `when`-branch to copy the right variant; keep a small private `ConvoRowUi.withUnread(n)`/`.withMuted(b)` extension that `when`-branches and copies). Add those extensions in `ConvoCachePatch.kt`.
  - `ConvoCachePatch.kt`: patch helpers updated to the sealed type + the `with*` extensions.
  - `ChatsViewModel.kt`: state holds `ImmutableList<ConvoRowUi>`; `ConvoTapped` now carries `convoId` (see Task 4).
  - unread store / `DmPollRunner` / fixtures: construct `ConvoRowUi.Direct(...)` (or `.Group` where a group fixture is wanted).
  - `BenchChatsMapper` / `BenchFakeChatRepository`: produce `ConvoRowUi`.

- [ ] **Step 5: update `ConvoListItem.kt`** to branch on the variant:

```kotlin
when (item) {
    is ConvoRowUi.Direct -> /* existing NubecitaAvatar(model=avatarUrl, fallback=avatarFallbackFor(otherUserDid, otherUserHandle, displayName)); title = displayName ?: otherUserHandle */
    is ConvoRowUi.Group  -> /* AvatarGroup(members = item.members, contentDescription = item.name, avatarSize = 40.dp); title = item.name */
}
```
Supporting/trailing (snippet, timestamp, unread) read interface props — unchanged.

- [ ] **Step 6: compile + tests.** `./gradlew :feature:chats:impl:assembleDebug :feature:chats:impl:testProductionDebugUnitTest :feature:chats:impl:compileBenchDebugKotlin :feature:chats:impl:compileProductionDebugAndroidTestKotlin` → PASS.

- [ ] **Step 7: commit.** `refactor(chats): sealed ConvoRowUi {Direct|Group} + kind-aware mapper`. Refs: nubecita-r3g1.

---

## Task 4: List opens by `convoId`

**Files:** `ChatsContract.kt` (`ConvoTapped`), `ChatsViewModel.kt`, `ChatsNavigationModule.kt`, the row click wiring in `ChatsScreenContent`.

- [ ] **Step 1: failing VM test** — `ConvoTapped(convoId="c1")` emits `ChatsEffect.NavigateToChat(convoId="c1")`.

- [ ] **Step 2: implement** — change `ConvoTapped(val convoId: String)`, `ChatsEffect.NavigateToChat(val convoId: String)`, handler `sendEffect(NavigateToChat(event.convoId))`. The row's `onClick` passes `row.convoId` (available on every `ConvoRowUi`). In `ChatsNavigationModule`, `onNavigateToChat = { convoId -> navState.replaceTop(Chat(convoId = convoId)) }`.

- [ ] **Step 3: run → PASS;** also `./gradlew :feature:chats:impl:assembleDebug`.

- [ ] **Step 4: commit.** `feat(chats): open thread by convoId from the list`. Refs: nubecita-r3g1.

---

## Task 5: Thread header sealed (`ChatHeader`) + VM normalizes NavKey

**Files:** `ChatContract.kt` (add `ChatHeader` + `canPost`), `ChatViewModel.kt`, `ChatScreenContent.kt` (TopAppBar).

- [ ] **Step 1: define** in `ChatContract.kt`:

```kotlin
sealed interface ChatHeader {
    data class Direct(val did: String, val handle: String, val displayName: String?, val avatarUrl: String?) : ChatHeader
    data class Group(val name: String, val members: ImmutableList<AuthorUi>) : ChatHeader
}
```
Add to `ChatScreenViewState`: `val header: ChatHeader? = null`, `val canPost: Boolean = true`. (Keep the existing `otherUser*` fields during migration or replace their reads with `header` — prefer replacing: the TopAppBar reads `header`.)

- [ ] **Step 2: failing VM tests** —
  - `Chat(convoId="c1")` → VM calls `repository.getConvo("c1")`, sets `header` (Direct/Group) + `canPost`, then `getMessages("c1")`.
  - `Chat(otherUserDid="did:x")` → VM calls `resolveConvo("did:x")` (today's path) → convoId, then `getConvo(convoId)` for the header + `getMessages`.
  - `canPost=false` (locked group) → `isSendEnabled` stays false even with non-blank text.

- [ ] **Step 3: implement** the VM `init`/`launchLoad` normalization:

```kotlin
private val convoIdArg: String? = chat.convoId
private val otherUserDidArg: String? = chat.otherUserDid
// in launchLoad():
val convoId = convoIdArg ?: repository.resolveConvo(otherUserDidArg!!).getOrThrow().convoId
val convo = repository.getConvo(convoId).getOrThrow()
setState { copy(header = convo.header, canPost = convo.canPost) }
// then getMessages(convoId) as today; store convoId for sends
```
Gate send: `isSendEnabled = canPost && textNotBlank` (combine the existing `snapshotFlow` with `canPost`).

- [ ] **Step 4: TopAppBar** in `ChatScreenContent.kt` branches on `state.header`: Direct → `ChatTopBarAvatar` + name (today); Group → `AvatarGroup(members, …)` + `name` + a "N members" subtitle.

- [ ] **Step 5: run VM tests + assembleDebug → PASS.**

- [ ] **Step 6: commit.** `feat(chats): sealed ChatHeader + VM convoId/did normalization + canPost`. Refs: nubecita-r3g1.

---

## Task 6: Per-message sender attribution (groups)

**Files:** `ChatContract.kt` (`MessageUi` sender fields), `data/MessageMapper.kt` (populate sender), `ThreadItemMapper.kt` (already computes `showAvatar` — reuse), `ui/MessageBubble.kt` + `ChatScreenContent.kt` (render avatar+name when `showAvatar`).

- [ ] **Step 1: failing mapper test** — `MessageView` → `MessageUi` populates `senderHandle`/`senderDisplayName`/`senderAvatarUrl` from the message sender's profile (the `getMessages`/convo members). Confirm `showAvatar` (already in `ThreadItemMapper`, line ~83: `!isOutgoing && runIndex == 0`) is true for the first incoming message of a run.

- [ ] **Step 2: implement** — add `senderHandle: String`, `senderDisplayName: String?`, `senderAvatarUrl: String?` to `MessageUi`; populate in `MessageMapper` (the sender profile is on the `MessageView`/derivable from convo members). `ThreadItemMapper` unchanged (the run-collapsing + `showAvatar` already exist).

- [ ] **Step 3: render** — in the message-row composable (`ChatScreenContent`/`MessageBubble`), when `!message.isOutgoing && showAvatar` and the thread is a Group, render the sender's `NubecitaAvatar(model=senderAvatarUrl, fallback=avatarFallbackFor(senderDid, senderHandle, senderDisplayName))` + the `senderDisplayName ?: senderHandle` label above the first bubble of the run. Own messages and direct threads: unchanged (no label/avatar). Pass `isGroup = state.header is ChatHeader.Group` down to the row.

- [ ] **Step 4: unit test** the sender-population + a screenshot fixture comes in Task 8. Run `./gradlew :feature:chats:impl:testProductionDebugUnitTest`.

- [ ] **Step 5: commit.** `feat(chats): per-message sender attribution in group threads`. Refs: nubecita-r3g1.

---

## Task 7: Gated composer UI

**Files:** `ChatScreenContent.kt` (composer area).

- [ ] **Step 1:** when `!state.canPost`, render the composer disabled with a hint string (new `chats_cannot_post` string res, e.g. "You can't post in this conversation") instead of the input. `isSendEnabled` already incorporates `canPost` from Task 5.

- [ ] **Step 2:** screenshot fixture for the disabled state (Task 8). `./gradlew :feature:chats:impl:assembleDebug`.

- [ ] **Step 3: commit.** `feat(chats): disable composer when convo is not postable`. Refs: nubecita-r3g1.

---

## Task 8: Group requests + screenshots + verification

**Files:** verify request path; screenshot tests.

- [ ] **Step 1: verify group requests render** — the Requests tab uses the same `ConvoRowUi` mapper, so a `GroupConvo` request renders as `ConvoRowUi.Group` automatically. Confirm `acceptConvo(convoId)` works from a group request row (it's convoId-keyed already). Add/extend a `ChatsViewModelTest` case for a group request row + accept.

- [ ] **Step 2: screenshot fixtures** (mirror existing chat screenshot tests; build `ConvoRowUi.Group`/`ChatHeader.Group` fixtures): group convo row (facepile + name), group thread header, group thread with sender-attributed runs (avatar+name on first-of-run), disabled composer. Generate via the module's `update*ScreenshotTest` task; **stage only chat baselines, revert any logomark/cross-module churn** (per the AvatarGroup-era lesson); validate.

- [ ] **Step 3: full gates.** `./gradlew spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug :feature:chats:impl:testProductionDebugUnitTest :app:assembleDebug` → all green. Validate chat screenshots (feature baselines may be CI-rendered → if local≠CI, the PR will use the `update-baselines` label).

- [ ] **Step 4: commit.** `test(chats): group request + screenshot coverage`. Refs: nubecita-r3g1.

---

## Self-Review

**Spec coverage:** Decision 1 (NavKey convoId|did + VM normalize) → Tasks 1, 5. Decision 2 (sealed `ConvoRowUi`) → Task 3. Decision 3 (avatar+name sender attribution, run-collapsing) → Task 6 (reuses existing `showAvatar`). Decision 4 (lightweight send-gating from loaded ConvoView) → Tasks 2 (`canViewerPost`) + 5 (`canPost` in state) + 7 (UI). Group row/header via AvatarGroup → Tasks 3, 5. Group requests via same row + acceptConvo → Task 8. `getConvo`/kind discrimination → Task 2/3. All covered.

**Placeholder scan:** The `lockStatus` "unlocked" sentinel and `MessageUi` sender-source are flagged as "implementer confirm against SDK/MessageView" — these are precise instructions to verify an external SDK value, not deferred work; the surrounding logic + fallback (fail-open) is specified.

**Type consistency:** `ConvoRowUi`/`ConvoRowUi.Direct`/`.Group`, `ChatHeader`/`.Direct`/`.Group`, `ChatConvo(convoId, header, canPost)`, `toConvoRowUi`, `toChatHeader`, `toAuthorUi`, `canViewerPost`, `Chat(otherUserDid?, convoId?)` — used consistently across tasks. Task 2 notes the `ChatHeader` ordering dependency on Task 5 (define `ChatHeader` first if running out of order).
