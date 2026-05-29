## Context

The read-only Chats MVP already exists in `:feature:chats`:

- `ChatRepository` (impl `data/`): `listConvos`, `resolveConvo(otherUserDid)` (wraps `chat.bsky.convo.getConvoForMembers` — resolves *or creates* the 1:1 convo and returns `ConvoResolution(convoId, otherUserHandle, …)`), and `getMessages(convoId, …)`. `DefaultChatRepository` calls `ConvoService(client)` from atproto-kotlin inside `withContext(dispatcher) { runCatching { … }.onFailure { Timber … } }`.
- Thread screen: `ChatScreen` + `ChatViewModel` + `ChatContract`. `ChatScreenViewState(otherUserHandle, …, status: ChatLoadStatus)` where `ChatLoadStatus = Loading | Loaded(items: ImmutableList<ThreadItem>, isRefreshing) | InitialError(error: ChatError)`. `ThreadItem = Message(message: MessageUi, runIndex, runCount, showAvatar) | DaySeparator`. `MessageUi(id, senderDid, isOutgoing, text, isDeleted, sentAt, embed)`. `ChatEvent = Refresh | RetryClicked | BackPressed | QuotedPostTapped`; `ChatEffect = NavigateToPost`. The VM is assisted-injected with the `Chat(otherUserDid)` nav key and auto-loads `resolveConvo → getMessages`.
- List screen: `ChatsScreen` + `ChatsViewModel` + `ChatsContract` (`ChatsLoadStatus`, `ConvoListItemUi`, `ChatsEvent = Refresh | ConvoTapped | RetryClicked`, `ChatsEffect = NavigateToChat(otherUserDid) | ShowRefreshError`).
- Nav keys (`:feature:chats:api`): `Chats` (data object) and `Chat(otherUserDid: String)`.
- Models (`MessageUi`, `ThreadItem`, `ConvoListItemUi`) live **in `:feature:chats:impl`**, not `:data:models`.

This change adds the **write** path on top, leaving the read path intact. Platform constraints: text-only per the chat lexicon (`messageView` admits only text + a record embed); `transition:chat.bsky` scope and `atproto-proxy` already configured; atproto-kotlin already generates `chat.bsky.convo.sendMessage`.

## Goals / Non-Goals

**Goals:** reply with text in an existing convo; optimistic send with reconcile + inline retry; start a new convo via a recipient picker; stay within existing MVI + design-system conventions; no auth/build changes.

**Non-Goals (deferred):** media attachments, reactions, typing indicators, read receipts, message edit/delete, group DMs, new-message push, in-conversation search, GIFs/stickers/voice, real-time streaming.

## Decisions

- **Decision:** the composer owns a Compose `TextFieldState` on `ChatViewModel`, observed via `snapshotFlow`; `ChatScreen` wires the `state =` text-field overload. Derived `isSendEnabled` (false when blank/whitespace) lives on `ChatScreenViewState`.
  - **Rationale:** the only sanctioned departure from "VM owns state" in CLAUDE.md is editor surfaces; mirrors `ComposerViewModel` and avoids cursor-jump bugs. Tests drive it via `Snapshot.sendApplyNotifications()` + `testScheduler.runCurrent()`.
  - **Alternatives considered:** routing each keystroke through `handleEvent`/`setState` (rejected — cursor-jump class, and explicitly discouraged for editors).

- **Decision:** model per-message send state as a sealed sum `MessageSendStatus = Sending | Sent | Failed`, carried on `MessageUi` (default `Sent` for server-fetched messages). Optimistic rows use a client temp id `local:<n>` from a VM-held incrementing counter.
  - **Rationale:** matches the project's "sealed status sum, not `Async<T>`" rule; server messages are always `Sent`, only locally-originated rows transition. A monotonic counter avoids `Date.now()`/`Math.random()` (unavailable in some test contexts) and namespaces temp ids so they never collide with server ids.
  - **Alternatives considered:** a parallel `pendingMessages` list outside `items` (rejected — duplicates ordering/run-grouping logic already in `ThreadItemMapper`).

