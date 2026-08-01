## Why

A user with pending message requests has no discoverable way to accept them. The Requests segment lists them, and the segment's own empty state promises "Messages from people you don't follow will appear here for you to accept or ignore" — but the only accept path is to long-press a row to enter multi-select and use the contextual bar. Reported from the field: three pending requests, no way found to accept any of them.

Opening a request is worse than a missing button. The thread renders a normal, fully-enabled composer, identical to an accepted conversation, with nothing indicating the message is a request. Typing and sending is invited by the UI and then rejected by the server, surfacing as a generic send error.

## What Changes

- A request conversation opened from the Requests segment SHALL replace its composer with a dedicated accept surface, so a request cannot be replied to before it is accepted.
- The accept surface offers **Accept** (direct) or **Accept and join** (group), plus a decline action that reuses the existing recoverable undo.
- Accepting from the thread transitions in place to the real composer; the conversation moves to the Chats segment without a manual refresh.
- The Requests segment gains a per-row Accept / Decline action, so accepting one request no longer requires entering multi-select.
- `ConvoView.status` is carried through the conversation mapper to both the list row and the thread, replacing today's status-blind mapping.
- The bench flavor gains request-status conversation fixtures, which do not exist today, so the flow is screenshot- and bench-verifiable.

No breaking changes. No SDK change: `chat.bsky.convo.acceptConvo` is already exposed by the pinned `atproto-kotlin` 9.9.2 and `DefaultChatRepository.acceptConvo` is already implemented, including the cache patching that moves an accepted conversation between segments.

## Capabilities

### New Capabilities
- `chat-request-acceptance`: How a pending conversation request is presented and accepted from inside the conversation thread — request detection from convo status, replacing the composer with the accept surface, the accept/decline/block actions, direct-vs-group labelling, and the transition to a live composer on success.

### Modified Capabilities
- `feature-chats`: The Requests segment gains a direct per-row Accept / Decline affordance. Today accepting requires entering multi-select first; the existing multi-select bulk path is retained, not replaced.

## Non-goals

- **Outbound group join requests.** `chat.bsky.convo.listConvoRequests` returns a union whose second member, `joinRequestConvoView`, is a group *the user asked to join* — actionable only via `withdrawJoinRequest`, never acceptable. It shares exactly one field (`convoId`) with `convoView`: no `lastMessage`, no `unreadCount`, no `members`, so it cannot populate the existing row model at all. Surfacing it is a second row type with an inverted action and a badge-semantics decision, and it overlaps the existing group join-request surface. Tracked separately as `nubecita-pygz`. The Requests segment continues to use `listConvos(status = "request")`.
- **Changing the owner-side group join approval flow.** `listJoinRequests` / `approveJoinRequest` / `rejectJoinRequest` already have their own screens and are untouched.
- **Bulk accept redesign.** The existing multi-select contextual bar keeps working exactly as specified today.
- **Request-specific notifications or badging changes.** The Requests count badge keeps its current meaning and source.

## Impact

**Affected code**

- `feature/chats/impl` — `ConvoMapper` (status is currently dropped), `ChatRepository.ChatConvo`, `ChatContract.ChatScreenViewState` / `ChatEvent`, `ChatViewModel`, `ChatScreenContent` bottomBar, `ChatsContract.ConvoRowUi`, `ui/ConvoListItem`.
- `feature/chats/impl/src/bench` — `BenchConvoDto` and `chats.json` gain a request status; `BenchFakeChatRepository` gains a real `acceptConvo` that moves a row between flows.

**Not affected**

- `:core:auth`, networking, and the atproto SDK pin. `canViewerPost` changes meaning but stays in the same mapper.
- `DefaultChatRepository.acceptConvo` and its cache patching are reused as-is.

**Baseline conformance**

No deviation from the MVI / Compose / Hilt baseline. Two existing constraints the implementation must respect rather than relax:

- The chat `Scaffold` bottomBar is the single window-inset owner (`contentWindowInsets = safeDrawing.exclude(ime)`), so the accept surface must live inside that same inset-owning `Column` — the pattern `ChatReplyBanner` already uses — and must not introduce a second IME layer.
- Accept and decline are network-bound actions on a screen that already routes errors through `UiEffect`; they follow that path rather than adding sticky error state.
