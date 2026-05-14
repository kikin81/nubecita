# Chat thread screen — read-only MVP

**Status:** Design approved · ready for writing-plans
**bd:** nubecita-nn3.2 (parent: nubecita-nn3 — the Chats MVP epic)
**Scope:** `:feature:chats:api` (one new NavKey) + `:feature:chats:impl` (new screen + repo extensions)
**Date:** 2026-05-14

## Problem

After nubecita-nn3.1 shipped the convo list, tapping a row logs a Timber line and no-ops. The Chats MVP epic needs a read-only thread view that renders messages from `chat.bsky.convo.getMessages` as Material 3 Expressive bubbles with the visual rhythm of Google Chat.

## Goal

Ship a read-only chat thread screen that:

1. Resolves `otherUserDid → convoId` via `chat.bsky.convo.getConvoForMembers`, then loads the first page of messages via `chat.bsky.convo.getMessages`.
2. Renders messages as M3 Expressive bubbles with **GChat-style segmented corner shapes** on the sender side (single = fully rounded; runs of 2+ flatten the sender-side corners between adjacent same-sender bubbles).
3. Groups consecutive same-sender messages (tighter 4dp gap, single avatar on the first incoming bubble of a run), with day-separator chips at calendar-day boundaries that **break the run** for shape + avatar purposes.
4. Wires the existing `ChatsScreen`'s convo-tap callback to push `Chat(otherUserDid)` onto `MainShellNavState`.

## Out of scope (V2 / future epics)

- Pagination beyond the first page (added only if cursor depth makes it necessary).
- Image / video / link-preview / quote-post embeds inside bubbles.
- Reactions, typing indicators, read receipts.
- Tap avatar/title in TopAppBar → navigate to Profile (sibling `nubecita-a7a` covers the inverse direction; Chat→Profile is a future bd if anyone asks).
- Long-press bubble to reveal absolute time.
- Composer / sending — a follow-up epic.
- Tap-Message-from-profile routing → bd `nubecita-a7a` (sibling, not blocked by this PR).
- Live message updates (no `getLog` subscription); the user pulls-to-refresh manually.

## Decisions

1. **Route by DID, not convoId.** `Chat(val otherUserDid: String) : NavKey`. The convo list row knows the other member's DID; the profile-tap path (future a7a) uses `state.header.did`. Both converge on a single identifier; the thread screen resolves DID → convoId itself. Avoids leaking the appview-internal opaque convoId into the navigation surface.

2. **Single-flight load via `init {}`.** `ChatViewModel.init { launchLoad() }` kicks off `resolveConvo → getMessages` on construction. Subsequent `Refresh` / `RetryClicked` re-issue the chain. Same `inFlightLoad: Job?` single-flight pattern as `ChatsViewModel`. No explicit `Load` event in the contract — `init` is the canonical entry.

3. **Bubble shape mirrors `ListItemDefaults.segmentedShapes`'s algorithm, asymmetrically.** Material 3 Expressive's `SegmentedListItem` shape resolver computes per-position corner profiles for a vertical list section. We mirror that algorithm for chat bubbles but apply the segmented (small) corners ONLY to the sender side — the opposite side stays fully rounded. `RoundedCornerShape` directly; we cannot reuse the framework `Shape` verbatim because it is symmetric across left/right.

4. **Grouping computed VM-side, baked into `ThreadItem` state.** Mirrors the CLAUDE.md convention used for the convo list — `UiState` is flat and UI-ready. The screen Composable iterates `items: ImmutableList<ThreadItem>` and renders each item without re-computing run-position or day-separator decisions.

5. **`reverseLayout = true` on the `LazyColumn`.** Items emitted newest-first; the list renders bottom-to-top so the freshest message appears at the bottom of the screen on first load with no explicit `scrollToItem` call. V2-friendly: future pagination prepends older messages to the source list without scroll-jank.

6. **Day-separator chips break same-sender runs.** If you sent two messages on Monday and one on Wednesday with no reply between, the Wed message starts a fresh run for shape + avatar purposes. The chip slicing between them visually breaks continuity; the bubble shape and avatar follow.

