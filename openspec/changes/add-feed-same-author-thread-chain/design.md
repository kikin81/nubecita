## Context

The Bluesky timeline often surfaces creator self-replies — `A.post1 → A.post2 → A.post3`, all by the same author, replying to themselves in chronological order. The wire model returns each as its own `FeedViewPost` entry, and the canonical Bluesky clients (web, official mobile) render them as one visually-connected block: each post sits flush above the next with one vertical line through the avatar gutter that ties the chain together.

Today, two halves of this feature already exist in nubecita:

- **Render side** — shipped via `nubecita-m28.2` Section A. `:designsystem`'s `PostCard` accepts `connectAbove: Boolean` / `connectBelow: Boolean` flags. `Modifier.threadConnector` (PR #77) draws the gutter line between flagged cards. The cross-author cluster work (`m28.3`, change `add-feed-cross-author-thread-cluster`) consumes these primitives via the inline `ThreadCluster` composable.
- **Per-entry projection** — `:feature:feed:impl/data/FeedViewPostMapper.kt` produces a `FeedItemUi.Single` or `FeedItemUi.ReplyCluster` per `FeedViewPost`. The `FeedItemUi` KDoc explicitly names a future `SelfThreadChain` variant as the planned successor (line 38–39 of `:data:models/.../FeedItemUi.kt`).

What's missing is the **detection layer**: the feed pipeline never groups consecutive entries into chains, so even when the wire model gives us a self-thread, the screen renders disconnected cards.

This change closes that gap and is the only m28-Section-B work item left in the home-feed epic (`nubecita-m28`).

## Goals / Non-Goals

**Goals:**

- Detect same-author chains across consecutive feed entries using a strict `parent.uri == prev.uri` link rule.
- Project chains into a new `FeedItemUi.SelfThreadChain` sealed variant carrying ≥ 2 same-author posts in chronological order.
- Merge chains across pagination boundaries so cursor cuts don't create visually-broken blocks.
- Render the chain via existing `:designsystem` `PostCard` + connector flags (no new design-system component).
- Preserve every existing `FeedItemUi.Single` / `FeedItemUi.ReplyCluster` screenshot baseline byte-for-byte. The detection is additive; non-chain entries route through unchanged paths.

**Non-Goals:**

- **Loose-chain detection** (skip-ahead self-replies where `e[i].reply.parent.uri != e[i-1].post.uri` even though same author). Rejected — see Decision 1.
- **ThreadFold inside a chain.** Same-author chains have every post inline; nothing to elide.
- **Cross-author posts collapsed into a chain.** Chains are pure same-author by construction. Mixed chains stay as separate `Single` / `ReplyCluster` entries.
- **Reposted self-replies as chain links.** `ReasonRepost` breaks the chain — see Decision 4.
- **New `:designsystem` composables.** Render is pure reuse of `PostCard` + `Modifier.threadConnector`.
- **Performance refactors of the feed paging engine.** Chain detection runs in the existing reducer; no changes to pagination machinery.
- **Persistence migrations.** State shape doesn't change; `FeedItemUi.key` semantics are preserved (leaf-anchored).

## Decisions

### Decision 1: Strict link rule (`parent.uri == prev.uri`), not loose same-author detection

**Choice:** Two consecutive feed entries `e[i-1]` and `e[i]` link into a chain if and only if:

1. `e[i].reply != null`
2. `e[i].reply.parent` is a `PostView` (not `BlockedPost` / `NotFoundPost` / open-union `Unknown`)
3. `e[i].reply.parent.author.did == e[i].post.author.did`
4. `e[i].reply.parent.uri == e[i-1].post.uri` — the link is unbroken in the wire response
5. Neither `e[i-1]` nor `e[i]` carries a `ReasonRepost`

**Why this over alternatives:**

- *Loose detection (drop the `parent.uri == prev.uri` check; chain any consecutive same-author self-replies)* — vulnerable to ordering surprises. If the server returns `[A.post1, A.post2, A.post3]` where `A.post3.reply.parent == A.post1` (skipping post2 in the wire), forcing them into a visual chain implies post3 follows post2. It doesn't. The honest representation is to keep them as separate `Single`s (or break into smaller chains if the data supports it). Strict detection makes the visual a faithful reflection of the wire causality.
- *Detect chains by walking the reply graph back to root (deep-link detection)* — would require N×depth reads on every page, complicates the mapper, and the server's pagination order doesn't correspond to walking-the-graph order anyway. Rejected.

The strict rule is also the simplest one that correctly handles every observed Bluesky-web rendering: their UI also displays disconnected cards when the wire ordering doesn't form an unbroken parent → child chain.

### Decision 2: New `FeedItemUi.SelfThreadChain` variant carries the whole chain as one LazyColumn item

**Choice:** Add a third sealed variant to `FeedItemUi`:

