# Chats MVP — read-only convo list + thread (epic spec)

**Status:** Design approved · ready to file plans per child
**bd:** nubecita-nn3 (epic) · children nubecita-nn3.1, nubecita-nn3.2, nubecita-a7a
**Scope:** `:feature:chats:api` (expand) + `:feature:chats:impl` (new module) · Compact width only
**Date:** 2026-05-13

## Problem

The Chats tab is a top-level destination in `MainShell` but is wired to a `Box { Text("Chats — coming soon") }` placeholder in `:app/MainShellPlaceholderModule`. The Message button on other-user profiles snackbars "Direct messages — coming soon." Users can't read their Bluesky DMs in Nubecita.

## Goal

Ship a read-only MVP that:

1. Renders the user's conversations as a Material 3 Expressive list on the Chats tab home, matching the visual style of Google Chat (dark surfaces, compact rows with avatar + name + last-message snippet + right-aligned timestamps).
2. Lets the user tap a conversation to open a chat thread that renders the message history as bubbles with asymmetric M3 corner radii (incoming left, outgoing right), grouped by sender, with day-separator chips at date boundaries.
3. Wires the Message button on other-user profiles to navigate into the chat thread for that user (switches to the Chats tab and pushes the thread).

## Out of scope (future epics)

- Sending messages (composer + `chat.bsky.convo.sendMessage`).
- Image / video / external-link attachments.
- Reactions, typing indicators, read receipts.
- Group chats (Bluesky 1:1 DMs only for now).
- Push notifications for new messages (lives in the notifications epic).
- Compose-new-chat FAB.
- Search inside the convo list or inside a thread.
- Pagination beyond the first page (added in V2 only if endpoint cursor depth makes it necessary).

## Decisions

1. **Single `:feature:chats:impl` module.** Hosts both screens (tab home + thread), the `ChatRepository` + default, and the Hilt entry-provider module. No separate `:core:chats` data layer — YAGNI for the MVP; an extraction lands when a second consumer needs the repo.
2. **Route by DID, not handle.** `Chat(otherUserDid: String) : NavKey`. The convo list row knows the other member's DID (from `convo.members.firstOrNull { it.did != viewerDid }`). The profile-tap path uses `state.header.did`. Both converge on the same identifier; the chat screen resolves DID → convoId itself via `chat.bsky.convo.getConvoForMembers(members = [viewerDid, otherUserDid])`.
3. **Drop the `:app`-side Chats placeholder.** `:feature:chats:impl` provides its own `@MainShell EntryProviderInstaller` for both the `Chats` (tab home) and `Chat(otherUserDid)` (thread) keys. `MainShellPlaceholderModule.provideChatsPlaceholderEntries` is removed in the first child's PR.
4. **Three PRs (one per child).** The convo list lands first; the thread follows; the tap-Message-from-profile wiring ships last (it's nubecita-a7a, re-parented under this epic). The `ChatRepository` interface is introduced in PR 1 and extended in PR 2; no breaking changes between them.
5. **No bd-1tc collapsing toolbar.** Both screens use a standard `Scaffold + TopAppBar`. The chats list isn't hero-driven; the chat thread's top bar carries the back arrow + leading avatar + display name and stays static.
6. **No composer in V1.** The chat thread is read-only. A trailing info banner is also explicitly out — the absence of a composer should speak for itself; adding "Sending coming soon" copy is noise.

## Architecture

```
:feature:chats:api
  ├─ data object Chats : NavKey                            ← existing (top-level tab)
  └─ data class Chat(val otherUserDid: String) : NavKey    ← new (per-convo thread)

:feature:chats:impl (NEW)
  ├─ ChatsScreen.kt          ← stateful tab-home entry
  ├─ ChatsScreenContent.kt   ← stateless content (previews + screenshot tests)
  ├─ ChatsViewModel.kt
  ├─ ChatsContract.kt        ← UiState + UiEvent + UiEffect per MVI conventions
  │
  ├─ ChatScreen.kt           ← stateful thread entry
  ├─ ChatScreenContent.kt    ← stateless content
  ├─ ChatViewModel.kt
  ├─ ChatContract.kt
  │
  ├─ ui/
  │    ├─ ConvoListItem.kt   ← GChat-style row composable
  │    ├─ MessageBubble.kt   ← incoming / outgoing bubble with asymmetric M3 radii
  │    └─ DaySeparator.kt    ← centered labelSmall chip for date boundaries
  │
  ├─ data/
  │    ├─ ChatRepository.kt          ← interface
  │    ├─ DefaultChatRepository.kt   ← Hilt-injectable, wraps chat.bsky.convo.{listConvos, getConvoForMembers, getMessages}
  │    ├─ ConvoMapper.kt             ← ConvoView → ConvoListItemUi
  │    └─ MessageMapper.kt           ← MessageView → MessageUi
  │
  └─ di/
       ├─ ChatsNavigationModule.kt   ← @Provides @IntoSet @MainShell EntryProviderInstaller for both keys
       └─ ChatsRepositoryModule.kt   ← @Binds ChatRepository

:app
  └─ shell/MainShellPlaceholderModule.kt
       └─ drop provideChatsPlaceholderEntries (now owned by :feature:chats:impl)
```

### File boundaries

Per CLAUDE.md "Design for isolation and clarity" — each file does one thing, communicates through a typed interface, and can be reasoned about independently. The split above keeps screens / view models / row composables / repository / DI in separate concerns. `ConvoMapper.kt` is the only place that touches `io.github.kikin81.atproto.chat.bsky.convo.*` runtime types — everything downstream sees `ConvoListItemUi` / `MessageUi` / `MessageThreadUi` UI models with primitive fields.

## Routing

### Convo list → thread

The `ChatsScreen` collector observes `ChatsEffect.NavigateToChat(otherUserDid: String)`. On emit:

```kotlin
mainShellNavState.add(Chat(otherUserDid))
```

This pushes the thread onto the Chats tab's back stack (the user is already on Chats since they're viewing the list). Back from the thread pops to the list.