7. **No timestamps inline on bubbles in V1.** Day-separator chips ("Today" / "Yesterday" / weekday-name / `MMM d`) provide the temporal context. Exact-time reveal is a future bd (long-press → absolute time, or GChat-style time chip at run start).

8. **`ChatRepository` is extended additively.** The interface gains `resolveConvo(otherUserDid): Result<String>` and `getMessages(convoId, cursor): Result<MessagePage>`. No breaking change to the `listConvos` surface shipped in nn3.1.

9. **`Refresh` re-runs the whole chain.** Resolve + getMessages on every refresh. The convoId could change in theory (an appview-side migration of convo identity) — re-resolving is cheap and side-steps stale-convoId bugs.

10. **No composer in V1, no "Sending coming soon" banner.** Matches the epic spec.

11. **`ZoneId` is a parameter to the mapper, not an ambient lookup.** `toThreadItems(now: Instant, zone: ZoneId = ZoneId.systemDefault())` — defaulted for production but explicitly overridable in tests so day-boundary cases pin deterministically across timezones. Matches `ConvoMapper`'s `relativeTimestamp` pattern (both functions read system-default at call time when called from production; both accept a deterministic zone in tests). Avoids the AT Protocol UTC-vs-local-day footgun.

## Architecture

```
:feature:chats:api
  ├─ data object Chats : NavKey                            ← existing
  └─ data class Chat(val otherUserDid: String) : NavKey    ← new

:feature:chats:impl
  ├─ ChatScreen.kt                  ← stateful thread entry
  ├─ ChatScreenContent.kt           ← stateless (previews + screenshot tests)
  ├─ ChatViewModel.kt
  ├─ ChatContract.kt                ← UiState / UiEvent / UiEffect / ChatError / ChatLoadStatus / ThreadItem / MessageUi
  │
  ├─ data/
  │    ├─ ChatRepository.kt         ← extended with resolveConvo + getMessages
  │    ├─ DefaultChatRepository.kt  ← extended impl
  │    ├─ MessageMapper.kt          ← MessageView → MessageUi (incl. DeletedMessageView, SystemMessageView)
  │    └─ ThreadItemMapper.kt       ← List<MessageUi>.toThreadItems(now): ImmutableList<ThreadItem>
  │
  ├─ ui/
  │    ├─ MessageBubble.kt          ← + internal messageBubbleShape(index, count, isOutgoing)
  │    └─ DaySeparatorChip.kt
  │
  └─ di/
       └─ ChatsNavigationModule.kt  ← extended: + entry<Chat>; replaces existing Timber placeholder
                                       in entry<Chats> with LocalMainShellNavState.current.add(Chat(did))
```

## ChatContract — types

```kotlin
data class ChatScreenViewState(
    val otherUserHandle: String,    // for the TopAppBar; resolved from convo.members
    val otherUserDisplayName: String?,
    val otherUserAvatarUrl: String?,
    val otherUserAvatarHue: Int,
    val status: ChatLoadStatus = ChatLoadStatus.Loading,
) : UiState

sealed interface ChatLoadStatus {
    data object Loading : ChatLoadStatus
    data class Loaded(
        val items: ImmutableList<ThreadItem>,    // newest-first; reverseLayout flips to bottom-up render
        val isRefreshing: Boolean = false,
    ) : ChatLoadStatus
    data class InitialError(val error: ChatError) : ChatLoadStatus
}

sealed interface ChatError {
    data object Network : ChatError
    data object NotEnrolled : ChatError              // same scope-not-granted error as the convo list
    data object ConvoNotFound : ChatError            // resolveConvo couldn't find one
    data class Unknown(val cause: String?) : ChatError
}

@Immutable
sealed interface ThreadItem {
    val key: String
    data class Message(
        val message: MessageUi,
        val runIndex: Int,         // 0-based position in run; 0 = OLDEST in the run = TOP of the run on screen.
                                   // (With reverseLayout=true, source-newer items render below source-older items
                                   // on screen, so the oldest-in-run sits at the top of the run visually.)
        val runCount: Int,
        val showAvatar: Boolean,   // !isOutgoing && runIndex == 0 — incoming + top-of-run on screen
    ) : ThreadItem { override val key: String get() = "msg-${message.id}" }
    data class DaySeparator(val label: String) : ThreadItem {
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

sealed interface ChatEffect : UiEffect    // empty for V1; V2 adds NavigateToProfile etc.
```

