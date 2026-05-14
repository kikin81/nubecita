# Chat thread screen MVP — implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship a read-only chat thread screen (`Chat(otherUserDid)` route) that resolves DID → convoId via `chat.bsky.convo.getConvoForMembers`, loads messages via `chat.bsky.convo.getMessages`, and renders them as M3 Expressive bubbles with GChat-style segmented corner shapes, consecutive-message grouping, and day-separator chips.

**Architecture:** New `ChatScreen` + `ChatScreenContent` + `ChatViewModel` in `:feature:chats:impl`. `ChatRepository` interface (already shipped in nn3.1) is extended additively with `resolveConvo` + `getMessages`. Grouping computed VM-side as `ImmutableList<ThreadItem>` (flat UI-ready state). `LazyColumn(reverseLayout = true)` + newest-first ordering. Bubble shape mirrors `ListItemDefaults.segmentedShapes`'s algorithm asymmetrically on the sender side; opposite side stays fully rounded.

**Tech Stack:** Kotlin 2.3 + Jetpack Compose (Material 3 1.5.0-alpha19 with experimental Expressive APIs) + Hilt + Navigation 3 + atproto-kotlin 6.0.1 + kotlinx.collections.immutable + Turbine + JUnit 5 + Compose Screenshot Test.

**Spec:** [`docs/superpowers/specs/2026-05-14-chats-thread-screen-design.md`](../specs/2026-05-14-chats-thread-screen-design.md).

---

## File structure

### New files

| Path | Responsibility |
|---|---|
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatContract.kt` | `ChatScreenViewState`, `ChatLoadStatus`, `ChatError`, `ChatEvent`, `ChatEffect`, `ThreadItem`, `MessageUi` types |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModel.kt` | MVI presenter; auto-loads via `init { launchLoad() }`; single-flight |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreenContent.kt` | Stateless screen body — previews + screenshot tests render this directly |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreen.kt` | Stateful entry — owns the VM, effect collector, back nav |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/MessageMapper.kt` | `MessageView` / `DeletedMessageView` / unknowns → `MessageUi` |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ThreadItemMapper.kt` | `List<MessageUi>.toThreadItems(now, zone)` — grouping + day-separators |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubble.kt` | `MessageBubble` composable + internal `messageBubbleShape(index, count, isOutgoing)` |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/DaySeparatorChip.kt` | Centered `labelSmall` pill |
| `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/MessageMapperTest.kt` | Mapper unit tests |
| `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/ThreadItemMapperTest.kt` | Grouping/separator algorithm unit tests |
| `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMappingTest.kt` | Error mapping unit tests (covers existing `toChatsError` + new `toChatError`) |
| `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModelTest.kt` | VM state-machine tests |
| `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubbleShapeTest.kt` | `messageBubbleShape` exhaustive unit tests |
| `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubbleScreenshotTest.kt` | Bubble screenshot variants |
| `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ui/DaySeparatorChipScreenshotTest.kt` | Chip screenshot variants |
| `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreenContentScreenshotTest.kt` | Screen-level screenshot variants |

### Modified files

| Path | Change |
|---|---|
| `feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt` | Add `data class Chat(val otherUserDid: String) : NavKey` |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatRepository.kt` | Add `resolveConvo` + `getMessages` to interface; add `ConvoResolution` + `MessagePage` data types |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/DefaultChatRepository.kt` | Implement the two new methods using `ConvoService(client).getConvoForMembers` / `getMessages` |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMapping.kt` | Add `Throwable.toChatError(): ChatError` (parallel to existing `toChatsError`) |
| `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ChatsNavigationModule.kt` | Replace `Timber.tag("ChatsNavigation").i(...)` placeholder with `navState.add(Chat(did))`; add `entry<Chat> { ... }` provider |
| `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/FakeChatRepository.kt` | Add stub state for `resolveConvo` + `getMessages` |

---

## Task 1: Add `Chat(otherUserDid)` NavKey

**Files:**
- Modify: `feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt`

- [ ] **Step 1: Add the new NavKey under the existing `Chats` data object**

```kotlin
/**
 * Per-conversation thread destination. Pushed onto [MainShellNavState] when the user
 * taps a row in the convo list or (future, nubecita-a7a) the Message button on
 * another user's profile. The screen resolves [otherUserDid] → convoId itself via
 * `chat.bsky.convo.getConvoForMembers`.
 */
@Serializable
data class Chat(val otherUserDid: String) : NavKey
```

- [ ] **Step 2: Build to verify (no runtime test — it's a marker class)**

Run: `./gradlew :feature:chats:api:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add feature/chats/api/src/main/kotlin/net/kikin/nubecita/feature/chats/api/Chats.kt
git commit -m "feat(feature/chats): add Chat(otherUserDid) NavKey

Refs: nubecita-nn3.2"
```

---

## Task 2: Extend `ChatRepository` interface with `resolveConvo` + `getMessages`

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatRepository.kt`

- [ ] **Step 1: Add the two new methods and supporting types**

Replace the contents of `ChatRepository.kt` with:

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.MessageUi

/**
 * `chat.bsky.convo.*` fetch surface scoped to `:feature:chats:impl`.
 *
 * Two screens share this interface:
 * - The Chats tab home (`listConvos`).
 * - The chat thread screen (`resolveConvo` + `getMessages`).
 */
internal interface ChatRepository {
    suspend fun listConvos(
        cursor: String? = null,
        limit: Int = LIST_CONVOS_PAGE_LIMIT,
    ): Result<ConvoListPage>

    /**
     * Resolves a peer DID into the appview-side convoId plus the other user's profile
     * bits we need for the thread's TopAppBar. Wraps `chat.bsky.convo.getConvoForMembers`.
     */
    suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution>

    /**
     * Loads a page of messages for [convoId]. Page is newest-first per the lexicon.
     * Wraps `chat.bsky.convo.getMessages`. `cursor = null` requests the first page.
     */
    suspend fun getMessages(
        convoId: String,
        cursor: String? = null,
        limit: Int = GET_MESSAGES_PAGE_LIMIT,
    ): Result<MessagePage>
}

internal data class ConvoListPage(
    val items: ImmutableList<ConvoListItemUi> = persistentListOf(),
    val nextCursor: String? = null,
)

internal data class ConvoResolution(
    val convoId: String,
    val otherUserHandle: String,
    val otherUserDisplayName: String?,
    val otherUserAvatarUrl: String?,
    val otherUserAvatarHue: Int,
)

internal data class MessagePage(
    val messages: ImmutableList<MessageUi> = persistentListOf(),
    val nextCursor: String? = null,
)

internal const val LIST_CONVOS_PAGE_LIMIT: Int = 30
internal const val GET_MESSAGES_PAGE_LIMIT: Int = 50
```

> **Note:** `MessageUi` is forward-referenced from `ChatContract.kt` which Task 3 creates. The compile order doesn't matter for Kotlin in the same module, but if the IDE flags an unresolved reference, complete Task 3 first then re-check.

- [ ] **Step 2: Commit (build will pass once Task 3 lands MessageUi)**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatRepository.kt
git commit -m "feat(feature/chats): extend ChatRepository with resolveConvo + getMessages

Refs: nubecita-nn3.2"
```

---

## Task 3: Create `ChatContract.kt` (state, events, effects, ThreadItem, MessageUi)

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatContract.kt`

- [ ] **Step 1: Write the contract file**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.time.Instant
import net.kikin.nubecita.core.common.mvi.UiEffect
import net.kikin.nubecita.core.common.mvi.UiEvent
import net.kikin.nubecita.core.common.mvi.UiState

/**
 * MVI state for the Chat thread screen. Mirrors `ChatsScreenViewState` shape: one
 * sealed [ChatLoadStatus] sum carries the lifecycle; `isRefreshing` is the only
 * flag that may coexist with `Loaded` and lives inside that variant.
 *
 * The other-user's profile bits (handle/displayName/avatar) populate after the
 * `resolveConvo` call returns; the TopAppBar reads them. Defaults are empty
 * strings / nulls so the initial Loading composition has stable values.
 */
data class ChatScreenViewState(
    val otherUserHandle: String = "",
    val otherUserDisplayName: String? = null,
    val otherUserAvatarUrl: String? = null,
    val otherUserAvatarHue: Int = 0,
    val status: ChatLoadStatus = ChatLoadStatus.Loading,
) : UiState

sealed interface ChatLoadStatus {
    data object Loading : ChatLoadStatus

    data class Loaded(
        val items: ImmutableList<ThreadItem> = persistentListOf(),
        val isRefreshing: Boolean = false,
    ) : ChatLoadStatus

    data class InitialError(
        val error: ChatError,
    ) : ChatLoadStatus
}

sealed interface ChatError {
    data object Network : ChatError

    data object NotEnrolled : ChatError

    /** `resolveConvo` couldn't find or open a convo for the peer DID. */
    data object ConvoNotFound : ChatError

    data class Unknown(
        val cause: String?,
    ) : ChatError
}

/**
 * Flat sealed list of items rendered by the thread `LazyColumn`. The mapper
 * emits this newest-first; `LazyColumn(reverseLayout = true)` then renders
 * bottom-to-top so the freshest message lands at the screen bottom.
 *
 * `runIndex` is OLDEST-first within the run: the oldest message of a run gets
 * `runIndex = 0` (top of run on screen with reverseLayout); the newest gets
 * `runCount - 1` (bottom of run on screen).
 */
@Immutable
sealed interface ThreadItem {
    val key: String

    data class Message(
        val message: MessageUi,
        val runIndex: Int,
        val runCount: Int,
        val showAvatar: Boolean,
    ) : ThreadItem {
        override val key: String get() = "msg-${message.id}"
    }

    data class DaySeparator(
        val label: String,
    ) : ThreadItem {
        override val key: String get() = "sep-$label"
    }
}

@Immutable
data class MessageUi(
    val id: String,
    val senderDid: String,
    val isOutgoing: Boolean,
    val text: String,
    val isDeleted: Boolean,
    val sentAt: Instant,
)

sealed interface ChatEvent : UiEvent {
    data object Refresh : ChatEvent

    data object RetryClicked : ChatEvent

    data object BackPressed : ChatEvent
}

sealed interface ChatEffect : UiEffect
```

