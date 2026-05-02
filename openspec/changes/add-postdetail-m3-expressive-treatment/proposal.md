## Why

The `PostDetail` screen shipped via PR #100 (nubecita-m28.5.1) is functionally complete — it loads a thread, renders ancestors / focus / replies, and wires up tap-to-navigate from the feed. But it's deliberately bland: every `ThreadItem` renders as the default `PostCard` on `surface`, so the user can't tell at a glance which post they tapped into. The MainShell's adaptive list-detail layout on tablets shows this same bland screen in the right pane. Without visual hierarchy, the screen fails the basic "where am I" test that every other Bluesky / threads-style client passes.

This change applies the locked design intent on top of that foundation: container hierarchy via Material 3 Expressive's `surfaceContainerHigh`, multi-image embeds via the standard `HorizontalMultiBrowseCarousel`, and a floating reply composer affordance. The deliberate non-goal is "look unique" via custom UI — every flourish is a Google-shipped M3 component used with intent. This is the moment the post-detail screen looks like itself.

## What Changes

- Wrap `ThreadItem.Focus`'s `PostCard` in a `Surface` with `MaterialTheme.colorScheme.surfaceContainerHigh` background and `RoundedCornerShape(24.dp)`. Ancestors, replies, and folds remain on the default `surface` background. Hierarchy comes from container color and shape — not typography weight, not borders, not custom shape morphs.
- Inside `:designsystem`'s `PostCard` image-embed branch, conditionally swap to `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` when `images.size > 1`. Single-image posts stay byte-for-byte unchanged (regression risk for the feed).
- Add a floating reply composer affordance to `PostDetailScreen`'s `Scaffold` `floatingActionButton` slot. On tap, push the composer NavKey shipped by `nubecita-8f6.3` via `LocalMainShellNavState.current.add(...)` — same call site shape as the PostCard reply button, so no composer API change.
- Wire image taps inside the focus post (and within carousel slides) to the fullscreen media viewer route. If that route doesn't exist yet (likely), file a follow-up bd issue and stub the tap as a no-op for v1.
- Wire `PullToRefreshBox` mirroring `FeedScreen`'s pattern so the existing `PostDetailLoadStatus.Refreshing` state from m28.5.1 has a UI affordance.
- Establish the screenshot-test contract: `feature/postdetail/impl/src/screenshotTest/` covers focused-with-ancestors, with-replies, single-post, blocked-root fallback, multi-image carousel at focus, and the container-hierarchy contrast pair (light + dark themes).

## Capabilities

### New Capabilities

- `feature-postdetail`: The post-detail thread screen — `:feature:postdetail:api` (`PostDetailRoute(uri)` NavKey) and `:feature:postdetail:impl` (the screen, ViewModel, repository, mapper). m28.5.1 shipped the foundation without openspec; this change is where the capability gets its first spec, capturing both the data/VM/navigation contract already in place AND the M3 Expressive render contract being added.

### Modified Capabilities

- `design-system`: `PostCard`'s image-embed rendering branch gains a conditional path for `images.size > 1` that delegates to `HorizontalMultiBrowseCarousel`. The single-image path is unchanged.

## Impact

- **Affected modules**: `:feature:postdetail:impl` (screen render path, screenshot harness), `:designsystem` (PostCard image-embed branch).
- **Affected specs**: `feature-postdetail` (new), `design-system` (delta).
- **Dependencies**: requires `androidx.compose.material3:material3` at a version that ships `androidx.compose.material3.carousel.HorizontalMultiBrowseCarousel` (1.3.x or later — already on the version catalog).
- **Out of scope for this change**: quote-post detail rendering, firehose subscription, inline composer, the fullscreen media viewer route itself, custom shape morphing, custom motion graphs, hand-rolled UI primitives, data-layer changes (m28.5.1 is settled).
- **Behavior under feature flags / build variants**: none. The screen has no flag gate; the carousel swap is a deterministic branch on `images.size`.
- **Backwards compatibility**: no breaking changes. The PostCard signature stays the same; the image-embed swap is internal. Single-image posts in the feed are byte-for-byte unchanged at the screenshot level.

## Non-goals

- **Inline composer.** Reply expands as a route push, not as an inline expansion in the detail screen — defer.
- **Real-time thread updates.** Initial impl is fetch-on-open + pull-to-refresh; firehose subscription on the open thread is deferred.
- **Quote-post detail rendering.** Quote posts continue to render as the existing PostCard quoted-embed treatment; a dedicated detail view ships separately if it ever diverges.
- **Custom UI primitives.** Explicitly forbidden by the design intent. If a flourish requires reaching outside standard Compose / M3 APIs, the answer is to drop the flourish, not to build a primitive.
- **Building the fullscreen media viewer.** This change wires the image-tap to navigate; the viewer destination ships separately. Until then, taps are stubbed as no-op with a follow-up bd issue tracking the gap.
