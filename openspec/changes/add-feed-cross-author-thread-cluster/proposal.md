## Why

When a Bluesky post in the home timeline is itself a reply, our current renderer drops the entire `FeedViewPost.reply` payload on the floor — users see a leaf reply with no context about the thread it belongs to. To get back the missing context (root post + immediate parent), users have to leave the app or navigate to the author's profile.

bsky.app's web client renders this as a folded "thread cluster": the root post on top, a "View full thread" fold when intermediate posts are elided, the immediate parent, and finally the leaf reply, joined by avatar-gutter connector lines. **All the data is already in the feed API response** (`replyRef.root` + `replyRef.parent` + `replyRef.grandparentAuthor`); we just don't render it.

This change closes that gap by adopting the same cluster rendering. The `ThreadConnector` + `ThreadFold` design-system primitives shipped under `nubecita-m28.2` are the building blocks; this change adds the data-layer plumbing, the `ThreadCluster` composable that composes those primitives, and the `FeedScreen` dispatch that picks cluster vs single rendering per feed entry.

## What Changes

- **New `FeedItemUi` sealed type** in `:data:models`. Variants:
  - `FeedItemUi.Single(post: PostUi)` — a standalone feed post (current behavior).
  - `FeedItemUi.ReplyCluster(root, parent, leaf, hasEllipsis)` — a cross-author cluster where `leaf` is a reply, `parent` is the immediate parent (`replyRef.parent`), `root` is the thread starter (`replyRef.root`), and `hasEllipsis` indicates intermediate posts elided between root and parent.
- **`FeedViewPostMapper.toFeedItemUiOrNull(...)`** — new entry-point mapper used by `DefaultFeedRepository`. Detects a populated `reply: ReplyRef` with renderable `parent` + `root` and produces `ReplyCluster`; otherwise produces `Single`. `BlockedPost` / `NotFoundPost` parent variants fall back to `Single` for v1 — context is dropped silently rather than rendering "Replying to a hidden post" (deferred to a follow-up). The existing `toPostUiOrNull(...)` stays `internal` for backwards compatibility with the 18+ existing mapper tests; its body is refactored to delegate to a new `private fun PostView.toPostUiCore(repostedBy)` helper that the new entry point also uses.
- **`FeedScreenViewState.Loaded`** — `posts: ImmutableList<PostUi>` becomes `feedItems: ImmutableList<FeedItemUi>`. Pagination + scroll plumbing keys on the same stable identifier (the leaf post's URI for clusters, the post's URI for singles).
- **New `ThreadCluster` composable** in `:designsystem` — composes `PostCard` + optional `ThreadFold` + `PostCard` + `PostCard` in a `Column`, with the `Modifier.threadConnector` flags wired per cluster role: root has `connectBelow = true`, parent has `connectAbove = true, connectBelow = true`, leaf has `connectAbove = true`. The cluster is a single `LazyColumn` item.
- **`PostCard` gains `connectAbove` / `connectBelow` params** — defaulted to `false` so existing callers are unaffected. When either is true, `PostCard` applies `Modifier.threadConnector(...)` from the `:designsystem` primitives.
- **`FeedScreen`'s `LoadedFeedContent`** — iterates `feedItems` and dispatches: `Single` renders `PostCard`, `ReplyCluster` renders `ThreadCluster`. Video coordinator continues to work for `Single` (one post per `LazyColumn` item); for `ReplyCluster`, the **leaf post** is the only video target — root/parent posts in cluster context use static-poster rendering (`videoEmbedSlot = null`) for v1.
- **Subsumes `nubecita-im8`** — the "Replying to @handle" header described in im8 was a minimal text-line treatment of reply context. Rendering the parent post inline (as in `ReplyCluster`) carries that information implicitly. On merge, `nubecita-im8` is closed as superseded.
- **Closes m28.2 Section A** — the PostCard `connectAbove` / `connectBelow` integration that was deferred from `nubecita-m28.2`'s primitives PR lands here.

No deviation from MVI / Compose / Hilt / Room / Coil baseline.

## Capabilities

### New Capabilities
<!-- None — this change extends existing capabilities rather than introducing new ones. -->