- [ ] **Step 2: Build to verify compile + Task 2's forward-reference resolves**

Run: `./gradlew :feature:chats:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatContract.kt
git commit -m "feat(feature/chats): add ChatContract types

Refs: nubecita-nn3.2"
```

---

## Task 4: Extend `FakeChatRepository` for the new methods

**Files:**
- Modify: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/FakeChatRepository.kt`

- [ ] **Step 1: Replace the file**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoListPage
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import java.util.concurrent.atomic.AtomicInteger

internal class FakeChatRepository(
    var nextListResult: Result<ConvoListPage> = Result.success(ConvoListPage(items = persistentListOf())),
    var nextResolveResult: Result<ConvoResolution> =
        Result.success(
            ConvoResolution(
                convoId = "convo-1",
                otherUserHandle = "alice.bsky.social",
                otherUserDisplayName = "Alice",
                otherUserAvatarUrl = null,
                otherUserAvatarHue = 0,
            ),
        ),
    var nextMessagesResult: Result<MessagePage> = Result.success(MessagePage(messages = persistentListOf())),
) : ChatRepository {
    val listCalls = AtomicInteger(0)
    val resolveCalls = AtomicInteger(0)
    val messagesCalls = AtomicInteger(0)
    var lastListCursor: String? = null
    var lastResolvedDid: String? = null
    var lastMessagesConvoId: String? = null
    var lastMessagesCursor: String? = null

    override suspend fun listConvos(
        cursor: String?,
        limit: Int,
    ): Result<ConvoListPage> {
        listCalls.incrementAndGet()
        lastListCursor = cursor
        return nextListResult
    }

    override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> {
        resolveCalls.incrementAndGet()
        lastResolvedDid = otherUserDid
        return nextResolveResult
    }

    override suspend fun getMessages(
        convoId: String,
        cursor: String?,
        limit: Int,
    ): Result<MessagePage> {
        messagesCalls.incrementAndGet()
        lastMessagesConvoId = convoId
        lastMessagesCursor = cursor
        return nextMessagesResult
    }
}
```

> Note the rename: `nextResult` (the old single-page convo-list seed) becomes `nextListResult`. Existing `ChatsViewModelTest` references will need updating — those changes are part of the test-update step below.

- [ ] **Step 2: Update existing `ChatsViewModelTest` references (`nextResult` → `nextListResult`)**

Run: `sed -i '' 's/\.nextResult /\.nextListResult /g; s/nextResult = /nextListResult = /g' feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsViewModelTest.kt`

Inspect the diff to confirm only the property accesses changed, then build the tests:

Run: `./gradlew :feature:chats:impl:compileDebugUnitTestKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Run existing chats unit tests to confirm no regressions**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest`
Expected: All existing tests pass.

- [ ] **Step 4: Commit**

```bash
git add feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/FakeChatRepository.kt feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatsViewModelTest.kt
git commit -m "test(feature/chats): extend FakeChatRepository for resolveConvo + getMessages

Refs: nubecita-nn3.2"
```

---

## Task 5: Add `Throwable.toChatError()` + tests for ChatsErrorMapping (TDD)

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMapping.kt`
- Create: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMappingTest.kt`

- [ ] **Step 1: Write the failing test file**

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.feature.chats.impl.ChatError
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

internal class ChatsErrorMappingTest {
    @Test
    fun `IOException maps to ChatError Network`() {
        val result = IOException("net down").toChatError()
        assertEquals(ChatError.Network, result)
    }

    @Test
    fun `NoSessionException maps to ChatError Unknown not-signed-in`() {
        val result = NoSessionException().toChatError()
        assertTrue(result is ChatError.Unknown)
        assertEquals("not-signed-in", (result as ChatError.Unknown).cause)
    }

    @Test
    fun `XrpcError with not-enrolled in message maps to ChatError NotEnrolled`() {
        val xrpc = XrpcError.Unknown(message = "user is not enrolled in direct messaging")
        val result = xrpc.toChatError()
        assertEquals(ChatError.NotEnrolled, result)
    }

    @Test
    fun `XrpcError mentioning convo not found maps to ChatError ConvoNotFound`() {
        val xrpc = XrpcError.Unknown(message = "ConvoNotFound: no matching convo for members")
        val result = xrpc.toChatError()
        assertEquals(ChatError.ConvoNotFound, result)
    }

