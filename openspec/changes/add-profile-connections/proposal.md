## Why

The profile shows follower and following counts as plain text, and the app has no way to see who those accounts are. A user asked for this directly. Bluesky exposes both lists, so the gap is ours.

## What Changes

- Tapping the follower or following count on any profile opens a new list screen.
- The screen switches between Followers and Following with segmented buttons, each showing its count.
- Rows show avatar, name and handle with a Follow / Following button, and open that profile on tap.
- Lists page in as the reader scrolls.
- The stats row uses singular forms correctly ("1 Follower").

Not in this change: search, a Mutual tab, offline caching, and deep links.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `feature-profile`: the stats row becomes interactive, and the module gains a followers and following list route.

## Impact

- `:feature:profile:api` gains a `NavKey`; `:feature:profile:impl` gains a repository, ViewModel and screen, and Paging 3.
- `:designsystem`: `NubecitaActorRow` gains an optional trailing slot.
- `:core:analytics`: one new `PostSurface` value.
- Adds two read endpoints: `app.bsky.graph.getFollowers` and `getFollows`.

Tracked by epic `nubecita-shej`.
