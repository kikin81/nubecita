# Tasks: Refactor Profile screen to match expressive design

## Phase 1: Foundation & Cleanup
- [x] 1.1 Remove the `Modifier.layout` hack from `ProfileScreenContent.kt`.
- [x] 1.2 Update `ProfileTopBar` to handle the transparent-to-opaque transition via a `derivedStateOf` on `listState`; add circular surface-tinted nav/settings icon buttons that read over the banner.
- [x] 1.3 Host `ProfileTopBar` in `Scaffold.topBar`; let `LazyColumn` extend edge-to-edge under the bar (no top `contentPadding` / `Modifier.padding`).

## Phase 2: Header Refactor
- [x] 2.1 Render the actual `bannerUrl` via `NubecitaAsyncImage` in `ProfileHero.kt`.
- [x] 2.2 Implement the overlapping-avatar layout (96dp, 4dp surface ring, `ContentScale.Crop`).
- [x] 2.3 Apply a black-to-transparent top scrim for status-bar legibility.
- [x] 2.4 Clip the banner image + scrim individually with `RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)` (NOT the parent Box) so the overlapping avatar isn't cropped.
- [x] 2.5 Update the loading shimmer to mirror the overlapping-avatar layout.

## Phase 3: Verbs Row
- [x] 3.1 Create `ProfileVerbsRow` as a plain `Row` of pill `Button` / `FilledTonalButton` + standalone `IconButton` overflow. (Decision against `ButtonGroup` + `toggleableItem` — see design.md Decision 3.)
- [x] 3.2 Render `ProfileVerbsRow` + `ProfilePillTabs` together in a single regular `LazyColumn` item (not a `stickyHeader` — see design.md Decision 5).
- [x] 3.3 Branch verbs content on `ownProfile`: Edit-profile for own; Follow/Following + optional Message + overflow for other.
- [x] 3.4 Promote Settings from the overflow menu to a top-bar icon button (own-profile only).

## Phase 4: Pill Tabs Tuning
- [x] 4.1 Set `ProfilePillTabs.ButtonGroup.expandedRatio = 0.025f` to keep a subtle press response without squeezing "Replies" into two lines under press.

## Phase 5: Validation
- [x] 5.1 Update `ProfileTopBarScreenshotTest` for the new `ownProfile` + `onSettings` parameters.
- [x] 5.2 Regenerate screenshot baselines across `feature/profile/impl`, `designsystem` (ProfilePillTabs + NubecitaIconShowcase), `feature/postdetail/impl`, `feature/search/impl`. The post-detail and search diffs are collateral from the new Settings glyph added to `material_symbols_rounded.ttf`.
- [x] 5.3 Verify 120Hz scrolling performance with the new header on-device.

## Phase 6: Follow-ups (out of scope for this change)
- [ ] 6.1 Add `@Preview` + `@PreviewTest` coverage for the new `ProfileVerbsRow` (own-profile, other-not-following, other-following, other-with-message, other-without-message).
- [ ] 6.2 When/if a fourth profile tab lands, swap `ProfilePillTabs`' internals to `PrimaryScrollableTabRow` (or a custom horizontally scrollable Row) — public API stays the same.