## Bubble shape

```kotlin
/**
 * Asymmetric M3 Expressive bubble shape for a message at [index] in a run of
 * [count] consecutive same-sender messages. Mirrors `ListItemDefaults.segmentedShapes`
 * structurally, but applies the segmented (small) corners ONLY on the sender side —
 * the opposite side stays fully rounded.
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
    val topSender    = if (isFirst || isSingle) large else small
    val bottomSender = if (isLast  || isSingle) large else small
    return if (isOutgoing) {
        RoundedCornerShape(
            topStart = large, topEnd = topSender,
            bottomStart = large, bottomEnd = bottomSender,
        )
    } else {
        RoundedCornerShape(
            topStart = topSender, topEnd = large,
            bottomStart = bottomSender, bottomEnd = large,
        )
    }
}
```

**Note on reverseLayout interaction.** The mapper assigns `runIndex` such that 0 = oldest = TOP of the run on screen. The shape resolver's `isFirst = (index == 0)` then means "no same-sender neighbor above me on screen" → top-sender corner stays LARGE; `isLast = (index == count - 1)` means "no same-sender neighbor below me on screen" → bottom-sender corner stays LARGE. The shape function is direction-agnostic — it just consumes `(index in run, count, isOutgoing)`. The reverseLayout flag affects only the LazyColumn's bottom-up rendering of the source-newest-first item list; the shape and avatar logic are anchored to the screen-top semantics of `runIndex`.

## Grouping algorithm

```kotlin
internal fun List<MessageUi>.toThreadItems(now: Instant): ImmutableList<ThreadItem> {
    // Input: newest-first time-sorted list of MessageUi.
    // Walk in order; for each, compare with the previous emitted message's senderDid + local-day.
    // If senderDid changed OR local-day changed: emit a DaySeparator (when day changed) and start a new run.
    //
    // IMPORTANT — TIMEZONE: AT Protocol returns ISO-8601 timestamps in UTC. Day-boundary
    // comparison MUST convert to the user's local timezone before computing the calendar
    // day, otherwise a thread that runs through ~4-7pm in California (= UTC midnight)
    // injects a spurious "Yesterday" separator mid-conversation. Use
    // `ZoneId.systemDefault()` + `ZonedDateTime.ofInstant(...).toLocalDate()` exactly as
    // `feature/chats/impl/src/main/kotlin/.../data/ConvoMapper.kt`'s `relativeTimestamp`
    // already does — same `ZoneId` source, same `toLocalDate()` comparison. Inject `now`
    // as a parameter (not `Clock.System.now()` inline) so tests can pin the boundary
    // deterministically.
    //
    // After the walk, assign runIndex + runCount to every Message within its run:
    //   - runIndex is OLDEST-first within the run: the oldest message of a run gets runIndex = 0,
    //     and the newest message of a run gets runIndex = runCount - 1.
    //   - This makes shape + avatar reasoning match the SCREEN-TOP-of-run perspective (with
    //     reverseLayout = true, the oldest message of the run renders at the top of the run on screen).
    //
    // showAvatar = !isOutgoing && runIndex == 0
    //   "First message of an incoming run" per the bd = first chronologically = oldest = runIndex 0 with this indexing
    //   = top of the run on screen (the natural place for an avatar to sit above the contiguous bubble block).
    //
    // Day label buckets — same as the convo list's relative-timestamp formatter:
    //   sent local-day == today           → "Today"
    //   sent local-day == yesterday       → "Yesterday"
    //   sent within last 7 calendar days  → short weekday ("Mon")
    //   older                              → "MMM d"  ("Apr 25")
}
```

**Decision: which end of the run carries the avatar?** Per the bd: "Avatar only on the first message of an incoming run." Chronologically first = oldest in the run. The mapper assigns `runIndex = 0` to the oldest message in each run, so `showAvatar = !isOutgoing && runIndex == 0` is the rule. With `reverseLayout = true`, that bubble renders at the TOP of the run on screen — matching the GChat convention of "avatar sits above the first bubble of a contiguous same-sender block".

## Layout

`Scaffold + TopAppBar`:

