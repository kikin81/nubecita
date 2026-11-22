# Thread viewing: post numbers and ordered post detail

Date: 2026-11-21 · Status: design approved 2026-11-21 · Part 1 of 2 (part 2, composing
threads, is designed separately afterwards)

## Intent

The official Bluesky app marks posts that belong to an author's thread with a small
`1/5` counter, and opening any of them shows the whole thread in order: 1, 2, 3 … 5,
then everyone else's replies. Nubecita shows no counter, and its post detail renders
whatever `getPostThread` returns, which is neither numbered nor ordered with the
author's posts first. Long threads are also cut short, because the call uses the
default reply depth of 6.

The user wants parity with the official app: the counter wherever the official app
shows it, and a post detail page where "all the posts in the thread are rendered in
order". Composing threads is a separate project that follows this one.

## Goals

1. **Thread counter.** A small pill reading `3/5` (spoken as "Post 3 of 5") on every
   post the server says is part of an author's thread, in three places: feed cards
   (Following, custom feeds, list feeds, profile tabs), quoted-post embeds, and every
   thread post in post detail.
2. **Ordered post detail.** Opening any post of a thread shows the author's thread
   contiguous and in order: earlier thread posts above the opened post, later ones
   directly below it, other people's replies after. The opened post sits at the top of
   the screen; earlier posts are reached by scrolling up. Thread lines follow the
   official app (section 5).
3. **Long threads stay reachable.** When the server returns only part of a thread or
   of a reply chain, a "read more" row continues it.
4. **Numbers survive a cold start.** The feed cache and the thread cache keep the
   numbers, so a cached feed or thread renders the pill before the network answers.

## Non-goals

- Composing threads (part 2).
- A tree / indented reply view, or a sort picker for replies.
- Loading the rest of a long thread in place. "Read more" opens the next post as its
  own post detail page, as the official app does.
- The pill on surfaces whose responses carry no numbers: search results,
  notifications, bookmarks, the video feed.
- Moving the video comments sheet (`PostCommentsViewModel`) to the new endpoint. It
  keeps today's `getPostThread` path untouched. Follow-up issue.
- Making the pill tappable.
- Threadgate UI from the new response's `threadgate` field.

## Anti-goals

- **Numbers computed by the app.** Every index and count comes from the server. The
  one derivation allowed is the feed-context rule below, which is exact, not a guess.
- A feed pill and a post detail pill that disagree for the same post.
- Post detail failing to open because the experimental endpoint changed.
- Any pixel change to a card that has no number. The card-level baselines of
  `PostCard`, `FocusPostCard` and the quoted-post card must stay byte-identical when
  the number is absent. Post detail **screen** baselines are a different matter: they
  are expected to change wherever a thread has ancestors or reply chains, because of
  the thread lines.
- Reordering done in the app. Post detail renders the server's order as given.

## Constraints and facts (verified 2026-11-21)

- **The server sends the numbers.** `opThreadPostIndex` (1-based) and
  `opThreadPostCount` are optional integers on `app.bsky.feed.defs#feedViewPost`,
  `app.bsky.embed.record#viewRecord` and `app.bsky.unspecced.defs#threadItemPost`.
  They are present only for posts in the "OP thread": the unbroken chain of
  self-replies by the root author, starting at the root. They are not on `postView`
  and not in `app.bsky.feed.getPostThread`.
- **atproto-kotlin 9.17.1 already exposes the first two** (`FeedViewPost` and
  `RecordViewRecord` have `opThreadPostIndex` / `opThreadPostCount`). Nubecita reads
  neither today.
- **atproto-kotlin has no `app.bsky.unspecced.*` types at all.** The namespace is
  left out of `generator/lexicons.json` on purpose.
- **`app.bsky.unspecced.getPostThreadV2`** (current upstream lexicon): params
  `anchor`, `above` (default true), `below` (default 6, max 20), `branchingFactor`
  (default 10, max 100), `sort` (`newest` / `oldest` / `top`, default `oldest`).
  Returns a **flat** `thread` list of `{uri, depth, value}` (anchor depth 0, parents
  negative, replies positive), plus `threadgate` and `hasOtherReplies`.
  `threadItemPost` carries `post`, `moreParents` (bool), `moreReplies` (int,
  best-effort), `opThread`, the two numbering fields, `hiddenByThreadgate`,
  `mutedByViewer`. Other item kinds: `threadItemNotFound`, `threadItemBlocked`
  (with `author`), `threadItemNoUnauthenticated`.
- **The server already does the ordering and the numbering** (read from
  `packages/bsky/src/views`): OP-thread posts are sorted to the front of each reply
  level, and the index and count are taken from the complete OP thread, not from the
  slice that `below` returned. A post is `3/40` even when only 11 posts came back.