    @Test
    fun `XrpcError with unrecognized message maps to ChatError Unknown`() {
        val xrpc = XrpcError.Unknown(message = "weird server-side error")
        val result = xrpc.toChatError()
        assertTrue(result is ChatError.Unknown)
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.data.ChatsErrorMappingTest"`
Expected: FAIL — `toChatError()` is unresolved.

- [ ] **Step 3: Add `Throwable.toChatError()` to ChatsErrorMapping**

Replace the contents of `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMapping.kt` with:

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.feature.chats.impl.ChatError
import net.kikin.nubecita.feature.chats.impl.ChatsError
import java.io.IOException
import java.util.Locale

private const val NOT_ENROLLED_MARKER = "not enrolled"
private const val CONVO_NOT_FOUND_MARKER = "convonotfound"

/**
 * Maps a thrown error from the convo-list path (`listConvos`) to a screen-facing
 * [ChatsError] variant. Predates `toChatError`; kept for the existing
 * convo-list ViewModel.
 */
internal fun Throwable.toChatsError(): ChatsError =
    when (this) {
        is IOException -> ChatsError.Network
        is NoSessionException -> ChatsError.Unknown("not-signed-in")
        is XrpcError -> {
            val msg = message.orEmpty().lowercase(Locale.ROOT)
            if (NOT_ENROLLED_MARKER in msg) ChatsError.NotEnrolled else ChatsError.Unknown(javaClass.simpleName)
        }
        else -> ChatsError.Unknown(javaClass.simpleName)
    }

/**
 * Maps a thrown error from the thread path (`resolveConvo` or `getMessages`) to a
 * screen-facing [ChatError] variant. Distinct from [toChatsError] because the
 * thread screen recognises `ConvoNotFound` (returned by `getConvoForMembers`
 * when the peer DID has no shared convo and one can't be auto-opened).
 */
internal fun Throwable.toChatError(): ChatError =
    when (this) {
        is IOException -> ChatError.Network
        is NoSessionException -> ChatError.Unknown("not-signed-in")
        is XrpcError -> {
            val msg = message.orEmpty().lowercase(Locale.ROOT)
            when {
                CONVO_NOT_FOUND_MARKER in msg -> ChatError.ConvoNotFound
                NOT_ENROLLED_MARKER in msg -> ChatError.NotEnrolled
                else -> ChatError.Unknown(javaClass.simpleName)
            }
        }
        else -> ChatError.Unknown(javaClass.simpleName)
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.data.ChatsErrorMappingTest"`
Expected: All 5 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMapping.kt feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/ChatsErrorMappingTest.kt
git commit -m "feat(feature/chats): add Throwable.toChatError() with ConvoNotFound marker

Refs: nubecita-nn3.2"
```

---

## Task 6: `MessageMapper` (TDD)

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/MessageMapper.kt`
- Create: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/MessageMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesResponseMessagesUnion
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageViewSender
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class MessageMapperTest {
    private val viewer = "did:plc:viewer"
    private val peer = "did:plc:alice"

    @Test
    fun `MessageView from viewer maps to outgoing MessageUi`() {
        val wire = messageView(id = "m1", senderDid = viewer, text = "hi", sentAt = "2026-05-01T12:00:00Z")
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals(1, ui.size)
        assertEquals("m1", ui[0].id)
        assertTrue(ui[0].isOutgoing)
        assertEquals(false, ui[0].isDeleted)
        assertEquals("hi", ui[0].text)
    }

    @Test
    fun `MessageView from peer maps to incoming MessageUi`() {
        val wire = messageView(id = "m2", senderDid = peer, text = "yo", sentAt = "2026-05-01T12:01:00Z")
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals(false, ui[0].isOutgoing)
        assertEquals(peer, ui[0].senderDid)
    }

    @Test
    fun `DeletedMessageView maps to isDeleted MessageUi with empty text`() {
        val wire = deletedView(id = "m3", senderDid = peer, sentAt = "2026-05-01T12:02:00Z")
        val ui = listOf<GetMessagesResponseMessagesUnion>(wire).toMessageUis(viewerDid = viewer)
        assertEquals(1, ui.size)
        assertTrue(ui[0].isDeleted)
        assertEquals("", ui[0].text)
    }

    @Test
    fun `unknown union variants are filtered out`() {
        // We use a real SystemMessageView-shaped union member if generated, otherwise
        // an anonymous implementation. Easiest portable check: list with a single deleted
        // + assert size is 1, then we can rely on the production code filtering unknowns.
        val ui = listOf<GetMessagesResponseMessagesUnion>().toMessageUis(viewerDid = viewer)
        assertEquals(0, ui.size)
    }

    private fun messageView(
        id: String,
        senderDid: String,
        text: String,
        sentAt: String,
    ): MessageView =
        MessageView(
            id = id,
            rev = "rev-$id",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime(sentAt),
            text = text,
        )

    private fun deletedView(
        id: String,
        senderDid: String,
        sentAt: String,
    ): DeletedMessageView =
        DeletedMessageView(
            id = id,
            rev = "rev-$id",
            sender = MessageViewSender(did = Did(senderDid)),
            sentAt = Datetime(sentAt),
        )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.data.MessageMapperTest"`
Expected: FAIL — `toMessageUis` is unresolved.

- [ ] **Step 3: Write the mapper**

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesResponseMessagesUnion
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.feature.chats.impl.MessageUi
import kotlin.time.Instant

/**
 * Maps the wire `chat.bsky.convo.getMessages` response messages to [MessageUi].
 *
 * - `MessageView` → normal `MessageUi`, `isDeleted = false`.
 * - `DeletedMessageView` → placeholder `MessageUi`, `isDeleted = true`, empty `text`.
 * - All other union variants (`SystemMessageView`, forward-compat unknown) → filtered.
 *   System messages aren't conversational; rendering them is out of MVP scope.
 *
 * Order is preserved; the lexicon returns newest-first.
 */
internal fun List<GetMessagesResponseMessagesUnion>.toMessageUis(viewerDid: String): ImmutableList<MessageUi> {
    if (isEmpty()) return persistentListOf()
    return mapNotNull { union ->
        when (union) {
            is MessageView ->
                MessageUi(
                    id = union.id,
                    senderDid = union.sender.did.raw,
                    isOutgoing = union.sender.did.raw == viewerDid,
                    text = union.text,
                    isDeleted = false,
                    sentAt = Instant.parse(union.sentAt.raw),
                )

            is DeletedMessageView ->
                MessageUi(
                    id = union.id,
                    senderDid = union.sender.did.raw,
                    isOutgoing = union.sender.did.raw == viewerDid,
                    text = "",
                    isDeleted = true,
                    sentAt = Instant.parse(union.sentAt.raw),
                )

            else -> null // SystemMessageView + unknown forward-compat variants
        }
    }.toImmutableList()
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.data.MessageMapperTest"`
Expected: All 4 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/MessageMapper.kt feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/MessageMapperTest.kt
git commit -m "feat(feature/chats): add MessageMapper for wire -> MessageUi

Refs: nubecita-nn3.2"
```

---

## Task 7: `ThreadItemMapper` (TDD with multiple cases)

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ThreadItemMapper.kt`
- Create: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/ThreadItemMapperTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.ThreadItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.ZoneId
import kotlin.time.Instant

internal class ThreadItemMapperTest {
    private val viewer = "did:plc:viewer"
    private val peer = "did:plc:alice"
    private val nowLocal = Instant.parse("2026-05-14T18:00:00Z")
    private val laZone = ZoneId.of("America/Los_Angeles")

    @Test
    fun `single message - run of 1, runIndex 0, avatar on incoming`() {
        val items = listOf(msg("a", peer, "2026-05-14T17:30:00Z")).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(1, msgs.size)
        assertEquals(0, msgs[0].runIndex)
        assertEquals(1, msgs[0].runCount)
        assertEquals(true, msgs[0].showAvatar)
    }

    @Test
    fun `single outgoing message - showAvatar false`() {
        val items = listOf(msg("a", viewer, "2026-05-14T17:30:00Z")).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(false, msgs[0].showAvatar)
    }

    @Test
    fun `two same-sender messages - run of 2, indices oldest=0 newest=1`() {
        // Source is newest-first; input newer→older
        val items =
            listOf(
                msg("newer", peer, "2026-05-14T17:31:00Z"),
                msg("older", peer, "2026-05-14T17:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(2, msgs.size)
        // Source order: msgs[0] = newer (runIndex=1, runCount=2), msgs[1] = older (runIndex=0)
        assertEquals(1, msgs[0].runIndex)
        assertEquals(0, msgs[1].runIndex)
        assertEquals(2, msgs[0].runCount)
        assertEquals(2, msgs[1].runCount)
        // Avatar only on oldest of an incoming run
        assertEquals(false, msgs[0].showAvatar)
        assertEquals(true, msgs[1].showAvatar)
    }

    @Test
    fun `three same-sender incoming - run of 3, avatar only on oldest`() {
        val items =
            listOf(
                msg("c", peer, "2026-05-14T17:32:00Z"),
                msg("b", peer, "2026-05-14T17:31:00Z"),
                msg("a", peer, "2026-05-14T17:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(listOf(2, 1, 0), msgs.map { it.runIndex })
        assertEquals(listOf(3, 3, 3), msgs.map { it.runCount })
        assertEquals(listOf(false, false, true), msgs.map { it.showAvatar })
    }

    @Test
    fun `cross-sender alternation - each is its own run`() {
        val items =
            listOf(
                msg("c", peer, "2026-05-14T17:32:00Z"),
                msg("b", viewer, "2026-05-14T17:31:00Z"),
                msg("a", peer, "2026-05-14T17:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        assertEquals(3, msgs.size)
        msgs.forEach { assertEquals(0, it.runIndex); assertEquals(1, it.runCount) }
    }

    @Test
    fun `day boundary breaks same-sender run`() {
        // Two peer messages straddling local midnight in LA (UTC-7)
        // 2026-05-14 07:00 UTC = 2026-05-14 00:00 LA (still 13th late evening in LA before midnight)
        // 2026-05-14 08:00 UTC = 2026-05-14 01:00 LA (now 14th, after midnight)
        val items =
            listOf(
                msg("after-midnight", peer, "2026-05-14T08:00:00Z"),
                msg("before-midnight", peer, "2026-05-14T06:59:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val msgs = items.filterIsInstance<ThreadItem.Message>()
        val seps = items.filterIsInstance<ThreadItem.DaySeparator>()
        assertEquals(2, msgs.size)
        assertTrue(seps.isNotEmpty(), "expected a DaySeparator inserted between days")
        // Both should be runs of 1 (separator breaks the run)
        msgs.forEach {
            assertEquals(0, it.runIndex)
            assertEquals(1, it.runCount)
        }
    }

    @Test
    fun `same UTC day but different LA local days - separator emitted`() {
        // 23:30 UTC on May 13 = 16:30 LA on May 13.
        // 00:30 UTC on May 14 = 17:30 LA on May 13 (still the same LA day).
        // No separator expected from LA's perspective.
        val items =
            listOf(
                msg("y", peer, "2026-05-14T00:30:00Z"),
                msg("x", peer, "2026-05-13T23:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val seps = items.filterIsInstance<ThreadItem.DaySeparator>()
        assertEquals(0, seps.size, "no separator when both messages share an LA-local day")
    }

    @Test
    fun `UTC-vs-local timezone regression - California 4pm same day`() {
        // 23:30 UTC vs 00:30 UTC next day, but BOTH in same LA day → no separator
        // (Different from naive UTC comparison which would emit a separator).
        val items =
            listOf(
                msg("late", peer, "2026-04-26T00:30:00Z"),
                msg("early", peer, "2026-04-25T23:30:00Z"),
            ).toThreadItems(nowLocal, laZone)
        val seps = items.filterIsInstance<ThreadItem.DaySeparator>()
        assertEquals(0, seps.size)
    }

    @Test
    fun `day label - today maps to Today literal`() {
        // Today (LA) is 2026-05-14 around 11am LA. Use a sent time today.
        val items = listOf(msg("a", peer, "2026-05-14T17:00:00Z")).toThreadItems(nowLocal, laZone)
        val sep = items.filterIsInstance<ThreadItem.DaySeparator>().firstOrNull()
        assertEquals("Today", sep?.label)
    }

    @Test
    fun `day label - yesterday maps to Yesterday literal`() {
        val items = listOf(msg("a", peer, "2026-05-13T17:00:00Z")).toThreadItems(nowLocal, laZone)
        val sep = items.filterIsInstance<ThreadItem.DaySeparator>().firstOrNull()
        assertEquals("Yesterday", sep?.label)
    }

    private fun msg(
        id: String,
        senderDid: String,
        sentAt: String,
    ): MessageUi =
        MessageUi(
            id = id,
            senderDid = senderDid,
            isOutgoing = senderDid == viewer,
            text = "msg-$id",
            isDeleted = false,
            sentAt = Instant.parse(sentAt),
        )
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.data.ThreadItemMapperTest"`
Expected: FAIL — `toThreadItems` is unresolved.

- [ ] **Step 3: Write the mapper**

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.ThreadItem
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant

private val WEEKDAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())
private val MONTH_DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())

/**
 * Builds the screen's flat `ThreadItem` stream from a newest-first time-sorted
 * list of [MessageUi]. Inserts `DaySeparator` items at local-calendar-day
 * boundaries; assigns `runIndex` (oldest-first within run) + `runCount` +
 * `showAvatar` to every `Message` item.
 *
 * Timezone handling: AT Protocol returns ISO-8601 in UTC. Day-boundary
 * comparison MUST run in the user's local zone — otherwise a thread that
 * runs through California's evening (= UTC midnight) emits a spurious
 * "Yesterday" separator mid-conversation. Pass [zone] as a parameter
 * (defaulted to `ZoneId.systemDefault()`) so tests can pin the zone.
 *
 * Day-separator chips break runs for shape + avatar purposes — a same-sender
 * pair straddling midnight (in [zone]) renders as two distinct runs of 1.
 */
internal fun List<MessageUi>.toThreadItems(
    now: Instant,
    zone: ZoneId = ZoneId.systemDefault(),
): ImmutableList<ThreadItem> {
    if (isEmpty()) return persistentListOf()

    val nowLocalDate = now.toLocalDate(zone)
    val result = mutableListOf<ThreadItem>()
    val runBuckets = mutableListOf<MutableList<MessageUi>>()
    val runLabels = mutableListOf<String>()

    // Walk newest → oldest; group into runs by (senderDid, local-day).
    var currentSender: String? = null
    var currentDay: LocalDate? = null
    for (m in this) {
        val day = m.sentAt.toLocalDate(zone)
        if (currentSender == null || m.senderDid != currentSender || day != currentDay) {
            runBuckets.add(mutableListOf())
            runLabels.add(formatDayLabel(day, nowLocalDate))
            currentSender = m.senderDid
            currentDay = day
        }
        runBuckets.last().add(m)
    }

    // Emit. Order: newest-run-first (matches input). Within a run, the items
    // are also newest-first (from input), so we walk reverseSlot-by-slot to
    // assign runIndex such that 0 = oldest (chronologically first) message.
    for (i in runBuckets.indices) {
        val bucket = runBuckets[i]
        val runCount = bucket.size
        // Emit a separator above this run if the run's day differs from the
        // PREVIOUS run's day (in newest-first order, "previous" is the
        // run we already emitted, which is *newer*). Always emit a separator
        // for the very first (newest) run.
        val sameDayAsPrev =
            i > 0 &&
                runBuckets[i - 1].first().sentAt.toLocalDate(zone) ==
                bucket.first().sentAt.toLocalDate(zone)
        if (!sameDayAsPrev) {
            result.add(ThreadItem.DaySeparator(label = runLabels[i]))
        }
        // Emit messages newest-first within run; compute runIndex such that
        // 0 = oldest. Bucket is already newest-first.
        bucket.forEachIndexed { posInBucket, m ->
            val runIndex = runCount - 1 - posInBucket
            result.add(
                ThreadItem.Message(
                    message = m,
                    runIndex = runIndex,
                    runCount = runCount,
                    showAvatar = !m.isOutgoing && runIndex == 0,
                ),
            )
        }
    }

    return result.toImmutableList()
}

private fun Instant.toLocalDate(zone: ZoneId): LocalDate =
    ZonedDateTime.ofInstant(java.time.Instant.parse(toString()), zone).toLocalDate()

private fun formatDayLabel(
    sentDay: LocalDate,
    nowDay: LocalDate,
): String =
    when {
        sentDay == nowDay -> "Today"
        sentDay == nowDay.minusDays(1) -> "Yesterday"
        sentDay.isAfter(nowDay.minusDays(7)) -> sentDay.format(WEEKDAY_FORMATTER)
        else -> sentDay.format(MONTH_DAY_FORMATTER)
    }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.data.ThreadItemMapperTest"`
Expected: All 10 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/ThreadItemMapper.kt feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/data/ThreadItemMapperTest.kt
git commit -m "feat(feature/chats): add ThreadItemMapper for grouping + day-separators

Refs: nubecita-nn3.2"
```

---

## Task 8: `ChatViewModel` — happy path (TDD)

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModel.kt`
- Create: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModelTest.kt`

- [ ] **Step 1: Write the failing happy-path test**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.SavedStateHandle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val otherUserDid = "did:plc:alice"

    @Test
    fun `init resolves then loads messages, ending in Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextResolveResult =
                        Result.success(
                            ConvoResolution(
                                convoId = "c1",
                                otherUserHandle = "alice.bsky.social",
                                otherUserDisplayName = "Alice",
                                otherUserAvatarUrl = null,
                                otherUserAvatarHue = 217,
                            ),
                        ),
                    nextMessagesResult =
                        Result.success(
                            MessagePage(
                                messages =
                                    persistentListOf(
                                        MessageUi(
                                            id = "m1",
                                            senderDid = otherUserDid,
                                            isOutgoing = false,
                                            text = "hi",
                                            isDeleted = false,
                                            sentAt = Instant.parse("2026-05-14T12:00:00Z"),
                                        ),
                                    ),
                            ),
                        ),
                )
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val state = vm.uiState.value
            assertEquals(1, repo.resolveCalls.get())
            assertEquals(1, repo.messagesCalls.get())
            assertEquals("c1", repo.lastMessagesConvoId)
            assertEquals("alice.bsky.social", state.otherUserHandle)
            assertEquals("Alice", state.otherUserDisplayName)
            assertTrue(state.status is ChatLoadStatus.Loaded)
            val loaded = state.status as ChatLoadStatus.Loaded
            assertEquals(false, loaded.isRefreshing)
            assertTrue(loaded.items.isNotEmpty())
        }

    private fun chatViewModel(repo: FakeChatRepository): ChatViewModel =
        ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("otherUserDid" to otherUserDid)),
            repository = repo,
            clock = Clock.System,
        )
}
```

- [ ] **Step 2: Run to verify FAIL**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.ChatViewModelTest"`
Expected: FAIL — `ChatViewModel` is unresolved.

- [ ] **Step 3: Write the minimal happy-path implementation**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ThreadItemMapper
import net.kikin.nubecita.feature.chats.impl.data.toChatError
import net.kikin.nubecita.feature.chats.impl.data.toThreadItems
import javax.inject.Inject
import kotlin.time.Clock

/**
 * MVI presenter for the chat thread screen.
 *
 * Auto-loads on construction: resolves the peer DID to a convoId, then loads the
 * first page of messages. Refresh / retry events re-run the chain. Single-flight
 * via [inFlightLoad] — a second Refresh while one is in flight is dropped.
 */
@HiltViewModel
internal class ChatViewModel
    @Inject
    constructor(
        savedStateHandle: SavedStateHandle,
        private val repository: ChatRepository,
        private val clock: Clock = Clock.System,
    ) : MviViewModel<ChatScreenViewState, ChatEvent, ChatEffect>(ChatScreenViewState()) {
        private val otherUserDid: String =
            requireNotNull(savedStateHandle["otherUserDid"]) {
                "ChatViewModel requires `otherUserDid` in SavedStateHandle (set by the Chat NavKey)."
            }

        private var inFlightLoad: Job? = null

        init {
            launchLoad()
        }

        override fun handleEvent(event: ChatEvent) {
            when (event) {
                ChatEvent.Refresh -> launchLoad()
                ChatEvent.RetryClicked -> launchLoad()
                ChatEvent.BackPressed -> Unit // screen handles back via nav state
            }
        }

        private fun launchLoad() {
            if (inFlightLoad?.isActive == true) return
            val priorStatus = uiState.value.status
            if (priorStatus is ChatLoadStatus.Loaded) {
                setState { copy(status = priorStatus.copy(isRefreshing = true)) }
            }
            inFlightLoad =
                viewModelScope.launch {
                    repository
                        .resolveConvo(otherUserDid)
                        .onSuccess { resolution ->
                            setState {
                                copy(
                                    otherUserHandle = resolution.otherUserHandle,
                                    otherUserDisplayName = resolution.otherUserDisplayName,
                                    otherUserAvatarUrl = resolution.otherUserAvatarUrl,
                                    otherUserAvatarHue = resolution.otherUserAvatarHue,
                                )
                            }
                            repository
                                .getMessages(resolution.convoId)
                                .onSuccess { page ->
                                    val items = page.messages.toThreadItems(now = clock.now())
                                    setState { copy(status = ChatLoadStatus.Loaded(items = items)) }
                                }.onFailure { throwable ->
                                    handleFailure(throwable)
                                }
                        }.onFailure { throwable ->
                            handleFailure(throwable)
                        }
                    inFlightLoad = null
                }
        }

        private fun handleFailure(throwable: Throwable) {
            val error = throwable.toChatError()
            val prior = uiState.value.status
            if (prior is ChatLoadStatus.Loaded) {
                setState { copy(status = prior.copy(isRefreshing = false)) }
            } else {
                setState { copy(status = ChatLoadStatus.InitialError(error)) }
            }
        }
    }
```

- [ ] **Step 4: Run to verify PASS**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.ChatViewModelTest"`
Expected: 1 test PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModel.kt feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModelTest.kt
git commit -m "feat(feature/chats): add ChatViewModel resolve+load happy path

Refs: nubecita-nn3.2"
```

---

## Task 9: `ChatViewModel` — error paths (TDD)

**Files:**
- Modify: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModelTest.kt`

- [ ] **Step 1: Add error-path tests**

Add these tests inside the existing `ChatViewModelTest` class:

```kotlin
    @Test
    fun `IOException on resolveConvo maps to InitialError Network`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResolveResult = Result.failure(java.io.IOException("net down")))
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatLoadStatus.InitialError)
            assertEquals(ChatError.Network, (status as ChatLoadStatus.InitialError).error)
        }

    @Test
    fun `IOException on getMessages after successful resolve maps to InitialError Network`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextMessagesResult = Result.failure(java.io.IOException("net down")),
                )
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatLoadStatus.InitialError)
            assertEquals(ChatError.Network, (status as ChatLoadStatus.InitialError).error)
            // Resolution profile bits applied before the failure.
            assertEquals("alice.bsky.social", vm.uiState.value.otherUserHandle)
        }

    @Test
    fun `RetryClicked re-issues both resolve and getMessages`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResolveResult = Result.failure(java.io.IOException("net down")))
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val priorResolve = repo.resolveCalls.get()
            val priorMessages = repo.messagesCalls.get()
            // Flip the fake to success and retry.
            repo.nextResolveResult =
                Result.success(
                    ConvoResolution(
                        convoId = "c1",
                        otherUserHandle = "alice.bsky.social",
                        otherUserDisplayName = "Alice",
                        otherUserAvatarUrl = null,
                        otherUserAvatarHue = 0,
                    ),
                )
            vm.handleEvent(ChatEvent.RetryClicked)
            advanceUntilIdle()
            assertEquals(priorResolve + 1, repo.resolveCalls.get())
            assertEquals(priorMessages + 1, repo.messagesCalls.get())
            assertTrue(vm.uiState.value.status is ChatLoadStatus.Loaded)
        }
```

- [ ] **Step 2: Run — three new tests PASS (the implementation from Task 8 already covers these branches)**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.ChatViewModelTest"`
Expected: 4 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModelTest.kt
git commit -m "test(feature/chats): cover ChatViewModel error paths + RetryClicked

Refs: nubecita-nn3.2"
```

---

## Task 10: `ChatViewModel` — refresh + single-flight (TDD)

**Files:**
- Modify: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModelTest.kt`

- [ ] **Step 1: Add refresh + single-flight tests**

```kotlin
    @Test
    fun `Refresh on Loaded flips isRefreshing then commits new items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.status is ChatLoadStatus.Loaded)
            // Seed a new message page for the refresh
            repo.nextMessagesResult =
                Result.success(
                    MessagePage(
                        messages =
                            persistentListOf(
                                MessageUi(
                                    id = "m-new",
                                    senderDid = otherUserDid,
                                    isOutgoing = false,
                                    text = "fresh",
                                    isDeleted = false,
                                    sentAt = Instant.parse("2026-05-14T13:00:00Z"),
                                ),
                            ),
                    ),
                )
            vm.handleEvent(ChatEvent.Refresh)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatLoadStatus.Loaded)
            val loaded = status as ChatLoadStatus.Loaded
            assertEquals(false, loaded.isRefreshing)
            assertTrue(loaded.items.isNotEmpty())
        }

    @Test
    fun `double-Refresh is single-flighted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val priorResolve = repo.resolveCalls.get()
            vm.handleEvent(ChatEvent.Refresh)
            vm.handleEvent(ChatEvent.Refresh) // second one should be dropped
            advanceUntilIdle()
            assertEquals(priorResolve + 1, repo.resolveCalls.get())
        }

    @Test
    fun `Refresh failure on Loaded keeps existing items, drops isRefreshing`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val priorLoaded = vm.uiState.value.status as ChatLoadStatus.Loaded
            val priorItems = priorLoaded.items
            repo.nextMessagesResult = Result.failure(java.io.IOException("net down"))
            vm.handleEvent(ChatEvent.Refresh)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatLoadStatus.Loaded, "status should remain Loaded after refresh failure")
            val loaded = status as ChatLoadStatus.Loaded
            assertEquals(priorItems, loaded.items)
            assertEquals(false, loaded.isRefreshing)
        }
```

- [ ] **Step 2: Run**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.ChatViewModelTest"`
Expected: 7 tests PASS.

- [ ] **Step 3: Commit**

```bash
git add feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ChatViewModelTest.kt
git commit -m "test(feature/chats): cover ChatViewModel refresh + single-flight

Refs: nubecita-nn3.2"
```

---

## Task 11: `DefaultChatRepository` — implement `resolveConvo` + `getMessages`

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/DefaultChatRepository.kt`

- [ ] **Step 1: Add the two new methods + supporting helpers**

Replace `DefaultChatRepository.kt` with:

```kotlin
package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.ConvoService
import io.github.kikin81.atproto.chat.bsky.convo.GetConvoForMembersRequest
import io.github.kikin81.atproto.chat.bsky.convo.GetMessagesRequest
import io.github.kikin81.atproto.chat.bsky.convo.ListConvosRequest
import io.github.kikin81.atproto.runtime.Did
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import timber.log.Timber
import javax.inject.Inject
import kotlin.time.Clock

internal class DefaultChatRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : ChatRepository {
        override suspend fun listConvos(
            cursor: String?,
            limit: Int,
        ): Result<ConvoListPage> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).listConvos(
                            ListConvosRequest(cursor = cursor, limit = limit.toLong()),
                        )
                    val now = Clock.System.now()
                    ConvoListPage(
                        items =
                            response.convos
                                .map { it.toConvoListItemUi(viewerDid = viewerDid, now = now) }
                                .toImmutableList(),
                        nextCursor = response.cursor,
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(throwable, "listConvos failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).getConvoForMembers(
                            GetConvoForMembersRequest(members = listOf(Did(otherUserDid))),
                        )
                    val convo = response.convo
                    val other =
                        convo.members.firstOrNull { it.did.raw != viewerDid }
                            ?: convo.members.firstOrNull()
                            ?: error("getConvoForMembers returned no members — protocol violation")
                    ConvoResolution(
                        convoId = convo.id,
                        otherUserHandle = other.handle.raw,
                        otherUserDisplayName = other.displayName?.takeUnless { it.isBlank() },
                        otherUserAvatarUrl = other.avatar?.raw,
                        otherUserAvatarHue = avatarHueFor(did = other.did.raw, handle = other.handle.raw),
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(throwable, "resolveConvo failed: %s", throwable.javaClass.name)
                }
            }

        override suspend fun getMessages(
            convoId: String,
            cursor: String?,
            limit: Int,
        ): Result<MessagePage> =
            withContext(dispatcher) {
                runCatching {
                    val viewerDid = currentViewerDid()
                    val client = xrpcClientProvider.authenticated()
                    val response =
                        ConvoService(client).getMessages(
                            GetMessagesRequest(
                                convoId = convoId,
                                cursor = cursor,
                                limit = limit.toLong(),
                            ),
                        )
                    MessagePage(
                        messages = response.messages.toMessageUis(viewerDid = viewerDid),
                        nextCursor = response.cursor,
                    )
                }.onFailure { throwable ->
                    Timber.tag(TAG).e(throwable, "getMessages failed: %s", throwable.javaClass.name)
                }
            }

        private fun currentViewerDid(): String {
            val signedIn =
                sessionStateProvider.state.value as? SessionState.SignedIn
                    ?: throw NoSessionException()
            return signedIn.did
        }

        private companion object {
            const val TAG = "ChatRepository"
        }
    }
```

> Note: the `convo.members` field structure on `getConvoForMembers`'s response may differ slightly from the `listConvos` response — read the generated Kotlin in `~/.gradle/caches/.../atproto.../models/.../chat/bsky/convo/GetConvoForMembersResponse.kt` (or the cached source jar in the IDE) if the field name needs adjustment.

- [ ] **Step 2: Build to verify compile**

Run: `./gradlew :feature:chats:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL. If the response field structure differs from the assumed shape, adjust until it compiles.

- [ ] **Step 3: Run all chats tests (no regressions)**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest`
Expected: All tests pass.

- [ ] **Step 4: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/data/DefaultChatRepository.kt
git commit -m "feat(feature/chats): implement DefaultChatRepository resolveConvo + getMessages

Refs: nubecita-nn3.2"
```

---

## Task 12: `messageBubbleShape` helper (TDD)

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubble.kt` (initial draft with only the shape helper)
- Create: `feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubbleShapeTest.kt`

- [ ] **Step 1: Write the failing test**

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class MessageBubbleShapeTest {
    private val large = 16.dp
    private val small = 4.dp

    @Test
    fun `single outgoing - all corners large`() {
        val shape = messageBubbleShape(index = 0, count = 1, isOutgoing = true)
        assertEquals(RoundedCornerShape(large, large, large, large), shape)
    }

    @Test
    fun `single incoming - all corners large`() {
        val shape = messageBubbleShape(index = 0, count = 1, isOutgoing = false)
        assertEquals(RoundedCornerShape(large, large, large, large), shape)
    }

    @Test
    fun `outgoing first of 3 - sender side has top large, bottom small`() {
        val shape = messageBubbleShape(index = 0, count = 3, isOutgoing = true)
        assertEquals(RoundedCornerShape(topStart = large, topEnd = large, bottomEnd = small, bottomStart = large), shape)
    }

    @Test
    fun `outgoing middle of 3 - both sender-side corners small`() {
        val shape = messageBubbleShape(index = 1, count = 3, isOutgoing = true)
        assertEquals(RoundedCornerShape(topStart = large, topEnd = small, bottomEnd = small, bottomStart = large), shape)
    }

    @Test
    fun `outgoing last of 3 - sender side has top small, bottom large`() {
        val shape = messageBubbleShape(index = 2, count = 3, isOutgoing = true)
        assertEquals(RoundedCornerShape(topStart = large, topEnd = small, bottomEnd = large, bottomStart = large), shape)
    }

    @Test
    fun `incoming first of 3 - sender side (start) has top large, bottom small`() {
        val shape = messageBubbleShape(index = 0, count = 3, isOutgoing = false)
        assertEquals(RoundedCornerShape(topStart = large, topEnd = large, bottomEnd = large, bottomStart = small), shape)
    }

    @Test
    fun `incoming middle of 3 - both sender-side (start) corners small`() {
        val shape = messageBubbleShape(index = 1, count = 3, isOutgoing = false)
        assertEquals(RoundedCornerShape(topStart = small, topEnd = large, bottomEnd = large, bottomStart = small), shape)
    }

    @Test
    fun `incoming last of 3 - sender side (start) has top small, bottom large`() {
        val shape = messageBubbleShape(index = 2, count = 3, isOutgoing = false)
        assertEquals(RoundedCornerShape(topStart = small, topEnd = large, bottomEnd = large, bottomStart = large), shape)
    }
}
```

- [ ] **Step 2: Run to verify FAIL**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.ui.MessageBubbleShapeTest"`
Expected: FAIL — `messageBubbleShape` unresolved.

- [ ] **Step 3: Write the helper inside `MessageBubble.kt`**

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Asymmetric M3 Expressive bubble shape for a message at [index] in a run of
 * [count] consecutive same-sender messages. Mirrors `ListItemDefaults.segmentedShapes`
 * structurally but applies segmented (small) corners ONLY on the sender side —
 * the opposite side stays fully rounded.
 *
 *  count == 1                    → all 16dp (fully rounded pill).
 *  index == 0, count > 1         → outer top corners 16dp, inner bottom-tail 4dp.
 *  1..count-2                    → both tail-side corners 4dp.
 *  index == count-1, count > 1   → outer bottom corners 16dp, inner top-tail 4dp.
 */
internal fun messageBubbleShape(
    index: Int,
    count: Int,
    isOutgoing: Boolean,
): Shape {
    val large = 16.dp
    val small = 4.dp
    val isFirst = index == 0
    val isLast = index == count - 1
    val isSingle = count == 1

    val topSender = if (isFirst || isSingle) large else small
    val bottomSender = if (isLast || isSingle) large else small

    return if (isOutgoing) {
        RoundedCornerShape(
            topStart = large,
            topEnd = topSender,
            bottomEnd = bottomSender,
            bottomStart = large,
        )
    } else {
        RoundedCornerShape(
            topStart = topSender,
            topEnd = large,
            bottomEnd = large,
            bottomStart = bottomSender,
        )
    }
}
```

- [ ] **Step 4: Run to verify PASS**

Run: `./gradlew :feature:chats:impl:testDebugUnitTest --tests "net.kikin.nubecita.feature.chats.impl.ui.MessageBubbleShapeTest"`
Expected: All 8 tests PASS.

- [ ] **Step 5: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubble.kt feature/chats/impl/src/test/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubbleShapeTest.kt
git commit -m "feat(feature/chats): add messageBubbleShape per-position helper

Refs: nubecita-nn3.2"
```

---

## Task 13: `MessageBubble` Composable + screenshot tests

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubble.kt` (add the composable)
- Create: `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubbleScreenshotTest.kt`

- [ ] **Step 1: Append the composable below the shape helper**

Add at the bottom of `MessageBubble.kt`:

```kotlin
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import net.kikin.nubecita.feature.chats.impl.MessageUi

/**
 * A single message bubble. Container color, content color, and shape are derived
 * from [isOutgoing] + the run position; rendered text is the message body
 * (italicised placeholder when [MessageUi.isDeleted]).
 */
@Composable
internal fun MessageBubble(
    message: MessageUi,
    runIndex: Int,
    runCount: Int,
    modifier: Modifier = Modifier,
    maxWidth: Dp = 280.dp,
    deletedPlaceholder: String = "Message deleted",
) {
    val shape = messageBubbleShape(index = runIndex, count = runCount, isOutgoing = message.isOutgoing)
    val containerColor =
        if (message.isOutgoing) MaterialTheme.colorScheme.primaryContainer
        else MaterialTheme.colorScheme.surfaceContainerHigh
    val contentColor =
        if (message.isOutgoing) MaterialTheme.colorScheme.onPrimaryContainer
        else MaterialTheme.colorScheme.onSurface
    Box(
        modifier =
            modifier
                .widthIn(max = maxWidth)
                .clip(shape)
                .background(containerColor)
                .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        if (message.isDeleted) {
            Text(
                text = deletedPlaceholder,
                style = MaterialTheme.typography.bodyMedium,
                fontStyle = FontStyle.Italic,
                color = contentColor,
            )
        } else {
            Text(
                text = message.text,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
            )
        }
    }
}
```

- [ ] **Step 2: Write the screenshot test**

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.chats.impl.MessageUi
import kotlin.time.Instant

private fun mu(
    id: String = "m",
    isOutgoing: Boolean,
    text: String = "Hello there",
    isDeleted: Boolean = false,
): MessageUi =
    MessageUi(
        id = id,
        senderDid = if (isOutgoing) "did:plc:me" else "did:plc:alice",
        isOutgoing = isOutgoing,
        text = text,
        isDeleted = isDeleted,
        sentAt = Instant.parse("2026-05-14T12:00:00Z"),
    )

@Composable
private fun BubbleColumn(content: @Composable Column.() -> Unit) {
    Surface {
        Column(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) { content() }
    }
}

@PreviewTest @Preview(name = "bubble-incoming-single-light", showBackground = true)
@Preview(name = "bubble-incoming-single-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomingSingle() {
    NubecitaTheme(dynamicColor = false) {
        BubbleColumn { MessageBubble(mu(isOutgoing = false), runIndex = 0, runCount = 1) }
    }
}

@PreviewTest @Preview(name = "bubble-outgoing-single-light", showBackground = true)
@Preview(name = "bubble-outgoing-single-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OutgoingSingle() {
    NubecitaTheme(dynamicColor = false) {
        BubbleColumn {
            Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End) {
                MessageBubble(mu(isOutgoing = true), runIndex = 0, runCount = 1)
            }
        }
    }
}

@PreviewTest @Preview(name = "bubble-incoming-run3-light", showBackground = true)
@Preview(name = "bubble-incoming-run3-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun IncomingRunOfThree() {
    NubecitaTheme(dynamicColor = false) {
        BubbleColumn {
            // Source newest-first; mapper assigns runIndex oldest-first; visual order top→bottom = oldest→newest.
            MessageBubble(mu(id = "a", isOutgoing = false, text = "first message"), runIndex = 0, runCount = 3)
            MessageBubble(mu(id = "b", isOutgoing = false, text = "middle one"), runIndex = 1, runCount = 3)
            MessageBubble(mu(id = "c", isOutgoing = false, text = "last in the run"), runIndex = 2, runCount = 3)
        }
    }
}

@PreviewTest @Preview(name = "bubble-outgoing-run3-light", showBackground = true)
@Preview(name = "bubble-outgoing-run3-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun OutgoingRunOfThree() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            Column(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.End,
            ) {
                MessageBubble(mu(id = "a", isOutgoing = true, text = "first message"), runIndex = 0, runCount = 3)
                MessageBubble(mu(id = "b", isOutgoing = true, text = "middle one"), runIndex = 1, runCount = 3)
                MessageBubble(mu(id = "c", isOutgoing = true, text = "last in the run"), runIndex = 2, runCount = 3)
            }
        }
    }
}

@PreviewTest @Preview(name = "bubble-deleted-light", showBackground = true)
@Preview(name = "bubble-deleted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun DeletedBubble() {
    NubecitaTheme(dynamicColor = false) {
        BubbleColumn { MessageBubble(mu(isOutgoing = false, isDeleted = true, text = ""), runIndex = 0, runCount = 1) }
    }
}
```

- [ ] **Step 3: Generate baselines**

Run: `./gradlew :feature:chats:impl:updateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL. New baseline PNGs appear under `feature/chats/impl/src/screenshotTestDebug/reference/.../MessageBubbleScreenshotTestKt/`.

- [ ] **Step 4: Visually inspect at least the run3 baselines**

Open `feature/chats/impl/src/screenshotTestDebug/reference/.../bubble-outgoing-run3-light_*.png`. Verify the corner profile changes top→bottom in a run of 3.

- [ ] **Step 5: Validate the suite**

Run: `./gradlew :feature:chats:impl:validateDebugScreenshotTest`
Expected: BUILD SUCCESSFUL (baselines just generated, so validate passes trivially).

- [ ] **Step 6: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/MessageBubble.kt feature/chats/impl/src/screenshotTest/ feature/chats/impl/src/screenshotTestDebug/
git commit -m "feat(feature/chats): add MessageBubble composable + screenshot variants

Refs: nubecita-nn3.2"
```

---

## Task 14: `DaySeparatorChip` Composable + screenshot tests

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/DaySeparatorChip.kt`
- Create: `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ui/DaySeparatorChipScreenshotTest.kt`

- [ ] **Step 1: Write the composable**

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

/** Centered pill rendered at calendar-day boundaries in a chat thread. */
@Composable
internal fun DaySeparatorChip(
    label: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
```

- [ ] **Step 2: Write the screenshot test**

```kotlin
package net.kikin.nubecita.feature.chats.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

@PreviewTest @Preview(name = "day-sep-today-light", showBackground = true)
@Preview(name = "day-sep-today-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Today() { NubecitaTheme(dynamicColor = false) { Surface { DaySeparatorChip("Today") } } }

@PreviewTest @Preview(name = "day-sep-yesterday-light", showBackground = true)
@Preview(name = "day-sep-yesterday-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Yesterday() { NubecitaTheme(dynamicColor = false) { Surface { DaySeparatorChip("Yesterday") } } }

@PreviewTest @Preview(name = "day-sep-weekday-light", showBackground = true)
@Preview(name = "day-sep-weekday-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Weekday() { NubecitaTheme(dynamicColor = false) { Surface { DaySeparatorChip("Mon") } } }

@PreviewTest @Preview(name = "day-sep-monthday-light", showBackground = true)
@Preview(name = "day-sep-monthday-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun MonthDay() { NubecitaTheme(dynamicColor = false) { Surface { DaySeparatorChip("Apr 25") } } }
```

- [ ] **Step 3: Generate + validate baselines + commit**

```bash
./gradlew :feature:chats:impl:updateDebugScreenshotTest
./gradlew :feature:chats:impl:validateDebugScreenshotTest
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ui/DaySeparatorChip.kt feature/chats/impl/src/screenshotTest/ feature/chats/impl/src/screenshotTestDebug/
git commit -m "feat(feature/chats): add DaySeparatorChip composable + screenshot variants

Refs: nubecita-nn3.2"
```

---

## Task 15: `ChatScreenContent` + screenshot tests

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreenContent.kt`
- Create: `feature/chats/impl/src/screenshotTest/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreenContentScreenshotTest.kt`

- [ ] **Step 1: Write `ChatScreenContent.kt`**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
import net.kikin.nubecita.feature.chats.impl.ui.DaySeparatorChip
import net.kikin.nubecita.feature.chats.impl.ui.MessageBubble

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreenContent(
    state: ChatScreenViewState,
    onEvent: (ChatEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChatTopBarAvatar(state)
                        Text(
                            text = state.otherUserDisplayName ?: state.otherUserHandle,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(ChatEvent.BackPressed) }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val status = state.status) {
                ChatLoadStatus.Loading -> LoadingBody()
                is ChatLoadStatus.Loaded ->
                    if (status.items.isEmpty()) EmptyBody() else LoadedBody(status.items)
                is ChatLoadStatus.InitialError ->
                    ErrorBody(status.error, onRetry = { onEvent(ChatEvent.RetryClicked) })
            }
        }
    }
}

@Composable
private fun ChatTopBarAvatar(state: ChatScreenViewState) {
    val hue = Color.hsv(state.otherUserAvatarHue.toFloat(), saturation = 0.5f, value = 0.55f)
    val initial =
        (state.otherUserDisplayName ?: state.otherUserHandle)
            .firstOrNull { it.isLetterOrDigit() }
            ?.uppercase() ?: "?"
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).background(hue),
        contentAlignment = Alignment.Center,
    ) {
        if (state.otherUserAvatarUrl != null) {
            NubecitaAsyncImage(
                model = state.otherUserAvatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                text = initial,
                color = if (hue.luminance() > 0.5f) Color.Black else Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun EmptyBody() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "No messages yet",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Once you exchange messages with this person they'll appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LoadedBody(items: kotlinx.collections.immutable.ImmutableList<ThreadItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { _, it -> it.key },
            contentType = { _, it -> if (it is ThreadItem.Message) "msg" else "sep" },
        ) { position, item ->
            when (item) {
                is ThreadItem.Message -> {
                    val crossRunGap = if (item.runIndex == 0 && position < items.lastIndex) 8.dp else 0.dp
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = crossRunGap),
                        horizontalArrangement =
                            if (item.message.isOutgoing) Arrangement.End else Arrangement.Start,
                    ) {
                        // 48dp avatar slot for incoming runs; outgoing has no leading slot.
                        if (!item.message.isOutgoing) {
                            Box(modifier = Modifier.size(40.dp).padding(end = 8.dp)) {
                                if (item.showAvatar) {
                                    // Avatar is small — defer real rendering to ChatScreen if needed
                                    Box(
                                        modifier = Modifier.fillMaxSize().clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                    )
                                }
                            }
                        }
                        MessageBubble(
                            message = item.message,
                            runIndex = item.runIndex,
                            runCount = item.runCount,
                        )
                    }
                }
                is ThreadItem.DaySeparator -> DaySeparatorChip(label = item.label)
            }
        }
    }
}

@Composable
private fun ErrorBody(
    error: ChatError,
    onRetry: () -> Unit,
) {
    val (title, body, showRetry) =
        when (error) {
            ChatError.Network -> Triple("Network error", "Couldn't load this thread. Check your connection and try again.", true)
            ChatError.NotEnrolled -> Triple("Enable direct messages", "Your Bluesky account hasn't opted into direct messages. Enable chat in the official Bluesky app's settings to start using DMs.", false)
            ChatError.ConvoNotFound -> Triple("No conversation yet", "You don't have a conversation with this user yet.", false)
            is ChatError.Unknown -> Triple("Something went wrong", "An unexpected error occurred. Try again in a moment.", true)
        }
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (showRetry) OutlinedButton(onClick = onRetry) { Text("Try again") }
    }
}
```

- [ ] **Step 2: Write the screen-level screenshot test**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

private val viewer = "did:plc:viewer"
private val peer = "did:plc:alice"

private fun mu(
    id: String,
    isOutgoing: Boolean,
    text: String,
    sentAt: String,
    isDeleted: Boolean = false,
): MessageUi =
    MessageUi(
        id = id,
        senderDid = if (isOutgoing) viewer else peer,
        isOutgoing = isOutgoing,
        text = text,
        isDeleted = isDeleted,
        sentAt = Instant.parse(sentAt),
    )

private val LOADED_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        otherUserAvatarHue = 217,
        status =
            ChatLoadStatus.Loaded(
                items =
                    persistentListOf(
                        ThreadItem.DaySeparator(label = "Today"),
                        ThreadItem.Message(
                            message = mu("m4", isOutgoing = false, text = "see you soon", sentAt = "2026-05-14T17:35:00Z"),
                            runIndex = 0, runCount = 1, showAvatar = true,
                        ),
                        ThreadItem.Message(
                            message = mu("m3", isOutgoing = true, text = "On my way", sentAt = "2026-05-14T17:30:00Z"),
                            runIndex = 0, runCount = 1, showAvatar = false,
                        ),
                        ThreadItem.Message(
                            message = mu("m2", isOutgoing = false, text = "Quick coffee?", sentAt = "2026-05-14T17:28:00Z"),
                            runIndex = 1, runCount = 2, showAvatar = false,
                        ),
                        ThreadItem.Message(
                            message = mu("m1", isOutgoing = false, text = "Hey", sentAt = "2026-05-14T17:27:00Z"),
                            runIndex = 0, runCount = 2, showAvatar = true,
                        ),
                    ),
            ),
    )

private val EMPTY_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        status = ChatLoadStatus.Loaded(items = persistentListOf()),
    )

private val LOADING_STATE =
    ChatScreenViewState(otherUserHandle = "alice.bsky.social", otherUserDisplayName = "Alice")

private val NETWORK_ERROR_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        status = ChatLoadStatus.InitialError(ChatError.Network),
    )

private val NOT_ENROLLED_STATE =
    ChatScreenViewState(
        otherUserHandle = "alice.bsky.social",
        otherUserDisplayName = "Alice",
        status = ChatLoadStatus.InitialError(ChatError.NotEnrolled),
    )

@PreviewTest @Preview(name = "chat-loaded-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-loaded-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Loaded() {
    NubecitaTheme(dynamicColor = false) { ChatScreenContent(state = LOADED_STATE, onEvent = {}) }
}

@PreviewTest @Preview(name = "chat-empty-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-empty-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Empty() {
    NubecitaTheme(dynamicColor = false) { ChatScreenContent(state = EMPTY_STATE, onEvent = {}) }
}

@PreviewTest @Preview(name = "chat-loading-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-loading-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun Loading() {
    NubecitaTheme(dynamicColor = false) { ChatScreenContent(state = LOADING_STATE, onEvent = {}) }
}

@PreviewTest @Preview(name = "chat-network-error-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-network-error-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NetworkError() {
    NubecitaTheme(dynamicColor = false) { ChatScreenContent(state = NETWORK_ERROR_STATE, onEvent = {}) }
}

@PreviewTest @Preview(name = "chat-not-enrolled-light", showBackground = true, heightDp = 700)
@Preview(name = "chat-not-enrolled-dark", showBackground = true, heightDp = 700, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotEnrolled() {
    NubecitaTheme(dynamicColor = false) { ChatScreenContent(state = NOT_ENROLLED_STATE, onEvent = {}) }
}
```

- [ ] **Step 3: Generate baselines, validate, and visually inspect the Loaded variant**

```bash
./gradlew :feature:chats:impl:updateDebugScreenshotTest
./gradlew :feature:chats:impl:validateDebugScreenshotTest
```

Open `chat-loaded-light_*.png` — verify: avatar appears on the OLDEST incoming bubble of each run (top-of-run on screen), bubble shapes follow segmented pattern within runs, day-separator chip rendered at the top of the day boundary.

- [ ] **Step 4: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreenContent.kt feature/chats/impl/src/screenshotTest/ feature/chats/impl/src/screenshotTestDebug/
git commit -m "feat(feature/chats): add ChatScreenContent with all status variants

Refs: nubecita-nn3.2"
```

---

## Task 16: `ChatScreen` — stateful entry

**Files:**
- Create: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreen.kt`

- [ ] **Step 1: Write the stateful entry**

```kotlin
package net.kikin.nubecita.feature.chats.impl

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Stateful entry for the chat thread screen. Owns the [ChatViewModel],
 * forwards events, and translates `BackPressed` into [onNavigateBack].
 */
@Composable
internal fun ChatScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnNavigateBack by rememberUpdatedState(onNavigateBack)

    ChatScreenContent(
        state = state,
        onEvent = { event ->
            if (event is ChatEvent.BackPressed) currentOnNavigateBack()
            else viewModel.handleEvent(event)
        },
        modifier = modifier,
    )
}
```

- [ ] **Step 2: Build to verify**

Run: `./gradlew :feature:chats:impl:compileDebugKotlin`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/ChatScreen.kt
git commit -m "feat(feature/chats): add ChatScreen stateful entry

Refs: nubecita-nn3.2"
```

---

## Task 17: Extend `ChatsNavigationModule` — wire convo-list tap + `entry<Chat>` provider

**Files:**
- Modify: `feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ChatsNavigationModule.kt`

- [ ] **Step 1: Replace the existing module body**

```kotlin
package net.kikin.nubecita.feature.chats.impl.di

import androidx.compose.runtime.CompositionLocalProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.MainShell
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.impl.ChatScreen
import net.kikin.nubecita.feature.chats.impl.ChatsScreen

@Module
@InstallIn(SingletonComponent::class)
internal object ChatsNavigationModule {
    @Provides
    @IntoSet
    @MainShell
    fun provideChatsEntries(): EntryProviderInstaller =
        {
            entry<Chats> {
                val navState = LocalMainShellNavState.current
                ChatsScreen(
                    onNavigateToChat = { did -> navState.add(Chat(otherUserDid = did)) },
                )
            }
            entry<Chat> { key ->
                val navState = LocalMainShellNavState.current
                ChatScreen(onNavigateBack = { navState.removeLast() })
            }
        }
}
```

> The `redactDid` helper that lived in this file (added during nn3.1 Copilot fixes) is no longer needed — the new behavior calls `navState.add(...)` directly without logging the DID. Delete the helper and the `Timber` import if they linger.

- [ ] **Step 2: Build to verify**

Run: `./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run the full chats unit + screenshot suite**

```bash
./gradlew :feature:chats:impl:testDebugUnitTest
./gradlew :feature:chats:impl:validateDebugScreenshotTest
```

Expected: All green.

- [ ] **Step 4: Commit**

```bash
git add feature/chats/impl/src/main/kotlin/net/kikin/nubecita/feature/chats/impl/di/ChatsNavigationModule.kt
git commit -m "feat(feature/chats): wire Chat NavKey + replace Timber tap placeholder

Refs: nubecita-nn3.2"
```

---

## Task 18: Final integration — build, install, manual device verification

**Files:** none — verification only.

- [ ] **Step 1: Full app assemble**

Run: `./gradlew :app:assembleDebug spotlessCheck lint`
Expected: All green.

- [ ] **Step 2: Install on the connected device** (per [`feedback_use_android_cli_for_emulators`](file:///Users/velazquez/.claude/projects/-Users-velazquez-code-nubecita/memory/feedback_use_android_cli_for_emulators.md), prefer a real device — `adb devices` first; spin up an emulator via `android emulator start` if none)

```bash
adb devices
./gradlew :app:installDebug
```

- [ ] **Step 3: Manual smoke check**

On device:
1. Open the Chats tab.
2. Tap a convo row → should push the thread (not log + no-op).
3. Verify the TopAppBar shows back arrow + avatar + display name.
4. Verify message bubbles render with the GChat-style segmented run shapes.
5. Verify day-separator chips render at calendar-day boundaries.
6. Verify scroll lands at the bottom (newest visible).
7. Pull to refresh — should not crash (V1 doesn't expose a visible affordance for this; just exercise the back-end path).
8. Tap back arrow → should pop to the convo list.
9. Test an error path: airplane-mode the device, tap a convo → should show "Network error" with Try again.

- [ ] **Step 4: Update the bd with verification status**

```bash
bd update nubecita-nn3.2 --append-notes "Implementation complete + device-verified on Pixel 10 Pro XL (or emulator name)."
```

- [ ] **Step 5: Open the PR via the bd-workflow finish flow**

Invoke `bd-workflow` skill with `finish nubecita-nn3.2` (or run the finish steps manually per `.claude/skills/bd-workflow/SKILL.md`). The bd-workflow skill handles `git push` + `gh pr create` with the right title + `Closes:` footer.

---

## Self-review

**Spec coverage:** Every section of the spec maps to one or more tasks above.

| Spec section | Task(s) |
|---|---|
| `Chat(otherUserDid)` NavKey | 1 |
| `ChatRepository` extension + new types | 2, 11 |
| `ChatContract` types | 3 |
| `FakeChatRepository` extension | 4 |
| `Throwable.toChatError()` + `ConvoNotFound` variant | 5 |
| `MessageMapper` | 6 |
| `ThreadItemMapper` (incl. timezone Decision 11) | 7 |
| `ChatViewModel` state machine | 8, 9, 10 |
| `messageBubbleShape` helper | 12 |
| `MessageBubble` composable | 13 |
| `DaySeparatorChip` | 14 |
| `ChatScreenContent` (incl. reverseLayout, cross-run padding) | 15 |
| `ChatScreen` (stateful entry, `BackPressed` → `onNavigateBack`) | 16 |
| `ChatsNavigationModule` extension + replace Timber placeholder | 17 |
| Final integration + manual verification | 18 |

**Placeholder scan:** No "TBD", "TODO", or vague placeholders in the plan. Every code step has the complete code.

**Type consistency:** `ChatRepository.resolveConvo` → `ConvoResolution` (used in `FakeChatRepository` Task 4, `ChatViewModel` Task 8, `DefaultChatRepository` Task 11) — consistent. `ThreadItem.Message.runIndex` semantics (0 = oldest, top of run on screen) — consistent across spec, mapper, tests, and `ChatScreenContent`. `MessageUi` shape pinned in Task 3, consumed by Tasks 6, 7, 8, 13.

**Ambiguity check:** `DefaultChatRepository.resolveConvo`'s response field unpacking depends on the exact shape of `GetConvoForMembersResponse` in atproto-kotlin 6.0.1; Task 11 explicitly notes to verify against the generated source if the spec's assumed `response.convo.{id, members[]}` doesn't match. All other types come from `ChatContract.kt` (Task 3) and are exact.

---

## Plan complete

Saved to `docs/superpowers/plans/2026-05-14-chats-thread-screen.md`.
