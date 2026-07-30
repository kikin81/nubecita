## Context

`FeedItemDedupe.kt` already carries two de-duplication passes, and **both key off
the leaf**:

- `dedupeClusterContext()` drops a `Single` whose URI appears as a cluster's
  `root` or `parent`.
- `dedupeByKey()` drops items sharing a `FeedItemUi.key`, which is leaf-anchored.

Neither compares **cluster against cluster**, so N replies into one thread all
survive and each re-renders the shared root.

### Measured on the production account

A temporary probe in `DefaultFeedRepository` logged the wire shape of 216
timeline entries across 9 pages:

| Measure | Count |
|---|---|
| Distinct thread roots | 180 |
| Roots appearing more than once | 23 |
| **Cluster-vs-cluster (this bug)** | **6** |
| Cluster-vs-standalone (already handled) | 17 |
| Repeats confined to one page | 8 |
| **Repeats spanning pages** | **15** |

Worst observed root, with the pages each entry landed on:

```
root=3mrtk5glh472t  n=7  replies=7  pages=[7294 x3, ffb5 x3, 6357 x1]
```

A confirmed reproduction of the reported symptom, one page apart, same author:

```
page1 #15: post=3mruq24b6bc2s  root=3mrulolaklc2m  parent=3mrupisd4uk2k
page2 #11: post=3mrupisd4uk2k  root=3mrulolaklc2m  parent=3mruoq6amok2k
```

Both share root `3mrulolaklc2m`, so that post renders twice — and
`3mrupisd4uk2k` is the *leaf* of the second cluster and the *parent* of the
first, so it renders twice as well.

The probe also confirmed the two other duplication classes already behave: a
cluster plus its own root as a standalone (`3mrupfynfss25`), and a post arriving
both reposted and original (`3mrupeuruzh2r`).

### What the official client does

`FeedTuner.dedupThreads`, `bluesky-social/social-app`,
`src/lib/api/feed-manip.ts`:

```js
for (let i = 0; i < slices.length; i++) {
  const rootUri = slices[i].rootUri
  if (!slices[i].isRepost && tuner.seenRootUris.has(rootUri)) {
    slices.splice(i, 1); i--          // drops the WHOLE slice
  } else {
    if (!dryRun) tuner.seenRootUris.add(rootUri)
  }
}
```

Keyed on `rootUri`; drops the entire slice, not just the repeated context;
`seenRootUris` is tuner state spanning pages and reset on refresh; reposts skip
the drop but still register.

## Goals / Non-Goals

**Goals:**

- One rendered item per thread root within a session.
- Match the official client's feed shape, since both users who reported the
  related reply-filter bug (`nubecita-1fmx`) compare Nubecita against it.
- Keep the suppressed content discoverable.
- Stay a pure `List<FeedItemUi>` function, consistent with the existing dedupe
  helpers and requiring no stateful tuner.

**Non-Goals:**

- Merging sibling replies into one expanded thread card.
- Rendering later siblings context-free.
- Persisting seen-roots across refreshes or app sessions.
- Any change to the reply filter, `FeedViewPrefs`, or the wire models.

## Decisions

### D1 — Drop later slices rather than merging or stripping context

Three shapes were considered for the 2nd+ reply into a thread.

| Option | Outcome | Verdict |
|---|---|---|
| Drop the slice (official) | one card per thread | **chosen** |
| Keep the reply, drop its context | all replies shown, none repeat | rejected |
| Merge siblings into one thread card | nothing lost, best reading | rejected |

Merging is the nicest outcome but the data does not support it: **15 of 23**
repeated roots span pages, and merging across pages would mean mutating a card
the viewer has already scrolled past — a visual jump, or invisible if they have
moved on. Restricting the merge to same-page siblings would have collapsed the
worst observed case from seven cards to three, buying roughly a third of the fix
for most of the complexity.

Stripping context was rejected on chronology: pagination walks backward in time,
so a sibling met later is always *older*. Rendering it context-free places a
stale, orphaned reply beneath newer content.

### D2 — First-wins, and first means newest

Retain the first occurrence in list order. On a newest-first timeline this is
always the newest reply in the thread, and every dropped sibling is older. The
rule is therefore chronologically defensible, not merely convenient.

### D3 — A pure function over the accumulated list, not a stateful tuner

The official client threads a `seenRootUris` set through a mutable tuner.
Nubecita does not need one: `FeedViewModel.loadMore` already applies the dedupe
passes to `trimmedExisting + newPage`, so a pure first-wins function over the
accumulated list spans pagination for free. `applyInitialPage` operates on a
fresh list, so refresh resets the seen set as a natural consequence rather than
an explicit step.

### D4 — Reposts are exempt from the drop but still register

Copied from the official rule. A repost is an explicit endorsement by someone the
viewer follows, so it carries its own signal even when the underlying thread has
already been seen. It still registers its root so that later plain replies into
the same thread do not stack on top of it.

### D5 — Resolve the ordering conflict with `dedupeClusterContext`

`dedupeClusterContext` states that *the cluster is canonical, drop the `Single`*,
regardless of position. `dedupThreads` states *first wins*. These disagree when a
`Single` sits **above** a cluster sharing its root.

On a newest-first timeline the reply is normally newer and therefore first, so
the two rules agree in the common case — but by luck, not design. Implementation
MUST pick one rule explicitly and pin it with a test. Since deriving a `Single`'s
thread root as its own id already covers the standalone-vs-context case, the
preferred resolution is for the new pass to **subsume** `dedupeClusterContext`,
leaving one rule instead of two that can drift apart.

### D6 — Surface the suppressed count (divergence from official)

The official client drops silently. With seven replies observed on one root, that
hides real content and gives the viewer no signal it existed. The surviving item
therefore carries a count of suppressed siblings and the feed renders an
affordance into the thread.

This is the one intentional divergence in this change, and it exists precisely
because dropping is otherwise lossy.

### D7 — `SelfThreadChain` root is an approximation

Chains do not retain the wire thread root, so the first chained post's id is
used. That post may itself be a reply, in which case the chain's true root is
higher and the approximation under-matches — two chains in the same thread would
both survive. Accepted for now: the observed duplication is
`ReplyCluster`-driven. If chains later prove to duplicate, the fix is to carry
the wire root on the chain rather than to complicate this pass.

## Risks / Trade-offs

- **Content is hidden.** One card per thread means a viewer following an active
  thread sees one reply where seven exist. That is current `bsky.app` behaviour,
  and D6 mitigates it, but it is a real product trade rather than a pure bug fix.
  Worth re-evaluating against option 3 (merge) if it feels wrong in use.
- **First-wins depends on newest-first ordering.** If a feed surface ever
  presents items in another order, "first" stops meaning "newest" and the rule
  silently becomes arbitrary. The pass is applied only to follow-scoped feeds
  today; a comment should state the assumption.
- **Cost on every append.** The pass runs over the accumulated list on each
  `loadMore`, as the existing dedupe passes already do. O(n) with n bounded by
  feed length; no new concern, but it does mean three full passes per append.
- **Over-dropping if root derivation is wrong.** Deriving a `Single`'s root as
  its own id means a standalone post reserves a thread. If that derivation is
  ever wrong, unrelated posts disappear from the feed — a much worse failure than
  the duplication being fixed. Tests must cover the negative case: unrelated
  items are never dropped.
- **The measurement is one account, one moment.** 216 entries from a single
  timeline. The same-page/cross-page split drove D1, and a very different
  following graph could shift it. The decision is recorded here so it can be
  revisited against new data rather than re-argued from scratch.
