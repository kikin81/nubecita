## Context

The post-detail screen foundation shipped in PR #100 (nubecita-m28.5.1):

- `:feature:postdetail:api` exposes `PostDetailRoute(uri)` as the only `NavKey`.
- `:feature:postdetail:impl` houses `PostDetailViewModel` (extends `MviViewModel<PostDetailState, PostDetailEvent, PostDetailEffect>`), `PostThreadRepository` (delegating to atproto-kotlin's `getPostThread`), the `ThreadItem` sealed projection (`Ancestor` / `Focus` / `Reply` / `Fold` / `Blocked` / `NotFound`), and `PostDetailScreen` rendering each `ThreadItem` as the existing `:designsystem` `PostCard` inside a plain `LazyColumn`.
- The ViewModel's load-status sum already models `Idle / InitialLoading / Refreshing / InitialError / NotFound / BlockedRoot`.
- Feed PostCard body tap navigates here via `FeedEffect.NavigateToPost(uri) → LocalMainShellNavState.current.add(PostDetailRoute(uri))`.

The screen is functionally complete but visually undifferentiated. The MainShell's `ListDetailSceneStrategy` renders this same screen in the right pane on tablets / foldables, so the design pass also unblocks the adaptive layout.

This change is purely UI-layer. No data flow changes, no new HTTP boundaries, no schema migrations. The challenge is keeping the visual polish strictly inside Material 3 Expressive's component vocabulary while still creating real hierarchy that survives both light and dark modes.

## Goals / Non-Goals

**Goals:**

- Make the Focus Post visually distinguishable from ancestors / replies at a glance, in both light and dark themes, without relying on typography weight or borders.
- Render multi-image posts at the focus position with a Material-native scrolling experience.
- Add a reply affordance that's discoverable without scrolling and feels like it belongs to the M3 surface vocabulary.
- Establish a screenshot-test contract that prevents the visual hierarchy from regressing when other PRs touch `PostCard` or theme tokens.
- Ship pull-to-refresh so the existing `PostDetailLoadStatus.Refreshing` state has a UI affordance.

**Non-Goals:**

- Custom shape morphing, custom motion graphs, hand-rolled UI primitives. If a flourish requires reaching outside Compose / M3 standard APIs, the answer is to drop the flourish.
- Any data-layer change. The repository, mapper, and ViewModel from m28.5.1 are settled.
- Building the fullscreen media viewer route. This change wires the image-tap; the destination ships separately.
- Inline composer on the detail screen. Reply continues to navigate to the composer route.
- Quote-post detail rendering, real-time firehose subscription on the open thread.

## Decisions

### Decision 1: Focus Post emphasis via container color + shape, not typography

**Choice:** Wrap `ThreadItem.Focus`'s `PostCard` in a `Surface` with `MaterialTheme.colorScheme.surfaceContainerHigh` background and `RoundedCornerShape(24.dp)`. Default elevation; no custom shadow. Ancestors / Replies / Fold render the existing `PostCard` defaults on `MaterialTheme.colorScheme.surface`.

**Why this over alternatives:**

- *Bigger typography on the focus post* — adds visual noise, fails when posts are long, doesn't compose with theme dark mode. Rejected.
- *Border / outline around focus* — looks like a debug overlay; not in the M3 Expressive vocabulary. Rejected.
- *Custom shape morph (e.g., the M3 "cookie" shape on focus only)* — explicitly forbidden by the design intent's "no custom UI nightmare" rule. Rejected.
- *Higher elevation on focus only* — works but the elevation delta is subtle in dark theme and doesn't survive the `surfaceContainerHigh` dark-theme rendering. Rejected as primary signal; default elevation suffices because the color delta carries the signal.

The `surfaceContainerHigh` ↔ `surface` delta is a Material-tested pair that survives both themes (verified by Google's M3 baseline contrast tables). Pairing it with a 24dp shape gives a "softer" softer-than-the-cards-around-it read without any custom drawing.

### Decision 2: Carousel swap inside `:designsystem`'s `PostCard`, conditional on `images.size > 1`

**Choice:** Modify `PostCard`'s existing image-embed branch to delegate to `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` when `images.size > 1`. Single-image posts go down the existing single-image path unchanged.

**Why this over alternatives:**

- *Carousel only on the postdetail screen, not in the feed* — would create two image-embed code paths and force `:feature:postdetail:impl` to either duplicate `PostCard` or layer wrapping logic that re-renders the same data. Rejected; the single-source-of-truth `PostCard` is worth the conditional.
- *Always use the carousel, even for single-image posts* — single-image posts in the feed would gain unwanted swipe affordance and visual jitter from the carousel's preferred-item-width math. Rejected.
- *A new `MultiImageEmbed` composable separate from `PostCard`* — would fragment the design system; PostCard is the right place for embed dispatch. Rejected.

The conditional swap is internal to PostCard's existing image-embed branch — callers see no API change. Single-image posts must remain byte-for-byte unchanged at the screenshot level (regression risk for the feed, which is the highest-traffic surface). The screenshot test suite for `:designsystem` enforces this.

### Decision 3: Floating reply composer via `Scaffold`'s `floatingActionButton` slot, M3 Expressive FAB variant

**Choice:** Use the M3 Expressive `FloatingActionButton` (or `LargeFloatingActionButton` if the available variants don't ship a circle-shaped option that matches the design intent at the desired size) docked via `Scaffold`'s `floatingActionButton` slot. Always visible — no hide-on-scroll behavior. Tapping pushes the composer NavKey from nubecita-8f6.3 via `LocalMainShellNavState.current.add(...)`.

**Why this over alternatives:**

- *Hand-positioned `Surface` with `CircleShape`* — works, but reinvents what `FloatingActionButton` already provides (elevation tokens, ripple, semantics). Rejected unless the FAB variant doesn't satisfy the design intent.
- *Hide-on-scroll behavior* — adds animation complexity and a custom motion graph; rejected by the design intent's "no custom motion graphs" rule. Always-visible is simpler and the user always wants to reply at any scroll position.
- *Bottom-bar attach* — bottom bars on a detail screen conflict with the MainShell's nav suite on the outer scaffold. Floating is the right axis.
- *In-line reply button on the focus post only* — already exists via the PostCard action row; the floating affordance complements it, doesn't replace it.

The FAB choice keeps the elevation / shape / semantics within the M3 vocabulary. If the M3 Expressive material3 library version on the catalog ships an "expressive" FAB variant, prefer it; otherwise the standard FAB with default elevation is sufficient.

**Occlusion safeguard.** Because the FAB anchors above the LazyColumn at a fixed position, the LazyColumn MUST apply a bottom `contentPadding` of approximately FAB-height + standard edge spacing (~80–100dp combined) so the user can scroll the bottom-most reply fully above the FAB. Without this padding the FAB permanently occludes the lower portion of the last reply when the user reaches the end of the thread — a subtle bug that the with-replies screenshot fixture catches.

### Decision 4: Image tap stubbed as Snackbar-acknowledged no-op until the fullscreen viewer route exists

**Choice:** Wire the image tap (both single-image and per-carousel-slide) to dispatch a `PostDetailEffect.NavigateToMediaViewer(uri, imageIndex)` effect. The screen's effect collector navigates to the media-viewer destination if it exists; if the destination route doesn't exist in `:core:common:navigation` yet, the collector (a) logs a debug Timber entry, AND (b) surfaces a transient `Snackbar` on the screen's `SnackbarHostState` reading "Fullscreen viewer coming soon". File a follow-up bd issue tracking the missing viewer.

**Why this over alternatives:**

- *Don't wire the tap at all* — leaves a tap target on the image that does nothing, which is worse UX than a delayed-arrival viewer. Rejected.
- *Crash / throw on tap* — explicitly bad. Rejected.
- *Show a placeholder dialog* — competes for visual attention with the actual content; blocks the user's flow. Rejected.
- *Pure silent no-op (Timber-only)* — initial choice in an earlier draft. Rejected on review: a silent no-op feels like a broken app. Testers / users repeatedly tap the image expecting something to happen. The Snackbar is a transient acknowledgment that costs the user nothing but tells them the tap registered.

The Snackbar is the middle ground between a silent no-op (which feels broken) and a dialog (which blocks). It's M3-native (`SnackbarHost` + `SnackbarHostState`), auto-dismisses, and stops competing for attention immediately. When the viewer ships, the tap is wired through with no changes to PostDetailScreen's tap handlers — only the effect collector's missing-route branch is removed and replaced with `LocalMainShellNavState.current.add(<media viewer NavKey>)`. The Timber log stays for the lifetime of the codebase as a debug breadcrumb.

### Decision 5: Pull-to-refresh anchored above the entire LazyColumn, mirroring `FeedScreen`

**Choice:** Wrap the `LazyColumn` in `androidx.compose.material3.pulltorefresh.PullToRefreshBox` exactly as `FeedScreen.kt` does. The indicator anchors at the top of the screen content area; pulling triggers `PostDetailEvent.Refresh`.

**Why this over alternatives:**

- *Anchor only on the focus post region* — non-standard, surprising to users, doesn't compose with the FAB or the TopAppBar. Rejected.
- *No pull-to-refresh on detail screens* — leaves the existing `PostDetailLoadStatus.Refreshing` state without a UI affordance, which means users have no way to manually retry a stale thread. Rejected.

Mirroring `FeedScreen.kt` keeps the gesture vocabulary identical across the two main read surfaces.

### Decision 6: Container-hierarchy contrast snapshotted in BOTH light and dark themes

**Choice:** The screenshot test suite captures the container-hierarchy contrast pair (focus surface vs ancestor / reply surface) in both `Light` and `Dark` theme variants. Each is a separate snapshot file; both must regenerate when any theme token changes.

**Why this over alternatives:**

- *Light-only snapshot* — easy to ship something that's crisp in light and washes out in dark (or vice versa); has happened in past PRs. Rejected.
- *Dark-only* — same risk in reverse. Rejected.
- *Compute the WCAG contrast delta numerically and assert in a unit test* — depends on Material color tokens being stable across compose-bom updates and doesn't catch perceptual regressions. Useful as a future addition; not a replacement for screenshots.

The visual contract IS the screenshot pair. Anything that breaks it (theme-token shift, PostCard padding change, surface-container palette update upstream) regenerates the baseline and forces an explicit human review.

## Risks / Trade-offs

- **Carousel ↔ feed regression risk** → The screenshot test suite for `:designsystem` includes the existing single-image PostCard fixture; that fixture must remain byte-for-byte unchanged through this change. Add an explicit "single-image unchanged" assertion to the PR description.
- **24dp `RoundedCornerShape` clipping PostCard's internal padding** → PostCard's existing internal padding is 16dp; a 24dp container shape gives an 8dp visual breathing margin between the PostCard's text and the rounded corner edge. If the clipping looks wrong in the first screenshot pass, fall back to 20dp before adding any custom drawing.
- **`surfaceContainerHigh` colliding with `PullToRefreshBox`'s indicator** → The indicator renders above the focus-post surface when pulling. If the indicator becomes visually indistinguishable from the focus container in either theme, push the pull-to-refresh wiring to a follow-up issue and ship without it. Don't compromise the container hierarchy to fit the gesture.
- **M3 Expressive FAB variants may not be available at the catalog's material3 version** → Fall back to the standard `FloatingActionButton` with default elevation. The shape stays a circle (default for FAB); the experience degrades gracefully.
- **`HorizontalMultiBrowseCarousel` mixed-aspect-ratio behavior** → 3-image posts with mixed portrait/landscape are rare but possible. The carousel's default sizing is per-slide (preferred-item-width); accept that mixed aspect ratios will produce uneven slide heights. Don't try to size to tallest — that introduces letterboxing on the smaller slides which is uglier than the variance.
- **`bd` worktree isolation** → m28.5.2's implementation worktree must keep the project's standard `block-bd.sh` PreToolUse hook to prevent the worktree-Claude from drifting bd state. Documented in the bd-worktree skill; not a new risk.

## Open Questions

None remaining at proposal time. Decisions 1–6 collectively answer every question raised in the originating bd ticket's "Open questions worth a Decision entry" list. The implementation phase may surface concrete clipping or contrast issues that require revisiting Decision 1's 24dp value or Decision 5's pull-to-refresh anchor; treat those as in-flight tuning rather than spec re-opens.
