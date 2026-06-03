## Context

PR1 (`nubecita-h5zd.1`, merged #420) migrated Search to the M3 Expressive `SearchBar` and left a `// PR2` width-keyed seam in `SearchBarSection` that currently always renders `ExpandedFullScreenSearchBar`. The `Search` entry has no list-detail metadata, so on a tablet it is single-pane and a tapped `PostDetailRoute` (tagged `detailPane()`) orphans full-screen.

Existing list-detail wiring this change mirrors:
- `MainShell` registers `rememberListDetailSceneStrategy(...)` on the inner `NavDisplay` (after the adaptive-dialog overlay strategy).
- `Feed` and `Chats` tag their entries with `ListDetailSceneStrategy.listPane(detailPlaceholder = …)`; `PostDetailRoute` is `detailPane()`; `Profile` is itself a `listPane()`.
- The strategy pairs the nearest `listPane()` entry on the back stack with the next `detailPane()` entry; a `detailPane()` with no preceding `listPane()` anchor renders full-screen.

On-device spike (Pixel_Tablet, bench build, 2026-06-03) findings:
- `ExpandedDockedSearchBar` → popup scoped to the list pane; detail pane + nav rail stay visible (Gmail behavior, Google's documented tablet recommendation).
- `ExpandedFullScreenContainedSearchBar` (+ `rememberContainedSearchBarState`) → covers the **whole window** even in a true two-pane scene. Rejected.

## Goals / Non-Goals

**Goals:**
- Search renders single-pane on Compact and two-pane list-detail on Medium/Expanded.
- Expanded search is pane-scoped (docked) on tablets, full-screen on phones — one width-gated call site.
- Tapping a post fills the detail pane via `replaceTop`; Back returns to results.
- Zero behavior change on Compact (phones).

**Non-Goals:**
- Changing the result tabs, per-tab ViewModels, typeahead, or recents.
- Making `Profile` render as Search's detail pane (it stays its own list pane).
- Any change to Feed/Chats/Profile list-detail wiring.
- Mocking actor/People search in bench (orthogonal).

## Decisions

### D1 — Search becomes a `listPane()` anchor
Tag `entry<Search>` with `ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPaneEmptyState(icon = Article, message = …) })` and add `material3-adaptive-navigation3` to `:feature:search:impl` (alias already in the catalog, used by `:feature:feed:impl`). This is the load-bearing change; the spike confirmed that without it a pushed `detailPane()` entry orphans full-screen on Medium/Expanded. Provide a string resource for the placeholder message (e.g. `search_detail_pane_select_post`).

### D2 — Width-gated expanded container (fills the PR1 seam)
`SearchBarSection` selects the expanded composable by width class at one call site:
```kotlin
when {
    widthClass.isAtLeastMedium -> ExpandedDockedSearchBar(state, inputField) { OverlayContent(...) }
    else                       -> ExpandedFullScreenSearchBar(state, inputField) { OverlayContent(...) }
}
```
The width class is read at the screen boundary (`SearchScreenContent` via `currentWindowAdaptiveInfo()` / the same source `MainShell` uses) and passed into `SearchBarSection` — not into the ViewModel. `SearchBarState` (plain `rememberSearchBarState()`) works for both; the contained state holder is not needed. `@OptIn(ExperimentalMaterial3ExpressiveApi::class)` is added alongside `ExperimentalMaterial3Api`.

### D3 — `replaceTop` for post selection
Search's result screens push `PostDetailRoute` via `navState.replaceTop(...)` instead of `add(...)`, so the detail pane swaps on each selection and Back returns to results (matches Chats' `replaceTop`). On Compact, `replaceTop` degrades to a normal push, preserving phone behavior. Actor taps keep `navState.add(Profile(...))` unchanged.

### D4 — Selected-result highlight (optional, follow Chats)
If low-cost, derive the open post URI from the back stack (à la `selectedConvoDid`) to highlight the selected result row on Medium/Expanded. Defer if it complicates the result list; not required for the core behavior.

## Risks / Trade-offs

- **[Docked popup sizing/scrim]** the docked surface is a `Popup` anchored to the bar; its height/scrim are M3 defaults → accept defaults; verify on-device at Medium and Expanded that it stays within the list-pane region.
- **[Alpha API]** `ExpandedDockedSearchBar` is `@ExperimentalMaterial3ExpressiveApi` and may shift → pinned via the catalog; the swap is one call site.
- **[Screenshot coverage]** the docked popup (like the full-screen overlay) renders in a window layoutlib can't fully capture → screenshot the list-pane home at 360/600/840 dp; cover the expanded content via the existing typeahead component tests; verify the docked expansion on-device.
- **[replaceTop on Compact]** must degrade to a normal push → confirm `MainShellNavState.replaceTop` behaves as a push when single-pane (it does for Chats).

## Migration Plan

1. Add the dep + `listPane()` metadata + placeholder string.
2. Thread the width class into `SearchBarSection`; add the docked branch + expressive opt-in.
3. Switch post navigation to `replaceTop`.
4. (Optional) selected-row highlight.
5. Screenshot baselines at 3 widths; `spotless` / `lint` / `testDebugUnitTest` / `validate…ScreenshotTest` / `:app:assembleProductionDebug`; tablet smoke test on the bench build.

Rollback: revert the PR; no schema/data changes.

## Open Questions

- None blocking. D4 (selected-row highlight) is the only optional item; decide during implementation based on cost.