- **Decision:** optimistic flow = on `Send`, clear field → append `Message(MessageUi(id=local:n, isOutgoing=true, status=Sending))` → `repository.sendMessage(convoId, text)`; on success replace the temp row with the returned server message (`Sent`); on failure flip to `Failed` and emit a transient error effect. Reconciliation/de-dup against the next `getMessages` refresh keys on **server id**.
  - **Rationale:** instant feedback; the `local:` prefix guarantees a refreshed page (which will contain the real message) doesn't double-render.
  - **Alternatives considered:** block UI until server confirms (rejected — poor latency feel).

- **Decision:** add a transient `ChatEffect.ShowSendError(error: ChatError)` (snackbar); sticky failure stays on the `Failed` message row with an inline retry (`ChatEvent.RetrySend(tempId)`).
  - **Rationale:** mirrors `ChatsEffect.ShowRefreshError` (transient via effect) vs sticky-in-state; the row is the durable record of failure, the snackbar is the notification.

- **Decision:** new-chat = new `NewChat` nav key (`@Serializable data object`) in `:feature:chats:api`; `ChatsScreen` shows an M3E small FAB (bottom-end) that emits an effect → `LocalMainShellNavState.current.add(NewChat)`. A new `NewChatScreen` + `NewChatViewModel` + `NewChatContract` (query `TextFieldState`, `ImmutableList<…>` results, sealed `NewChatLoadStatus = Idle | Searching | Results | Empty | Error`) does `searchActors`; selecting a result emits `NavigateToChat(otherUserDid)` → `add(Chat(otherUserDid))`.
  - **Rationale:** reuses the existing `Chat(otherUserDid)` route and `resolveConvo`, so the picker only needs to produce a DID. `getConvoForMembers` resolves-or-creates the convo at thread open, so "create on first send" needs no special path — a brand-new convo simply renders an empty thread with the composer enabled, and the first `sendMessage` materializes it server-side.
  - **Alternatives considered:** a dedicated `NewChat(did)` route distinct from `Chat` (rejected — duplicates thread wiring).

- **Decision:** spec delta is expressed as a new `feature-chats` capability with `## ADDED Requirements` only.
  - **Rationale:** there is no existing `feature-chats` spec in `openspec/specs/`, and composing/new-convo are net-new behaviors; nothing to modify or remove.

## Risks / Trade-offs

- **Double-render on refresh.** Mitigated by server-id-keyed reconciliation + `local:` temp-id namespacing.
- **`searchActors` placement.** `app.bsky.actor.searchActors` is core-lexicon (NOT chat-proxied), so it must not go through the chat proxy header. Add it to `ChatRepository` for module cohesion, or reuse an existing actor-search repository if one is cheaply available — verify during implementation.
- **New convo with no follow / DMs disabled.** `resolveConvo` already maps `MessagesDisabled` / `NotFollowedBySender` to `ChatError.MessagesDisabled`; the picker should ideally gate or gracefully surface this when opening the thread.
- **Send-status on `MessageUi`.** Adding a defaulted field touches the mapper and existing tests/fixtures; default `Sent` keeps read-path callers unchanged.

## Migration Plan

Purely additive; no data migration. The two sub-flows are independent and can land as separate child issues/PRs under `nubecita-b6uv`: **A–D** (send in existing convo: repo `sendMessage`, composer UI, optimistic reconcile, error+retry) and **E–G** (new convo: FAB + `NewChat` key, recipient picker, tap→resolve wiring). Either may land first. Screenshot baselines updated as composer/FAB/picker surfaces are added.

## Open Questions

- Does a reusable actor-search repository already exist (`:feature:search` / `:core:profile`) to back the picker, or do we add `searchActors` to `ChatRepository`?
- On selecting a recipient, pop `NewChat` off the back stack so Back returns to the convo list (vs. leaving it for Back-to-search)?
- Typeahead debounce interval for the recipient search (align with the composer mention-typeahead value)?
