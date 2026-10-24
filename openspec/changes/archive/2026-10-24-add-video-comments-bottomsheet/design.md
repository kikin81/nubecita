## Context

Nubecita supports video consumption in two distinct surfaces:
1. `:feature:videos:impl` (`VideoFeedScreen`): Vertical snapping feed on `VerticalPager` with a persistent single `PlayerSurface` translated across reel items via `graphicsLayer`.
2. `:feature:videoplayer:impl` (`VideoPlayerScreen`): Dedicated media player with M3 Expressive controls, wavy seek bar, and PiP support.

In both surfaces, tapping the comment action currently dispatches `ComposerRoute(replyToUri = videoUri)` directly. This causes total context loss and disrupts video consumption. We need an adaptive comments sheet supporting continuous 120Hz video playback, nested gesture arbitration, adaptive side-panel presentation on tablets/foldables, and a phased comment composer.

### Relationship to Active Changes
This proposal unifies comments behavior across **both** the vertical reel feed and the fullscreen detail player. It **supersedes decisions D5 and D7 as well as tasks 8 and 9** of the active `revamp-fullscreen-video-player` change, replacing its non-modal inline composer design with this shared adaptive scaffold and phased composer rollout.

## Goals / Non-Goals

**Goals:**
- Provide a non-disruptive, continuous-playback comments viewing experience over active video.
- Resolve vertical scroll gesture conflicts between the comments sheet and `VerticalPager`.
- Prevent race conditions during pager page transitions and PiP entry snapshots.
- Deliver adaptive layouts across all window sizes: bottom sheet for `WindowWidthSizeClass.Compact`, and zero-scrim side sheet / two-pane split for `WindowWidthSizeClass.Medium` & `Expanded` (constrained width, scoped IME).
- Enable cross-shell composer navigation for both `@MainShell` (reels) and `@OuterShell` (detail player).
- Implement Phase 1 docked `"Add a comment..."` pill launching `ComposerRoute` with clean state restoration on return, immediate count badge increment, and fast hydration.
- Coordinate with playback lifecycle, PiP auto-collapse, and predictive back gestures via `PredictiveBackHandler`.

**Non-Goals:**
- Inline text composition and attachment pickers directly within the sheet in Phase 1 (deferred to Phase 2 headless extraction).
- Deeply nested recursive comment replies inside the bottom sheet (replies deeper than 1 level navigate to `PostDetailRoute`).
- PiP playback showing comments (PiP is strictly video-only).

## Decisions

### D1 — Gesture arbitration via settled-state pager gating and custom `NestedScrollConnection`
- **Choice**:
  1. **Settled-State Pager Gating**: Gate `VerticalPager(userScrollEnabled = isSheetSettledHidden)` where `isSheetSettledHidden = sheetState.currentValue == SheetValue.Hidden && !sheetState.isAnimationRunning`. `userScrollEnabled` MUST remain `false` during any opening, dragging, or closing animation to prevent mid-transition flings from triggering pager swipes.
  2. **Page-Settling & Filtered List Invariant**: Tapping the comment CTA is a strict no-op if `pagerState.isScrollInProgress` or `pagerState.settledPage != page`. The item binding is resolved strictly against the active filtered list (`status.items[page]`) rendered by the pager, preventing `IndexOutOfBoundsException` from deleted/filtered items.
  3. **Nested Scroll Connection**: Within the sheet, wire a custom `NestedScrollConnection` that prioritizes expanding the sheet from `PartiallyExpanded` to `Expanded` on upward drag, delegates to the comments `LazyColumn` for content scroll, and only collapses the sheet downward when the `LazyColumn` is at scroll position 0 (`firstVisibleItemIndex == 0 && firstVisibleItemScrollOffset == 0`).
- **Rationale**: Completely isolates reel paging from sheet manipulation and eliminates touch-event contention.
- **Alternatives Considered**:
  - *Gating on `targetValue == Hidden`*: Rejected; leaves a ~200-300ms window during the exit spring where late multi-touch flings the parent pager.

