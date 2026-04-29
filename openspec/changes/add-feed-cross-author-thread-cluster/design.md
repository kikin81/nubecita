## Context

The home timeline currently maps every `app.bsky.feed.defs#feedViewPost` to a `PostUi` and renders it as a single `PostCard`. The `feedViewPost.reply: ReplyRef?` field — which carries `root: PostView`, `parent: PostView`, and `grandparentAuthor: ProfileViewBasic?` for any reply — is dropped. Users see leaf replies with no thread context.

PR #77 (`nubecita-m28.2` partial) shipped the `Modifier.threadConnector` + `ThreadFold` design-system primitives. This change is the consumer: data-layer plumbing to project the reply payload into a UI shape, the `ThreadCluster` composable that arranges three `PostCard`s with the connector primitives applied, and the `FeedScreen` dispatch that picks cluster vs single per feed item.

The current FeedScreen plumbing has two non-trivial coupling points worth flagging because they shape the data-model decision:

1. **`FeedScreenViewState.Loaded.posts: ImmutableList<PostUi>`** — pagination, scroll state, and the `key = { it.id }` lambda all assume one item per element. Switching to a cluster representation requires the cluster to remain *one* `LazyColumn` item, otherwise pagination and scroll-position math gets confused.
2. **Video coordinator** — `LoadedFeedContent` derives "most visible video" from `listState.layoutInfo.visibleItemsInfo` (one entry per `LazyColumn` item) and binds video playback per visible item. If a cluster contains three potentially-video posts within one item, the coordinator's "most visible" math doesn't naturally distinguish them.

## Goals / Non-Goals

**Goals:**

