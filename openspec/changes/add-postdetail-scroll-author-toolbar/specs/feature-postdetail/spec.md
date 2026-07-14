## ADDED Requirements

### Requirement: The post-detail toolbar swaps its title for the focus post's author on scroll

`PostDetailScreenContent`'s `topBar` SHALL render a `PostDetailTopBar` whose title is scroll-reactive. At rest it displays the localized "Post" title (`R.string.postdetail_title`). Once the **focus** post's card has scrolled up under the app bar, the title is replaced by an author block: the focus post's avatar (28dp `NubecitaAvatar`, including its deterministic initial fallback for avatarless accounts) followed by the author's display name, falling back to `@handle` when `displayName` is null or blank, on a single ellipsized line.

The bar tracks exactly one author for the screen's lifetime — the focus post's. It never re-targets to an ancestor's or a reply's author.

The author block is **not** interactive. It carries no `clickable` and no `onClickLabel`.

#### Scenario: Toolbar reads "Post" at rest

- **WHEN** the post-detail screen renders at scroll position 0
- **THEN** the toolbar title is the localized "Post" string, and no avatar or author name is present in the bar. Existing post-detail screenshot baselines remain byte-for-byte unchanged.

#### Scenario: Author appears once the focus card scrolls under the bar

- **WHEN** the user scrolls until the focus card's top edge is tucked at least `enterThresholdPx` (56dp) above the bottom of the app bar
- **THEN** the "Post" title fades out in place, and the author block slides in from the layout-direction start edge while fading in.

#### Scenario: Author disappears on the way back to the top

- **WHEN** the author block is showing and the user scrolls back until the focus card's top edge has come back down past `exitThresholdPx` (40dp)
- **THEN** the author block slides back out toward the start edge and fades out, and the "Post" title fades back in. The transition is the exact reverse of the entry.

#### Scenario: Threads with ancestors keep the "Post" title until the focus card passes

- **GIVEN** a thread whose focus post has one or more ancestors rendered above it, so the list opens at the root ancestor
- **WHEN** the user scrolls through the ancestors but the focus card has not yet reached the app bar
- **THEN** the toolbar still reads "Post". The author appears only once the focus card itself passes under the bar — never while the focus post is still below the fold.

#### Scenario: No focus post resolved

- **WHEN** the screen is in `InitialLoading`, `InitialError`, or any state where `PostDetailState.focusPost` is null
- **THEN** the toolbar reads "Post" at every scroll position and no author block can appear.

### Requirement: The swap threshold is a hysteresis band evaluated by a pure function

The show/hide decision SHALL be computed by an `internal` pure function `shouldShowAuthorInBar(focusIndex, firstVisibleItemIndex, focusItemTopPx, enterThresholdPx, exitThresholdPx, currentlyShown)` returning `Boolean`, and consumed by the stateful `PostDetailTopBar` overload inside a `derivedStateOf` over `LazyListState.layoutInfo`.

The function takes distinct enter (56dp) and exit (40dp) thresholds so that a slow drag parked on the boundary cannot flip the state repeatedly and re-fire the transition.

When the focus card is not present in `visibleItemsInfo`, the function SHALL disambiguate "scrolled off the top" from "not yet reached" by comparing `focusIndex` against `firstVisibleItemIndex` — these two cases require opposite answers and a `null` lookup alone cannot distinguish them.

#### Scenario: Focus card scrolled entirely off the top

- **WHEN** `focusIndex < firstVisibleItemIndex` and the focus card is absent from `visibleItemsInfo`
- **THEN** the function returns `true` — the author is shown.

#### Scenario: Focus card still below the fold

- **WHEN** the focus card is absent from `visibleItemsInfo` and `focusIndex >= firstVisibleItemIndex`
- **THEN** the function returns `false` — the author is not shown.

#### Scenario: Hysteresis band holds state

- **GIVEN** the focus card is visible and tucked 48dp under the bar (between the 40dp exit and 56dp enter thresholds)
- **WHEN** `currentlyShown` is `true`
- **THEN** the function returns `true` (it stays shown).
- **WHEN** `currentlyShown` is `false`
- **THEN** the function returns `false` (it stays hidden).

#### Scenario: No focus row

- **WHEN** `focusIndex` is `-1`
- **THEN** the function returns `false` regardless of every other argument.

### Requirement: The toolbar's motion honours reduce-motion and layout direction

The title swap SHALL be implemented with `AnimatedContent` using `SizeTransform(clip = false)`, with animation specs read from `MaterialTheme.motionScheme` (`defaultSpatialSpec()` for the slide, `defaultEffectsSpec()` for the fades). Hand-rolled `spring()` / `tween()` specs are prohibited on this surface — they would opt it out of `NubecitaMotionScheme`'s `isReduced` branch.

The horizontal slide offset SHALL be a fixed 24dp (not a fraction of the content width), and its sign SHALL be derived from `LocalLayoutDirection`.

Only the incoming element moves: the outgoing "Post" title fades without translating.

#### Scenario: Reduce-motion is honoured without a feature-local branch

- **WHEN** the system reduce-motion preference is enabled, so `NubecitaTheme` installs `NubecitaMotionScheme(isReduced = true)`
- **THEN** the slide and fade collapse to the scheme's short linear tweens, with no reduce-motion conditional written inside `:feature:postdetail:impl`.

#### Scenario: RTL slides in from the correct edge

- **WHEN** the app renders under an RTL layout direction
- **THEN** the author block slides in from the **right** (the visual start edge), because the slide offset's sign is taken from `LocalLayoutDirection`. It does not slide in from the left.

#### Scenario: Slide speed is independent of name length

- **WHEN** the author's display name is very long versus very short
- **THEN** the block travels the same fixed 24dp over the same spec in both cases. The slide distance does not scale with the rendered content width.

### Requirement: The scroll-reactive toolbar introduces no ViewModel or contract change

`PostDetailViewModel`, `PostDetailState`, `PostDetailEvent`, and `PostDetailEffect` SHALL be unchanged by this change. Scroll position terminates at the screen Composable, consistent with the project's rule that ViewModels do not observe Compose-runtime scroll state.

The author is derived from the existing `PostDetailState.focusPost` extension; the focus row index is derived as `items.indexOfFirst { it is ThreadItem.Focus }` at the screen layer.

#### Scenario: VM sources are untouched

- **WHEN** `PostDetailViewModel.kt` and `PostDetailContract.kt` are diffed before and after this change
- **THEN** there are no additions or modifications.

#### Scenario: Existing VM tests pass unmodified

- **WHEN** `./gradlew :feature:postdetail:impl:testDebugUnitTest` runs after this change merges
- **THEN** every pre-existing test method passes without source-level modification.
