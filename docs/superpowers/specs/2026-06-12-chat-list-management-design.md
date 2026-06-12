# Chat list management — message requests, multi-select actions, leave-with-undo

- **Date:** 2026-06-12
- **Epic:** `nubecita-kc17`
- **OpenSpec change:** `add-chat-list-management`
- **Surface:** `:feature:chats:impl` (the Chats tab home + its data layer), one dedicated `:designsystem` icon change.

## Problem

The Chats tab home (`ChatsScreen` / `ChatsScreenContent`) is a flat, single-list inbox. It is missing three things a native messaging surface needs:

1. **No separation of accepted chats from message requests.** `chat.bsky.convo.listConvos` returns only accepted convos by default; pending requests (people you don't follow) are invisible, so there is no way to triage them.
2. **No per-conversation actions.** You cannot leave, mute, report, or block a conversation, nor jump to the other person's profile, from the list.
3. **No safe way to remove a conversation.** Leaving a chat (`leaveConvo`) is irreversible server-side. A naive "Leave" that fires the call immediately means a mis-tap is gone forever.

## Goals

- Triage requests vs accepted chats with a familiar segmented toggle.
- Select one or many conversations and act on them, with an action set that adapts to what's selected.
- Make leaving a conversation recoverable for a few seconds without any "undo on the server" (there is none).

## Non-goals (explicitly deferred)

- **In-thread per-message delete** (`deleteMessageForSelf`). The SDK supports it; it is reserved for a later change so this set stays scoped to the list surface.
- The Pro / paywall angle — none of this is gated.
- Reactions, typing indicators, or any thread-screen change beyond what G2's "open from selection" needs.

## AT Protocol primitives (confirmed present in the vendored SDK)

| Need | Operation |
|---|---|
| Accepted vs request lists | `listConvos(status = "accepted" \| "request")` (also `readState`) |
| Per-convo status on a row | `ConvoView.status` |
| Leave / decline | `leaveConvo(convoId)` |
| Accept a request | `acceptConvo(convoId)` |
| Mute / unmute | `muteConvo(convoId)` / `unmuteConvo(convoId)` |
| (deferred) per-message delete | `deleteMessageForSelf(convoId, messageId)` |

## Sequencing — one epic, four PRs

The order is driven by one hard constraint: **adding glyphs regenerates the Material Symbols subset font, which must be its own designsystem PR** so the font's repo-wide baseline blast radius never rides a feature PR (lesson from `nubecita-a580`/`nubecita-6rdb`).

### G0 — New glyphs (`nubecita-kc17.1`, designsystem, lands first)

Add four `NubecitaIconName` entries: `Logout` (leave), `NotificationsOff` (mute), `Flag` (report), `Block` (block). `Person` / `Check` / `Close` already exist and cover go-to-profile / accept / exit-selection.

- Confirm each glyph's codepoint against the cached upstream cmap with fontTools (don't guess).
- Verify **zero outline drift** on shared glyphs (`getCoordinates` diff) before regenerating, so only `NubecitaIconShowcase` moves.
- Regenerate the subset on CI via the `update-baselines` label (Linux render is the source of truth); make all visual changes before applying the label, and don't push to the branch while that workflow runs.
- `NubecitaIconNameTest.every_codepoint_isASingleScalar` gates validity.

### G1 — Chats / Requests segmentation (`nubecita-kc17.2`, depends on G0)

A pill-tab toggle above the list (reusing the `ProfilePillTabs` pattern already used by Search), two segments: **Chats** (accepted) and **Requests** (pending), with a count badge on Requests.

- `ChatRepository` fetches both lists. `refreshConvos()` issues the two `listConvos` calls (`accepted`, `request`) **concurrently** (`async` within a `coroutineScope`) since they're independent — one round-trip of latency, not two. Each is a single user-initiated XRPC, so this is battery-neutral (no new background work).
- A Requests-fetch failure **degrades gracefully**: Chats still loads; the Requests segment renders its own inline error/empty body. The whole screen never fails just because requests couldn't load.
- Tapping a request opens the thread normally — replying auto-accepts server-side. Explicit accept/decline arrives in G2, which keeps G1 small and independently shippable.

### G2 — Multi-select contextual app bar + actions (`nubecita-kc17.3`, depends on G0 + G1)

Long-press a row enters **selection mode**; the `TopAppBar` swaps to a contextual bar (close ✕, selected count, adaptive action icons). While in selection mode, a row tap **toggles** selection instead of opening the thread.

The action set is **derived** from `selection.size` and the active segment, so invalid combinations are unrepresentable:

| Segment | 1 selected | 2+ selected |
|---|---|---|
| Chats | Leave · Mute/Unmute · Go to profile · Report · Block | Leave · Mute/Unmute (bulk) |
| Requests | Accept · Leave (decline) · Go to profile | Accept · Leave (bulk) |

Single-target actions (profile / report / block) are hidden when more than one convo is selected (the Google Messages model the user asked for). For a mixed mute/unmute multi-selection, show **Mute** if any selected convo is unmuted, else **Unmute**.