### D2 — Adaptive layout branching on `WindowWidthSizeClass` (`Compact` vs `Medium` / `Expanded`)
- **Choice**:
  - `WindowWidthSizeClass.Compact` (< 600dp): Bottom sheet overlaying the lower half of the screen (`PartiallyExpanded` ~50%, `Expanded` ~90%).
  - `WindowWidthSizeClass.Medium` (600dp–840dp, foldables/tablet portrait) & `WindowWidthSizeClass.Expanded` (>= 840dp, landscape/tablets): Two-pane split layout using M3 `SupportingPaneScaffold` with hinge awareness. Left pane renders unscaled aspect-fit video; right pane renders comments constrained between `360dp` and `440dp` (default `400dp`).
  - **Scoped IME Handling**: In two-pane mode, `WindowInsets.ime` padding is applied strictly to the right pane container. The left video pane is never re-measured or squeezed when the soft keyboard appears.
  - **Coordinated Insets**: The bottom sheet container sets `windowInsets = WindowInsets(0, 0, 0, 0)` so the docked pill's `.navigationBarsPadding()` handles insets without double-padding.
  - **Zero Scrim**: Side sheet mode has no dimming scrim. Video controls remain 100% interactive.
- **Rationale**: Maximizes screen real estate on tablets/foldables without distorting the video viewport.

### D3 — Continuous playback, looping invariant, and layout-phase surface geometry
- **Choice**: ExoPlayer playback is never paused when the comments sheet opens, closes, or resizes. The persistent `PlayerSurface` remains attached.
  - **Looping & Auto-Advance Invariant**: The active video continues to loop seamlessly while comments are open. If auto-advance to next video is enabled in user settings, auto-advance is suspended while `isCommentsSheetOpen == true`.
  - **Layout Phase Sizing**: Sizing adjustments during sheet expansion run purely in the Compose layout/draw phase (`graphicsLayer` + layout offsets).
  - **Chrome Auto-Fade Suspension**: While the initial thread fetch progress indicator is visible, suspend the player chrome's auto-fade timer so loading progress is never hidden prematurely.
- **Rationale**: Keeps playback at 120Hz without dropped frames or black surface flashes.

### D4 — Instant PiP snap-to-hidden and `PredictiveBackHandler` integration
- **Choice**:
  1. **Instant PiP Snap**: When entering PiP (`rememberIsInPipMode()`), bypass animated dismissal and perform an immediate state drop (`sheetState.snapTo(Hidden)` or conditional non-composition `if (!isInPip) { CommentsSheet(...) }`). This avoids the race condition where the OS PiP capture snapshot snaps a half-collapsed sheet.
  2. **Predictive Back Handler**: Adopt `PredictiveBackHandler(enabled = isCommentsSheetOpen)` (matching `FeedHost.kt:186-205`) collecting progress from `progress.collect { backEvent -> ... }` to translate the sheet offset interactively, with `BackHandler` as a non-predictive fallback on older APIs.
- **Rationale**: Guarantees a clean video frame in PiP and smooth predictive back motion on Android 14+.

### D5 — Shared module ownership and cross-shell composer navigation
- **Choice**:
  - **Shared Module Architecture**:
    - Container UI: `:designsystem` hosts `VideoCommentsSheetScaffold`.
    - Content & Presenter: `:core:post-interactions-ui` hosts `VideoCommentsSheetContent`, `VideoCommentsState`, and presenter logic.
    - Data: `:core:posts` provides `PostThreadRepository`.
    Both `:feature:videos:impl` and `:feature:videoplayer:impl` already depend on `:core:post-interactions-ui` and `:designsystem`, strictly observing the prohibition against feature-to-feature implementation dependencies.
  - **Cross-Shell Composer Navigation**: Update `ComposerNavigationModule` to register `ComposerRoute` in `@OuterShell` EntryProviderInstaller multibindings in addition to `@MainShell`. This allows `LocalAppNavigator` in `VideoPlayerScreen` to push `ComposerRoute` cleanly and pop back.
  - **Return Hydration & SWR Reconciliation**: `ComposerSubmitEvent` carries `newPostUri` and `replyToUri`. On submit:
    1. Immediately increment the parent video's `replyCount` badge (+1) on the action rail.
    2. Trigger an immediate hydration refetch via `PostThreadRepository.getPostThread(uri)`.
    3. Reconcile smoothly: if the server response temporarily lags behind the optimistic state, preserve the incremented count badge until confirmed.
  - **Docked Pill Accessibility**: Apply `Modifier.semantics { role = Role.Button; onClick(label = "Add a comment") { ... } }` to the pill, keeping internal icons decorative (`contentDescription = null`) to avoid duplicate announcements.
