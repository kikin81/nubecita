# Design

## Context

See `proposal.md` for motivation. The full approved design, including the facts
verified against the upstream lexicons and the official client's source, is
`docs/superpowers/specs/2026-11-21-thread-viewing-design.md`; the task-level plan is
`docs/superpowers/plans/2026-11-21-thread-viewing.md`. This file records the decisions
only.

The server already numbers and orders an author's thread: `opThreadPostIndex` /
`opThreadPostCount` arrive on feed entries, quoted record views and thread V2 items,
and V2 sorts the author's next post to the front of each reply level. atproto-kotlin
9.17.1 exposes the feed and quote fields but nothing under `app.bsky.unspecced`.

## Goals / Non-Goals

**Goals:**
- One source for the numbers on every surface.
- Post detail order decided by the server.
- Post detail keeps opening if the experimental endpoint changes.

**Non-Goals:**
- Anything listed under Non-goals in the proposal.

## Decisions

1. **Post detail moves to `getPostThreadV2`** rather than ordering v1 in the app. v1
   has no numbers and no author-first order, and a locally counted chain can disagree
   with the feed. Alternative rejected: v1 plus numbers passed from the feed, which
   fails from notifications, search and deep links.
2. **The number lives on the post model** (`PostUi.threadPosition`,
   `QuotedPostUi.threadPosition`), created through one factory that applies the
   validity rule. It is a server fact about the post, identical on every surface.
3. **Feed cache stores the number per feed-item member**, not on `cached_post`, which
   several writers share and some cannot fill. The entry context blob also keeps the
   two keys so a local re-projection does not lose them.
4. **Request values `below = 10`, `branchingFactor = 1`, `sort = top`**: the official
   app's linear-view values, the ones the server is exercised with.
5. **Read-more rows are thread items**, derived at map time from `moreReplies` /
   `moreParents`; the "other replies" marker is stored as a trailing body-less
   snapshot row.
6. **v1 stays as a fallback and for the video comments sheet.** The fallback covers
   decode failures and unknown-method errors only and always logs.
7. **The opened post is pinned by holding ancestors back until it has been placed**,
   relying on the list keeping its first visible item by key, plus trailing space when
   the tail is short.
8. **The SDK is built locally first** (mavenLocal, `-PuseMavenLocal=true`) and
   released once the generated API is proven against real responses. The version bump
   is the last commit before the stack merges.

## Risks / Trade-offs

- [The V2 endpoint changes without notice] → fallback to v1, logged; the SDK ignores
  unknown keys and the mapper skips unknown item kinds.
- [Children 4 and 5 are red on CI until the SDK release] → children 1 to 3 do not
  depend on it; the stack does not merge before the release.
- [Thread lines change existing post detail screenshots] → all such baseline changes
  land in the last child only, rendered by the baseline bot.
- [A long thread's last returned post might not report more replies] → checked
  against a real thread before the repository is built; if so `below` becomes 20.

## Migration Plan

Two additive Room auto-migrations (12 → 13 feed member columns, 13 → 14 thread
snapshot columns). Rows written before them read as "no number". No rollback step
beyond reverting the stack: the columns are nullable and unused by older code.
