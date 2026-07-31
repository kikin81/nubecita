# Design — add-chat-list-management

Full narrative design: `docs/superpowers/specs/2026-06-12-chat-list-management-design.md`. Epic `nubecita-kc17`. This file records the load-bearing decisions (D-1…D-9) the tasks depend on.

## Context

`:feature:chats:impl` hosts the Chats tab home (`ChatsScreen` → `ChatsScreenContent`) over a `@Singleton` `ChatRepository` whose in-memory reactive cache (`MutableStateFlow<ImmutableList<ConvoListItemUi>?>`) is the single source of truth shared with the thread screen. The vendored SDK exposes `listConvos(status, readState)`, `ConvoView.status`, `leaveConvo`, `acceptConvo`, `muteConvo` / `unmuteConvo`, and `deleteMessageForSelf`.

## Decisions

- **D-1 — Sequence into four PRs, glyphs first.** G0 (icons) → G1 (segmentation) → G2 (multi-select actions) → G3 (leave-undo). The ordering is forced by the icon-subset rule: regenerating the Material Symbols subset changes the committed font and risks a repo-wide screenshot-baseline blast radius, so it MUST be a standalone `:designsystem` PR (`nubecita-a580` / `nubecita-6rdb` lessons). Verify zero outline drift on shared glyphs; regen on CI via the `update-baselines` label.

- **D-2 — Extend the existing `feature-chats` capability.** `feature-chats` was introduced by the `add-chats-composing` change (composing/sending requirements). This change does **not** create it; it adds list-management requirements to it via a `## ADDED Requirements` delta (additive — no existing requirement changes). The proposal lists it under Modified Capabilities so two changes never both claim to create it (avoids the archive-time provenance clash noted in the openspec-archive lessons).

- **D-3 — Two-list cache, one user-initiated refresh, concurrent fetch.** Extend the repository cache to hold both accepted and request lists. `refreshConvos()` issues both `listConvos` calls (`accepted`, `request`) **concurrently** (`async` within a `coroutineScope`) since they're independent — one round-trip of latency, not two. No new background work — battery-neutral, the hard project rule. A request-fetch failure does not fail the accepted list. The interface exposes the two lists explicitly (a `status`-parameterized `observeConvos(segment)` or a sibling `observeRequestConvos()`), decided at 2.1; the existing `observeConvos()` shape is the starting point.

- **D-4 — Segment + selection are flat MVI fields; the load lifecycle stays a sealed sum.** `ChatsScreenViewState` gains `activeSegment`, `requestCount`, and a nullable `selection` (set of convoIds). `selection != null` drives the contextual app bar; available actions are **derived** from `selection.size` × `activeSegment` so invalid combinations are unrepresentable (no per-action boolean flags). Per `CLAUDE.md`: no `Async<T>` at the VM→UI boundary; the existing `ChatsLoadStatus` sealed sum is unchanged.

- **D-5 — Multi-select, Google-Messages style, with an overflow split.** The contextual bar shows the **bulk-capable** actions inline as icons (Leave, Mute/Unmute) and tucks the **single-only** actions (Go to profile, Report, Block) under an overflow (⋮) that appears only at exactly one selection. So ≥2 selected → just the inline bulk icons, no overflow. Mixed mute state → present "Mute" if any selected convo is unmuted, else "Unmute". Requests segment substitutes Accept / Leave(decline) inline + Go-to-profile in the overflow. Rationale: five inline icons crowd a phone bar; the clean rule "inline = works for 1 and N, overflow = single-only" makes the bar self-explanatory and the multi-select transition obvious. Every icon-only action carries a `contentDescription` plus an M3 `PlainTooltip` (long-press / hover) so an unlabeled destructive bar stays legible and accessible. (Note: AT Proto chat has a single removal op — `leaveConvo` — so there is exactly one Leave action; there is no separate Archive/Delete, despite the Google-Messages reference having both.)

- **D-6 — Reuse cross-feature surfaces.** Report → push `Report.forAccount(otherUserDid)` from `:feature:moderation:api` (a NavKey, no impl dependency). Go-to-profile → existing Profile route via `LocalMainShellNavState`. Block → existing block path. The pill-tab toggle reuses the `ProfilePillTabs` component (as Search does). No new moderation/profile code.

- **D-7 — Undo deferral orchestration lives in the ViewModel; the commit runs on an application scope.** The repo exposes a plain `leaveConvo` and stays a thin XRPC+cache layer. The VM owns the pending-leave set, the optimistic projection (rows removed from what the UI sees while still cached), the dismiss timer, and undo. Rationale: `leaveConvo` has no server-side undo, so the only safe recovery is to *not have called it yet*; that windowing is a UI concern. **But the terminal commit call is launched on an injected application-scoped `CoroutineScope`, not `viewModelScope`** — so a commit (snackbar dismiss, supersede, or screen-leave flush) completes even as the VM's scope tears down. The VM still orchestrates; only the fire-and-forget commit outlives it.

- **D-8 — Single pending batch; commit on normal clearance, drop only on process death.** Starting a new leave commits the prior pending batch immediately (Gmail-style). **Normal screen clearance commits, it does not drop:** when the Chats VM is cleared normally (navigated away), `onCleared()` flushes any pending leave through the application scope — leaving a chat and then navigating away must *leave* it, not silently restore it. Only **process death** (where `onCleared()` does not run) drops a still-pending leave — an un-acknowledged destructive action must never execute after the UI is gone, and we do **not** persist pending leaves across death to fire them later. This split (flush in `onCleared`; nothing fires on death because `onCleared` is skipped) honors the user's "process death drops" intent while fixing the silent-drop-on-navigation bug.

- **D-9 — Acceptance is implicit-on-reply in G1, explicit in G2.** G1 ships Requests as a read+open surface (replying auto-accepts server-side), so it is independently shippable without new action UI. Explicit Accept / decline arrives with G2's selection mode. `acceptConvo` moves a convo from the request cache to the accepted cache.

## Risks

- **Screenshot baselines (G0 + UI groups).** G0's font regen must show zero shared-glyph drift; the feature groups add new screenshot fixtures (Requests+badge, selection 1 vs 2+) — regenerate via the `update-baselines` CI label, not local Mac runs, and make all visual changes before applying the label.
- **Selection vs tablet list-detail.** The contextual app bar replaces the list-pane top bar only; verify the detail pane and `selectedOtherUserDid` highlight are unaffected. **Edge case:** if a leave (single or bulk) includes the conversation currently open in the detail pane, that pane clears to its empty placeholder; undoing the leave restores the list row but does not auto-reopen the thread.
- **Undo + refresh interplay.** A background/pull refresh while a leave is pending must not resurrect a row that is mid-undo; the VM's optimistic projection is applied over the freshest cache.

## Out of scope

In-thread per-message delete (`deleteMessageForSelf`), Pro/paywall gating, thread-screen changes beyond opening from selection.
