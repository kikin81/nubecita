# Group chat Phase 2 (message reactions) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users add/remove emoji reactions on chat messages (1:1 and group), rendering reaction chips under each message.

**Architecture:** `MessageUi` gains an aggregated `reactions: ImmutableList<ReactionUi>` (emoji + count + reactedByViewer), mapped from the wire `MessageView.reactions`. A single `ToggleReaction(messageId, emoji)` VM event drives an optimistic update (guarded against rapid double-taps by an in-flight `(messageId, emoji)` set), then calls `ChatRepository.addReaction`/`removeReaction` (both return the updated `MessageView`) and reconciles from the authoritative response, rolling back on failure. Reaction chips render in a `FlowRow` under each `MessageBubble`; long-pressing a bubble opens a quick-react bar (6 emoji) + `+` that launches the AndroidX `emoji2-emojipicker` in a `Dialog`.

**Tech Stack:** Kotlin 2.3, Jetpack Compose + M3 Expressive, `atproto-kotlin` 9.3.1 (`chat.bsky.convo` reactions), `androidx.emoji2:emoji2-emojipicker`, `kotlinx.collections.immutable`, JUnit 5, AGP screenshot tests.

**Spec:** `docs/superpowers/specs/2026-06-20-group-chat-phase2-reactions-design.md` · **bd:** nubecita-hwix.3 (epic nubecita-hwix) · **branch:** `feat/nubecita-hwix.3-group-chat-reactions`

**Confirmed SDK shapes (9.3.1):**
- `MessageView.reactions: List<ReactionView>`; `ReactionView(value: String, sender: ReactionViewSender, createdAt)`; `ReactionViewSender.did` (value class → `.raw`).
- `ConvoService(client).addReaction(AddReactionRequest(convoId, messageId, value)).message` → `MessageView`.
- `ConvoService(client).removeReaction(RemoveReactionRequest(convoId, messageId, value)).message` → `MessageView`.
- `value` = a single emoji grapheme.

**Out of scope:** who-reacted names, reaction notifications, animations, convo-list `lastReaction` preview, Phase 3 (creation/management).

---

## File Structure

- `feature/chats/impl/.../ChatContract.kt` — add `ReactionUi`; `MessageUi.reactions`; `ChatEvent.ToggleReaction`; `ChatEffect.ShowReactionError`.
- `feature/chats/impl/.../data/MessageMapper.kt` — `List<ReactionView>.toReactionUis(viewerDid)`; populate `reactions` in `toMessageUi`; new pure `applyOptimisticToggle(...)`.
- `feature/chats/impl/.../data/ChatRepository.kt` + `DefaultChatRepository.kt` — `addReaction`/`removeReaction`.
- The 5 `ChatRepository` fakes — implement the two methods.
- `feature/chats/impl/.../ChatViewModel.kt` — `ToggleReaction` handling (in-flight guard + optimistic + reconcile + rollback).
- `feature/chats/impl/.../ui/MessageBubble.kt` — reaction chips `FlowRow`.
- `feature/chats/impl/.../ui/ReactionMenu.kt` (new) — long-press quick-react bar + `+`.
- `feature/chats/impl/.../ui/EmojiPickerDialog.kt` (new) — `emoji2` picker in a `Dialog`.
- `feature/chats/impl/.../ChatScreenContent.kt` — long-press wiring, menu/picker state, pass `onToggleReaction` to bubbles.
- `gradle/libs.versions.toml` + `feature/chats/impl/build.gradle.kts` — `emoji2-emojipicker`.
- Bench: `data/BenchChatsDto.kt` (`BenchMessageDto.reactions`), `data/BenchChatsMapper.kt`, `bench/assets/chats.json`.
- Tests: `MessageMapperTest`, `ChatViewModelTest`, `MessageBubbleScreenshotTest`, `ChatScreenContentScreenshotTest`.

---

## Task 1: `ReactionUi` model + mapper aggregation