```kotlin
data class SelfThreadChain(
    val posts: ImmutableList<PostUi>, // size >= 2, same author, chronological
) : FeedItemUi {
    override val key: String get() = posts.last().id
}
```

The chain is one LazyColumn item; the screen iterates `posts` internally and applies the right `connectAbove` / `connectBelow` flags per index.

**Why this over alternatives:**

- *Keep emitting `FeedItemUi.Single` for each post and add `connectAbove` / `connectBelow` boolean fields on `Single`* — would require the screen to maintain external state (next/prev item) to know when to draw connector lines. Worse, the de-dupe + page-boundary-merge logic in `FeedViewModel.LoadMore` keys on `FeedItemUi.key`; if Single posts gain connector flags, the dedupe would need to compare both URIs and connector state, which is fragile. Rejected.
- *Make `SelfThreadChain` a `List<FeedItemUi.Single>` rather than a `List<PostUi>`* — would let each chain post carry its own `repostedBy` overlay (which `Single` has via `PostUi`), but Decision 4 already excludes `ReasonRepost` entries from chain links, so `repostedBy` inside a chain is always `null`. Keeping it `List<PostUi>` matches the `ReplyCluster` shape (root / parent / leaf are also `PostUi`) and is simpler.
- *Symmetric with `ReplyCluster`* — both variants are "one feed entry, multiple PostCards rendered as one connected block". The two render paths share the connector-flag pattern, the same de-dupe-by-leaf-URI semantic, and the same screen-level dispatch surface.

The leaf-anchored `key` matches `ReplyCluster`'s contract: scroll-position and de-dupe anchor on the post the user thinks of as "this entry in my timeline" (the most recent reply).

### Decision 3: Page-boundary merge in the VM, not in the mapper

**Choice:** The mapper does per-page chain projection: `List<FeedViewPost>.toFeedItemsUi(): ImmutableList<FeedItemUi>` runs over one page's worth of feed entries and emits the right mix of `Single` / `ReplyCluster` / `SelfThreadChain`. The VM, in `applyInitialPage` and the `LoadMore` merge step, attempts to extend the existing tail of `feedItems` into a chain with the new page's head before appending.

The merge logic:

1. Get the new page's projected `List<FeedItemUi>` from the mapper.
2. Look at the current `feedItems.lastOrNull()` (the existing tail) and the new page's `firstOrNull()` (the incoming head).
3. **If** the current tail is a `SelfThreadChain` AND the incoming head is a `Single` AND the head's `PostUi.id` corresponds to a `FeedViewPost` whose `reply.parent.uri == tail.posts.last().id` AND `reply.parent.author.did == head.post.author.did` AND no `ReasonRepost` on either: pop the tail, append a new `SelfThreadChain(tail.posts + head.post)`, then process the rest of the new page recursively (the new tail might extend further with the next entry).
4. **Else if** both tail and head are `Single` whose URIs satisfy the same link rule: pop the tail, emit `SelfThreadChain(listOf(tail.post, head.post))`, process the rest.
5. **Else**: just append the new page as-is.

The link information for the page-boundary check requires access to the head entry's `reply.parent.uri` — i.e., the wire-level `FeedViewPost`, not the projected `FeedItemUi`. The VM's merge step takes `TimelinePage` (which carries `feedItems` AND the raw `posts: List<FeedViewPost>`) and uses the wire-level data to decide.

**Why this over alternatives:**

- *Do everything in the mapper, including buffering across pages* — the mapper would need to be stateful or take "context from the previous page" as an input, breaking its pure-function contract.
- *Use a separate `FeedReducer` / `FeedMerger` helper class* — premature; one merge call site (`applyInitialPage`) plus one append call site (`LoadMore`) doesn't justify a class. Inline a private helper extension on the VM.
- *Don't merge at all; live with cursor-cut chains* — produces a glaring visual bug ("why is this 4-post thread split into two blocks?"). Arbitrary API pagination boundaries should not dictate visual UI boundaries.

The VM-side merge is small (~30 lines) and is the right architectural seam: the mapper stays a pure per-page function, and merge logic lives in the same reducer that already handles `loadMore` deduplication.

### Decision 4: `ReasonRepost` breaks the chain; reposted entries stay as standalone `Single`s

**Choice:** A `FeedViewPost` whose `reason` is `ReasonRepost` cannot be a chain link in either position (predecessor or successor). The presence of `ReasonRepost` on either `e[i-1]` or `e[i]` disqualifies the link.

**Why this over alternatives:**

- *Allow reposted entries to chain if the underlying author is the same* — semantically wrong. The user added that post to their feed because someone else (the reposter) chose to amplify it. Chains imply unmediated authorship; threading a reposted post into a same-author chain blurs whose voice the user is actually reading.
- *Allow chains where ALL entries are reposts of the same author by the same reposter* — a legitimate corner case (a reposter amplifying a self-thread), but rare enough that v1 punts. The fallback (display as separate `Single` cards each with their `repostedBy` line) is honest.

