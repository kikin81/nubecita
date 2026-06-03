## Why

PR1 (`nubecita-h5zd.1`, merged in #420) migrated the Search input to the M3 Expressive `SearchBar` but stayed single-pane, leaving a `// PR2` seam. On a tablet, the Search tab is still single-pane and — because the `Search` entry carries no `listPane()` metadata — tapping a post **orphans** `PostDetailRoute` full-screen, wasting the second pane. This change makes Search adaptive: single-pane on phones, two-pane list-detail on tablets/foldables, matching the Feed and Chats tabs and the Gmail-style search UX.

This is PR2 of the "Search panes" epic (`nubecita-h5zd`), tracked as `nubecita-h5zd.2`. It depends on PR1.

## What Changes

- Tag the `Search` entry with `ListDetailSceneStrategy.listPane(detailPlaceholder = …)` so Search is a valid list-pane anchor; add the `material3-adaptive-navigation3` dependency to `:feature:search:impl`. (Load-bearing: without the anchor, a pushed `detailPane()` entry has nothing to pair with and renders full-screen on Medium/Expanded.)
- Fill the PR1 width-keyed expanded-container seam in `SearchBarSection`: **Compact → `ExpandedFullScreenSearchBar`** (unchanged); **Medium/Expanded → `ExpandedDockedSearchBar`** — a popup scoped to the list pane so the detail pane stays visible. Same `SearchBarState` / `inputField` / overlay content; only the expanded composable swaps on the width class.
- Push the post detail via **`replaceTop`** (not `add`) from Search's result rows, so tapping result B swaps the detail pane and system Back returns to the results list (matches Chats; the "one selection at a time" model).
- Leave actor taps unchanged: `Profile` (already tagged `listPane()`) takes over as its own list-detail context.
- Opt into `@ExperimentalMaterial3ExpressiveApi` (the docked variant's gate) on top of the existing `@ExperimentalMaterial3Api`. No version bump — `material3 1.5.0-alpha20` ships it.

**Empirically validated (on-device spike, Pixel_Tablet, bench build):** `ExpandedDockedSearchBar` is pane-scoped (detail + nav rail stay visible); `ExpandedFullScreenContainedSearchBar` is **not** — it covers the whole window despite the name, so it is rejected.

## Capabilities

### New Capabilities
<!-- None. -->

### Modified Capabilities
- `feature-search`: extends the search-input requirements established in PR1 with adaptive list-detail behavior — Search as a list pane, the width-gated expanded container (full-screen on Compact, docked on Medium/Expanded), and the replaceTop detail-selection semantics.

## Impact

- **Code:** `:feature:search:impl` — `di/SearchNavigationModule.kt` (listPane metadata + placeholder), `SearchBarSection.kt` (width-keyed docked/full-screen branch), `SearchScreen.kt` (pass the width class), the result screens' post-navigation (`replaceTop`); `build.gradle.kts` (+ `material3-adaptive-navigation3`).
- **Deps:** one new module dependency (`material3-adaptive-navigation3`); no new version-catalog entry (alias already exists, used by `:feature:feed:impl`).
- **Tests:** screenshot baselines at Compact / Medium / Expanded (360 / 600 / 840 dp) for the list-pane home and the docked expanded state; existing PR1 baselines unaffected on Compact. The docked popup, like the full-screen overlay, is a window layoutlib can't fully capture — its content stays covered by the typeahead component tests.
- **No deviation** from MVI / Compose / Hilt baseline. `SearchBarState` stays in the Composable; the width class is read at the screen boundary, not the VM.
- **Phones unchanged:** Compact width keeps the exact PR1 single-pane behavior.
