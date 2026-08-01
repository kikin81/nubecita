## Context

`ConvoView` carries a `status` field (`chat.bsky.convo.defs#convoStatus`, known values `request` / `accepted`) and `getConvo` returns a full `ConvoView`. The app discards it: `ConvoMapper.toConvoRowUi` reads `kind`, `lastMessage`, `unreadCount`, `muted`, `members` and never `status`, and `toChatHeader` / `canViewerPost` do the same. `canViewerPost` returns `isMember && !locked`, so a request conversation yields `canPost = true`.

That single omission produces both reported symptoms. In the list, a request row is byte-identical to an accepted row, so there is nothing to hang a per-row action on. In the thread, `ChatScreenViewState.canPost` is `true`, so `ChatScreenContent` takes the composer branch of its bottomBar and invites a reply that the server will reject.

Everything below the mapper is already in place. `DefaultChatRepository.acceptConvo` calls `chat.bsky.convo.acceptConvo` and then patches both caches — removing the row from the request cache and prepending it to the accepted cache — so the inbox reacts correctly to an accept initiated from anywhere.

Two structural constraints shape the UI work:

- `ChatScreenContent`'s `Scaffold` sets `contentWindowInsets = safeDrawing.exclude(ime)` and gives the bottomBar sole ownership of the IME inset. Whatever replaces the composer must live inside that same inset-owning `Column`.
- The bottomBar today has exactly two branches: `canPost` → composer, else → `CannotPostNotice`. `CannotPostNotice` is a static `Surface(surfaceContainerHigh)` with no actions, used for locked groups and non-membership.

## Goals / Non-Goals

**Goals:**

- A pending request is unmistakably a request at both entry points, and acceptable at both.
- A request cannot be replied to before it is accepted — enforced structurally, not by validation.
- Accepting from the thread leaves the user in a working conversation without a manual refresh or a round trip through the list.
- The flow is verifiable in bench and screenshot tests, which is impossible today.

**Non-Goals:**

- Outbound group join requests (`listConvoRequests` / `joinRequestConvoView`) — see the proposal's Non-goals; tracked as `nubecita-pygz`.
- Replacing the existing multi-select bulk accept.
- Any change to `DefaultChatRepository.acceptConvo` or its cache patching.

## Decisions

### D1 — Replace the composer rather than banner it

The accept surface takes the composer's place in the bottomBar; it does not sit above a live composer.

*Alternative considered:* a banner above a usable composer, where sending implicitly accepts. Rejected because implicit accept is unrecoverable and easy to trigger by accident — a stray tap on a message request from a stranger both accepts it and reveals that you read it. Replacing the composer makes the invariant structural: there is no text field to type into, so "reply before accept" cannot be represented in the UI at all, and `isSendEnabled` needs no new guard.

*Alternative considered:* a full-bleed intro screen before the thread. Rejected as disproportionate — it adds a navigation destination and a back-stack entry for what is a one-tap decision, and it hides the message the user is deciding about.

### D2 — Read request status from `getConvo`, not from navigation

`isRequest` is derived from the `ConvoView.status` returned by the thread's own `getConvo` call, not passed as a nav argument from the list.

*Alternative considered:* passing a flag through the `NavKey`. Rejected because it makes the thread's correctness depend on the caller — a deep link, a notification tap, or a future entry point would arrive without the flag and silently render the wrong bottomBar. Reading it from the conversation makes every entry point correct by construction, and costs nothing: `getConvo` is already called on load.

### D3 — Stacked buttons, not an M3 connected button group

The accept surface stacks a full-width primary button above a row of text buttons.

M3 Expressive's connected button group is the idiomatic choice for a two-action cluster and is what this would otherwise use. It is rejected here specifically because of label length: "Accept and join" localises to "Aceptar y unirse" (es-419) and "Aceitar e participar" (pt-BR). A two-up connected group has previously overflowed in this app on exactly this class of string, and the failure is invisible when reviewing in English. A full-width primary button is immune to the label growing.

### D4 — Reuse `NubecitaPrimaryButton` for Accept

`NubecitaPrimaryButton` already exposes `isLoading` with a preserved accessibility label, which is precisely what a network-bound accept needs. No new design-system component is introduced.