- **`getPostThreadOtherV2`** (`anchor` only) returns the replies the main call
  leaves out: muted, hidden by threadgate, hidden by tags. It is called when
  `hasOtherReplies` is true.
- **The endpoint is labelled experimental** ("WILL change without notice"). The
  official app ships on it today with `below = 10, branchingFactor = 1` for its
  default (linear) view.
- **Today's repository** (`DefaultPostThreadRepository`) calls v1 with default depth,
  flattens to `RawThreadEntry(kind, uri, depth, authorDid, post)`, stores that list in
  the thread snapshot cache (`thread_snapshot_item`, bodies in `cached_post`) and maps
  it to `ThreadItem` with the viewer's current moderation prefs on every read. The
  flat-with-depth shape already matches V2.
- **Feed cache:** `feed_item` + `feed_item_member` (one row per post in an item) +
  `cached_post` (the `PostView` blob, which has no numbering). Room is at version 12.
- **Official client's validity rule** (`ThreadItemPostNumber`, `feed-manip.ts`): show
  the number only when `index >= 1 && count >= 1 && index <= count`.
- **Official client's feed-context rule:** a feed entry numbers only its own post. For
  the root and parent it renders as context, it uses root = `1`, parent = `index − 1`,
  same count. This is exact: a post at index *i* of an unbroken chain from the root
  has the chain's post *i − 1* as its parent and post 1 as its root.

## Approaches

**A. Move post detail to `getPostThreadV2` (chosen).** Add the endpoint to
atproto-kotlin, release, bump. Order and numbers come from the server, the same source
as the feed pill. Costs: a cross-repo SDK release first, an endpoint that may change,
and the thread cache has to store new fields.

**B. Stay on v1 and order in the app.** Reorder replies so the root author's
self-replies come first, count the chain locally, raise `depth`. No SDK work, but the
count is only as complete as the fetch, so post detail can say `3/7` where the feed
says `3/12`; deep fetches on busy threads are large (v1 has no branching limit); and
it is logic to delete once V2 graduates.

**C. v1 plus numbers passed in from the feed (dropped).** Breaks when post detail is
opened from a notification, search or a deep link, and creates two sources of truth.

The user picked **A**: one source for the numbers, and it follows the standing
preference for atproto APIs over app-side logic.

## Design

### 1. The number on the models

- New `:data:models` type `ThreadPositionUi(index: Int, count: Int)`, `@Immutable`,
  with a single factory that applies the validity rule and returns null otherwise.
  Every producer goes through that factory.
- `PostUi.threadPosition: ThreadPositionUi? = null` and
  `QuotedPostUi.threadPosition: ThreadPositionUi? = null`. Defaulted so existing
  fixtures and construction sites compile unchanged. The number is a server fact
  about the post within its thread; it is the same value whichever surface delivered
  it, so it lives on the post model rather than on the feed item.

### 2. The pill

- New design-system component (working name `ThreadPositionBadge`): a rounded pill,
  `labelSmall`, tabular figures, text `onSurfaceVariant`, container
  `surfaceContainerHigh` (the "raised affordance" role, as day-separator chips use).
  Not interactive. `outline` is not used for the text (fails AA).
- **Placement:** inline at the end of the post text, on the last line, as the official
  app does. A post with no text shows the pill on its own line where the text would
  be, **above** any image, video or quote embed. It appears in `PostCard`,
  `FocusPostCard` and `PostCardQuotedPost`.
- The pill follows the text with trailing whitespace and newlines trimmed, so a post
  ending in `\n` does not leave a blank line above it. Appending it must not shift
  or widen any facet's click range, including a link facet that ends the text.
- **Accessibility:** content description "Post 3 of 5" (new plural-free string with
  two integer arguments), translated to es-419 and pt-BR in the same commit.
- The visible form is `index/count` with no spaces, from a string resource.

### 3. Feeds and quotes (no SDK change)

- `FeedViewPostMapper` (and the profile `AuthorFeedMapper`) read the two fields from
  each `FeedViewPost` and set `threadPosition` on that entry's post.
- For a `ReplyCluster`, the root and parent get the feed-context rule (root `1`,
  parent `index − 1`, same count) when the leaf is numbered. If the same post also
  arrives as its own numbered entry in the page, the server's number wins.
- `SelfThreadChain` members are separate feed entries, so each carries its own
  number.
- The quoted-post mapper reads the fields from `RecordViewRecord`. The quote lives
  inside the `PostView` blob, so the feed cache keeps it with no schema change.
- **Feed cache:** `feed_item_member` gains nullable `op_thread_index` and
  `op_thread_count`. `FeedProjectionWriter` writes them; the projection read puts
  them back on the member's `PostUi`. Additive, so `@AutoMigration`. Rows written
  before the migration have no number until the feed refreshes.