- A reply in the timeline renders the bsky.app-style folded cluster: root → optional fold → parent → leaf with avatar-gutter connectors.
- The cluster is one `LazyColumn` item — preserves pagination + scroll behavior unchanged.
- Mapper handles the lexicon's `BlockedPost` / `NotFoundPost` parent variants without crashing — falls back to `Single` rendering.
- Tap targets exist on root, parent, leaf, and fold but are no-ops in v1 (post-detail screen doesn't exist yet).

**Non-Goals:**

- Auto-play video on the cluster's root + parent posts. Only the leaf participates in the video coordinator; root + parent posts render `videoEmbedSlot = null` (static-poster fallback).
- Inline fold expansion. Fold tap will navigate to post-detail (when that exists); v1 = no-op.
- Same-author chain detection (`nubecita-m28.2` Section B).
- "Replying to a hidden post" header for `BlockedPost` / `NotFoundPost` parents — silently fall back to `Single`.

## Decisions

### D1. `FeedItemUi` sealed type, not flat fields on `PostUi`

`FeedItemUi` is a sealed interface in `:data:models` with two variants:

```kotlin
sealed interface FeedItemUi {
    data class Single(val post: PostUi) : FeedItemUi
    data class ReplyCluster(
        val root: PostUi,
        val parent: PostUi,
        val leaf: PostUi,
        val hasEllipsis: Boolean,
    ) : FeedItemUi
}
```

**Alternatives considered:**

- *Flat fields on `PostUi`*: add nullable `replyParent: PostUi?`, `replyRoot: PostUi?`, `replyHasEllipsis: Boolean = false` to the existing `PostUi` data class. Rejected — encodes invariants implicitly ("if `replyRoot != null` then `replyParent` must also be non-null and form a valid chain") that the type system doesn't enforce. The sealed-type path makes the cluster-vs-single distinction exhaustive at compile time.
- *Wrap PostUi in a generic `FeedRow<T>` with metadata*: rejected — over-engineered. We only have two variants, both well-understood.

The sealed type also reads cleanly in `FeedScreen.kt`'s render dispatch (`when (item)` is exhaustive), and unit-testing the mapper produces a sealed result that's straightforward to assert against.

### D2. Composite cluster (one `LazyColumn` item), not flat-mapped rows

A `ReplyCluster` renders as **one** `LazyColumn` item. The `ThreadCluster` composable arranges its three `PostCard`s in a `Column` and dispatches the connector flags per role.

**Alternatives considered:**

- *Flat-mapped*: `FeedItemUi` could be `Single`, `ClusterPost(post, role: Root|Parent|Leaf, hasEllipsisAfter: Boolean)`, `ClusterFold(count)`. The mapper produces a flat list where one logical cluster expands to 3-4 list items. This matches the reference `ThreadFeedScreen.kt` shape and lets the video coordinator naturally distinguish each cluster post (each gets its own `visibleItemsInfo` entry).
  - Rejected for v1 because: (a) pagination keys would need a synthetic stable identifier ("cluster-X-root", "cluster-X-fold", "cluster-X-parent", "cluster-X-leaf") that bloats the key space and complicates `LazyListState.firstVisibleItemIndex` semantics; (b) the cluster is conceptually *one* feed entry — the leaf is "the post in your feed", root + parent are context — and treating it as multiple items conflates that mental model with implementation; (c) the video coordinator complexity gain isn't worth it given root + parent rarely have videos in practice.
  - Worth revisiting if (a) self-thread chains land (m28.2 Section B) and the cluster shape generalizes to N posts, or (b) profiling shows the composite layout is a perf hotspot.

- *Hybrid: one `LazyColumn` item with internal `Column`, but with intermediate `key`s exposed for scroll-anchor math*: rejected — we don't currently scroll-anchor inside a cluster, so the complexity has no payoff.

### D3. Video coordinator: leaf-only

`ThreadCluster` passes `videoEmbedSlot = null` to root + parent `PostCard`s and the host's actual `videoEmbedSlot` only to the leaf `PostCard`.

**Alternatives considered:**

- *Wire all three to the coordinator*: would need a new "primary post" concept inside the coordinator's binding so it knows which video in the cluster to favor. Pure complexity, no observed user need (root + parent rarely have videos).
- *Disable video entirely inside clusters*: also acceptable. Picking leaf-only because a video in the leaf reply is the most likely scenario and matches expectations from bsky.app's web client.

If a future user-research signal shows root or parent videos are common in clusters, swap to the "all three" approach with a coordinator extension. Tracked under `nubecita-0tf`'s related concerns.

### D4. `BlockedPost` / `NotFoundPost` parent → fall back to `Single`

When `feedViewPost.reply.parent` is the lexicon's `BlockedPost` or `NotFoundPost` variant (vs the `PostView` variant), the mapper emits `FeedItemUi.Single(leaf)` — same as if `reply` were null. Context is silently dropped.

**Alternatives considered:**

- *Drop the entire feed entry*: harsh. The leaf is still readable on its own merits and dropping it would make the timeline feel sparse for users following accounts that reply often into blocked threads.
- *Render `Single` with a "Replying to a hidden post" header*: better UX. Deferred because (a) it requires a new render variant (a `Single` + reply-context-header subtype), (b) the visual treatment depends on bsky.app's pattern for the hidden-context case, which warrants its own design pass, (c) the `BlockedPost` / `NotFoundPost` cases are uncommon enough that v1 silent-fallback is acceptable. File as a follow-up if we observe complaints.

The mapper logs at `Timber.w` level when this fallback fires so we can surface frequency in dev builds. (Production logs are silent — Timber's release tree is no-op.)

### D5. `hasEllipsis` heuristic

The lexicon doesn't tell us how many intermediate posts were elided between root and parent. We use `grandparentAuthor` as a proxy: when `grandparentAuthor != null && grandparentAuthor.did != root.author.did`, **at least one** post sits between root and parent (the grandparent itself), so we set `hasEllipsis = true`.

When `grandparentAuthor == null` or `grandparentAuthor.did == root.author.did`, parent is a direct reply to root and the cluster is contiguous (`hasEllipsis = false`).

`ThreadFold` accepts a `count` parameter that we pass `0` to in v1 (no precise count available without `getPostThread`). The fold renders without a "· N more" suffix when count is 0.

**Alternative considered:**

- *Always render the fold when `reply` is present*: too aggressive — direct replies to root would have a confusing "View full thread" indicator pointing at empty space.
- *Fetch `getPostThread` to compute the precise count*: extra network call per cluster. Heavy. v1 does without.

### D6. `PostCard` connector params: defaulted off, dispatched to `Modifier.threadConnector`

`PostCard` gains:

```kotlin
fun PostCard(
    ...,
    connectAbove: Boolean = false,
    connectBelow: Boolean = false,
)
```

When either flag is true, `PostCard`'s root `Modifier` chain applies `Modifier.threadConnector(connectAbove, connectBelow, color = MaterialTheme.colorScheme.outlineVariant)`. When both are false, no modifier is applied — zero overhead for non-cluster usage.

`ThreadCluster` wires the flags per role:

| Cluster position | `connectAbove` | `connectBelow` |
|---|---|---|
| root | false | true |
| parent | true | true |
| leaf | true | false |

Connector geometry uses the modifier's defaults (gutterX=42dp, avatarTop=12dp, avatarBottom=56dp), which match `PostCard`'s avatar layout. No per-cluster geometry override needed.

This unblocks `nubecita-m28.2` Section A (PostCard integration was the part that wasn't shipped in PR #77's primitives-only scope).

## Risks / Trade-offs

- **Mapper surface area grows.** A new `toFeedItemUiOrNull` entry point + the existing `toPostUiOrNull` private helper means two functions where there was one. Mitigation: clear naming, single-call-site rule for the entry point in the repository, mapper unit tests cover both branches.
- **`FeedScreenViewState.Loaded.feedItems` instead of `posts` is a breaking shape change.** All consumers of the `Loaded` viewState (FeedViewModel projection, FeedScreen LazyColumn, screenshot tests, fixtures) need updating in lockstep. Mitigation: single-PR landing of the data-shape change; spec-driven tests catch regressions.
- **No video on root + parent in clusters.** Documented non-goal but worth flagging in the PR body so reviewers don't expect parity. Real-world impact small (root + parent video is uncommon).
- **Tap targets are no-ops in v1.** Users tap the cluster root / parent / leaf / fold and nothing happens. Tracked: post-detail screen lands later, then this PR's `onTap` callbacks get hooked up. Acceptable for v1 because the alternative — a transient toast — isn't better UX.

## Migration Plan

Single-PR landing. No phased rollout because the data-model change (Loaded.posts → Loaded.feedItems) has to land coherently with the mapper change and the FeedScreen dispatch, and the openspec workflow expects spec deltas alongside implementation.

Order of operations within the PR:

1. Add `:data:models/FeedItemUi.kt`.
2. Update `FeedViewPostMapper`: refactor existing `toPostUiOrNull` to private helper, add new `toFeedItemUiOrNull` entry, mapper unit tests.
3. Update `FeedScreenViewState.Loaded` → `feedItems`.
4. Update `FeedViewModel`'s state-to-viewstate projection.
5. Add `connectAbove` / `connectBelow` to `PostCard`; existing `PostCard` screenshots unchanged (defaults preserve old behavior); two new screenshots for parent-role variant.
6. Add `:designsystem/component/ThreadCluster.kt` with previews + screenshot tests.
7. Update `FeedScreen.LoadedFeedContent` to dispatch on `FeedItemUi`; previews updated.
8. Add a `FeedScreen` screenshot test that includes both `Single` and `ReplyCluster` items.

Each step builds incrementally; intermediate states compile but only step 8's commits exercise the full integration.

## Open Questions

- **`ThreadCluster.onRootTap` / `onParentTap` / `onLeafTap` / `onFoldTap` callback shape.** v1 wires all four to no-ops. When post-detail lands, do we want all four to navigate to the post-detail of the *leaf*? Or root → root's detail, parent → parent's detail, leaf → leaf's detail, fold → leaf's detail? Probably the latter (each tap navigates to that tapped post's thread view) but punt the actual decision to when the post-detail epic lands.
- **`PostCard` cluster-context styling.** Should root + parent posts in a cluster render slightly differently from a top-level `Single` (e.g., subdued action row, smaller avatar)? bsky.app's web client renders all three at full size with normal action rows. v1 matches that. If the visual ends up too "loud" relative to neighboring `Single`s, a `compact: Boolean` flag on `PostCard` is one knob; defer the decision until we look at the live rendering.
- **Parent post action interactions.** The cluster's parent and root posts are real posts with stats and viewer state. Their like / repost / reply buttons work as expected in v1 (they're regular `PostCard`s with full callbacks). If users find tapping a like on the root mid-cluster confusing, the cluster could pass action callbacks `null` for root + parent. Defer; observe.
