## Why

Bluesky surfaces same-author chains — a creator replying to themselves N times in a row — as a single visually-connected block: each post sits flush above the next, with one continuous vertical line running through the avatar gutters that ties the chain together. Today, even though `:designsystem`'s `PostCard` already accepts `connectAbove` / `connectBelow` flags and `Modifier.threadConnector` is shipped (PR #77), nothing in the feed pipeline detects same-author chains across feed entries — so consecutive posts by the same author still render as visually-disconnected cards. This is the only m28.2-Section-B gap and the last unfinished child of the home-feed epic (`nubecita-m28`).

Cross-author reply clusters (m28.3) were the harder structural case (root + parent + leaf inside one LazyColumn item, with optional `ThreadFold`). Same-author chains are simpler — the wire model already gives us every post in the chain as its own feed entry — but the detection lives at the feed-page level, not at the per-entry level. That's the work this change ships.

## What Changes

### Data model layer (`:data:models`)

- Add a third `FeedItemUi` variant: `SelfThreadChain(posts: ImmutableList<PostUi>)`. Carries ≥ 2 posts, all by the same author, in chronological reply order (root-most first). The KDoc on the existing `FeedItemUi` sealed interface already names this as the planned successor; the variant slots in cleanly.
- `key` for the chain anchors on the **last** post's URI (matches `ReplyCluster`'s leaf-anchored pagination contract).

### Mapping layer (`:feature:feed:impl/data/FeedViewPostMapper`)

- New top-level pass `List<FeedViewPost>.toFeedItemsUi(): ImmutableList<FeedItemUi>` that runs after the existing per-entry `toFeedItemUiOrNull()` pass and groups consecutive entries into chains using strict link rules:
  - `e[i].reply != null`
  - `e[i].reply.parent` is a `PostView` (not `BlockedPost` / `NotFoundPost` / `Unknown`)
  - `e[i].reply.parent.author.did == e[i].post.author.did`
  - `e[i].reply.parent.uri == e[i-1].post.uri` (strict — the link must be unbroken in the wire; non-adjacent same-author posts stay separate `Single`s)
  - Neither entry has a `ReasonRepost` (a reposted self-reply is structurally weird and shouldn't get a chain treatment)
- A chain is a maximal run of linked entries; minimum size is 2.
- Per-entry projection (single posts, cross-author clusters) is unchanged — the new pass only collapses runs that satisfy the link rule.

### ViewModel layer (`:feature:feed:impl/FeedViewModel`)

- Page-boundary chain merging in `applyInitialPage` and the `LoadMore` merge step. When a new page arrives, attempt to extend the previous tail (if it's a `Single` or `SelfThreadChain` whose last post matches the strict-chain rules of the new page's first entry) into a single chain before appending. Arbitrary cursor boundaries don't dictate visual chain boundaries.
- Existing `findPost` and effect-dispatch helpers gain a `is FeedItemUi.SelfThreadChain ->` branch (the sealed-interface-with-exhaustive-when contract from `FeedItemUi`'s KDoc).

### Render layer (`:feature:feed:impl/FeedScreen`)

- New render branch `is FeedItemUi.SelfThreadChain ->` renders each post in the chain via the existing `:designsystem` `PostCard` with connector flags wired by index (first: `connectBelow=true`; middle: both true; last: `connectAbove=true`). No new design-system component — `Modifier.threadConnector` machinery already does the work via PostCard's existing flags.
- Quote-post embed compatibility: when a self-thread-chain post carries a `Record` / `RecordWithMedia` embed, the connector line continues through the avatar gutter as normal; the quoted-post chrome sits inside PostCard's content slot, which is to the right of the gutter, so the two surfaces don't overlap. Verified via screenshot fixture.

### Tests

- Mapper unit tests: 3-post chain, 2-post chain, broken chain (different author at the link point), reposted-link rejection, mixed `Single` / `SelfThreadChain` / `ReplyCluster` ordering preserved.
- VM unit tests: page-boundary merge happy path, page-boundary merge no-match (chain doesn't extend), `findPost` resolves URIs inside the chain.
- Screenshot fixture: a 3-post same-author chain in the feed showing the continuous gutter line; a chain whose middle post carries a quote-post embed verifying connector ↔ quote-chrome non-collision.
- Existing feed unit tests + screenshot baselines for `Single` / `ReplyCluster` MUST stay byte-for-byte unchanged. The detection is additive; non-chain entries flow through the unchanged `Single` / `ReplyCluster` paths.

## Capabilities

### New Capabilities

None — the `feed-same-author-chain` behavior is part of the existing `feature-feed` capability surface.

### Modified Capabilities

- `data-models`: `FeedItemUi` gains a third sealed variant `SelfThreadChain`. Existing `Single` / `ReplyCluster` shapes are unchanged. Sealed-interface exhaustiveness forces every render dispatch site to handle the new variant — that's by design (the FeedItemUi KDoc explicitly notes this).
- `feature-feed`: feed-page projection produces `SelfThreadChain` entries when consecutive feed entries satisfy the strict-chain link rule. Page-boundary chain merging is added to the LoadMore / initial-page reducer. Existing per-entry projection (Single / ReplyCluster) is unchanged.

## Impact

- **Affected modules**: `:data:models` (new sealed variant), `:feature:feed:impl` (mapper top-level pass, VM merge logic, FeedScreen render branch, screenshot fixtures).
- **Affected specs**: `data-models` (delta — new variant), `feature-feed` (delta — chain detection + page-boundary merge contracts).
- **Out of scope for this change**:
  - Cross-author reply trees inside a chain (m28.3 already handles cross-author clusters; mixed chains are explicitly out — chains are pure same-author by construction).
  - Loose-chain detection (non-adjacent same-author posts that share a deeper ancestor): the strict `parent.uri == prev.uri` rule ships v1; if it materially under-detects in production, file a follow-up.
  - Bluesky-web's "ThreadFold-on-gap" behavior: same-author chains have every post inline by construction, nothing to elide. ThreadFold stays a `ReplyCluster`-only affordance for now.
  - Render-side primitives (`Modifier.threadConnector`, PostCard's connector flags, ThreadFold composable) — already shipped via PR #77 / PR #80.
  - Performance / cancellation refactors of the feed paging engine.
- **Dependencies**: no new library deps; uses `kotlinx.collections.immutable` already on the version catalog.
- **Backwards compatibility**: additive sealed variant with a leaf-anchored `key`. The de-dupe step in `FeedViewModel.LoadMore` keys on `FeedItemUi.key`, which still functions correctly for `SelfThreadChain` (de-dupes by leaf URI). No persisted state shape changes; no API contract changes.

## Non-goals

- **Loose-chain detection** (skip-ahead self-replies). Strict `parent.uri == prev.uri` only.
- **Cross-page chain detection beyond adjacent-page tail-to-head**. If a chain crosses two pagination boundaries (rare), the two halves merge sequentially as each page arrives.
- **ThreadFold inside the chain**. Inline-only.
- **Reposted self-replies as chain links**. A reposted entry breaks the chain (treated as standalone) — chains imply unmediated authorship, which a repost violates.
- **New `:designsystem` composables**. Render is pure reuse of `PostCard` + connector flags + `Modifier.threadConnector`.
