# Design Spec: Refactor Profile screen to match expressive design

## Context
The current Profile screen uses a generated gradient as the header background, derived from the user's banner image via `BoldHeroGradient`. While functional, it hides the actual banner image. The layout also uses a complex `Modifier.layout` shift and manual inset calculations to achieve a "draw behind top bar" effect. We want to modernize this screen to match the Material 3 Expressive design patterns seen in the Google Contacts app.

## Goals
*   Display the actual banner image in the profile header, with rounded top corners so it sits flush with the device screen curvature.
*   Overlap the user's avatar with the banner and content area.
*   Provide a primary actions row (Edit / Follow / Message / overflow) that reads as part of the hero on first paint.
*   Simplify the layout logic by using standard M3 Expressive components.
*   Promote the Settings entry-point from the overflow menu to a top-bar icon button so it is reachable without a second tap.

## Decisions

### Decision 1: Render actual banner image
We replaced `BoldHeroGradient` with a layout that renders the actual `bannerUrl`.
*   Use `NubecitaAsyncImage` for the banner.
*   Apply a top scrim (black-to-transparent vertical gradient over the status-bar reservation area) to ensure status-bar icons remain legible against any banner.
*   The banner extends edge-to-edge under the (initially transparent) `TopAppBar`.

### Decision 2: Expressive "Overlapping Avatar" Layout
We adopted the Material 3 Expressive "overlapping avatar" pattern:
*   The avatar is centered horizontally on the boundary between the banner and the info column.
*   Avatar size: 96dp (increased from 88dp for better expressive presence).
*   Avatar ring: 4dp surface-colored ring to detach the circle from a busy banner.
*   Avatar image uses `ContentScale.Crop` so non-square source images fill the circle instead of letterboxing.

### Decision 3: Plain pill verbs row (NOT sticky, NOT a ButtonGroup)
We considered M3 Expressive `ButtonGroup` with `toggleableItem` for the primary actions, and considered docking that row as a `stickyHeader` below the top bar. Both were rejected during implementation.
*   **Actions**:
    *   Own profile: `Edit profile` (single full-width `FilledTonalButton`). `Settings` lives in the top bar.
    *   Other profile: `Follow` (`Button`) / `Following` (`FilledTonalButton`), optional `Message` (`FilledTonalButton`), overflow (`IconButton` anchoring a `DropdownMenu` for Block / Mute / Report).
*   **No `ButtonGroup`**: `ButtonGroup.toggleableItem` is a *selection-state* primitive (segmented control). Using it for one-shot verbs forced an `onCheckedChange { if (it) onAction() }` adapter, and the overflow ended up as a toggle-button hosting a `DropdownMenu` inside its icon slot — visually awkward and semantically wrong. A plain `Row` of pill `Button` / `FilledTonalButton` + standalone `IconButton` overflow reads cleaner.
*   **Not sticky**: the verbs row scrolls away with the hero. A pinned verbs + pill-tabs block consumed too much vertical viewport when docked under the top bar; the feed is the primary scroll target and should keep that real estate.

### Decision 4: Simplify Layout and Top Bar
The `Modifier.layout` hack from the previous implementation was removed.
*   `Scaffold` hosts the `TopAppBar` in its `topBar` slot; the bar transitions from transparent to opaque based on scroll position via a `derivedStateOf` on the first item's offset.
*   The `LazyColumn` extends edge-to-edge under the transparent bar (Scaffold's default content-behind-topBar behavior). No `contentPadding(top = …)` and no `Modifier.padding(top = …)` is applied for the bar's reservation — the hero is the first item and draws at screen `y = 0`.
*   `LazyColumn.contentPadding` carries only the bottom navigation-bar inset.
*   Bar icons (back / settings) use circular surface-tinted `Surface(onClick=…)` buttons so they remain legible over the banner while the bar background is transparent.

### Decision 5: Verbs row + pill tabs are a single regular LazyColumn item
Per Decision 3, neither block is sticky. They live together in a `Column` (with a `surface` background to read as one unit) inside a single `LazyColumn` item that follows the hero. They scroll out of view as the user scrolls into the feed; the top bar (which is opaque by that point and shows the display-name + handle) carries the persistent identity affordance.

### Decision 6: Rounded-corner banner clip
The banner is clipped with `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)` to echo the device's screen curvature. The clip is applied to the banner image and the top scrim **individually** rather than to the parent `Box`, because `Modifier.clip` on a parent crops *all* descendants — including the overlapping avatar's bottom half, which is offset past the parent box's bottom edge. Per-element clip preserves both the rounded banner and the unclipped avatar overlap.

### Decision 7: Dampened pill-tab press response
`ProfilePillTabs` (the `ButtonGroup`-based segmented control for Posts / Replies / Media) sets `expandedRatio = 0.025f`. The 15% default expansion-on-press squeezes the "Replies" label into two lines on narrow widths when "Posts" or "Media" is pressed. 2.5% keeps a perceptible press response without the wrap regression. For a future fourth tab, this row should switch to a horizontally scrollable variant — see Open Questions.

## Risks / Trade-offs
*   **Contrast**: Showing the real banner image makes status-bar icons harder to read. The top scrim mitigates this but on very bright banners legibility may still be marginal.
*   **Loss of "expressive" press feedback on tabs**: At `expandedRatio = 0.025f` the press response is intentionally subtle. Acceptable for now; revisit if user testing flags it as feeling unresponsive.
*   **Performance**: Loading a large banner image + avatar + feed can be heavy. Coil's memory management is relied upon here; no special pre-sizing has been added.

## Open Questions
*   **What happens when we add a fourth profile tab?** Current `ButtonGroup` + `weight(1f)` divides available width equally. On a 360dp-wide device, four pills leaves ~50dp for each label — "Replies" overflows even without the press squeeze. When the fourth tab lands, `ProfilePillTabs` should swap its internals to `PrimaryScrollableTabRow` (or a custom horizontally scrollable `Row` of `ToggleButton`s) without changing its public `PillTab<T>` / `selectedValue` / `onSelect` API surface.
*   **Banner-behind-topBar contrast on white-heavy banners.** The top scrim is fixed at `Black.copy(alpha = 0.4f) → Color.Transparent`. If real-world banners surface legibility issues, consider an adaptive scrim that samples the banner's top region.
