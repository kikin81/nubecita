# Group chat — Phase 1: view + send messages

**bd:** nubecita-r3g1 (Phase 1) under epic nubecita-hwix (Group chat support)
**Date:** 2026-06-19
**Status:** Pending review

## Problem

Bluesky added group conversations. Nubecita renders them in the convo list but
**tapping a group convo errors / opens the wrong thread**, because the chats
feature models a conversation's identity as `otherUserDid`. A group has no single
"other user," so:

- `ConvoMapper` picks `members.firstOrNull { it != viewer }` — an arbitrary group
  member — and stores that member's DID on the row.
- Tapping calls `resolveConvo(otherUserDid)` → `getConvoForMembers([thatOneDid])`,
  which resolves/creates a **synthetic 1:1** with that member, not the group.
- The thread `TopAppBar` shows a single member's handle.

The fix is to re-key the chat path to **`convoId`** (the AT Protocol identity that
`getConvo` / `getMessages` / `sendMessage` already use) and to model the
direct-vs-group distinction explicitly.

SDK note: `atproto-kotlin` 9.3.1 is feature-complete for this — `ConvoView.kind`
(`DirectConvo` / `GroupConvo`), `members`, convoId-keyed `getConvo`/`getMessages`/
`sendMessage`, `lockStatus`, `acceptConvo`. No SDK bump.

## Scope

**Phase 1: view + send in group conversations.** Out of scope: message reactions
(Phase 2) and group creation / invite links / member management (Phase 3,
`GroupService`).

## Decisions

1. **Identity = `convoId`.** Re-key the `Chat` NavKey from `otherUserDid` to
   `convoId`. The "message this user" entry points (profile, new-chat) resolve
   did→convoId via `getConvoForMembers` **before** navigating, then push
   `Chat(convoId)`. One unified thread route for 1:1 and group.
2. **Fully sealed `ConvoRowUi`** for the convo list, with shared fields as
   interface properties so existing shared-field operations (multi-select, mute,
   leave — all keyed on `convoId`) keep working polymorphically.
3. **Avatar + name sender attribution** in group threads, collapsing consecutive
   runs from the same sender; own messages stay bare/right-aligned.
4. **Lightweight send-gating in Phase 1** — derive can-post from the already-loaded
   `ConvoView` (`lockStatus` / membership), no extra capability call; disable the
   composer with a hint when you can't post; fall back to the send-error path.

## Models

### Convo list row (`ChatsContract`)

```kotlin
sealed interface ConvoRowUi {
    val convoId: String
    val lastMessage: String?          // existing shared fields, now interface props
    val timestamp: ...
    val unreadCount: Int
    val muted: Boolean
    val isSelected: Boolean
    // ...other shared fields the row already carries...

    data class Direct(
        // shared overrides +
        val otherUserDid: String,
        val handle: String,
        val displayName: String?,
        val avatarUrl: String?,
    ) : ConvoRowUi

    data class Group(
        // shared overrides +
        val name: String,
        val members: ImmutableList<AuthorUi>,   // for the AvatarGroup facepile
    ) : ConvoRowUi
}
```

Consumers that only need shared data read `row.convoId` / `row.unreadCount` via the
interface (no `when`). Only avatar/title rendering branches on the variant.

### Thread state (`ChatContract`)

`ChatScreenViewState` keeps the shared thread fields flat (`messages`, composer
state, `isLoading`, error, `canPost`) and adds a sealed identity:

```kotlin
sealed interface ChatHeader {
    data class Direct(val handle: String, val displayName: String?, val avatarUrl: String?, val did: String) : ChatHeader
    data class Group(val name: String, val members: ImmutableList<AuthorUi>) : ChatHeader
}
// ChatScreenViewState(..., val header: ChatHeader?, val canPost: Boolean, ...)
```

(The thread state is **not** fully sealed — messages/composer are shared; only the
header identity is a sum.)

### Message row

The message UI model gains a `sender` (the author's `AuthorUi`) plus a derived
`showSender: Boolean` (true on the first incoming message of a consecutive run from
that sender; always false for own messages). The run-collapsing is computed once
when mapping the message list (pure function, unit-tested).

## Data flow

- **Convo list:** `ConvoMapper` branches on `ConvoView.kind`:
  - `DirectConvo` → `ConvoRowUi.Direct` (existing pick-the-other-member logic).
  - `GroupConvo` → `ConvoRowUi.Group(name, members.map { it.toAuthorUi() })`.
  The **Requests tab uses the same row**, so group invites render correctly for
  free; accepting stays `acceptConvo(convoId)` (already convoId-keyed).
- **Open thread:** `Chat(convoId)` → `ChatViewModel` calls `getConvo(convoId)`
  (header + kind + lockStatus + members) then `getMessages(convoId)`.
- **"Message this user" entry points:** profile / new-chat resolve did→convoId via
  `getConvoForMembers` before navigating (a brief resolve step on tap; this moves
  the resolve from inside-the-thread to before-nav — note the minor UX shift, show
  a loading affordance on the tapped action).
- **Send:** `sendMessage(convoId, …)` — unchanged.
- **Send-gating:** `canPost` derived from the loaded `ConvoView`
  (`lockStatus`/membership); composer disabled + hint when false.

## Files touched

- `feature/chats/api` — `Chat` NavKey (`otherUserDid` → `convoId`).
- `feature/chats/impl`:
  - `data/ConvoMapper.kt` — `kind` discrimination → sealed `ConvoRowUi`.
  - `data/ChatRepository.kt` / `DefaultChatRepository.kt` — resolve→convoId,
    `getConvo` load, `lockStatus`/membership for gating.
  - `ChatsContract.kt` / `ChatContract.kt` — sealed `ConvoRowUi` / `ChatHeader`,
    `canPost`, message `sender`/`showSender`.
  - `ui/ConvoListItem.kt` — variant rendering (Group → `AvatarGroup` + name).
  - `ChatScreenContent.kt` — group header (name + member count + `AvatarGroup`),
    sender-attributed message rows, gated composer.
  - `ChatViewModel.kt` — load by `convoId`; compute `canPost`.
  - "Message this user" call sites (profile / new-chat entry points).
  - All chat fakes / tests / screenshot fixtures updated to the sealed models.

## Error handling

Failures route through the existing `ShowError` effect; "can't post" is a non-error
disabled state, not an error. `CancellationException` is rethrown in repository
`runCatching` blocks (matches the established auth-repo pattern).

## Testing

- **Unit:** `ChatViewModel` (load by convoId, kind→header, `canPost` gating);
  `ConvoMapper` group + direct cases; the sender-run-collapsing pure function
  (first-of-run / consecutive / own-message).
- **Screenshot:** group convo row (facepile + name), group thread header, group
  thread with sender-attributed runs, disabled composer (can't-post).
- Existing 1:1 chat tests re-validated against the sealed models.

## Non-goals (Phase 1)

- Message reactions (Phase 2).
- Group creation, invite links, add/remove members, approve/reject join requests
  (Phase 3 — `GroupService`).