### Modified Capabilities
- `data-models`: adds `FeedItemUi` sealed type as the projection target for feed mappers; existing `PostUi` requirements unchanged.
- `feature-feed`: `FeedScreenViewState.Loaded` now carries `feedItems: ImmutableList<FeedItemUi>` instead of `posts: ImmutableList<PostUi>`; mapper API signature changes accordingly; FeedScreen renders cluster vs single per feed item.
- `design-system`: `PostCard` accepts `connectAbove` / `connectBelow` parameters; new `ThreadCluster` composable.

## Impact

**Code:**
- `:data:models/PostUi.kt` — unchanged (PostUi is reused inside `FeedItemUi.ReplyCluster`).
- `:data:models/FeedItemUi.kt` — new file with the sealed type.
- `:feature:feed:impl/data/FeedViewPostMapper.kt` — new top-level `toFeedItemUiOrNull` entry point; existing `toPostUiOrNull` becomes a private leaf-projection helper.
- `:feature:feed:impl/FeedScreenViewState.kt` — `Loaded.posts` → `Loaded.feedItems`.
- `:feature:feed:impl/FeedScreen.kt` — `LoadedFeedContent` dispatches on `FeedItemUi`; previews updated.
- `:feature:feed:impl/FeedViewModel.kt` — projection from VM state to `FeedScreenViewState.Loaded` updated to call `toFeedItemUiOrNull` per `FeedViewPost`.
- `:designsystem/component/PostCard.kt` — new `connectAbove` / `connectBelow` parameters wired through `Modifier.threadConnector`.
- `:designsystem/component/ThreadCluster.kt` — new file.

**Tests:**
- Mapper unit tests: `reply.parent` populated → produces `ReplyCluster`. `reply.parent` is `BlockedPost` / `NotFoundPost` → falls back to `Single`. No `reply` → `Single`. `grandparentAuthor != null && grandparentAuthor.did != root.author.did` → `hasEllipsis = true`. `grandparentAuthor.did == root.author.did` (parent is direct child of root) → `hasEllipsis = false`.
- `ThreadCluster` previews: 4 baselines (with/without ellipsis × light/dark).
- `ThreadCluster` screenshot tests: same 4 baselines.
- `FeedScreen` screenshot test: a `Loaded` viewState with one `Single` and one `ReplyCluster` (with ellipsis) to verify the cluster renders correctly within the feed and visually contrasts with neighboring singles. Light + dark variants → 2 baselines.
- `PostCard` screenshot tests: 2 new baselines for `connectAbove=true, connectBelow=true` (cluster-parent role) variant in light + dark.

**Tickets closed:**
- `nubecita-m28.3` — closed by this change.
- `nubecita-im8` — closed as superseded.
- `nubecita-m28.2` — Section A's PostCard integration completes; ticket transitions from in_progress to in_progress (Section B detection still pending). On merge, file a follow-up bd issue for Section B.

**Tickets unchanged:**
- `nubecita-0tf` — perf-audit follow-up against the new ThreadCluster + ThreadConnector usage in lazy lists; trigger when profiling shows a hotspot.

## Non-goals

- **Tap-to-thread-detail navigation.** `ThreadCluster.onTap` (root, parent, leaf, fold) wires to a no-op for v1 because no post-detail screen exists yet. When the post-detail epic lands, callers will route taps to that screen.
- **Self-thread chain detection** (`nubecita-m28.2` Section B). Out of scope; same-author chains in feed continue to render as separate `Single` items.
- **Auto-play video on cluster's root / parent posts.** `ThreadCluster` passes `videoEmbedSlot = null` to root + parent's `PostCard`, so video embeds in those posts render as static posters. Only the leaf post participates in the feed video coordinator. Reasoning: the coordinator's "most visible" math operates on whole `LazyColumn` items; clusters are one item but contain three potentially-video posts. Wiring three video bindings per cluster expands coordinator complexity for a marginal use case.
- **Threadgate / postgate awareness.** "You can't reply to this thread" UI is a separate concern.
- **Inline expansion of the fold.** Tapping `ThreadFold` navigates to post-detail (when that exists); v1 fold tap is a no-op. Inline expansion would require an additional `getPostThread` call.
- **Rendering for `BlockedPost` / `NotFoundPost` parents.** v1 silently falls back to `Single`. A future ticket can add a "Replying to a hidden post" header if the UX demand surfaces.