There is no shared banner or callout component in `:designsystem` to reuse for the surrounding surface — the closest precedents are all feature-private (`ChatReplyBanner`, `CannotPostNotice`, `GroupJoinPreviewCard`). The accept surface therefore starts feature-private in `feature/chats/impl`, matching `CannotPostNotice`'s existing placement. Promoting it to `:designsystem` is deferred until a second surface needs it.

### D5 — Decline reuses the deferred-undo leave

Decline maps to the existing `leaveConvo` path with its 5s optimistic undo, already specified in `feature-chats`. No second destructive path is introduced, and declining a request is recoverable for the same window as leaving a conversation.

### D6 — Success is signalled by the composer arriving

On accept, the bottomBar cross-fades from the accept surface to the real composer via `AnimatedContent` with the expressive spatial spring. No snackbar.

The state change is the confirmation: the user asked for a conversation and now has one. A snackbar would compete with the transition and add a dismissible element over the thread they just unlocked. Failure still routes through `UiEffect` as a snackbar, matching the screen's existing error convention.

### D7 — Label varies on convo kind, not on a separate flag

"Accept" for `directConvo`, "Accept and join" for `groupConvo`, derived from the `kind` union already present on `ConvoView`. Accepting a group request genuinely joins the group, and the copy should say so.

### D8 — Bench fixtures gain a status field

`BenchConvoDto` has no `status`, so a request conversation is not representable and `BenchFakeChatRepository` hard-codes an empty request list with a no-op `acceptConvo`. The fixture gains a status field, `ensureLoaded` routes request rows to the request flow, and the fake's `acceptConvo` moves a row between the two flows.

Both a direct and a group request are seeded, because D7 means the group case exercises a different label and is otherwise untested.

## Risks / Trade-offs

- **A conversation's status changes server-side while the thread is open** (accepted from another device) → The thread reads status once at load, so it would keep showing the accept surface. Accepting an already-accepted convo is harmless: `acceptConvo` returns a response whose `rev` is absent, documented as "the convo was already accepted". The accept therefore succeeds and the UI converges. Not worth polling for.

- **`convoStatus` is an open string, not an enum** (`typealias ConvoStatus = String`) → Treat any value that is not `"request"` as acceptable-to-post rather than matching `"accepted"` explicitly, so a future status value fails toward a working composer instead of a permanently blocked one.

- **The list and thread both need the request flag on their own models** → Two mappers change, and a partial implementation could leave the list showing an accept action for a conversation the thread thinks is accepted. Mitigated by both reading from the same `ConvoView.status` and by landing the mapper change once, shared by both tasks.

- **Declining is destructive and now one tap from the thread** → It reuses the existing 5s undo, so the exposure matches the already-shipped leave action rather than exceeding it.

- **Screenshot coverage depends on the bench fixture landing first** → Sequenced as the first task; the two UI tasks are otherwise blocked from meaningful visual verification.

## Migration Plan

No data migration, no persisted-state change, no API version change. Ships behind no flag: the accept surface only appears for conversations the server already reports as `status = "request"`, and users with no pending requests see no difference.

Rollback is a straight revert — `acceptConvo` and its cache patching predate this change and are untouched, so reverting restores the previous (status-blind) rendering without stranding any state.

### D9 — The accept surface offers accept and decline only

No Block or Report action on the accept surface.

Block was in an earlier draft and was removed on review. It has no single meaning for a group request — there is no one "sender" to block — and the Requests segment does not offer Block today: the existing `feature-chats` spec puts only "Go to profile" in the Requests overflow, reserving Block and Report for the Chats segment. Adding Block only for direct requests would make the surface's action set vary by conversation kind for something other than the primary label, which is more inconsistency than the action is worth here.

Safety actions against an individual remain one tap away through the profile the row already links to.

## Open Questions

None blocking. Deliberately deferred: reporting from the accept surface. The capability exists (the list's overflow offers Report for a single selected conversation in the Chats segment) and it could be added without a spec change if request spam turns out to be common, but it is left out to keep the primary decision — accept or decline — uncluttered.
