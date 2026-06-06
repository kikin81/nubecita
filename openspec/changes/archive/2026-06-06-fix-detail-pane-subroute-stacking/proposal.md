## Why

On a tablet/foldable (Medium/Expanded width), the inner `MainShell` list-detail layout exhibits two spatially-confusing quirks, both reproduced live on a Pixel Tablet (bench flavor):

**Quirk 1 — a sub-route opened from the detail pane evicts the list.** Feed (list) | PostDetail (detail) → tap the post author's avatar → **Profile replaces Feed** in the left pane, leaving Profile | PostDetail. The user loses the list they were browsing. Applies to any sub-route reached from a detail pane (author → `Profile`, nested/quoted `PostDetail`, `Settings`, …), across Feed, Search, and Chats.

**Quirk 2 — switching tabs leaves a stale detail pane.** With Feed | PostDetail open, tapping another list-detail tab (e.g. Chats) updates the **left** pane to the Chats list but leaves the **right** pane showing the previous tab's PostDetail. You see a Chats list next to an unrelated post (header still "← Post").

Both have one structural root cause: pane assignment is driven by **static per-entry metadata** over the **concatenated multi-tab back stack**. `MainShellNavState.backStack` concatenates the start-tab (Feed) stack with the active tab's stack and hands the whole list to Material3's `ListDetailSceneStrategy`, which is *not* scoped to the active tab:
- Quirk 1: `Profile` carries `listPane()` (it is *also* a top-level tab), so on `[Feed, PostDetail, Profile]` the strategy re-anchors the list to the newest `listPane()` entry, evicting `Feed`. One static per-type metadata value can't be both a tab-root anchor and a non-anchoring sub-route.
- Quirk 2: switching to Chats yields `[Feed, PostDetail(A), Chats]`; the strategy anchors the list to `Chats` but keeps Feed's lingering `detailPane` entry (`PostDetail(A)`) in the detail pane, because pane selection spans tabs instead of the active tab's segment.

## What Changes

- Scope list-detail pane assignment to the **active tab's back-stack segment**: the panes SHALL be computed from the active tab's entries only (list anchor = the active tab's root; detail region = entries above it within that tab). The concatenated start-tab stack that sits underneath for predictive/system-back SHALL NOT contribute panes. *(Fixes Quirk 2: switching tabs resets the detail pane to the new tab's detail/placeholder rather than leaking the prior tab's detail.)*
- Establish a **positional detail-stacking rule** within that active-tab segment: the first `listPane()` entry is the list anchor, and **every entry after it belongs to the detail region** and stacks in the detail (right) pane — regardless of that entry's own `listPane()`/`detailPane()`/no-metadata tag. A second `listPane()`-tagged entry pushed into the detail region SHALL NOT re-anchor the list. *(Fixes Quirk 1.)*
- v1 user-visible behavior (**MVP**): a sub-route opened while a detail pane is active joins the detail pane, stacking over the current detail entry; the **list pane stays intact**; Back pops within the detail region back to the previous detail entry, then to the list/placeholder.
- Apply both rules uniformly to the **Feed, Search, and Chats** list-detail surfaces and to all sub-routes reachable from a detail pane (author → `Profile`, nested/quoted `PostDetail`, `Settings`, etc.).
- Keep the existing **compact-width** behavior unchanged (single-pane stacking) and keep the `AdaptiveDialogSceneStrategy` overlay behavior unchanged.
- Test-fidelity (optional, bundled): make the bench `BenchFakeProfileRepository` resolve per-handle profiles so a bench build shows the tapped author's profile (today it always returns the static `bench.nubecita.app` profile).
- Document the rule and the eventual three-pane "push" north star in `docs/adaptive-layouts.md`.

## Capabilities

### New Capabilities
<!-- none — this refines existing MainShell navigation behavior -->

### Modified Capabilities
- `app-navigation-shell`: Add requirements that (a) list-detail panes are computed from the active tab's stack segment so switching tabs resets the detail pane (Quirk 2), and (b) sub-routes opened from a detail pane stack within the detail region instead of re-anchoring the list pane (Quirk 1). Reconcile with the existing "Cross-tab navigation links push onto the active tab's stack" requirement (which currently produces the buggy eviction) and the "Inner `NavDisplay` applies a `ListDetailSceneStrategy`" requirement (already stale — the shell now also supplies `AdaptiveDialogSceneStrategy`).

## Impact

- **Code**: `app/.../shell/MainShell.kt` (inner `NavDisplay` `sceneStrategies`); a new/wrapping scene strategy under `app/.../shell/adaptive/` (alongside `AdaptiveDialogSceneStrategy`); possibly `core/common/.../navigation/MainShellNavState.kt` (back-stack shaping); the `@MainShell` nav modules for Feed/Search/Chats/PostDetail/Profile only if metadata changes are chosen.
- **No VM/MVI change**: ViewModels still never touch `MainShellNavState` (CompositionLocal-only); the fix lives entirely in the scene strategy / nav-state / nav-module layer.
- **Tests**: a bench instrumented test on the tablet emulator asserting the list pane survives the author tap; screenshot tests for the list-detail pane states; existing `app-navigation-shell` unit/screenshot tests must stay green.
- **Bench**: optional `BenchFakeProfileRepository` per-handle resolution (`feature/profile/impl/src/bench/...`).
- **Docs**: `docs/adaptive-layouts.md`.
- **No new dependencies.** Stays within the Material3 Adaptive + Navigation 3 baseline.
- **Tracking**: `nubecita-xqp7` (this change). Subsumes `nubecita-s1f3` (Quirk 2, the Chats placeholder variant — linked as blocked-by). Related: `nubecita-fkev`/#416 (introduced list-detail), `nubecita-msoj` (two-pane screenshot coverage), `nubecita-bq29` (coalescing dialog scene).

## Non-goals

- The Nav3 **three-pane "push"** animation (list slides out, detail + new pane slide in side-by-side) is the eventual north star but is explicitly **out of scope for v1** — v1 keeps the list pane in place and swaps the detail-pane content.
- No change to compact-width (phone) navigation, which already stacks full-screen correctly.
- No change to the outer `Splash → Login → Main` `NavDisplay`.
- No change to the `AdaptiveDialogSceneStrategy` dialog behavior or to overlay-style entries (e.g. `Report`).
