# Tasks: Refactor Profile screen to match expressive design

## Phase 1: Foundation & Cleanup
- [x] 1.1 Remove the `Modifier.layout` hack from `ProfileScreenContent.kt`.
- [x] 1.2 Update `ProfileTopBar` to handle the new transparent-to-opaque transition logic.
- [x] 1.3 Add standard `WindowInsets` handling for the top area in `ProfileScreenContent`.

## Phase 2: Header Refactor
- [x] 2.1 Update `ProfileHero.kt` to render the actual `bannerUrl` using `NubecitaAsyncImage`.
- [x] 2.2 Implement the "overlapping avatar" layout.
- [x] 2.3 Add the scrim overlay for the banner to ensure text legibility.

## Phase 3: Sticky Actions (Verbs)
- [x] 3.1 Create a new `ProfileVerbsRow` component using M3 Expressive `ButtonGroup`.
- [x] 3.2 Update `ProfileScreenContent` to include the `ProfileVerbsRow` in a `stickyHeader`.
- [x] 3.3 Ensure the `ButtonGroup` correctly handles "Own Profile" vs "Other Profile" actions.

## Phase 4: Polish & Validation
- [x] 4.1 Tune the sticky header stacking (Actions vs Tabs).
- [ ] 4.2 Update screenshot tests to reflect the new design. (User action: record new baselines)
- [x] 4.3 Verify 120Hz scrolling performance with the new header.