- Back arrow (`Icons.AutoMirrored.Filled.ArrowBack`) → `onEvent(ChatEvent.BackPressed)` → screen Composable calls `LocalMainShellNavState.current.removeLast()`.
- Leading 40dp avatar (deterministic-hue fallback identical to `ConvoListItem`).
- Title: display name as `titleMedium`, single line, ellipsized on overflow. No subtitle.

Body:

```kotlin
LazyColumn(
    modifier = Modifier.fillMaxSize(),
    reverseLayout = true,
    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),  // baseline same-sender gap
) {
    itemsIndexed(
        items = state.items,
        key = { _, it -> it.key },
        contentType = { _, it -> if (it is ThreadItem.Message) "msg" else "sep" },
    ) { position, item ->
        when (item) {
            is ThreadItem.Message -> {
                // Add 8.dp BOTTOM padding on the oldest message of a run (runIndex == 0)
                // when it isn't the very last item in source — that 8.dp stacks on top of the
                // 4.dp baseline arrangement to produce the 12.dp cross-run gap. With
                // reverseLayout = true, `bottom` padding visually appears between this item
                // and the item that comes AFTER it in source (which is below it on screen).
                val crossRunGap = if (item.runIndex == 0 && position < state.items.lastIndex) 8.dp else 0.dp
                MessageBubbleRow(item, modifier = Modifier.padding(bottom = crossRunGap))
            }
            is ThreadItem.DaySeparator -> DaySeparatorChip(label = item.label)
        }
    }
}
```

`MessageBubbleRow`:

- `Row` aligned to start (incoming) or end (outgoing).
- Fixed-width 48dp leading box for the avatar slot — `showAvatar` controls whether the box renders the avatar or stays empty (consistent left-alignment for the bubble text regardless).
- The bubble itself is a `Surface(shape = messageBubbleShape(runIndex, runCount, isOutgoing), color = containerColor)` wrapping the text.

Container colors: incoming = `MaterialTheme.colorScheme.surfaceContainerHigh` on `onSurface`; outgoing = `primaryContainer` on `onPrimaryContainer`.

## ChatRepository extensions

```kotlin
internal interface ChatRepository {
    // existing
    suspend fun listConvos(cursor: String? = null, limit: Int = LIST_CONVOS_PAGE_LIMIT): Result<ConvoListPage>
    // new
    suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution>
    suspend fun getMessages(convoId: String, cursor: String? = null, limit: Int = GET_MESSAGES_PAGE_LIMIT): Result<MessagePage>
}

internal data class ConvoResolution(
    val convoId: String,
    val otherUserHandle: String,
    val otherUserDisplayName: String?,
    val otherUserAvatarUrl: String?,
    val otherUserAvatarHue: Int,
)

internal data class MessagePage(
    val messages: ImmutableList<MessageUi>,  // newest-first
    val nextCursor: String?,
)

internal const val GET_MESSAGES_PAGE_LIMIT: Int = 50
```

`DefaultChatRepository` impls follow the same `withContext(dispatcher) { runCatching { ... } }` shape as `listConvos`. Sender DID comparison uses the cached viewer DID from `SessionStateProvider` (same source as the convo list). Errors map through `ChatsErrorMapping.toChatsError()` extended with the new `ConvoNotFound` variant.

## Navigation wiring

`ChatsNavigationModule`:

1. Replace the existing placeholder `Timber.tag("ChatsNavigation").i(...)` lambda inside `entry<Chats> { ... }` with:
   ```kotlin
   val navState = LocalMainShellNavState.current
   ChatsScreen(onNavigateToChat = { did -> navState.add(Chat(did)) })
   ```
2. Add a sibling `entry<Chat> { key -> ChatScreen(otherUserDid = key.otherUserDid, onNavigateBack = { navState.removeLast() }) }` provider. The `ChatScreen` Composable uses `hiltViewModel()` and gets the `otherUserDid` injected via `SavedStateHandle` (Nav3 stores the NavKey).

## MessageView mapper edge cases