The check is one `when (reason)` test in the link rule and adds no fixture complexity to existing reposted-post tests.

### Decision 5: Connector-flag wiring in the render branch

**Choice:** The new render branch in `FeedScreen` iterates `chain.posts` with index, applying:

- `index == 0`: `connectAbove = false, connectBelow = true`
- `0 < index < posts.lastIndex`: `connectAbove = true, connectBelow = true`
- `index == posts.lastIndex`: `connectAbove = true, connectBelow = false`

The chain renders inside a single `Column` (no `LazyColumn` nesting; chains are size-bounded by feed-page size — typically ≤ 5 posts in practice). The `Column` becomes one LazyColumn item.

**Why this over alternatives:**

- *Render each post as its own LazyColumn item, keyed by URI* — would let the LazyColumn skip recompositions of unchanged posts more aggressively, but breaks the `key` contract (a chain has one leaf-anchored key, matching the `ReplyCluster` precedent) and complicates de-dupe.
- *Use a `LazyListScope` extension that yields `posts.size` items with shared connector state* — over-engineered for chains capped by feed-page size.

The `Column` approach matches `ThreadCluster` (the m28.3 cross-author render component) and shares its layout primitive. PostCard's existing `HorizontalDivider`-suppression contract (`connectBelow=true → no divider`) handles the visual transition between cards inside the chain — same as `ReplyCluster`.

### Decision 6: Quote-post embed compatibility — gutter is to the left of the embed slot

**Choice:** PostCard's body content (text + embed slot + action row) sits in a `Column` to the right of the avatar gutter. `Modifier.threadConnector` draws into the gutter region (left of the avatar), not into the body region. Quote-post embeds (`Record`, `RecordWithMedia`) render inside the body's embed slot, which is right of the gutter — the two surfaces don't overlap by construction.

The screenshot fixture for "chain whose middle post has a quote-post embed" is the regression contract that locks this property.

**Why this is worth calling out:**

The render-side primitives already enforce the geometry, but a future refactor of the embed slot (e.g., changing `PostCardQuotedPost`'s padding to overflow into the gutter for some "card overlap" effect) would silently break the chain rendering. A screenshot fixture flagged for this scenario forces the breakage to surface as a baseline diff.

## Risks / Trade-offs

- **Mapper test surface grows substantially.** The strict-link rule has many edge cases (`reply == null`, parent is `BlockedPost`, parent's author DID differs, parent URI doesn't match prev, `ReasonRepost` on either side, mixed chain + cluster + single). Every case is one unit test. Mitigated by writing a fixture-driven table-test rather than N parallel test methods.
- **Page-boundary merge is the trickiest seam.** A miswritten merge could double-render the tail post (it appears in both `feedItems.last()` and the merged chain) or drop it. Mitigated by VM unit tests covering: tail-extends, tail-doesn't-extend, tail-is-`Single`-with-no-reply, tail-is-`ReplyCluster` (chain doesn't extend across `ReplyCluster` boundaries — that's not a same-author chain).
- **Loose-chain false negatives.** The strict rule under-detects chains where the server returns out-of-order entries that happen to be same-author. If this materially hurts UX in production, file a follow-up to add loose-detection as a fallback (still gated on `parent.author.did == post.author.did`).
- **Quote-post connector ↔ embed collision.** Render-side primitives keep them apart by geometry; a future PostCard refactor could break this silently. Mitigated by the screenshot fixture (Decision 6).
- **Performance.** Chain detection is O(N) over the page's feed entries; page sizes are typically ≤ 50, so the overhead is negligible. The page-boundary merge is O(1) — just inspects the tail. No 120hz-scroll risk.

## Migration Plan

No persisted state is affected. The change is hot-reloadable: a session that started before the change and a session after the change both render correctly because:

- `FeedItemUi` is an in-memory projection rebuilt from the wire response on every `getTimeline` call.
- The new `SelfThreadChain` variant is additive; consumers of `FeedItemUi` (`FeedViewModel.findPost`, `FeedScreen` render branch, the like/repost dispatchers) all gate on `when (item)` and the new branch is required for compile-time exhaustiveness — meaning the build either compiles end-to-end or fails at the missing branch, never silently mis-renders.

There is no rollback dance: revert the change, the new variant disappears, the build still compiles (because callers' `when` branches go back to `Single` / `ReplyCluster` only).

## Open Questions

None at proposal time. Decisions 1–6 cover every architectural choice raised in the bd ticket's description and the brainstorming round. Implementation may surface concrete edge cases in the page-boundary merge that warrant another decision entry; treat those as in-flight tuning rather than spec re-opens.