**Files:** Modify `ChatContract.kt`, `data/MessageMapper.kt`; Test `data/MessageMapperTest.kt`.

- [ ] **Step 1: failing test** in `MessageMapperTest.kt` (reuse its `MessageView`/`ReactionView` fixtures; add a `sampleReaction(value, senderDid)` helper building `ReactionView(value = value, sender = ReactionViewSender(did = Did(senderDid)), createdAt = Datetime("2026-06-20T00:00:00Z"))`):

```kotlin
@Test
fun `reactions aggregate by emoji with count and viewer flag`() {
    val view = sampleMessageView(
        reactions = listOf(
            sampleReaction("👍", "did:plc:alice"),
            sampleReaction("👍", VIEWER_DID),
            sampleReaction("❤️", "did:plc:bob"),
        ),
    )
    val ui = view.toMessageUi(viewerDid = VIEWER_DID)
    assertEquals(
        listOf(ReactionUi("👍", 2, true), ReactionUi("❤️", 1, false)),
        ui.reactions,
    )
}

@Test
fun `no reactions yields empty list`() {
    assertTrue(sampleMessageView(reactions = emptyList()).toMessageUi(VIEWER_DID).reactions.isEmpty())
}
```
(If `sampleMessageView` doesn't yet take a `reactions` param, add it defaulting to `emptyList()`.)

- [ ] **Step 2: run → FAIL** (`ReactionUi` / `.reactions` unresolved). `./gradlew :feature:chats:impl:compileProductionDebugUnitTestKotlin` → FAIL.

- [ ] **Step 3: implement.** In `ChatContract.kt` add:

```kotlin
@Immutable
data class ReactionUi(
    val emoji: String,
    val count: Int,
    val reactedByViewer: Boolean,
)
```
and add to `MessageUi`: `val reactions: ImmutableList<ReactionUi> = persistentListOf(),` (place after `embed`, before `sendStatus`; `persistentListOf`/`ImmutableList` already imported).

In `data/MessageMapper.kt` add the aggregator and call it from `toMessageUi`:

```kotlin
import io.github.kikin81.atproto.chat.bsky.convo.ReactionView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.feature.chats.impl.ReactionUi

/** Aggregate wire reactions into per-emoji [ReactionUi] (count + whether the viewer reacted). */
fun List<ReactionView>.toReactionUis(viewerDid: String): ImmutableList<ReactionUi> =
    groupBy { it.value }
        .map { (emoji, views) ->
            ReactionUi(
                emoji = emoji,
                count = views.size,
                reactedByViewer = views.any { it.sender.did.raw == viewerDid },
            )
        }
        // Stable order: most-reacted first, then emoji for ties.
        .sortedWith(compareByDescending<ReactionUi> { it.count }.thenBy { it.emoji })
        .toImmutableList()
```
In `MessageView.toMessageUi(viewerDid)` add `reactions = reactions.toReactionUis(viewerDid),`. The `DeletedMessageView` branch in `toMessageUis` leaves `reactions` defaulted (empty).

- [ ] **Step 4: run → PASS.** `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*MessageMapperTest*"`

- [ ] **Step 5: commit.** `feat(chats): aggregate message reactions into ReactionUi`. Refs: nubecita-hwix.3.

---

## Task 2: Repository `addReaction` / `removeReaction`

**Files:** Modify `data/ChatRepository.kt`, `data/DefaultChatRepository.kt`, all 5 fakes; Test: extend the repo-adjacent tests if a harness exists (else rely on VM tests in Task 3).

- [ ] **Step 1: interface** — in `ChatRepository.kt` add:

```kotlin
/**
 * Adds [emoji] (a single emoji grapheme) as the viewer's reaction to [messageId]
 * via `chat.bsky.convo.addReaction`; returns the server-updated message (its
 * authoritative reactions). The composer-side optimistic update reconciles to this.
 */
suspend fun addReaction(convoId: String, messageId: String, emoji: String): Result<MessageUi>

/** Removes the viewer's [emoji] reaction from [messageId] via `removeReaction`; returns the updated message. */
suspend fun removeReaction(convoId: String, messageId: String, emoji: String): Result<MessageUi>
```

- [ ] **Step 2: implement in `DefaultChatRepository`** (mirror `getConvo`'s shape — `withContext` + `runCatching` + rethrow `CancellationException` + `Timber.w` with `javaClass` only; reuse `toMessageUi`):

```kotlin
override suspend fun addReaction(convoId: String, messageId: String, emoji: String): Result<MessageUi> =
    withContext(dispatcher) {
        runCatching {
            val viewerDid = currentViewerDid()
            val client = xrpcClientProvider.authenticated()
            ConvoService(client)
                .addReaction(AddReactionRequest(convoId = convoId, messageId = messageId, value = emoji))
                .message
                .toMessageUi(viewerDid)
        }.onFailure {
            if (it is CancellationException) throw it
            Timber.tag(TAG).w(it, "addReaction failed: %s", it.javaClass.name)
        }
    }

override suspend fun removeReaction(convoId: String, messageId: String, emoji: String): Result<MessageUi> =
    withContext(dispatcher) {
        runCatching {
            val viewerDid = currentViewerDid()
            val client = xrpcClientProvider.authenticated()
            ConvoService(client)
                .removeReaction(RemoveReactionRequest(convoId = convoId, messageId = messageId, value = emoji))
                .message
                .toMessageUi(viewerDid)
        }.onFailure {
            if (it is CancellationException) throw it
            Timber.tag(TAG).w(it, "removeReaction failed: %s", it.javaClass.name)
        }
    }
```
Add imports `AddReactionRequest`, `RemoveReactionRequest`.

- [ ] **Step 3: implement in ALL 5 fakes** (`git grep -n ': ChatRepository'`): `src/test/.../FakeChatRepository.kt`, `src/androidTest/.../FakeChatRepository.kt`, `src/bench/.../data/BenchFakeChatRepository.kt`, the nested fake in `src/test/.../store/ChatsUnreadPollingObserverTest.kt`. For the **test** fake, make it drive VM tests:

```kotlin
val addReactionCalls = mutableListOf<Triple<String, String, String>>()   // convoId, messageId, emoji
val removeReactionCalls = mutableListOf<Triple<String, String, String>>()
var addReactionResult: Result<MessageUi>? = null   // null → echo a default
var removeReactionResult: Result<MessageUi>? = null

override suspend fun addReaction(convoId: String, messageId: String, emoji: String): Result<MessageUi> {
    addReactionCalls += Triple(convoId, messageId, emoji)
    return addReactionResult ?: Result.success(DEFAULT_SENT_MESSAGE.copy(id = messageId))
}
override suspend fun removeReaction(convoId: String, messageId: String, emoji: String): Result<MessageUi> {
    removeReactionCalls += Triple(convoId, messageId, emoji)
    return removeReactionResult ?: Result.success(DEFAULT_SENT_MESSAGE.copy(id = messageId))
}
```
For the bench / androidTest / nested fakes, a static default suffices: `Result.success(<a MessageUi with id = messageId>)` (bench can build one inline; the nested fake may `error("not used")` if reactions are never exercised there).

- [ ] **Step 4: compile all source sets.** `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:compileProductionDebugUnitTestKotlin :feature:chats:impl:compileBenchDebugKotlin :feature:chats:impl:compileProductionDebugAndroidTestKotlin` → PASS.

- [ ] **Step 5: commit.** `feat(chats): repository addReaction/removeReaction`. Refs: nubecita-hwix.3.

---

## Task 3: VM `ToggleReaction` (optimistic + in-flight guard + reconcile)

**Files:** Modify `ChatContract.kt` (event + effect), `ChatViewModel.kt`, `data/MessageMapper.kt` (pure toggle helper); Test `ChatViewModelTest.kt`, `MessageMapperTest.kt`.

- [ ] **Step 1: pure optimistic-toggle helper (TDD).** In `MessageMapperTest.kt`:

```kotlin
@Test fun `optimistic add appends a new viewer chip`() {
    assertEquals(listOf(ReactionUi("👍", 1, true)), applyOptimisticToggle(emptyList(), "👍"))
}
@Test fun `optimistic add increments an existing chip and flips viewer flag`() {
    assertEquals(listOf(ReactionUi("👍", 2, true)), applyOptimisticToggle(listOf(ReactionUi("👍", 1, false)), "👍"))
}
@Test fun `optimistic remove decrements and drops at zero`() {
    assertEquals(emptyList<ReactionUi>(), applyOptimisticToggle(listOf(ReactionUi("👍", 1, true)), "👍"))
}
@Test fun `optimistic remove keeps others' count, clears viewer flag`() {
    assertEquals(listOf(ReactionUi("👍", 1, false)), applyOptimisticToggle(listOf(ReactionUi("👍", 2, true)), "👍"))
}
```
Implement in `MessageMapper.kt`:

```kotlin
/**
 * Pure optimistic toggle of the viewer's [emoji] reaction on a reaction list:
 * if the viewer already reacted → remove (count-1, drop at 0, clear flag); else add
 * (count+1 or a new chip, set flag). Order preserved; a new chip is appended.
 */
fun applyOptimisticToggle(reactions: List<ReactionUi>, emoji: String): List<ReactionUi> {
    val existing = reactions.firstOrNull { it.emoji == emoji }
    return when {
        existing == null -> reactions + ReactionUi(emoji, 1, true)
        existing.reactedByViewer ->
            reactions.mapNotNull {
                if (it.emoji != emoji) it
                else (it.count - 1).takeIf { c -> c > 0 }?.let { c -> it.copy(count = c, reactedByViewer = false) }
            }
        else -> reactions.map { if (it.emoji == emoji) it.copy(count = it.count + 1, reactedByViewer = true) else it }
    }
}
```
Run `--tests "*MessageMapperTest*"` → PASS.

- [ ] **Step 2: contract.** In `ChatContract.kt` add to `ChatEvent`:

```kotlin
/** Toggle the viewer's [emoji] reaction on the message with [messageId] (chip tap or picker selection). */
data class ToggleReaction(val messageId: String, val emoji: String) : ChatEvent
```
and to `ChatEffect`:

```kotlin
/** An add/remove-reaction call failed; the optimistic change was rolled back. Surface a transient snackbar. */
data object ShowReactionError : ChatEffect
```

- [ ] **Step 3: failing VM tests** in `ChatViewModelTest.kt` (group thread loaded with a Sent message `m1`):

```kotlin
@Test fun `ToggleReaction add fires addReaction and optimistically shows the chip`() = runTest(mainDispatcher.dispatcher) {
    val repo = FakeChatRepository(nextMessagesResult = Result.success(MessagePage(messages = persistentListOf(incoming("m1","did:plc:bob")))))
    repo.getConvoResult = groupConvo("c1")
    repo.addReactionResult = Result.success(serverMessage("m1", reactions = persistentListOf(ReactionUi("👍",1,true))))
    val vm = ChatViewModel(Chat(convoId="c1"), repo); advanceUntilIdle()
    vm.handleEvent(ChatEvent.ToggleReaction("m1","👍")); advanceUntilIdle()
    assertEquals(Triple("c1","m1","👍"), repo.addReactionCalls.single())
    assertEquals(ReactionUi("👍",1,true), vm.reactionsFor("m1").single())
}

@Test fun `ToggleReaction on an existing viewer reaction fires removeReaction`() = runTest(mainDispatcher.dispatcher) {
    val repo = FakeChatRepository(nextMessagesResult = Result.success(MessagePage(messages = persistentListOf(incoming("m1","did:plc:bob").copy(reactions = persistentListOf(ReactionUi("👍",1,true)))))))
    repo.getConvoResult = groupConvo("c1")
    repo.removeReactionResult = Result.success(serverMessage("m1", reactions = persistentListOf()))
    val vm = ChatViewModel(Chat(convoId="c1"), repo); advanceUntilIdle()
    vm.handleEvent(ChatEvent.ToggleReaction("m1","👍")); advanceUntilIdle()
    assertEquals(Triple("c1","m1","👍"), repo.removeReactionCalls.single())
    assertTrue(vm.reactionsFor("m1").isEmpty())
}

@Test fun `failed ToggleReaction rolls back and emits ShowReactionError`() = runTest(mainDispatcher.dispatcher) {
    val repo = FakeChatRepository(nextMessagesResult = Result.success(MessagePage(messages = persistentListOf(incoming("m1","did:plc:bob")))))
    repo.getConvoResult = groupConvo("c1")
    repo.addReactionResult = Result.failure(java.io.IOException("down"))
    val vm = ChatViewModel(Chat(convoId="c1"), repo); advanceUntilIdle()
    vm.effects.test {
        vm.handleEvent(ChatEvent.ToggleReaction("m1","👍")); advanceUntilIdle()
        assertEquals(ChatEffect.ShowReactionError, awaitItem())
    }
    assertTrue(vm.reactionsFor("m1").isEmpty(), "optimistic chip rolled back")
}

@Test fun `a second toggle for the same key is ignored while one is in flight`() = runTest(mainDispatcher.dispatcher) {
    val repo = FakeChatRepository(nextMessagesResult = Result.success(MessagePage(messages = persistentListOf(incoming("m1","did:plc:bob")))))
    repo.getConvoResult = groupConvo("c1"); repo.reactionGate = CompletableDeferred()
    val vm = ChatViewModel(Chat(convoId="c1"), repo); advanceUntilIdle()
    vm.handleEvent(ChatEvent.ToggleReaction("m1","👍")); advanceUntilIdle()
    vm.handleEvent(ChatEvent.ToggleReaction("m1","👍")); advanceUntilIdle()
    repo.reactionGate!!.complete(Unit); advanceUntilIdle()
    assertEquals(1, repo.addReactionCalls.size, "second toggle dropped while in-flight")
}
```
Add test helpers: `groupConvo(convoId)` (a `Result<ChatConvo>` with a `ChatHeader.Group`), `serverMessage(id, reactions)` (a `MessageUi`), `ChatScreenViewState.reactionsFor(id)` (the `ThreadItem.Message` with that id → `message.reactions`), and a `reactionGate: CompletableDeferred<Unit>?` on the fake that `addReaction`/`removeReaction` `await()` before returning (mirrors `sendGate`).

- [ ] **Step 4: implement** in `ChatViewModel.kt`:

```kotlin
private val inFlightReactions = mutableSetOf<Pair<String, String>>()   // (messageId, emoji)

// in handleEvent:
is ChatEvent.ToggleReaction -> onToggleReaction(event.messageId, event.emoji)

private fun onToggleReaction(messageId: String, emoji: String) {
    val convo = convoId ?: return
    val key = messageId to emoji
    if (key in inFlightReactions) return
    val target = messages.firstOrNull { it.id == messageId } ?: return
    if (target.sendStatus != MessageSendStatus.Sent || target.isDeleted) return
    val wasReacted = target.reactions.firstOrNull { it.emoji == emoji }?.reactedByViewer == true
    val original = target.reactions
    inFlightReactions += key
    // optimistic
    updateMessageReactions(messageId, applyOptimisticToggle(original, emoji).toImmutableList())
    viewModelScope.launch {
        val result = if (wasReacted) repository.removeReaction(convo, messageId, emoji)
                     else repository.addReaction(convo, messageId, emoji)
        result
            .onSuccess { server -> updateMessageReactions(messageId, server.reactions) }
            .onFailure {
                updateMessageReactions(messageId, original)   // rollback
                sendEffect(ChatEffect.ShowReactionError)
            }
        inFlightReactions -= key
    }
}

private fun updateMessageReactions(messageId: String, reactions: ImmutableList<ReactionUi>) {
    messages = messages.map { if (it.id == messageId) it.copy(reactions = reactions) else it }
    commitMessages()
}
```
(Imports: `ImmutableList`, `toImmutableList`, `ReactionUi`, `applyOptimisticToggle`.)

- [ ] **Step 5: run → PASS.** `./gradlew :feature:chats:impl:testProductionDebugUnitTest --tests "*ChatViewModelTest*" --tests "*MessageMapperTest*"`

- [ ] **Step 6: commit.** `feat(chats): ToggleReaction with optimistic update, in-flight guard, reconcile`. Refs: nubecita-hwix.3.

---

## Task 4: Reaction chips under the bubble

**Files:** Modify `ui/MessageBubble.kt`, `ChatScreenContent.kt`.

- [ ] **Step 1: add chips to `MessageBubble`.** Add params `reactions: ImmutableList<ReactionUi>` (or read `message.reactions`) and `onReactionToggle: (emoji: String) -> Unit = {}`. After the embed/text and before the send-status footer, render:

```kotlin
@OptIn(ExperimentalLayoutApi::class)
if (message.reactions.isNotEmpty()) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.padding(top = 4.dp),
    ) {
        message.reactions.forEach { r ->
            val bg = if (r.reactedByViewer) MaterialTheme.colorScheme.secondaryContainer
                     else MaterialTheme.colorScheme.surfaceContainerHigh
            Surface(
                onClick = { onReactionToggle(r.emoji) },
                shape = RoundedCornerShape(12.dp),
                color = bg,
            ) {
                Text(
                    text = "${r.emoji} ${r.count}",
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}
```
(Imports: `androidx.compose.foundation.layout.FlowRow`, `ExperimentalLayoutApi`, `Surface`, `RoundedCornerShape`.)

- [ ] **Step 2: wire from `ChatScreenContent.LoadedBody`** — pass `onReactionToggle = { emoji -> onEvent(ChatEvent.ToggleReaction(item.message.id, emoji)) }` into each `MessageBubble` (both the group-incoming and the plain/outgoing branches added in Phase 1).

- [ ] **Step 3: compile + screenshot compile.** `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:compileProductionDebugScreenshotTestKotlin :feature:chats:impl:assembleDebug` → PASS.

- [ ] **Step 4: commit.** `feat(chats): render reaction chips under message bubbles`. Refs: nubecita-hwix.3.

---

## Task 5: Long-press reaction menu (quick-react bar + `+`)

**Files:** Create `ui/ReactionMenu.kt`; Modify `ChatScreenContent.kt`.

- [ ] **Step 1: create `ReactionMenu.kt`** — a `Popup`-based bar of 6 quick emoji + a `+`:

```kotlin
internal val QUICK_REACTIONS = persistentListOf("❤️", "😂", "👍", "😮", "😢", "🙏")

@Composable
internal fun ReactionMenu(
    onPick: (emoji: String) -> Unit,
    onMore: () -> Unit,
    onDismiss: () -> Unit,
) {
    Popup(onDismissRequest = onDismiss, alignment = Alignment.Center) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
            Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                QUICK_REACTIONS.forEach { e ->
                    Text(e, style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.clip(CircleShape).clickable { onPick(e) }.padding(6.dp))
                }
                IconButton(onClick = onMore) {
                    NubecitaIcon(name = NubecitaIconName.Add, contentDescription = stringResource(R.string.chat_react_more))
                }
            }
        }
    }
}
```
(Add string `chat_react_more` = "More emoji". Use an existing `Add`/plus icon name from `NubecitaIconName`; if none exists, use Material `Icons.Default.Add` via the project's icon convention.)

- [ ] **Step 2: wire long-press + menu state in `LoadedBody`.** Add `var reactionMenuFor by remember { mutableStateOf<String?>(null) }` and `var pickerFor by remember { mutableStateOf<String?>(null) }`. Give each message Row a `Modifier.combinedClickable(onClick = {}, onLongClick = { if (canPost) reactionMenuFor = item.message.id })` — pass `canPost: Boolean` down from `ChatScreenContent` (from `state.canPost`); only Sent, non-deleted messages get the long-press (guard inside the lambda). When `reactionMenuFor == item.message.id`, render `ReactionMenu(onPick = { onEvent(ChatEvent.ToggleReaction(item.message.id, it)); reactionMenuFor = null }, onMore = { pickerFor = item.message.id; reactionMenuFor = null }, onDismiss = { reactionMenuFor = null })`.

- [ ] **Step 3: compile.** `./gradlew :feature:chats:impl:compileProductionDebugKotlin :feature:chats:impl:assembleDebug` → PASS.

- [ ] **Step 4: commit.** `feat(chats): long-press reaction menu with quick-react bar`. Refs: nubecita-hwix.3.

---

## Task 6: Full emoji picker (`emoji2-emojipicker` in a Dialog)

**Files:** Modify `gradle/libs.versions.toml`, `feature/chats/impl/build.gradle.kts`; Create `ui/EmojiPickerDialog.kt`; Modify `ChatScreenContent.kt`.

- [ ] **Step 1: add the dependency.** In `gradle/libs.versions.toml` under `[versions]`: `emoji2 = "1.5.0"`. Under `[libraries]`: `androidx-emoji2-emojipicker = { module = "androidx.emoji2:emoji2-emojipicker", version.ref = "emoji2" }`. In `feature/chats/impl/build.gradle.kts` add `implementation(libs.androidx.emoji2.emojipicker)` (keep deps sorted — run `./gradlew :app:checkSortDependencies` after).

- [ ] **Step 2: create `EmojiPickerDialog.kt`** — host `EmojiPickerView` in a `Dialog` (NOT a draggable `ModalBottomSheet`, to avoid the scroll/drag conflict; see spec):

```kotlin
@Composable
internal fun EmojiPickerDialog(
    onEmojiPicked: (emoji: String) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface) {
            AndroidView(
                factory = { ctx ->
                    EmojiPickerView(ctx).apply {
                        // onEmojiPicked yields exactly one emoji; pass through verbatim (no truncation).
                        setOnEmojiPickedListener { item -> onEmojiPicked(item.emoji) }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(360.dp),
            )
        }
    }
}
```
(Imports: `androidx.compose.ui.window.Dialog`, `androidx.compose.ui.viewinterop.AndroidView`, `androidx.emoji2.emojipicker.EmojiPickerView`.)

- [ ] **Step 3: wire in `LoadedBody`** — when `pickerFor != null`, render `EmojiPickerDialog(onEmojiPicked = { onEvent(ChatEvent.ToggleReaction(pickerFor!!, it)); pickerFor = null }, onDismiss = { pickerFor = null })`. (Capture `pickerFor` into a local `val id = pickerFor` before the lambda to avoid the `!!`.)

- [ ] **Step 4: build.** `./gradlew :app:checkSortDependencies :feature:chats:impl:assembleDebug` → PASS. Manually verify on-device (bench build) that the picker scrolls without dismissing the dialog; if a `Dialog` proves cramped, switch to a non-draggable full-height container per the spec.

- [ ] **Step 5: commit.** `feat(chats): full emoji picker via emoji2-emojipicker`. Refs: nubecita-hwix.3.

---

## Task 7: Bench fixture reactions (Fastlane)

**Files:** Modify `data/BenchChatsDto.kt`, `data/BenchChatsMapper.kt`, `bench/assets/chats.json`.

- [ ] **Step 1: DTO** — add to `BenchMessageDto`: `val reactions: List<BenchReactionDto> = emptyList()` and a new `@Serializable data class BenchReactionDto(val emoji: String, val count: Int = 1, val reactedByViewer: Boolean = false)`.

- [ ] **Step 2: mapper** — in `BenchChatsMapper.toMessage`, set `reactions = dto.reactions.map { ReactionUi(it.emoji, it.count, it.reactedByViewer) }.toImmutableList()`.

- [ ] **Step 3: fixture** — in `chats.json`, add `reactions` to a couple of the group (`convo_group_design`) messages, e.g. the carmen message gets `"reactions":[{"emoji":"👍","count":2},{"emoji":"❤️","count":1,"reactedByViewer":true}]`.

- [ ] **Step 4: build.** `./gradlew :feature:chats:impl:compileBenchDebugKotlin :app:assembleBenchDebug` → PASS; `python3 -c "import json;json.load(open('feature/chats/impl/src/bench/assets/chats.json'))"`.

- [ ] **Step 5: commit.** `chore(chats): seed bench reactions for Fastlane`. Refs: nubecita-hwix.3.

---

## Task 8: Screenshots + verification

**Files:** Modify `ui/MessageBubbleScreenshotTest.kt`, `ChatScreenContentScreenshotTest.kt`.

- [ ] **Step 1: screenshot fixtures** — add a `MessageBubble` fixture (light+dark) whose `MessageUi.reactions = persistentListOf(ReactionUi("👍",2,false), ReactionUi("❤️",1,true))` to pin chip rendering incl. the own-reacted highlight; and a `ChatScreenContent` group fixture with a reacted message. Match each file's existing wrapper/`@PreviewTest` conventions.

- [ ] **Step 2: generate baselines + stage ONLY new ones.** `./gradlew :feature:chats:impl:updateProductionDebugScreenshotTest`; then `git status` the reference dir — `git add` ONLY the new `*reaction*` PNGs; `git restore` any modified pre-existing baselines (the known Mac-vs-CI light-variant drift). The PR needs the `update-baselines` label so CI regenerates them authoritatively.

- [ ] **Step 3: full gate.** `./gradlew spotlessCheck :app:checkSortDependencies :feature:chats:impl:lintProductionDebug :feature:chats:impl:testProductionDebugUnitTest :app:assembleDebug` → all green.

- [ ] **Step 4: commit.** `test(chats): reaction screenshot coverage`. Refs: nubecita-hwix.3.

---

## Self-Review

**Spec coverage:** ReactionUi + MessageUi.reactions + aggregation → Task 1. addReaction/removeReaction (return updated MessageView) + 5 fakes → Task 2. ToggleReaction optimistic + in-flight (messageId,emoji) guard + reconcile + rollback, Sent-only, canPost gate → Tasks 3 (logic) + 5 (long-press canPost gate). Chips (own highlighted, tap toggles) → Task 4. Quick-react bar (6 emoji) + `+` → Task 5. emoji2 picker in a Dialog (gesture-conflict avoidance) → Task 6. Grapheme safety (trust single-emoji sources, no take(1)) → Tasks 5/6 (constants + EmojiPickerView verbatim). Bench reactions → Task 7. Unit + screenshot tests → Tasks 1/3/8. All covered.

**Placeholder scan:** The `NubecitaIconName.Add` / `chat_react_more` string and the on-device picker-scroll verification are flagged as "confirm against the icon set / verify on device" — concrete instructions, not deferred work. The `canPost` gate on reacting is implemented as the long-press guard (Task 5) and is the agreed behavior.

**Type consistency:** `ReactionUi(emoji, count, reactedByViewer)`, `MessageUi.reactions: ImmutableList<ReactionUi>`, `toReactionUis(viewerDid)`, `applyOptimisticToggle(reactions, emoji)`, `addReaction/removeReaction(convoId, messageId, emoji): Result<MessageUi>`, `ChatEvent.ToggleReaction(messageId, emoji)`, `ChatEffect.ShowReactionError`, `inFlightReactions: Set<Pair<String,String>>` — used consistently across tasks.
