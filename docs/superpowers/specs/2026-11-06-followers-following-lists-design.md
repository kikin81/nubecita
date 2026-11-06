# Followers and following lists

Issue: `nubecita-shej`. Requested by a user:
[post](https://bsky.app/profile/revistaviera.bsky.social/post/3mzil7ho2fn2q).

## Why

The profile shows "5 Followers · 3 Following" as plain text, and the app has no
way to see who those accounts are. Readers want to see who follows an account
and who it follows, and to follow people from there. Threads is the reference
for the shape of the screen.

## Goals

- Tapping the follower or following count on any profile opens a list screen.
- The screen switches between **Followers** and **Following**, each with its
  count, and opens on the one that was tapped.
- Each row shows avatar, name and handle, plus a **Follow** / **Following**
  button.
- Tapping a row opens that profile.
- Lists page in as the reader scrolls.

## Non-goals (v1)

- **Search.** Bluesky has no API to search within one account's followers:
  `getFollowers` / `getFollows` only page, and `searchActors` can't be scoped.
  Client-side search would mean walking pages in the background, which isn't
  worth the paging complexity. Revisit if Bluesky adds an API.
- **A Mutual tab.** `getKnownFollowers` exists, so this can come later.
- Sorting, filtering, and offline caching of the lists.
- A deep link to the screen.

## Anti-goals

- Rows that show the wrong follow state after a tap, or that lose it while
  scrolling or switching tabs.
- Anything on this screen slowing the profile screen: nothing here loads until
  a count is tapped.

## How it behaves

```
Profile hero                     Connections screen
"5 Followers · 3 Following"      ←  Nubecita.app
   │ tap Followers               [ 5 Followers ][ 3 Following ]
   └───────────────────────▶     (o) Philip J. Fry    [ Follow ]
                                     @philip-j-fry...
                                 (o) Francisco        [ Following ]
```

**Tabs.** Reuse `ProfilePillTabs` from `:designsystem`, the segmented buttons
the profile and Chats screens already use (the user's choice). Labels include
the count. Check them in es-419 and pt-BR with large counts, since button
groups have failed on long labels before.

**Follow button.** Matches the profile's button: it updates immediately, sends
the request, and reverts with a message on failure. Tapping **Following**
unfollows straight away with no confirmation, as the profile does today.

**Rows with no button.** The viewer's own row, and accounts where the viewer
blocks or is blocked by them, show no button.

**States.** A loading state, an empty message per tab, and retry on failure,
for the first page and for later pages.

## How it's built

These are the builder's defaults, grounded in the current code. None of them
were decided by the user unless stated above.

- **Route.** A new `NavKey` in `:feature:profile:api`, registered under
  `@MainShell`, pushed with `navState.add(...)` through a `UiEffect`. It carries
  the actor, the starting tab, the display name, and both counts, because
  `getFollowers` / `getFollows` return `ProfileView`, which has no counts. The
  counts are a snapshot from the profile.
- **Module.** The screen, ViewModel and data live in `:feature:profile:impl`,
  which is flavored.
- **Data.** A new small repository rather than growing `ProfileRepository`,
  wrapping `app.bsky.graph.getFollowers` and `getFollows` and mapping to a row
  model that carries the viewer's follow URI. `ActorUi` has no follow state, so
  it isn't enough alone. Follow writes go through the existing `FollowRepository`.
- **Paging.** Paging 3 with a cursor `PagingSource`, following
  `JoinRequestPagingSource`. One flow per tab, `cachedIn(viewModelScope)`, so
  switching tabs keeps each list and scroll position.
- **Follow state.** A per-DID overlay in the ViewModel applied over the paged
  rows, so optimistic state survives page reloads. Pending taps are guarded per
  DID, and a committed follow keeps the URI needed to unfollow.
- **Row UI.** `NubecitaActorRow` gains an optional trailing slot, leaving
  existing callers unchanged, or wraps it if that reads better.
- **Stats row.** `ProfileStatsRow` is a single `Text` today. Followers and
  following become separate tappable targets with accessible labels and a
  sensible touch size. The posts count stays plain. Fix singular forms here
  ("1 Follower"), which overlaps `nubecita-181n`.
- **Analytics.** Reuse `InteractActor` with a new surface value.
- **Strings.** New strings ship in English, es-419 and pt-BR together.
- **Bench.** The bench flavor needs a fake repository and fixture rows, so the
  screen works offline and can be smoke-tested on a device.

## Left to the builder

- The exact row model, and whether moderation labels affect rows. Match the
  search People tab.
- Whether the profile refreshes its counts after follows made here. A stale
  count until the next refresh is acceptable for v1.
- Tablet layout: a normal `@MainShell` sub-route unless something looks wrong.

## Testing

Unit tests for paging mapping, follow overlay, rollback and the pending guard.
Screenshot tests for rows, tabs and the empty and error states, with fixture
data that makes each state look different. A bench device pass that follows
and unfollows and confirms the change in a screenshot. A real-account check
before release, since bench doesn't exercise the network paging.

## Delivery

Epic with children landed as one `gh stack` stack, so it cuts one release:
data and route, then the screen, then the profile entry point.
