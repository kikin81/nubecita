# Proposal

## Why

The official Bluesky app marks posts that belong to an author's thread with a `1/5`
counter and opens any of them as an ordered thread. Nubecita shows no counter, and its
post detail renders `getPostThread`'s tree, which is neither numbered nor ordered with
the author's posts first, and is cut short on long threads. Readers cannot tell a post
is part of a thread, nor read the thread in order.

## What Changes

- A thread counter pill (`3/5`, spoken "Post 3 of 5") on posts the server marks as
  part of an author's thread: feed cards, quoted-post embeds, and post detail.
- Post detail reads the thread through `app.bsky.unspecced.getPostThreadV2` and
  renders the server's order: earlier thread posts above the opened post, later ones
  directly below, other replies after.
- The opened post is pinned to the top of the screen; earlier posts are reached by
  scrolling up.
- Thread lines in post detail follow the official app: ancestors joined into the
  opened post, reply chains joined, no line leaving the opened post downward.
- "Read more replies", "Read earlier posts" and "Show more replies" rows continue a
  thread the server returned only in part.
- The feed cache and the thread snapshot cache store the numbers (two additive Room
  migrations).
- atproto-kotlin gains the thread V2 endpoints (built locally first, released before
  the stack merges).

## Non-goals

- Composing threads (a separate change follows).
- A tree / indented reply view or a reply sort picker.
- Loading the rest of a long thread in place.
- The pill on surfaces whose responses carry no numbers (search, notifications,
  bookmarks, video feed).
- Moving the video comments sheet off `getPostThread`.

## Capabilities

### New Capabilities
- `thread-numbering`: where a post's position in its author's thread comes from, when
  it is valid, and where it is displayed.

### Modified Capabilities
- `feature-postdetail`: the thread item projection gains read-more rows and loses the
  unused fold; post detail gains ordered reading through V2, the pinned opened post,
  thread lines, and the fallback to the v1 read.

## Impact

- Modules: `:data:models`, `:designsystem`, `:core:feed-mapping`, `:core:feed-cache`,
  `:core:database` (version 12 → 14), `:core:posts`, `:feature:postdetail:impl`,
  `:feature:profile:impl`; bench fixtures in `:core:feed-cache` and `:core:posts`.
- Dependency: a new atproto-kotlin release carrying `app.bsky.unspecced.getPostThreadV2`,
  `getPostThreadOtherV2` and `app.bsky.unspecced.defs`.
- No deviation from the MVI / Compose / Hilt / Room baseline.
- Tracking: bd epic `nubecita-hpl8`. Full design:
  `docs/superpowers/specs/2026-11-21-thread-viewing-design.md`.
