## Why

The "…" on a post opens a dropdown anchored to the action row. It is hard to reach
one-handed on a tall phone, it is already seven rows long, and on your own posts it
offers actions aimed at yourself: Report, Mute @you, Block @you. "Mute thread" only
shows a "coming soon" snackbar, and there is no way to pin one of your own posts to
your profile. The official Bluesky app offers both, from a grouped bottom sheet.

Approved design: `docs/superpowers/specs/2026-10-31-post-overflow-sheet-and-pin-design.md`.
Epic `nubecita-iwjz`, landed as one `gh stack` in this order:

1. `nubecita-iwjz.3` (sheet)
2. `nubecita-iwjz.4` (thread mute)
3. `nubecita-iwjz.2` (pin)

## What Changes

- **One shared bottom sheet.** A grouped M3 `ModalBottomSheet` replaces both overflow
  dropdowns: `PostCard`'s `PostOverflowAffordance` and the vertical video feed's
  `VideoOverflowMenu`. The rows come from one shared pure rule function. Each row has
  a label on the left and an icon on the right.
- **Own posts drop self-aimed rows.** "Report post", "Report @author",
  "Mute/Unmute @author" and "Block/Unblock @author" no longer appear on your own posts.
- **New mapped fields.** `:core:feed-mapping` maps `viewerState.pinned`,
  `viewerState.threadMuted`, and the reply's thread-root URI into the UI models.
- **Mute thread is real.** "Mute thread" / "Unmute thread" calls
  `app.bsky.graph.muteThread` / `unmuteThread` on the thread root.
  - The change shows right away on every cached post in that thread.
  - It rolls back if the call fails.
- **Pin your own posts.** Own posts gain "Pin to your profile" / "Unpin from profile"
  on every surface (Feed, Post detail, Profile, Videos).
  - Saving is a read-modify-write of `app.bsky.actor.profile` that keeps every other
    field and sends `swapRecord`.
  - The change shows right away, with rollback on failure.
  - Unpin only clears the pin if the profile still points at that post.
  - The profile's pinned slot updates without a manual refresh.

## Non-goals

- "Mute words & tags", "Edit interaction settings", "Hide post for me", feed-feedback
  rows, and the algo-labelling rows from the official sheet.
- Making "Unblock @author" real. It stays a coming-soon snackbar.
- In-place translation (`nubecita-s6xk`).
- The PostCard repost menu (Repost / Quote) and the ProfileHero overflow, which stay
  dropdowns.

## Capabilities

### New Capabilities

- `post-overflow-menu`: the shared post overflow sheet, covering:
  - row grouping and visibility rules (own vs others' posts, blank text,
    muted/blocked/thread-muted/pinned toggles)
  - laziness on the scroll path
  - the real thread mute/unmute write path, with optimistic state
- `post-pinning`: pin/unpin your own post, covering:
  - eligibility
  - the profile-record read-modify-write, and compare-and-clear on unpin
  - optimistic state across surfaces
  - the profile pinned-slot refresh

### Modified Capabilities

- `core-feed-mapping`: adds mapping of `viewerState.pinned`, `viewerState.threadMuted`,
  and the reply root URI onto `ViewerStateUi` / `PostUi`.

## Impact

- **`:designsystem`**
  - `PostCard.kt`: the overflow affordance becomes the sheet.
  - `PostOverflowAction`: adds `PinPost` and `UnpinPost`.
  - A new sheet composable and new strings.
  - Screenshot baselines for the sheet.
- **`:data:models`**
  - `ViewerStateUi`: `isPinned`, `isThreadMuted`.
  - `PostUi`: the reply-root URI.
- **`:core:feed-mapping`**: the new field mappings.
- **`:core:post-interactions`**
  - The handler routes `MuteThread` / `UnmuteThread` / `PinPost` / `UnpinPost`.
  - `PostInteractionsCache` gains optimistic flags for pinned and thread-muted.
  - A new `ThreadMuteRepository`, plus a bench fake.
- **`:core:profile`**: a shared profile-record writer (or a narrow pin writer). The
  existing `:feature:profile:impl` `updateProfile` reuses it rather than duplicating the
  read-modify-write.
- **`:feature:profile:impl`**: the pinned slot follows the cache's pin flag.
- **`:feature:videos:impl`**: `VideoOverflowMenu` moves to the shared sheet.
- **Bench fixtures**: one own post comes back pinned, and one post has its thread muted.
- **Strings**: new strings ship with es-419 and pt-BR.
- **Baseline deviations**: none. The work stays on MVI, Hilt and Compose. Post
  interactions keep the sanctioned `PostInteractionHandler by handler` delegation, and
  there is no new event bus.
