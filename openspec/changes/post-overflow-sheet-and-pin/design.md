## Context

The full approved design, with the user's decisions and the claims checked against
social-app, is `docs/superpowers/specs/2026-10-31-post-overflow-sheet-and-pin-design.md`.
This file condenses it for the OpenSpec change. Where the two differ, the superpowers doc
is the authority.

**Current state:**

- **`PostCard`'s overflow** (`designsystem/.../component/PostCard.kt`,
  `PostOverflowAffordance`) is a `DropdownMenu`. Its laziness is pinned by
  `DropdownMenuLazinessTest`.
- **The video feed's overflow** (`feature/videos/impl/.../ui/VideoOverflowMenu.kt`) is a
  second `DropdownMenu`, with its own copy of the row rules.
- **Thread mute and Unblock** both go to `ShowComingSoon` in
  `DefaultPostInteractionHandler.onOverflowAction`.
- **Pin display** (`nubecita-iwjz.1`) is shipped. The profile record write path lives in
  `:feature:profile:impl`'s `DefaultProfileRepository.updateProfile`, as a
  read-modify-write with `swapRecord`.

## Goals / Non-Goals

**Goals:**

- One grouped bottom sheet, built from one rule function, for both surfaces.
- Own posts drop the rows aimed at yourself.
- Thread mute and unmute work for real.
- Own posts can be pinned and unpinned.
- All of it optimistic and consistent across surfaces.

**Non-Goals:**

- The proposal's non-goals.
- Changing the repost menu or the ProfileHero overflow.

## Decisions

### D1 — Pin eligibility = authorship

social-app gates the pin group on `isAuthor` alone, so replies and quotes are pinnable.
Gate on `viewer.isOwnPost`.

- **Alternative:** top-level posts only, which was the ticket's original guess.
- **Rejected because:** it diverges from the official client, which the ticket said to
  match.

### D2 — Row groups follow social-app's order, with our existing labels

- We keep the "Mute @handle" wording and the Unblock row, so existing translations and
  behaviour carry over.
- **Alternative:** copy social-app's "Mute account" wording and its hidden Unblock.
- **Rejected because:** it means new translations and behaviour churn, for no user gain.

### D3 — One pure rule function plus one sheet composable in `:designsystem`

- `PostUi` goes in, grouped rows come out. It is unit-testable without Compose.
- The sheet is `ModalBottomSheet` with `NubecitaListGroup` / `NubecitaListItem` group cards.
- The sheet is composed only while open.
- **Alternative:** keep a per-surface menu.
- **Rejected because:** the two copies have already drifted (the video menu has no
  Translate row).

### D4 — The pin write lives in `:core:profile`, shared with Edit profile

The handler in `:core:post-interactions` can't depend on `:feature:profile:impl`, so
the profile-record read-modify-write is extracted (or a narrow writer is added) in
`:core:profile`. `updateProfile` reuses it.

- **Unpin is compare-and-clear.** The writer takes the post being unpinned, not a bare
  `null`.
- **Alternative:** duplicate the read-modify-write in `:core:post-interactions`.
- **Rejected because:** two writers on the same record will drift, and a drift there
  clobbers profile fields.

### D5 — Optimistic flags live in `PostInteractionsCache`, as overrides kept until the server agrees

`isPinned` and `isThreadMuted` each carry a local override, set by a write. Seeding
from the wire keeps the override until the wire value equals it, then clears it.

- **Two-way, unlike likes.** The like rule only protects the "on" direction: a cached
  `true` survives a stale `false`. A pin move (A off, B on) and an unmute both need the
  "off" direction protected too.
- **Pin:** pinning B clears the flag on any previously pinned cached post.
- **Thread mute:** keyed by root URI, so it flips every cached post in the thread.
- **Alternative:** let server refreshes always win.
- **Rejected because:** server lag would show a stale pin or mute right after a
  successful write.

### D6 — The profile's pinned slot follows the cache

`ProfileViewModel` already merges `cache.state`, and uses it to set or clear
`pinnedPost`.

- **Alternative:** move `ownProfileUpdates` into `:core:profile`.
- **Rejected because:** it creates an app-wide event stream (a bus) that this repo avoids.

### D7 — Thread mute targets the thread root

The post's reply-root URI comes from the record's `reply.root.uri`, falling back to the
post's own URI. This mirrors social-app's `rootUri`. `PostUi` gains that field through
`:core:feed-mapping`.

## Risks / Trade-offs

- **[Sheet in a scrolling list could regress 120 Hz]** → Compose the sheet only while
  open, with no per-card sheet state, and pin it with a laziness test. Check scrolling
  on a real device.
- **[Pin write races an Edit profile save]** → `swapRecord` makes the later write fail
  instead of overwriting the other. The pin rolls back and shows an error.
- **[A stale "Unpin" button]** → compare-and-clear prevents unpinning a different post.
- **[Screenshot churn]** → new sheet baselines come from the CI `update-baselines` bot
  and are never committed locally. Existing `PostCard` baselines are unchanged: the
  closed affordance renders the same icon.

## Migration Plan

- There are no data migrations. The new fields default to `false` / `null`.
- Ship as one `gh stack`, with one PR per child:
  1. `iwjz.3`
  2. `iwjz.4`
  3. `iwjz.2`
- Land the stack with `gh stack merge`, so there is one release.
- Rollback is a revert of the squash commits.
