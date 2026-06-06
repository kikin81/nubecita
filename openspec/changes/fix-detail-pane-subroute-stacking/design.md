## Context

`MainShell` hosts an inner `NavDisplay` whose pane layout on Medium/Expanded widths is driven by Material3's `rememberListDetailSceneStrategy` (`androidx.compose.material3.adaptive.navigation3`), with an `AdaptiveDialogSceneStrategy` overlay in front of it. Each `@MainShell` entry declares its pane role through **static, per-NavKey-type metadata**: `Feed`/`Search`/`Chats`/`Profile` → `listPane(detailPlaceholder=…)`, `PostDetail`/`Chat` → `detailPane()`, `Settings`/`Report` → no pane metadata.

The active tab's back stack is a flat `SnapshotStateList<NavKey>` in `MainShellNavState`. Sub-routes are pushed via `navState.add(target)` from the screen Composable (ViewModels never touch nav state — they emit a `NavigateTo`/`NavigateToProfile` effect that the Composable collects; this MVI boundary is fixed and must be preserved).

Critically, `MainShellNavState.backStack` is **not** the active tab's stack — it concatenates the **start-tab (Feed) stack + the active tab's stack** (so predictive/system back can "exit through home"). The whole concatenated list is handed to the list-detail strategy, which is therefore **not scoped to the active tab**.

**Two reproduced defects (Pixel Tablet, bench flavor), one root cause:**
- **Quirk 1 — sub-route from detail evicts the list.** From `[Feed, PostDetail]` (Feed | PostDetail), tapping the author pushes `Profile(handle)` → `[Feed, PostDetail, Profile]`. `Profile` carries `listPane()` (it is also a tab), so the strategy re-anchors the list to the newest `listPane()` entry → **Profile replaces Feed**. Back correctly restores Feed | PostDetail.
- **Quirk 2 — tab switch leaves a stale detail.** From `[Feed, PostDetail(A)]`, switching to Chats makes the concatenated stack `[Feed, PostDetail(A), Chats]`; the strategy anchors the list to `Chats` but keeps Feed's `PostDetail(A)` (a `detailPane` entry from the *other* tab's segment) in the detail pane → **Chats list next to an unrelated post**.

The conflict is structural and shared: pane selection runs over the concatenated multi-tab stack with static per-type metadata. A route that is a **list anchor as a tab root** must **not re-anchor as a sub-route opened from a detail pane** (Quirk 1), and pane selection must be **scoped to the active tab's segment** so another tab's detail can't leak in (Quirk 2). The fix must be a general rule across Feed/Search/Chats, not a per-route patch.

## Goals / Non-Goals

**Goals:**
- Sub-routes opened from a detail pane stack **within the detail region**; the list pane stays put (Quirk 1, MVP behavior).
- Switching tabs recomputes both panes from the **active tab's segment**, so the detail pane never shows another tab's detail (Quirk 2).
- A single, active-tab-scoped positional rule that covers every list-detail surface and every sub-route, independent of the pushed route's own pane metadata.
- No change to the MVI boundary (ViewModels stay `MainShellNavState`-free), to compact-width behavior, to the outer `NavDisplay`, or to the dialog/overlay strategy.
- Existing `app-navigation-shell` scenarios stay green; new behavior is covered by an instrumented bench test on the tablet emulator and screenshot tests.

**Non-Goals:**
- The Nav3 **three-pane "push"** animation (list slides out; detail + new pane slide in) — documented as the north star, deferred.
- Reworking how tabs/back stacks are stored or the "exit through home" back policy.
- Any phone (Compact) layout change.

## Decisions

### D1 — Active-tab-scoped, positional "detail bucketing" is the canonical rule
Pane assignment SHALL operate on the **active tab's segment only** (using `MainShellNavState.topLevelKey` + per-tab stacks), never the concatenated multi-tab `backStack`. Within that segment the **first** `listPane()` entry is the list anchor; **every entry after it is the detail region** and renders in the detail pane (top of region visible, rest retained for Back). A later `listPane()`-tagged entry in the detail region does **not** re-anchor the list; if the active tab has no detail-region entry, the anchor's `detailPlaceholder` fills the detail pane.

*Why:* active-tab scoping fixes Quirk 2 (no cross-tab detail bleed); positional, metadata-role-agnostic bucketing fixes Quirk 1 (`Profile` and any future tab-root route reached from a detail pane), across Feed/Search/Chats in one stroke. The concatenated start-tab stack remains untouched and continues to serve predictive/system back.

### D2 — Mechanism: a wrapping/positional scene strategy (recommended), pending an empirical check
We already own a custom scene strategy (`AdaptiveDialogSceneStrategy` under `app/.../shell/adaptive/`). The recommended mechanism is a thin **positional list-detail scene strategy** that buckets the back stack per D1 and delegates pane rendering, rather than relying on Material3's metadata-driven anchor selection.

