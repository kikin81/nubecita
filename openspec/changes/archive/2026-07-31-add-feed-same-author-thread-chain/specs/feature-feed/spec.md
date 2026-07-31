## ADDED Requirements

### Requirement: Feed mapping produces `SelfThreadChain` for consecutive same-author self-replies

The system's `:feature:feed:impl` mapping layer SHALL include a top-level pass `internal fun List<FeedViewPost>.toFeedItemsUi(): ImmutableList<FeedItemUi>` that runs after per-entry projection (`FeedViewPost.toFeedItemUiOrNull`) and groups consecutive feed entries into `FeedItemUi.SelfThreadChain` instances.

Two consecutive feed entries `e[i-1]` and `e[i]` SHALL link into a chain if and only if **all** of the following hold:

1. `e[i].reply != null`
2. `e[i].reply.parent` is a `PostView` (not `BlockedPost`, not `NotFoundPost`, not the open-union `Unknown` fallback)
3. `e[i].reply.parent.author.did == e[i].post.author.did` (same-author self-reply)
4. `e[i].reply.parent.uri == e[i-1].post.uri` (the link is unbroken in the wire response — strict)
5. `e[i-1].reason !is ReasonRepost` AND `e[i].reason !is ReasonRepost` (reposted entries cannot be chain links)
6. `e[i-1].toPostUiOrNull()` and `e[i].toPostUiOrNull()` both return non-null `PostUi` values (per the existing mapper-purity contract)

A chain SHALL be a maximal run of linked entries (size ≥ 2). Non-linked entries SHALL flow through the existing per-entry projection paths (`FeedItemUi.Single` / `FeedItemUi.ReplyCluster`) unchanged.

#### Scenario: Three consecutive same-author self-replies project to one SelfThreadChain

- **WHEN** the mapper receives a page of three `FeedViewPost` entries `[A.post1, A.reply2, A.reply3]` where `reply2.reply.parent.uri == post1.uri`, `reply3.reply.parent.uri == reply2.uri`, all share `author.did = A`, and none has a `ReasonRepost`
- **THEN** `toFeedItemsUi` returns one `FeedItemUi` whose type is `SelfThreadChain` and whose `posts.size == 3`.

#### Scenario: A broken link splits the chain

- **WHEN** the mapper receives `[A.post1, A.reply2, B.replyX, A.reply3]` where `A.reply3.reply.parent.uri != B.replyX.post.uri`
- **THEN** `toFeedItemsUi` returns three items: a `SelfThreadChain` of `[A.post1, A.reply2]`, then `Single(B.replyX)` (or `ReplyCluster`, depending on `B.replyX.reply`), then `Single(A.reply3)` (or `ReplyCluster`). The presence of the cross-author entry between the two `A` entries breaks the chain rule's adjacency requirement.

#### Scenario: Reposted entries cannot be chain links

- **WHEN** the mapper receives `[A.post1, A.reply2, A.reply3]` where `A.reply2` carries `reason = ReasonRepost`
- **THEN** `toFeedItemsUi` returns three `Single` items (or whatever the per-entry projection yields). The `ReasonRepost` on `A.reply2` disqualifies the link to `A.post1` and the link from `A.reply3`, regardless of URI matching.

#### Scenario: Same author but parent URI doesn't match prev produces no chain

- **WHEN** the mapper receives `[A.post1, A.post2, A.post3]` where `post3.reply.parent.uri == post1.uri` (skipping `post2` in the wire) but all three share `author.did = A`
- **THEN** `toFeedItemsUi` returns three `Single` items. Strict link rule rejects skip-ahead chains because the wire ordering doesn't form an unbroken parent → child chain (per design Decision 1).

#### Scenario: Per-entry projection paths are unchanged for non-chain entries

- **WHEN** the mapper receives a page where no two consecutive entries satisfy the chain link rule
- **THEN** `toFeedItemsUi` returns the same `ImmutableList<FeedItemUi>` that the previous per-entry pass produced, in the same order, with no item replaced or reordered.

### Requirement: Page-boundary chain merge preserves chains across pagination cuts

The system's `FeedViewModel` SHALL merge chains across pagination boundaries in the `LoadMore` reducer step (appending a new page). The VM MUST attempt to absorb the existing tail of `feedItems` into the incoming page's first feed item before appending, so an arbitrary cursor cut never visually splits a self-thread chain.

The merge runs only in `LoadMore`. `applyInitialPage` and `Refresh` REPLACE `feedItems` entirely, so there is no existing tail to merge — chain detection within the new page is already complete via the page-internal `toFeedItemsUi` projection.

The merge is a single-step extension, not an iterative loop. The page-internal projection has already grouped consecutive linked entries within the new page into one `SelfThreadChain` at the head; the boundary merge prepends the existing tail's posts to that head item. Subsequent new-page entries are unaffected — the strict link rule's adjacency requirement is preserved by construction.

