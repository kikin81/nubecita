## Why

Scrolling the Following feed shows what looks like the same post two or three
times in a row, each time as the top of a different card. It reads as a bug even
though every card is technically a distinct post.

The cause: the timeline returns entries in post-time order, and when several
posts are replies into the **same thread**, each becomes its own
`FeedItemUi.ReplyCluster` and re-renders that thread's root as context. N replies
to one thread therefore draw the root N times.

Measured on the production account (216 timeline entries across 9 pages):

| Measure | Count |
|---|---|
| Distinct thread roots | 180 |
| Roots appearing more than once | 23 |
| **Cluster-vs-cluster (this bug)** | **6** |
| Cluster-vs-standalone (already handled) | 17 |
| Repeats confined to a single page | 8 |
| **Repeats spanning pages** | **15** |

Worst case observed: one root with **seven** replies spread across three pages.

## What Changes

- Add a thread-root dimension to feed de-duplication: at most one feed item per
  thread root, keeping the first (newest) occurrence.
- Reposts are exempt from the drop but still register their root, matching the
  official client.
- Surface what was suppressed. The surviving card carries a count of the replies
  dropped from its thread so the content stays discoverable rather than silently
  vanishing. **This is a deliberate divergence from the official client**, which
  drops silently.
- No change to the reply filter (`nubecita-1fmx`), to `FeedViewPrefs`, or to the
  wire models.

## Non-goals

- **Merging sibling replies into one expanded thread card.** Considered and
  rejected: 15 of 23 repeated roots span pages, and merging across pages would
  require mutating a card the user has already scrolled past. Same-page-only
  merging would have collapsed the worst case from seven cards to three, so it
  buys a third of the fix for most of the complexity.
- **Rendering later siblings context-free.** Pagination runs backward in time, so
  a sibling met later is always *older*. Showing it stripped of context places a
  stale, orphaned reply beneath newer content.
- **Persisting seen-roots across sessions or refreshes.** Refresh intentionally
  resets, so a newly-arrived reply resurfaces.
- Changing how `SelfThreadChain` is built.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `feature-feed`: adds a requirement that the feed renders at most one item per
  thread root within a session, and that the surviving item reports how many
  sibling replies were suppressed.

## Impact

- `feature/feed/impl/.../data/FeedItemDedupe.kt` — new pure pass; possible
  absorption of `dedupeClusterContext`.
- `feature/feed/impl/.../FeedViewModel.kt` — apply the pass alongside existing
  ones in the initial-load, refresh and append reducers.
- `data/models/.../FeedItemUi.kt` — a suppressed-reply count on every rendered
  variant that can survive de-duplication, `Single` included.
- `:feature:feed:impl` screenshot baselines for the new affordance.
- No dependency, DI, or database changes. Stays within the MVI / Compose
  baseline; the pass is a pure `List<FeedItemUi>` function, consistent with the
  existing dedupe helpers.
