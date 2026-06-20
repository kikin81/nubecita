# Group chat — Phase 2: message reactions

**bd:** nubecita-hwix.3 (Phase 2) under epic nubecita-hwix (Group chat support)
**Date:** 2026-06-20
**Status:** Approved

## Problem

Phase 1 (view + send, #555) shipped reading and sending in 1:1 and group
conversations. Bluesky chat supports **emoji reactions** on messages, and the
official client renders them — but Nubecita doesn't yet (Phase 1 listed
reactions as a non-goal). This phase adds adding/removing emoji reactions and
rendering reaction chips, in both 1:1 and group threads.

## Scope

Add/remove emoji reactions on chat messages (both 1:1 and group). Long-press a
message → a quick-react bar (6 common emoji) plus a `+` that opens a full emoji
picker; reaction chips (emoji + count, the viewer's own highlighted) render
under each message and tapping a chip toggles it.

**Out of scope (deferred):** who-reacted names (count only), reaction
notifications, animated reactions, and the convo-list `lastReaction` preview
(e.g. "Alice reacted ❤️ to your message").

## SDK (atproto-kotlin 9.3.1, `chat.bsky.convo`)

Feature-complete — no SDK bump:

- `MessageView.reactions: List<ReactionView>`; `ReactionView(value: String,
  sender: ReactionViewSender, createdAt)`; `ReactionViewSender` is **DID-only**.
- `ConvoService.addReaction(AddReactionRequest(convoId, messageId, value))` →
  `AddReactionResponse(message: MessageView)`.
- `ConvoService.removeReaction(RemoveReactionRequest(convoId, messageId, value))`
  → response carrying the updated `MessageView`.
- Both mutations **return the updated `MessageView`**, so reconciliation replaces
  the message's reactions from the authoritative server view.
- `value` is a **single emoji grapheme** (the lexicon caps graphemes at 1). The
  server also enforces a per-user/per-message reaction cap.

Because `ReactionViewSender` is DID-only, a "who reacted" list would need the
profile hydration built in #557 — that's why who-reacted names are out of scope
here; we show **counts only**, which needs no extra fetch.

## Decisions

1. **Quick-react bar + full picker.** Long-press a message bubble opens a
   reaction menu: a row of 6 common emoji (`❤️ 😂 👍 😮 😢 🙏`) and a `+`. The
   `+` opens the AndroidX `emoji2-emojipicker` `EmojiPickerView` inside a
   `ModalBottomSheet` (via `AndroidView`) — full set, search, skin-tone variants,
   recents, for ~no code. Both the bar and the picker funnel into one toggle
   path. Rationale: least code, matches the official client's capability, and the
   picker naturally yields a single emoji grapheme (the lexicon requirement).
2. **Counts only (emoji + count chips), viewer's own highlighted.** No
   who-reacted names in Phase 2 — keeps it network-free (no DID hydration).
3. **One `ToggleReaction(messageId, emoji)` path.** Tapping an existing chip and
   selecting from the bar/picker are the same operation: if the viewer already
   reacted with that emoji → remove, else → add.
4. **Optimistic with authoritative reconcile.** Update the in-memory message's
   reactions immediately, then call the repository; on success replace that
   message's reactions from the returned `MessageView`; on failure roll back and
   surface a transient snackbar. The server's reaction cap is handled by this
   rollback path (no client-side cap guess).
5. **Gated on `canPost`.** A read-only convo (Phase 1's `canPost = false`) shows
   no reaction affordance, matching the disabled composer.

## Models (`ChatContract`)

```kotlin
@Immutable
data class ReactionUi(
    val emoji: String,
    val count: Int,
    val reactedByViewer: Boolean,
)
```

`MessageUi` gains `val reactions: ImmutableList<ReactionUi> = persistentListOf()`
(defaulted, so existing construction sites and the optimistic-send echo are
unaffected). `ThreadItem.Message` is unchanged — it already wraps a `MessageUi`.

## Data flow

- **Read.** `MessageMapper.toMessageUi(viewerDid)` aggregates the wire
  `reactions`: group by `value`, `count = size`, `reactedByViewer = any {
  sender.did == viewerDid }`; stable order (count desc, then emoji). Pure,
  unit-tested. Deleted messages carry no reactions.
- **Toggle.** `ChatViewModel` handles `ToggleReaction(messageId, emoji)`:
  - Resolve the current reaction state for that message+emoji from the in-memory
    `messages`.
  - Optimistically mutate that message's `reactions` (add a viewer reaction with
    `count + 1`, or remove the viewer's and `count - 1`, dropping the chip at 0)
    and `commitMessages`.
  - Call `repository.addReaction` / `removeReaction`; on success, replace that
    message's `reactions` with the returned `MessageUi`'s (authoritative); on
    failure, revert to the pre-toggle reactions and emit a reaction-error effect.
  - Only **Sent** server messages get the affordance — optimistic `Sending` rows
    and deleted messages are excluded.
- **Repository.** `addReaction(convoId, messageId, emoji): Result<MessageUi>` and
  `removeReaction(...)` wrap the XRPC calls and map the response `MessageView` →
  `MessageUi` (reusing `toMessageUi` with the viewer DID). Rethrow
  `CancellationException`; log only the failure's `javaClass` (matches the
  repo's redaction discipline). All five `ChatRepository` implementors (Default +
  test/androidTest/bench + the nested test fake) implement the two methods.

## UI (`MessageBubble` / `ChatScreenContent`)

- **Chips.** A `FlowRow` under the bubble content (below text/embed, above the
  send-status footer), aligned to the message side (start for incoming, end for
  outgoing). Each chip shows `emoji count`; `reactedByViewer` chips use a
  `secondaryContainer` tonal highlight (per the surface-roles contract); tapping
  a chip emits `ToggleReaction`.
- **Reaction menu.** Long-press a message bubble opens the menu (Compose `Popup`
  or small sheet) with the 6 quick emoji + `+`. Selecting an emoji emits
  `ToggleReaction(messageId, emoji)` and dismisses. The `+` opens the picker
  sheet. Thread long-press is currently unused (the convo-list long-press —
  multi-select — is a different screen), so there's no gesture conflict.
- **Picker.** `:feature:chats:impl` adds `androidx.emoji2:emoji2-emojipicker`. An
  `EmojiPickerSheet` composable hosts `EmojiPickerView` via `AndroidView` in a
  `ModalBottomSheet`; `setOnEmojiPickedListener` → `onEmojiPicked(emoji)` →
  `ToggleReaction`. Take the first grapheme defensively; ignore empties.
- The affordances render only when `state.canPost` is true.

## Error handling

Optimistic rollback on failure plus a transient snackbar, collected by the
screen's existing `ChatEffect` collector (a new `ShowReactionError` variant, or
the existing error effect reused). `CancellationException` is rethrown in the
repository `runCatching` blocks (the established pattern).

## Bench / Fastlane

Seed a couple of reactions on the bench group fixture (e.g. `👍 ×2` and a `❤️`
by the viewer) so the Fastlane Google Play capture shows reactions. Extend
`BenchMessageDto` with an optional `reactions` list and populate `MessageUi`
in `BenchChatsMapper` (no profile fetch needed — counts only).

## Testing

- **Unit:** `MessageMapper` reaction aggregation (grouping, count,
  `reactedByViewer`, deleted-message has none); `ChatViewModel` toggle (add →
  `addReaction` called + optimistic chip; toggle off → `removeReaction`; success
  reconcile from the returned message; failure → rollback + error effect; no
  affordance for optimistic/deleted rows).
- **Screenshot:** a message with reaction chips (own-highlighted vs not), and the
  quick-react bar. The `emoji2` picker sheet is verified via preview/on-device,
  not a baseline (it's an AndroidView).

## Non-goals (Phase 2)

- Who-reacted names / reactor lists.
- Reaction push notifications.
- Animated / bursting reaction effects.
- Convo-list `lastReaction` activity preview.
- Group creation / invite links / member management (Phase 3).