- New thin repo ops: `leaveConvo`, `acceptConvo` (moves a convo from the request cache to the accepted cache), `setMuted(id, Boolean)`. Each optimistically patches the in-memory cache and reverts on failure.
- Reuse, don't rebuild: Report → push `Report.forAccount(otherUserDid)` (`:feature:moderation:api`); Go to profile → existing Profile route via `LocalMainShellNavState`; Block → existing block path.
- Action failures surface a transient snackbar (generalize the existing `ChatsEffect.ShowRefreshError` to an action-error effect); the optimistic patch reverts.
- Tablet split: the contextual bar replaces the **list pane's** top app bar only; the detail pane is untouched.

### G3 — Leave-with-undo (`nubecita-kc17.4`, depends on G2)

Leaving (single or bulk) removes the row(s) immediately and shows a Snackbar `Conversation left · Undo`. The `leaveConvo` network call is **deferred until the leave commits** (snackbar dismiss, supersede, or normal screen-leave):

- **Undo** restores the rows with **zero network** — nothing was ever sent, so nothing is lost. This is a "we never deleted in the first place" window, not a server-side reversal (there is none).
- **Dismiss** (timeout or superseded by a new action) fires `leaveConvo` once per held convo; then it is final.
- **One pending batch at a time.** Starting a new leave while a batch is pending commits the prior batch immediately (Gmail-style).
- **Normal screen clearance commits, it does not drop.** When the Chats VM is cleared normally (navigated away), `onCleared()` flushes any pending leave — leaving a chat then navigating away must *leave* it, not silently restore it. Only **process death** (where `onCleared()` does not run) drops a still-pending leave; we do not persist pending leaves across death to fire them later. The split falls out naturally: `onCleared` flushes on normal teardown; process death skips `onCleared`, so nothing fires and the leave drops — honoring the "process death drops" intent while fixing the silent-drop-on-navigation bug.
- **Partial failure is per-conversation.** When a committed bulk-leave's calls partly fail, only the failed conversations are restored; the successfully-left ones stay gone.
- The deferral **orchestration** lives in the **ViewModel**, not the repository (the repo stays a thin XRPC+cache layer exposing a plain `leaveConvo`; the VM owns the pending-set, optimistic projection, timer, and undo). But the **terminal commit call runs on an injected application-scoped `CoroutineScope`**, not `viewModelScope`, so a commit completes even as the VM scope tears down (this is what makes the `onCleared` flush actually finish).

## Data layer

`ChatRepository` / `DefaultChatRepository` extend the existing in-memory reactive cache (today a single `MutableStateFlow<ImmutableList<ConvoListItemUi>?>`) to hold **both** the accepted and request lists — either keyed by status or as two flows. `refreshConvos()` populates both. New suspend ops (`leaveConvo`, `acceptConvo`, `setMuted`) patch the cache optimistically; `acceptConvo` moves a convo across the two lists. The repository deliberately does **not** know about the undo window — that is a ViewModel concern.

## MVI (`ChatsContract`)

- `ChatsScreenViewState` gains `activeSegment: ChatsSegment`, `requestCount: Int`, and a nullable `selection` (set of selected convoIds; `null` ⇒ normal mode). `selection` may coexist with `Loaded`; the contextual app bar is driven by `selection != null`, and available actions are derived from `selection.size` + `activeSegment`.
- New events: `SegmentSelected`, `ConvoLongPressed`, `SelectionToggled`, `ClearSelection`, `LeaveSelected`, `ToggleMuteSelected`, `AcceptSelected`, `ReportSelected`, `BlockSelected`, `ProfileSelected`, `UndoLeave`.
- New effects: `NavigateToProfile(did)`, `NavigateToReport(Report)`, `ShowLeaveUndo(count)` (drives the snackbar), and a generalized `ShowActionError`.

Per `CLAUDE.md`'s MVI conventions: the segment and selection are flat, UI-ready fields; the load lifecycle stays in the existing `ChatsLoadStatus` sealed sum. No `Async<T>` wrapper is introduced.

## UI

`ChatsScreenContent` conditionally renders a contextual app bar when `selection != null` (close, count, adaptive icons) in place of the normal `TopAppBar` (title + settings gear + FAB). The pill-tab segment row sits under the app bar in normal mode and is hidden in selection mode. Rows: tap = open thread (normal) / toggle (selection); long-press = enter selection. The existing `SnackbarHostState` hosts the leave-undo snackbar. Reuse `ProfilePillTabs` for the segment toggle.

## Error handling

- Action failures (leave/accept/mute) → transient snackbar via the generalized action-error effect; optimistic cache patch reverts.
- Requests-fetch failure → graceful degrade (Chats loads; Requests segment shows inline error).
- A leave that fires after the snackbar and fails → snackbar; the row is restored (it wasn't actually left).

## Testing

- **VM unit tests:** segment switching; selection set transitions; the action-availability matrix (count × segment); leave-undo defer / commit-on-dismiss / restore-on-undo / supersede-prior-batch / bulk; accept moves a convo between lists.
- **Repo unit tests:** two-list refresh; leave/accept/mute cache patches and reverts.
- **Screenshot tests:** normal list; Requests segment with badge; selection mode with 1 vs 2+ selected; light/dark.
- **G0:** `NubecitaIconNameTest` + the `NubecitaIconShowcase` baseline regen on CI.

## Workflow

Tracked as epic `nubecita-kc17` with children G0–G3 (`kc17.1`–`kc17.4`), dependency-ordered. This design migrates into the OpenSpec change `add-chat-list-management` (proposal / design / tasks), whose tasks cross-link the bead ids. Each task group lands as its own PR.