The merge logic operates over both projected `FeedItemUi` values AND the wire-level `FeedViewPost` data (for the `reply.parent.uri` check). The new `TimelinePage` carrier MUST expose both surfaces. The merge MUST also strip a leading cursor-resync overlap (a new-page wire entry whose `post.uri` matches the existing tail's leaf URI) before running the link check, so the chain extends across the overlap rather than rejecting and rendering visually split.

#### Scenario: Chain extends across a pagination boundary

- **WHEN** `feedItems = [Single(A.post1), Single(A.reply2)]` (these two formed two `Single`s because they appeared on different pages — page 1 ended on `A.post1`, page 2 begins with `A.reply2`)
- **AND** the existing tail-to-head link rule is satisfied (`A.reply2.reply.parent.uri == A.post1.uri`, same author, no reposts)
- **WHEN** the new page projects to `[Single(A.reply2), Single(A.reply3), Single(B.unrelated)]` and `A.reply3` further extends the chain
- **THEN** after the merge step, `feedItems` SHALL contain `[SelfThreadChain([A.post1, A.reply2, A.reply3]), Single(B.unrelated)]` — the merge popped the existing tail, reformed a chain spanning the boundary, and processed the next entry.

#### Scenario: Chain extension stops at the first non-linking entry

- **WHEN** `feedItems = [..., SelfThreadChain([A.post1, A.reply2])]` and the new page projects to `[Single(B.unrelated), Single(A.reply3)]` where `A.reply3.reply.parent.uri == A.reply2.uri`
- **THEN** the merge SHALL NOT skip over `B.unrelated` to extend the chain. The new page is appended as-is: `[..., SelfThreadChain([A.post1, A.reply2]), Single(B.unrelated), Single(A.reply3)]`.

#### Scenario: ReplyCluster tail does not extend into a chain

- **WHEN** `feedItems` ends with a `ReplyCluster` (a cross-author thread-cluster entry) and the new page's head is a `Single` whose `reply.parent.uri` matches the cluster's `leaf.id`
- **THEN** the merge SHALL leave the `ReplyCluster` intact. Chains are pure same-author by construction; a cross-author cluster preceding a same-author reply forms two distinct visual entries, not one chain.

#### Scenario: De-dupe by `FeedItemUi.key` continues to work after merge

- **WHEN** the merge produces a `SelfThreadChain` whose leaf is `A.reply2` and a subsequent `LoadMore` returns a page that re-includes `A.reply2` at its head
- **THEN** the existing `seen.add(it.key)` de-dupe step (keyed on `FeedItemUi.key`) SHALL drop the duplicate `A.reply2` entry. The chain's `key == A.reply2.id`, so the duplicate fails `seen.add` and is filtered out before merge attempts.

#### Scenario: Cursor-resync overlap at the page head extends rather than splits

- **WHEN** `feedItems` ends with `Single(A.post1)` and the new page's wire entries are `[A.post1, A.reply2, ...]` — the server replayed the existing tail's leaf as the first wire entry (cursor-resync overlap), and `A.reply2.reply.parent.uri == A.post1.uri`
- **THEN** the merge SHALL strip the leading overlap entry (`A.post1`) from both the wire and projected feed-items lists in lockstep, then run the link check against the next wire entry (`A.reply2`). Because the link rule passes, the result is `[..., SelfThreadChain([A.post1, A.reply2]), ...]` — the chain extends across the resync rather than rendering visually split with `A.post1` shown twice.

### Requirement: `SelfThreadChain` rendering uses existing `PostCard` connector flags

The system's `FeedScreen` render dispatch SHALL include a render branch for `FeedItemUi.SelfThreadChain` that iterates `chain.posts` and renders each post via `:designsystem`'s existing `PostCard` with connector flags wired by index:

- The first post (`index == 0`): `connectAbove = false`, `connectBelow = true`
- Middle posts (`0 < index < posts.lastIndex`): `connectAbove = true`, `connectBelow = true`
- The last post (`index == posts.lastIndex`): `connectAbove = true`, `connectBelow = false`

The chain SHALL render as a single LazyColumn item (one `Column` containing N `PostCard`s). No new `:designsystem` composable is introduced; the render branch composes existing primitives.

#### Scenario: Chain renders with continuous gutter line

- **WHEN** a `SelfThreadChain` of 3 posts renders inside `FeedScreen`'s LazyColumn
- **THEN** the rendered output is one LazyColumn item whose visual surface contains 3 `PostCard`s stacked vertically, joined by `Modifier.threadConnector` lines through their avatar gutters: post 0 → connector line below the avatar only; post 1 → connector lines both above and below; post 2 → connector line above the avatar only.

#### Scenario: Quote-post embed does not collide with the gutter connector

- **WHEN** a chain renders a middle post that carries a `Record` or `RecordWithMedia` embed (a quote post)
- **THEN** the quote-post chrome (`PostCardQuotedPost`) renders inside the body content slot to the right of the avatar gutter, while the threadConnector line draws inside the gutter. The two surfaces SHALL NOT visually overlap. A screenshot fixture for "chain with quote-post middle" is the regression contract that locks this property.

### Requirement: Existing `Single` and `ReplyCluster` projection paths remain byte-for-byte unchanged

The introduction of chain detection SHALL NOT modify the existing per-entry projection or render paths for `FeedItemUi.Single` or `FeedItemUi.ReplyCluster`. Existing feed unit tests MUST pass without modification, and existing feed screenshot baselines (every fixture except the new same-author-chain fixtures) MUST stay byte-for-byte identical.

#### Scenario: Existing feed unit tests pass unchanged

- **WHEN** `./gradlew :feature:feed:impl:testDebugUnitTest` runs after this change merges
- **THEN** every existing test method SHALL pass without source-level modification.

#### Scenario: Existing feed screenshot baselines unchanged

- **WHEN** `./gradlew :feature:feed:impl:validateDebugScreenshotTest` runs after this change merges
- **THEN** every fixture that existed before this change SHALL match its baseline exactly. Only NEW fixtures (the same-author-chain renderings) introduce new baselines.