### 4. SDK: add thread V2 to atproto-kotlin (prerequisite)

In `~/code/atproto-kotlin`: add `app.bsky.unspecced.defs`,
`app.bsky.unspecced.getPostThreadV2` and `app.bsky.unspecced.getPostThreadOtherV2` to
`generator/lexicons.json`, install the lexicons, regenerate, and update the
drift-detector note that says the namespace is excluded. Nothing else in the
namespace is opted in.

**Local first, release last (user decision, 2026-11-21).** The SDK change is built
on a branch in atproto-kotlin and published to mavenLocal; Nubecita consumes it with
`-PuseMavenLocal=true` while the feature is built. The SDK pull request and release
happen only once the generated API has been proven against real thread responses.
Consequences:

- CI cannot see mavenLocal, so the children that use the V2 types stay red on CI
  until the SDK is released and the bump child pins the released version.
- The stack does not merge before that release. The bump child is the last thing
  finalised.

### 5. Post detail on V2

- **Request:** `anchor = <opened post>`, `above = true`, `below = 10`,
  `branchingFactor = 1`, `sort = top`. These are the official app's linear-view
  values; staying on the parameters the server is exercised with is the safest use of
  an experimental endpoint.
- **What that returns:** every ancestor of the opened post; the opened post; all of
  its direct replies with the author's next thread post first; under each reply, its
  single top reply, up to 10 levels. Opened at post 1 of a 5-post thread: 1, 2, 3, 4,
  5, then other replies to post 1. Opened at post 3: 1 and 2 above, 3, then 4 and 5,
  then other replies to post 3.
- **Mapping:** `RawThreadEntry` gains `opThreadIndex`, `opThreadCount`, `moreReplies`
  and `moreParents`. Depth below 0 is an ancestor, 0 the focus, above 0 a reply. The
  list order is the server's; the mapper does not sort. `threadItemBlocked` and
  `threadItemNotFound` map to the existing tombstones.
- **Read-more rows** (replacing the reserved, never-emitted `ThreadItem.Fold`):
  - A post with `moreReplies > 0` whose next row is not its own reply gets a
    "Read more replies" row after it. Tapping opens that post in post detail. This is
    what continues a thread longer than 11 posts. The rule reads only `moreReplies`
    and the next row's depth; it does not care whether the cut-off post is the
    author's thread post or someone else's reply, and both route the same way.
  - The top-most ancestor with `moreParents = true` gets a "Read earlier posts" row
    above it, opening that ancestor.
  - When `hasOtherReplies` is true, a "Show more replies" row ends the list. Tapping
    calls `getPostThreadOtherV2` and appends the result in place.
- **Thread lines** follow the official app's rules (`getThreadPostUI` in social-app),
  for every thread, not only numbered ones:
  - Each ancestor is joined to the row below it, and the line runs into the opened
    post's avatar from above. `FocusPostCard` gains a `connectAbove` flag for this.
  - **No line leaves the opened post downward.** Its body is full width, so there is
    no gutter to carry one, and the official app draws none. Opened at post 3 of 5,
    posts 1 → 2 → 3 are joined, 4 → 5 are joined, and the pill is what ties 3 to 4.
  - Below the opened post, a reply is joined to the next row when that row is its
    direct reply (`next.depth == depth + 1`). This joins the rest of the author's
    thread and makes the one-deep reply chains readable.
  - A "Read more replies" row continues the line of the post it follows, as
    `ThreadFold` already does in feed clusters.
- **Where the page opens:** the opened post is pinned to the top of the list on every
  path (cached snapshot, focus-only cache, fresh load, refresh), and ancestors are
  reached by scrolling up, as in the official app. Post detail has no scroll-to-focus
  today, so the list starts at the root-most ancestor whenever ancestors are in the
  first emission. With V2 the ancestors are the earlier thread posts, so without the
  pin, opening post 30 of 40, or following "Read more replies", would land on post 1.
  The pin must not produce a visible jump when ancestors arrive after the focus, and
  must not fight the user once they have scrolled.
  - The focus position is known before the list first composes (the initial list
    index, or a scroll request applied in the same frame), never a scroll effect
    that runs after the first layout.
  - A list clamps its scroll so the last row meets the bottom of the viewport. When
    the opened post has ancestors but little below it, the list needs enough
    trailing space for the post to reach the top. No trailing space is added when
    the opened post is the first row.
- **Thread cache:** `thread_snapshot_item` gains nullable `op_thread_index`,
  `op_thread_count`, `more_replies`, `more_parents` (`@AutoMigration`). The
  `hasOtherReplies` flag is stored with the snapshot (the plan keeps it as a trailing
  body-less row rather than a column). Cache-first open is otherwise
  unchanged. Opening a post never seen in a thread shows the cached focus without a
  pill until the refresh lands; that is acceptable.
