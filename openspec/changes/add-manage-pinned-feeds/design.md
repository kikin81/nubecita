# Design — Add Manage Pinned Feeds

> Full narrative source (with code receipts): `docs/superpowers/specs/2026-07-05-manage-pinned-feeds-design.md`. This document is the openspec-normalized distillation.

## Context

Pinned feeds are already modeled end-to-end in `:core:feeds`: `PinnedFeedsRepository` exposes `observePinnedFeeds()`, `pinFeed`, `unpinFeed` (already non-destructive — sets `pinned=false`, never deletes), and `refresh()`, backed by AT Proto `getPreferences`/`putPreferences` (`SavedFeedsPrefV2`) plus a Room `saved_feeds` cache that already has `pinned: Boolean` and `position: Int` columns. The Feeds-home pill row's `[＋]` button already navigates to the `Feeds` `NavKey`; today that resolves to an `:app`-side `FeedsPlaceholderModule` ("coming soon"). The `sh.calvin.reorderable` library, an `@ApplicationScope` `CoroutineScope`, and a `@Singleton` `DefaultPinnedFeedsRepository` all already exist. The only genuinely new work is the UI module, one repository operation, and the durable-commit wiring.

## Goals / Non-Goals

**Goals:**
- A `:feature:feeds:impl` screen that lists pinned feeds in server order, reorders them via drag-and-drop, and removes (unpins) them non-destructively.
- A `reorderPinnedFeeds` domain operation whose network write is durable across screen-teardown and app-backgrounding, and safe against concurrent `refresh()` and cross-client edits.
- Full TalkBack operability (reorder + remove without dragging).

**Non-Goals:**
- A saved-but-unpinned "library" section (additive later; the `pinned`/`position` columns already support it).
- An empty state (structurally impossible: Following is non-removable and the repo falls back to a default pinned set).
- Swipe-to-dismiss, on-drop/debounced network writes, and making Following removable.

## Decisions

### D1 — Pinned-only scope (Option 3)
Manage only the pinned subset now; design so a saved-library section is purely additive. **Alternative:** a two-section pinned+saved screen (rejected — separate UX with its own state; the reorder + module scaffolding is the real work here).

### D2 — Remove = existing non-destructive `unpinFeed`
"Remove" calls the existing `unpinFeed`, which keeps the feed saved (survives cross-client sync). **Alternative:** unpin-and-unsave (rejected — destructive; frustrates users who also run the official app). No repository change needed for remove.

### D3 — Following identified by `kind`, locked from removal
Rows omit the remove affordance when `kind == FeedKind.Following` (Following is a synthesized `type="timeline"` entry, sentinel `FOLLOWING_FEED_URI`). It stays reorderable. **Alternative:** URI string-matching (rejected — `kind` is already on `PinnedFeedUi`).

### D4 — Explicit affordances, no swipe
Leading drag handle (`Modifier.draggableHandle`, long-press lift + tonal-elevation + haptics) + trailing remove icon. **Alternative:** swipe-to-dismiss (rejected — horizontal swipe fights the vertical drag; an off-axis drag would trigger accidental unpins).

### D5 — `reorderPinnedFeeds` re-reads + merges at commit
The new op re-reads fresh prefs inside the commit `Mutex` and rebuilds `SavedFeedsPrefV2.items` = reordered pinned (with server-pinned URIs absent from the local list appended, never dropped) + unpinned in prior order; foreign prefs preserved via `mergeSavedFeedsPrefs`; the Following sentinel mapped back to its timeline entry. **Alternative:** rebuild from the phone's possibly-stale snapshot (rejected — clobbers a feed pinned on another client).

### D6 — Durable commit-on-exit (ported from G3 leave-with-undo, `nubecita-kc17.4`)
Compose owns the drag-mutable order; the network write is deferred to `onCleared` **and** lifecycle `ON_STOP`, dirty-checked, launched on `@ApplicationScope`. A repository `Mutex` + `reorderPending` flag (read/written inside the lock) makes `refresh()` skip saved-feed reconciliation while pending and serializes concurrent commits; Room positions roll back on `putPreferences` failure. **Alternatives:** `viewModelScope`-flush in `onCleared` (rejected — cancelled on pop, the network call dies in flight); on-drop/debounced writes (rejected — more writes/races; user preferred commit-on-exit). `ON_STOP` closes the swipe-from-Recents process-death gap that `onCleared` alone leaves.

### D7 — Fresh-table rebuild is a DB concern, not here
`SavedFeedDao` gets a `@Transaction` batch-position write; no schema/column changes (`position` already exists), so no migration.

## Risks / Trade-offs

- **`onCleared` not guaranteed before process death** → mitigated by the additional `ON_STOP` trigger committing while the app is still alive; both paths are idempotent (dirty-check + `Mutex`).
- **Silent failure after the screen is gone** (no snackbar surface) → mitigated by Room-position rollback + reconciliation on the next `refresh()`; the failure path is recovery, not a user prompt. Acceptable because issue-critical state is unaffected — only pinned order.
- **`refresh()` stomping a pending reorder** → mitigated by the `Mutex`-guarded `reorderPending` flag (checked inside the lock, closing the check-then-act window a bare boolean would leave).
- **Cross-client change during the sub-second in-flight window** → reconciled on the next `refresh()` after the token clears; acceptable given the window size.

## Migration Plan

- Additive: new module + one repository method + one DAO method; delete the `:app` placeholder provider and register the new `@MainShell` provider (clean migration per the api/impl sequencing convention — no bridging artifacts).
- No DB schema bump, no AT Proto surface change.
- Rollback: revert the module + repository/DAO additions and restore `FeedsPlaceholderModule`.

## Open Questions

- None blocking. The exact `IN`-list vs point-lookup strategy for the DAO batch write is an implementation detail resolved in `.2`.
