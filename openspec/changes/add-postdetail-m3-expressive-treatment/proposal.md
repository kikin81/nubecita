## Why

The `PostDetail` screen shipped via PR #100 (nubecita-m28.5.1) is functionally complete — it loads a thread, renders ancestors / focus / replies, and wires up tap-to-navigate from the feed. But it's deliberately bland: every `ThreadItem` renders as the default `PostCard` on `surface`, so the user can't tell at a glance which post they tapped into. The MainShell's adaptive list-detail layout on tablets shows this same bland screen in the right pane. Without visual hierarchy, the screen fails the basic "where am I" test that every other Bluesky / threads-style client passes.

This change applies the locked design intent on top of that foundation: container hierarchy via Material 3 Expressive's `surfaceContainerHigh`, multi-image embeds via the standard `HorizontalMultiBrowseCarousel`, and a floating reply composer affordance. The deliberate non-goal is "look unique" via custom UI — every flourish is a Google-shipped M3 component used with intent. This is the moment the post-detail screen looks like itself.

## What Changes

### Data layer (planned debt from m28.5.1, due now)

- Extract the four shared mapping helpers earmarked by `m28.5.1`'s `PostThreadMapper` (`toPostUiCore`, `toAuthorUi`, `toViewerStateUi`, `toEmbedUi`) and the three private embed wrapper-construction helpers (`ImagesView.toEmbedUiImages`, `VideoView.toEmbedUiVideo`, `ExternalView.toEmbedUiExternal`) from `:feature:feed:impl/data/FeedViewPostMapper.kt` into a new `:core:feed-mapping` module. Both `FeedViewPostMapper` and `PostThreadMapper` consume them; neither owns the implementation.
- Update `PostThreadMapper` to delegate embed mapping to the shared helpers. The previously-deferred `EmbedUi.Empty` placeholder is replaced by full embed dispatch — without this, the carousel and image-tap behaviors below never execute on `PostDetailScreen` because no PostDetail post would carry `EmbedUi.Images` to trigger them. (m28.5.1's mapper KDoc explicitly identified this extraction as the m28.5.2 trigger; honoring that contract here.)

### UI layer (the original visual treatment)

- Wrap `ThreadItem.Focus`'s `PostCard` in a `Surface` with `MaterialTheme.colorScheme.surfaceContainerHigh` background and `RoundedCornerShape(24.dp)`. Ancestors, replies, and folds remain on the default `surface` background. Hierarchy comes from container color and shape — not typography weight, not borders, not custom shape morphs.
- Inside `:designsystem`'s `PostCard` image-embed branch, conditionally swap to `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` when `images.size > 1`. Single-image posts stay byte-for-byte unchanged (regression risk for the feed). PostCard's signature gains a backwards-compatible `onImageClick: (imageIndex: Int) -> Unit = {}` parameter for the post-detail's media-viewer wiring; all existing call sites compile unchanged via the default no-op.
- Add a floating reply composer affordance to `PostDetailScreen`'s `Scaffold` `floatingActionButton` slot. On tap, push the composer NavKey shipped by `nubecita-8f6.3` via `LocalMainShellNavState.current.add(...)` — the same effect-collector pattern PostDetailScreen already uses for `NavigateToPost` / `NavigateToAuthor`. (No "PostCard reply button" pattern to mirror — the feed's `OnReplyClicked` and PostDetailScreen's `onReply` are both no-ops in m28.5.1.)
- Wire image taps inside the focus post (and within carousel slides) to a `NavigateToMediaViewer(postUri, imageIndex)` effect. If the fullscreen media viewer route doesn't yet exist in `:core:common:navigation`, the effect collector logs a Timber breadcrumb AND surfaces a transient `Snackbar` ("Fullscreen viewer coming soon") on the screen's `SnackbarHostState`. Silent no-op was rejected as feeling broken; a dialog was rejected as blocking. File a follow-up bd issue tracking the missing destination.
- **Verification-only** for pull-to-refresh: `PullToRefreshBox` already wraps the LazyColumn (shipped in m28.5.1). The visual pass MUST verify the existing pull indicator stays visible against the new `surfaceContainerHigh` focus container during the gesture — captured as a screenshot fixture at the indicator-visible scroll position. No re-implementation of pull-to-refresh.
- Apply bottom `contentPadding` to the LazyColumn equal to FAB height + standard edge spacing (~80–100dp combined) so the FAB never occludes the bottom-most reply when the user scrolls to the end of the thread.
- Establish the screenshot-test contract: `feature/postdetail/impl/src/screenshotTest/` covers focused-with-ancestors, with-replies (incl. the bottom-padding-clears-FAB position), single-post, blocked-root fallback (rendered as a `ThreadItem.Blocked` row, not as a top-level load-status variant), multi-image carousel at focus, the pull-to-refresh-indicator-vs-focus-container contrast moment, and the container-hierarchy contrast pair (light + dark themes).

## Capabilities

### New Capabilities

- `feature-postdetail`: The post-detail thread screen — `:feature:postdetail:api` (`PostDetailRoute(postUri: String)` NavKey) and `:feature:postdetail:impl` (the screen, ViewModel, repository, mapper). m28.5.1 shipped the foundation without openspec; this change is where the capability gets its first spec, capturing both the data/VM/navigation contract already in place AND the M3 Expressive render contract being added.
- `core-feed-mapping`: New `:core:feed-mapping` module hosting the shared atproto-wire-type → UI-model conversion helpers. Both `:feature:feed:impl`'s `FeedViewPostMapper` and `:feature:postdetail:impl`'s `PostThreadMapper` consume the helpers; neither owns the implementation. Earmarked for extraction by `m28.5.1`'s `PostThreadMapper` KDoc.

### Modified Capabilities

- `design-system`: `PostCard`'s image-embed rendering branch gains a conditional path for `images.size > 1` that delegates to `HorizontalMultiBrowseCarousel`. The single-image path is unchanged. PostCard's signature gains a backwards-compatible `onImageClick: (imageIndex: Int) -> Unit = {}` parameter (default no-op) so post-detail can wire per-image navigation without breaking existing call sites.
- `feature-feed`: `FeedViewPostMapper`'s embed wrapper-construction helpers are sourced from `:core:feed-mapping` instead of being declared inline in `:feature:feed:impl`. Behavior is unchanged; ownership moves.

## Impact

- **Affected modules**: `:feature:postdetail:impl` (screen render path, mapper embed wiring, screenshot harness), `:designsystem` (PostCard image-embed branch + signature), `:feature:feed:impl` (FeedViewPostMapper delegates to shared helpers), NEW `:core:feed-mapping` module.
- **Affected specs**: `feature-postdetail` (new), `core-feed-mapping` (new), `design-system` (delta), `feature-feed` (delta).
- **Dependencies**: requires `androidx.compose.material3:material3` at a version that ships `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` (1.3.x or later — already on the version catalog).
- **Out of scope for this change**: quote-post detail rendering, firehose subscription, inline composer, the fullscreen media viewer route itself, custom shape morphing, custom motion graphs, hand-rolled UI primitives.
- **Behavior under feature flags / build variants**: none. The screen has no flag gate; the carousel swap is a deterministic branch on `images.size`.
- **Backwards compatibility**: no breaking changes. PostCard's signature gains an additive optional parameter (default no-op); existing call sites compile unchanged. Single-image posts in the feed are byte-for-byte unchanged at the screenshot level. The mapping extraction relocates code between modules but produces the same outputs from the same inputs — feed timeline rendering MUST stay byte-for-byte identical at the screenshot level.

## Non-goals

- **Inline composer.** Reply expands as a route push, not as an inline expansion in the detail screen — defer.
- **Real-time thread updates.** Initial impl is fetch-on-open + pull-to-refresh; firehose subscription on the open thread is deferred.
- **Quote-post detail rendering.** Quote posts continue to render as the existing PostCard quoted-embed treatment; a dedicated detail view ships separately if it ever diverges.
- **Custom UI primitives.** Explicitly forbidden by the design intent. If a flourish requires reaching outside standard Compose / M3 APIs, the answer is to drop the flourish, not to build a primitive.
- **Building the fullscreen media viewer.** This change wires the image-tap to navigate; the viewer destination ships separately. Until then, taps are stubbed as no-op with a follow-up bd issue tracking the gap.