### Profile → thread (nubecita-a7a)

`ProfileViewModel.handleEvent(MessageTapped)` reads `state.header?.did` and emits `ProfileEffect.NavigateToChat(otherUserDid)`. The `ProfileScreen` collector switches tab + pushes the thread:

```kotlin
mainShellNavState.topLevelKey = Chats
mainShellNavState.add(Chat(otherUserDid))
```

Order matters: `topLevelKey = Chats` first so the subsequent `add` lands on the Chats stack (not whatever tab the user came from). Back from the thread pops to the Chats list, not to the profile — the user is now navigating inside the Chats tab.

### DID resolution

`ChatScreen` is parameterized by `otherUserDid`. On first composition the VM kicks off `chat.bsky.convo.getConvoForMembers(members = [viewerDid, otherUserDid])` to find or open the convo. Result drives the subsequent `getMessages` call. Cached against the DID inside the VM so a quick back-and-pop doesn't re-resolve.

## Data flow

`ChatRepository` interface:

```kotlin
internal interface ChatRepository {
    suspend fun listConvos(cursor: String? = null, limit: Int = LIST_PAGE_LIMIT): Result<ConvoListPage>
    suspend fun resolveConvo(otherUserDid: String): Result<ConvoView>
    suspend fun getMessages(convoId: String, cursor: String? = null, limit: Int = MESSAGE_PAGE_LIMIT): Result<MessageThreadPage>
}
```

All methods follow the existing `DefaultProfileRepository` pattern: `withContext(IoDispatcher) { runCatching { … } }`, error-identity logging via Timber. No in-memory cache layer for the MVP — refetch on screen open.

The interface is introduced in nubecita-nn3.1 (convo list PR) with only `listConvos`; nubecita-nn3.2 (thread PR) extends it with `resolveConvo` and `getMessages` — additive, no breaking changes. Default impl + DI binding are shared across both PRs (the binding lands in PR 1 and is unmodified in PR 2 except for the method bodies expanding).

### State shapes (per CLAUDE.md MVI conventions)

`ChatsScreenViewState` and `ChatScreenViewState` use **flat fields for independent flags + a sealed `Status` sum for the screen's mutually-exclusive lifecycle**. Each screen's `Status` carries its load lifecycle (`Idle / Loading / Loaded(items) / Error(error)`) and may carry per-variant payloads. This is the same pattern `ProfileScreenViewState`'s `TabLoadStatus` uses — sealed sums for "exactly one of these," flat booleans for independently-mutating flags.