- `MessageView` (normal): map directly. `isOutgoing` = `senderDid == viewerDid`. `isDeleted = false`.
- `DeletedMessageView`: emit a `MessageUi` with `isDeleted = true`, `text = "Message deleted"` (resource). Bubble renders the placeholder copy in italic, same shape rules apply.
- `SystemMessageView` (and the catch-all `else` branch for forward-compat unknown variants): **filter out** of the message list. System messages aren't part of the conversational stream and the MVP doesn't define rendering for them. Future bd can add inline rendering if telemetry shows we're filtering meaningful events.

## Tests

Per the project's UI-task convention (unit + previews + screenshot tests).

### Unit

- `MessageMapperTest`:
  - `MessageView` → outgoing / incoming bucketing via `senderDid` vs viewer.
  - `DeletedMessageView` → `isDeleted = true` + placeholder text resource ID.
  - `SystemMessageView` + unknown union variant → filtered out of the page.
- `ThreadItemMapperTest`:
  - Single message → single `Message` item with `runIndex = 0, runCount = 1, showAvatar = !isOutgoing`.
  - Two same-sender messages → run of 2 with `runIndex = 0` on the OLDER message and `runIndex = 1` on the newer.
  - Three same-sender messages → run of 3 with `runIndex 0, 1, 2` mapped oldest → newest.
  - Cross-sender alternation → each is its own run of 1 with `runIndex = 0`.
  - Incoming run of 3 → `showAvatar = true` ONLY on the oldest message (`runIndex == 0`).
  - Day boundary across same-sender messages → separator inserted; two distinct runs (separator breaks run); `showAvatar` re-applies to the oldest of each side.
  - Day-label buckets: "Today" / "Yesterday" / weekday / "MMM d" with deterministic `now` injection.
  - **Timezone regression**: two messages at `2026-04-25T23:30Z` and `2026-04-26T00:30Z` with a `ZoneId.systemDefault()` of `America/Los_Angeles` (UTC-7) → BOTH local to 2026-04-25, so NO `DaySeparator` between them. Conversely, same UTC pair viewed from `Europe/Berlin` (UTC+2) → splits across local days, separator emitted. Pin via `@TestZone("America/Los_Angeles")` or by passing the zone through the mapper signature (see Decision 11 below).
- `ChatViewModelTest`:
  - `init` kicks off resolve + getMessages; on success → `Loaded`.
  - `resolveConvo` failure with `NotEnrolled` / `Network` / `ConvoNotFound` / `Unknown` → matching `InitialError` variant.
  - `getMessages` failure after successful resolve → `InitialError(Network)` / `InitialError(Unknown(...))`.
  - `Refresh` on `Loaded` flips `isRefreshing = true` then back to `false`; preserves items on refresh-time failure.
  - Single-flight: two rapid Refresh events only fire one repository call.
- `MessageBubbleShapeTest` (or `MessageBubbleTest` covering both shape + render): `messageBubbleShape(index, count, isOutgoing)` returns the expected `RoundedCornerShape` for every combination — single, first-of-N, middle-of-N, last-of-N × incoming + outgoing.

### Screenshot

- `MessageBubbleScreenshotTest`:
  - Incoming single / outgoing single / incoming first-of-3 / incoming middle-of-3 / incoming last-of-3 / outgoing matching set / deleted-italic.
  - Light + dark for each.
- `DaySeparatorChipScreenshotTest`: "Today" / "Yesterday" / "Mon" / "Apr 25", light + dark.
- `ChatScreenContentScreenshotTest`: Loading / Loaded (single-day thread with mixed runs / multi-day thread with two separators / single-message thread / empty) / each `InitialError` variant. Light + dark.

### Optional androidTest

Open `ChatScreen` with a fake DI binding for `ChatRepository`, assert message bubbles render at the expected count.

## Refs

- Parent epic: `nubecita-nn3` · epic spec: `docs/superpowers/specs/2026-05-13-chats-mvp-design.md`.
- Sibling shipped: `nubecita-nn3.1` (convo list MVP) provides the `ChatRepository` interface this bd extends.
- Pinned by `chat-rpcs-need-proxy-header-AND-chat-bsky-scope` memory: chat AppView routing (`atproto-proxy` header) and OAuth scope (`transition:chat.bsky`) are already in place from `nubecita-nn3.5` + `nubecita-nn3.6`. No additional auth / wiring work needed for this bd.