- **Rationale**: Completely resolves module ownership, cross-shell navigation, and accessibility contracts.

## Risks / Trade-offs

- **[Risk] Pager touch events race with sheet close animation** → Mitigation: Gate `userScrollEnabled` on `isSheetSettledHidden`, verifying both `currentValue == Hidden` and `!isAnimationRunning`.
- **[Risk] PiP capture snapshot captures collapsing sheet** → Mitigation: Instant `snapTo(Hidden)` / conditional composition bypass on `isInPip`.
- **[Risk] Cross-shell composer state loss** → Mitigation: Register `ComposerRoute` in `@OuterShell`, preserving `VideoPlayerRoute` underneath.
- **[Risk] Premature semantic-release triggers on incomplete feature** → Mitigation: Employ 4-layer `gh stack` and restrict the `feat:` conventional commit type strictly to the Layer 3 integration PR.

## Migration Plan & Delivery Strategy (`gh stack`)

To prevent premature Semantic Release bumps on partially baked code while preserving reviewer velocity and 120Hz quality bars, implementation is delivered via **GitHub Stacked Pull Requests (`gh stack`)** across 4 reviewable layers:

```
(main)
  ▲
  │ Layer 1: refactor/nubecita-76q8-sheet-foundations (Tasks 1.1–1.5)
  │          • State models & MVI presenter in :core:post-interactions-ui
  │          • VideoCommentsSheetScaffold in :designsystem (SupportingPaneScaffold / BottomSheet)
  │          • Settled-state gesture arbitration & NestedScrollConnection
  ▲
  │ Layer 2: refactor/nubecita-76q8-comments-ui (Tasks 2.1–2.4)
  │          • Comments LazyColumn (Loaded, Empty, Error) & optimistic likes
  │          • Docked comment pill with accessibility semantics & ComposerRoute cross-shell registration
  │          • Component-only @Preview screenshot baselines
  ▲
  │ Layer 3: feat/nubecita-76q8-video-comments-sheet (Tasks 3.1–3.4)  <-- SEMANTIC RELEASE TRIGGER
  │          • Reel Player integration (settled-page lookup on filtered list & auto-advance guards)
  │          • Detail Player integration (@OuterShell composer navigation & scoped IME)
  │          • Instant PiP snap, chrome auto-fade pause, & PredictiveBackHandler
  │          • Full-screen integration screenshot baselines across Compact, Medium, and Expanded
  ▲
  │ Layer 4: test/nubecita-76q8-benchmarks-and-spike (Tasks 4.1–4.3)
             • 120Hz Macrobenchmark (pre-resolving UIAutomator queries in setup)
             • Phase 2 Headless Composer extraction spike report
```

### Stack Hygiene & Execution Rules
1. **Semantic Release Hygiene**: Layers 1 and 2 use `refactor(video):` or `chore(video):` so intermediate commits never trigger premature user-facing Play Store releases. Only Layer 3 uses `feat(video):`, triggering exactly one minor version bump when the feature turns on end-to-end.
2. **Screenshot Baseline Isolation**: Component-level screenshot baselines are checked in with Layer 2. Full-screen integration baselines are added in Layer 3 across `Compact`, `Medium`, and `Expanded`.
3. **Atomic Merge Protocol**: In accordance with `AGENTS.md`, never merge child PRs individually from GitHub. Once all layers pass CI and review, merge the entire stack atomically via:
   ```bash
   gh stack merge --squash --yes
   ```