## Error handling

Three error variants per screen:

| Variant | Trigger | UI |
|---|---|---|
| `Network` | `IOException` from the atproto stack | inline error composable in the body slot: icon + "Network error" + Retry button |
| `NotEnrolled` | Bluesky's chat API surfaces a specific "user has not opted in" error code | inline error composable: icon + copy explaining DMs need to be enabled in the Bluesky settings; no Retry (the user has to act outside the app) |
| `Unknown` | Anything else (`XrpcError`, `IllegalStateException`, etc.) | inline error composable: generic copy + Retry |

`NotEnrolled` is the one MVP-specific error variant — Bluesky chat requires explicit opt-in and the API returns a recognizable failure for users who haven't. We treat it as a known empty-ish state, not a catastrophic error.

No screen-level snackbar effects in V1; errors are sticky inline so the user always knows the screen failed to load.

## Visual styling (Material 3 Expressive — GChat-inspired)

The styling notes below are the **shared visual language** for both screens. Per-screen layout details are deferred to each child's plan doc.

### Convo list row (`ConvoListItem`)

- 64dp minimum row height (touch target).
- 16dp leading + trailing padding; 12dp vertical content padding.
- Layout: `Row { Avatar (40dp circle) | Spacer(12dp) | Column(weight 1f) { Title row, Subtitle } | Spacer(12dp) | Timestamp }`.
- **Title row**: `displayName ?: handle` in `titleSmall` with `FontWeight.Medium`, single line, ellipsized. Timestamp is **inside** this row, right-aligned, `labelSmall`, `onSurfaceVariant`.
- **Subtitle**: last-message snippet in `bodyMedium`, `onSurfaceVariant`, max 2 lines, ellipsized. If the snippet is from the viewer, prefix with `"You: "` (matches GChat). If the snippet describes a non-text payload, italicize (matches GChat's "Sent an image").
- **Avatar fallback**: deterministic-hue circle with the display-name's first character in white. `:feature:chats:impl/data/ConvoMapper.kt` inlines its own copy of `avatarHueFor(did, handle)` — the helper currently lives in `:feature:profile:impl/data/AuthorProfileMapper.kt`. Per YAGNI, we don't extract to `:designsystem` yet; the second copy is cheap, and the moment a third consumer appears, that change extracts both into a shared helper. The two copies stay byte-identical until extraction (a deliberate constraint — diverging implementations would re-paint avatars differently for the same DID).
- **No row dividers**. Vertical rhythm comes from row padding, not from `HorizontalDivider`.
- **Press feedback**: standard M3 ripple via `Modifier.clickable`. Roles: `Role.Button`.

### Message bubble (`MessageBubble`)

- **Outgoing** (sender == viewer): right-aligned, `MaterialTheme.colorScheme.primaryContainer` fill, `onPrimaryContainer` text.
- **Incoming**: left-aligned, `MaterialTheme.colorScheme.surfaceContainerHigh` fill, `onSurface` text.
- **Asymmetric corner radii**: 16dp on three corners, **4dp on the tail corner** pointing toward the sender side. For outgoing (right-aligned) bubbles, the bottom-right corner is 4dp; for incoming bubbles, the bottom-left corner is 4dp. Implemented via `RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 4.dp, bottomStart = 16.dp)` (outgoing) — flip for incoming.
- **Max width**: 75% of the screen width, so long messages wrap and don't run edge-to-edge.
- **Text style**: `bodyLarge`.
- **Horizontal padding**: 16dp screen-edge padding, 12dp horizontal text padding inside the bubble; 8dp vertical text padding inside the bubble.

### Sender grouping

- Consecutive messages from the same sender share a run; spacing between messages within a run is **4dp**. Spacing between different-sender runs is **12dp**.
- **Incoming avatar**: 32dp circle, shown only on the **first** message of a run; subsequent messages in the run get a 40dp spacer (32 + 8 padding) so the bubbles align.
- **Outgoing**: no avatar (the viewer doesn't need to see their own face).

### Day separator chip (`DaySeparator`)

- Centered horizontally with the message list.
- Pill shape: 16dp rounded corners, `surfaceContainer` background, `onSurfaceVariant` text in `labelSmall`.
- Copy: `"Today"`, `"Yesterday"`, or `"MMM d"` (e.g., `"Apr 25"`) — locale-aware.
- Inserted between two adjacent messages whose `createdAt` fall on different days (viewer's local time zone).

### Top bars

- **Chats tab home**: `TopAppBar(title = { Text("Chats") })`. No actions in V1.
- **Chat thread**: `TopAppBar` with `navigationIcon = back arrow` + `title = { Row { Avatar(28dp) | displayName / @handle Column } }`. The back arrow is always visible (consistent with the profile-bar pattern from bd-1tc).

### Theme assumptions

We rely on the existing `NubecitaTheme(dynamicColor = false)` to give us the right M3 surface tones in both light and dark. No new colors land in this MVP. Future polish can introduce a deeper-blue surface tone if the dark theme reads too generic vs. GChat's signature navy.

## Testing

### Per child

| Child | Unit | Screenshot | androidTest |
|---|---|---|---|
| nubecita-nn3.1 (convo list) | `ChatsViewModel` state machine: idle → loading → loaded(empty/non-empty) → error variants | `ChatsScreen` with `loaded(3 items)`, `loaded(empty)`, `error(network)`, `error(not-enrolled)` × light + dark | One androidTest launching `ChatsScreen` with a fake `ChatRepository` to verify the list renders + ripples on tap |
| nubecita-nn3.2 (thread) | `ChatViewModel` state machine: DID → resolveConvo → loading → loaded → error variants; sender-grouping helper unit tests | `ChatScreen` with `loaded(mixed thread)`, `loaded(empty)`, `error(network)`. `MessageBubble` in isolation: incoming, outgoing, mid-run, day-separator. × light + dark | One androidTest launching `ChatScreen` with a fake `ChatRepository` to verify message bubbles render + back works |
| nubecita-a7a (profile wiring) | `ProfileViewModelTest`: existing `MessageTapped emits ShowComingSoon(Message)` test replaces to `MessageTapped emits NavigateToChat(otherUserDid)` | No new screenshots (visual unchanged on Profile) | None (covered by existing Profile instrumentation tests) |

### Cross-cutting fakes

Each child PR ships a `FakeChatRepository` in `:feature:chats:impl/src/test/`. The fakes carry the same `Result<…>`-returning shape as `DefaultChatRepository` so state-machine tests can drive each lifecycle variant deterministically.

## Migration / rollout

Each child lands as its own PR. No feature flag — the Chats tab visibly changes from placeholder to real content in PR 1, the thread becomes reachable from the list in PR 2, and the profile-tap routing converges in PR 3. Each PR is self-contained.

`:app`'s `MainShellPlaceholderModule.provideChatsPlaceholderEntries` removal in PR 1 means the moment that PR lands, the Chats tab home renders the new ChatsScreen. There's no half-state where the tab is still placeholder but the impl module exists.

## Open questions

None at the epic level. Per-child planning may surface implementation-detail questions (e.g., the exact `getConvoForMembers` error shape for the `NotEnrolled` mapping); those are addressed in each child's brainstorm pass.

## References

- bd nubecita-nn3 (this epic), nubecita-nn3.1 (convo list), nubecita-nn3.2 (thread), nubecita-a7a (profile wiring).
- Visual inspiration: Google Chat Android app (dark theme).
- Lexicon: `io.github.kikin81.atproto.chat.bsky.convo.*` (generated in atproto-kotlin; coverage verified 2026-05-13).
- Pattern precedent for the data layer: `:feature:profile:impl/data/DefaultProfileRepository` (Hilt + IoDispatcher + runCatching + Timber error-identity logging).
- Pattern precedent for `:feature:*:api` + `:feature:*:impl` split: `:feature:postdetail`, `:feature:composer`, `:feature:feed`.
- Pattern precedent for `@MainShell EntryProviderInstaller`: `MainShellPlaceholderModule` + `:feature:feed:impl` + `:feature:profile:impl`.
