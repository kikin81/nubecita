# Design — add-chat-list-management

Full narrative design: `docs/superpowers/specs/2026-06-12-chat-list-management-design.md`. Epic `nubecita-kc17`. This file records the load-bearing decisions (D-1…D-9) the tasks depend on.

## Context

`:feature:chats:impl` hosts the Chats tab home (`ChatsScreen` → `ChatsScreenContent`) over a `@Singleton` `ChatRepository` whose in-memory reactive cache (`MutableStateFlow<ImmutableList<ConvoListItemUi>?>`) is the single source of truth shared with the thread screen. The vendored SDK exposes `listConvos(status, readState)`, `ConvoView.status`, `leaveConvo`, `acceptConvo`, `muteConvo` / `unmuteConvo`, and `deleteMessageForSelf`.

## Decisions

- **D-1 — Sequence into four PRs, glyphs first.** G0 (icons) → G1 (segmentation) → G2 (multi-select actions) → G3 (leave-undo). The ordering is forced by the icon-subset rule: regenerating the Material Symbols subset changes the committed font and risks a repo-wide screenshot-baseline blast radius, so it MUST be a standalone `:designsystem` PR (`nubecita-a580` / `nubecita-6rdb` lessons). Verify zero outline drift on shared glyphs; regen on CI via the `update-baselines` label.

- **D-2 — New capability `feature-chats`.** Chats has no OpenSpec capability yet; this change creates it and scopes it to list management. Future chat work extends the same spec.

- **D-3 — Two-list cache, one user-initiated refresh.** Extend the repository cache to hold both accepted and request lists. `refreshConvos()` issues two `listConvos` calls (`accepted`, `request`). No new background work — battery-neutral, the hard project rule. A request-fetch failure does not fail the accepted list.

- **D-4 — Segment + selection are flat MVI fields; the load lifecycle stays a sealed sum.** `ChatsScreenViewState` gains `activeSegment`, `requestCount`, and a nullable `selection` (set of convoIds). `selection != null` drives the contextual app bar; available actions are **derived** from `selection.size` × `activeSegment` so invalid combinations are unrepresentable (no per-action boolean flags). Per `CLAUDE.md`: no `Async<T>` at the VM→UI boundary; the existing `ChatsLoadStatus` sealed sum is unchanged.

- **D-5 — Multi-select, Google-Messages style.** Single selection exposes single-target actions (profile / report / block); ≥2 hides them and offers only bulk Leave / Mute. Mixed mute state → present "Mute" if any selected convo is unmuted, else "Unmute". Requests segment substitutes Accept / Leave(decline) for the action set.

- **D-6 — Reuse cross-feature surfaces.** Report → push `Report.forAccount(otherUserDid)` from `:feature:moderation:api` (a NavKey, no impl dependency). Go-to-profile → existing Profile route via `LocalMainShellNavState`. Block → existing block path. The pill-tab toggle reuses the `ProfilePillTabs` component (as Search does). No new moderation/profile code.

- **D-7 — Undo deferral lives in the ViewModel, not the repository.** The repo exposes a plain `leaveConvo` and stays a thin XRPC+cache layer. The VM owns the pending-leave set, the optimistic projection (rows removed from what the UI sees while still cached), the dismiss timer, and undo. Rationale: `leaveConvo` has no server-side undo, so the only safe recovery is to *not have called it yet*; that windowing is a UI concern, not a data concern.

- **D-8 — Single pending batch; drop on process death.** Starting a new leave commits the prior pending batch immediately (Gmail-style). A leave still pending and uncommitted at process death is dropped — an un-acknowledged destructive action must never execute after the UI is gone. (We do **not** persist pending leaves to survive death and then fire them.)

- **D-9 — Acceptance is implicit-on-reply in G1, explicit in G2.** G1 ships Requests as a read+open surface (replying auto-accepts server-side), so it is independently shippable without new action UI. Explicit Accept / decline arrives with G2's selection mode. `acceptConvo` moves a convo from the request cache to the accepted cache.

## Risks

- **Screenshot baselines (G0 + UI groups).** G0's font regen must show zero shared-glyph drift; the feature groups add new screenshot fixtures (Requests+badge, selection 1 vs 2+) — regenerate via the `update-baselines` CI label, not local Mac runs, and make all visual changes before applying the label.
- **Selection vs tablet list-detail.** The contextual app bar replaces the list-pane top bar only; verify the detail pane and `selectedOtherUserDid` highlight are unaffected.
- **Undo + refresh interplay.** A background/pull refresh while a leave is pending must not resurrect a row that is mid-undo; the VM's optimistic projection is applied over the freshest cache.

## Out of scope

In-thread per-message delete (`deleteMessageForSelf`), Pro/paywall gating, thread-screen changes beyond opening from selection.