Two sub-options, to be chosen after the **empirical check in D3**:
- **D2a (preferred if needed):** a custom/wrapping strategy that computes `(listAnchor, detailRegion)` from the **active tab's segment** positionally (it needs the tab boundary — expose the active-tab segment or `topLevelKey` from `MainShellNavState` to the strategy) and feeds Material3's `ListDetailPaneScaffold` (or the library scene) a normalized two-entry view (`listAnchor`, `detailTop`) while retaining the full region for Back. Fully general; no per-route metadata edits; fixes both quirks.
- **D2b (lightest, only if D3 proves it sufficient):** keep Material3's strategy and ensure detail-region routes are seen as detail entries — e.g. by normalizing metadata at the `entryProvider`/strategy boundary based on stack position (not by adding per-type `detailPane()`, which conflicts with the tab role).

*Why a strategy and not back-stack shaping in `MainShellNavState`:* pane assignment is a render-time concern; `MainShellNavState` must keep the true linear stack so Back, tab-switch, and process-death restore semantics in `app-navigation-shell` stay intact. Shaping the stored stack would entangle navigation history with layout.

**Alternatives considered:**
- *Per-route `detailPane()` metadata / distinct "as-detail" NavKeys* (e.g. a `ProfileDetail(handle)` separate from the `Profile(handle)` tab): rejected as the general solution — it multiplies NavKeys across Profile + nested PostDetail + Settings + Search, duplicates entry registrations, and still can't let one `Profile` key be both a tab anchor and a detail entry. May survive only as a localized fallback if D3 shows the library can't be wrapped cleanly.
- *Instance-derived metadata in the `entryProvider`*: viable only if Nav3 lets entry metadata vary per key instance by stack position; folded into D2b pending the D3 finding.

### D3 — Resolve the Material3 FIRST-vs-LAST `detailPane()` behavior before coding
Whether `ListDetailSceneStrategy` renders the **first** or the **last** `detailPane()` entry after the list anchor determines how much custom work is needed (and whether nested `detailPane` routes already stack correctly). Resolve by reading the `androidx.compose.material3.adaptive.navigation3` source and confirming on the live tablet emulator (push a nested detail-region entry and observe). This is task #1 and gates the D2a/D2b choice.

### D4 — Preserve the MVI boundary and the push site
The push stays `navState.add(target)` from the Composable (e.g. `PostDetailNavigationModule`'s `onNavigateToAuthor`). No ViewModel learns about panes. The strategy alone decides placement, so the same `add(Profile(handle))` call yields list-eviction-free behavior with no call-site change.

### D5 — Bench test fidelity (bundled, optional)
`BenchFakeProfileRepository` ignores its `actor` arg and always returns the static `bench.nubecita.app` profile, so a bench build shows the wrong user after an author tap. Make it resolve per-handle fixtures (at least the bench thread authors: `jess.trails`, `gabe.climbs`, `carmen.alpine`) so the instrumented repro asserts on the *right* profile and screenshots are meaningful. Strictly a test-support change in `src/bench`; does not affect production.

## Risks / Trade-offs

- **Fighting a library's internal scene logic** → Mitigation: D3 first; prefer wrapping/normalizing inputs (D2a) over forking the library; keep the change behind the existing `sceneStrategies` seam so it's revertible by swapping one strategy.
- **Regressing other `app-navigation-shell` scenarios** (tab switch, process-death restore, "exit through home", placeholders, `Report` overlay) → Mitigation: keep `MainShellNavState` semantics untouched (D2 decision); run the full existing unit + screenshot suite; the change is render-time only.
- **Compact-width regression** → Mitigation: the rule is explicitly Medium/Expanded-only; a Compact scenario asserts unchanged full-screen stacking.
- **MVP ≠ north star** (users on very wide screens still lose side-by-side feed+post when viewing a profile, since the detail pane swaps) → Accepted for v1; the three-pane push is the documented follow-up.
- **Animation polish**: swapping detail-pane content may lack a satisfying transition → Acceptable for MVP; revisit with the three-pane work.

## Migration Plan

1. Verify D3 (first-vs-last `detailPane`) from source + emulator; pick D2a or D2b.
2. Implement the positional strategy under `app/.../shell/adaptive/`; wire into `MainShell`'s `sceneStrategies` (overlay/dialog first, then list-detail).
3. Confirm Feed, Search, Chats surfaces all obey the rule (they share the one inner `NavDisplay`, so this should be automatic — verify each).
4. Add the bench instrumented test (tablet) + screenshot tests; optionally land D5 for fidelity.
5. Update `docs/adaptive-layouts.md` (rule + north star).
No data migration; rollback = revert the strategy wiring.

## Open Questions

- **D3 outcome** — does Material3 render the first or last `detailPane()` after the anchor? (gates D2a vs D2b)
- Should the retained-but-hidden detail-region entries below the top keep their ViewModels alive (like `OverlayScene.overlaidEntries` does for the dialog), or be paused? Lean: keep alive for instant Back, consistent with current behavior.
- Does `Report` (overlay, no pane metadata) need any special-casing under the positional rule, or does the dialog/overlay strategy (which runs first) fully claim it? Verify during implementation.