- **Fallback:** if the V2 call fails for a reason other than connectivity (unknown
  method, a response the SDK cannot decode), the repository falls back to today's v1
  fetch and mapper. Post detail then opens without numbers or guaranteed order. The
  v1 path stays in the codebase anyway for the comments sheet. A fallback is never
  silent: each one is logged with its cause, so lexicon drift in the SDK shows up in
  a debug build instead of hiding behind a working page.
- **Repository surface:** `PostThreadRepository` keeps `getPostThread` as is (v1,
  used by the comments sheet). Post detail moves to the V2-backed read; whether that
  is a new method or a second implementation is the builder's choice, as long as the
  comments sheet's behaviour does not change.

### 6. Bench flavor

The bench build has no network, so the fixtures carry the feature:
`BenchTimelineDto` gets the two numbering fields and one author thread appears in the
bench timeline (entry numbered `1/5`, plus a cluster showing a later post);
`BenchFakePostThreadRepository` serves that thread ordered and numbered, with one
"read more" case. The device pass runs on these.

## Delivery

One bd epic, one `gh stack`, one release. Suggested children, in order:

1. `ThreadPositionUi` + the pill + rendering in the three cards (previews, screenshot
   tests, strings in three languages).
2. Feed and quote mapping, including the feed-context rule.
3. Feed cache columns, projection write/read, bench timeline fixture.
4. Thread V2 fetch, mapper, thread cache columns, fallback, other replies, bench
   thread fixture. The atproto bump is the last commit on this child, made once the
   SDK is released.
5. Post detail rows, thread lines and the pin.

Children 1 to 3 do not depend on the SDK. Children 4 and 5 are built against the
mavenLocal SDK and pass CI only after the SDK release (section 4).

Screenshot baselines stay readable per child: card-level baselines land with child
1, and every post detail screen baseline that changes because of thread lines lands
with child 5 and nowhere else. Baselines come from the `update-baselines` bot, not
from a local render.

## Testing

- Unit: the validity factory; feed mapper numbering and the feed-context rule; V2
  mapper (order preserved, kinds, read-more placement); snapshot store round-trip of
  the new fields; the v1 fallback; ViewModel handling of "Show more replies".
- Room: migration tests for both schema bumps (`run-instrumented` label).
- Screenshots: the pill in `PostCard` (with text, without text, long text wrapping to
  a full last line), `FocusPostCard`, the quoted card; a post detail thread with
  lines and each read-more row. Card-level baselines for un-numbered posts must not
  change; post detail screen baselines with ancestors or reply chains will.
- Opening position: a ViewModel or UI test per path (snapshot hit, focus-only hit,
  cold load) that the focus is the first visible row, and the device pass opens a
  mid-thread post from the feed and from a "Read more replies" row.
- Device: bench build on the foldable for the fixtures; production build against a
  real thread longer than 11 posts to confirm the continuation row.

## To confirm while building

- That the last returned thread post really reports `moreReplies > 0` when the
  thread is longer than `below`. If it does not, raise `below` to 20 and note the
  limit.
- That `sort = top` gives a sensible reply order for a signed-in viewer.
- That atproto-kotlin's generator handles a non-`main` union def (`#threadItem`)
  inside a query lexicon.

## Calls made by the designer (not asked of the user)

Each follows from "match the official app" unless noted. The user reviews these when
approving the doc.

1. Request values `below = 10`, `branchingFactor = 1`, `sort = top`.
2. Thread lines on every thread in post detail, with none below the opened post.
3. The opened post pinned to the top; earlier posts above it.
4. "Read more" opens a new post detail page rather than loading in place.
5. Pill inline at the end of the text; own line when the post has no text.
6. Pill colours: `surfaceContainerHigh` container, `onSurfaceVariant` text.
7. v1 kept as a fallback for post detail (not from the official app; it is cheap
   insurance against the experimental label, since v1 stays for the comments sheet).
8. The comments sheet stays on v1.
9. One epic and one stack, with the SDK release as a prerequisite, rather than
   shipping the feed pill first in its own release.

## Left to the builder

- How the opened post is pinned (initial list index plus key anchoring, or deferring
  ancestors as the official app does), within the constraints in section 5.
- Names (`ThreadPositionBadge`, the new `ThreadItem` variants, column names).
- How the pill is placed inline (`InlineTextContent` is the expected route) and how
  it coexists with translation, highlighted text and "show more" truncation.
- Whether the two Room bumps are one version or two.
- How `threadItemNoUnauthenticated` renders (the not-found tombstone is fine).
- Exactly which failures count as "not connectivity" for the fallback.
