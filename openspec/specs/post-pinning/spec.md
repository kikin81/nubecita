# post-pinning Specification

## Purpose
Pinning one of the viewer's own posts to their profile (`app.bsky.actor.profile#pinnedPost`): eligibility, the shared profile-record read-modify-write with compare-and-clear unpin, optimistic pin state across every surface, and the profile's pinned slot — including revealing a just-pinned post.
## Requirements
### Requirement: Own posts offer Pin to your profile or Unpin from profile

The overflow sheet SHALL show, as the first group on a post where `viewer.isOwnPost = true`, exactly one row:

- "Unpin from profile" (`PostOverflowAction.UnpinPost`) when `viewer.isPinned` is `true`
- "Pin to your profile" (`PostOverflowAction.PinPost`) otherwise

Eligibility is authorship only, matching social-app's `PostMenuItems.tsx` (verified 2026-10-31): replies and quote posts of your own are pinnable. Posts by anyone else MUST NOT show either row.

#### Scenario: Own reply is pinnable

- **WHEN** the sheet opens on the viewer's own reply that is not pinned
- **THEN** the first row is "Pin to your profile"

#### Scenario: Pinned own post offers Unpin

- **WHEN** the sheet opens on the viewer's own post with `isPinned = true`
- **THEN** the first row is "Unpin from profile"

#### Scenario: Others' posts have no pin row

- **WHEN** the sheet opens on a post with `isOwnPost = false`
- **THEN** neither pin row is present

### Requirement: Pinning rewrites the profile record without clobbering it

Pin SHALL be a read-modify-write of the viewer's `app.bsky.actor.profile` record:

1. `getRecord`
2. Set `pinnedPost` to the post's strong ref (`uri` and `cid`).
3. `putRecord` with `swapRecord` set to the CID that was read.

Every other field MUST be written back unchanged.

#### Scenario: Pin preserves other profile fields

- **WHEN** a pin is written to a profile record that has a displayName, description, avatar, banner, labels and website links
- **THEN** the written record differs from the read record only in `pinnedPost`, and `swapRecord` equals the read CID

### Requirement: The profile record writer is shared and lives in `:core:profile`

The profile-record read-modify-write SHALL live in `:core:profile` and be reused by Edit profile's update path; it is not duplicated. `:core:post-interactions` MUST NOT depend on a feature module.

#### Scenario: Edit profile reuses the writer

- **WHEN** `:feature:profile:impl`'s `updateProfile` and the pin path are inspected
- **THEN** both call the same `:core:profile` writer, and `:core:post-interactions` has no `:feature:*` dependency

### Requirement: Unpin clears only the post it was asked to unpin

Unpin SHALL clear `pinnedPost` only if the record's current `pinnedPost.uri` equals the unpinned post's URI. Otherwise — including when `pinnedPost` is absent — it MUST NOT call `putRecord`, and reports success.

#### Scenario: Unpin of a no-longer-pinned post is a no-op

- **WHEN** Unpin is tapped on post `A`, but the record's `pinnedPost.uri` is post `B`
- **THEN** no `putRecord` is issued, the call succeeds, and `A` shows `isPinned = false`

#### Scenario: Unpin with nothing pinned is a no-op

- **WHEN** Unpin is tapped on post `A`, and the record has no `pinnedPost`
- **THEN** `putRecord` is called zero times, and the call succeeds

### Requirement: Pin state updates optimistically across every surface

On `PinPost` for post `B`, `PostInteractionsCache` SHALL immediately set `isPinned = true` on `B`, and `false` on any other pinned cached post **by `B`'s author**; other authors' posts are untouched. Feed, Post detail, Profile and Videos all reflect this. Flipped values are kept as a local override, in both directions, until a server refresh agrees with it.

#### Scenario: Moving the pin from A to B

- **WHEN** post `A` is pinned and the user pins post `B`
- **THEN** `B` shows "Unpin from profile" and `A` shows "Pin to your profile" on every loaded surface, before the write returns

#### Scenario: Another author's pinned post is untouched

- **WHEN** a cached post by another author is flagged pinned and the user pins their own post `B`
- **THEN** that other author's post keeps `isPinned = true`

### Requirement: Pin failures roll back, and successes confirm

On a failed pin or unpin, the handler MUST restore every flag it changed and emit an error snackbar. On success, it emits "Post pinned" or "Post unpinned". Repeat taps while a pin write is in flight are ignored.

#### Scenario: Failed pin rolls back both posts

- **WHEN** the pin write for `B` fails while `A` was pinned
- **THEN** `A` is pinned again, `B` is not pinned, and an error snackbar is emitted

### Requirement: The profile's pinned slot follows pin changes without a refresh

`ProfileViewModel` SHALL update `ProfileState.pinnedPost` from `PostInteractionsCache` state. When a post by the profile's DID becomes pinned, the VM resolves it by URI via `getPost` (the cache holds flags, not content) and it becomes `pinnedPost`; when the pinned post becomes unpinned, `pinnedPost` clears. This MUST NOT introduce an app-wide event stream; in particular, `ownProfileUpdates` stays inside `:feature:profile:impl`.

#### Scenario: Pin from Feed shows on Profile

- **WHEN** the user pins their own post from the Feed, then opens their profile without pulling to refresh
- **THEN** that post renders in the pinned slot at the top of the Posts tab

#### Scenario: Pinning a post the profile hasn't loaded

- **WHEN** the user pins an older reply from the Feed that no loaded profile tab contains
- **THEN** `ProfileViewModel` resolves it via `getPost` and renders it in the pinned slot

### Requirement: Pin and Unpin send the requested value

`PinPost` / `UnpinPost` SHALL pass the requested pinned value to the cache rather than toggling, so a stale "Unpin" can never pin. A target the cache already holds writes nothing.

#### Scenario: Stale Unpin on a post that isn't pinned

- **WHEN** "Unpin from profile" is dispatched for a post the cache shows unpinned
- **THEN** neither `pin` nor `unpin` is called

### Requirement: The Pin row waits for a real CID and an uncached post is seeded

The Pin / Unpin row SHALL appear only when the post has a non-blank CID. Before a pin or mute, a post absent from the interaction cache SHALL be seeded from the `PostUi` in hand.

#### Scenario: Just-sent post

- **WHEN** the sheet opens on the viewer's own post whose CID is still blank
- **THEN** there is no Pin or Unpin row

### Requirement: Slot reconciliation is own-profile only and survives reloads

On the viewer's own profile, the pinned slot SHALL follow the cache (a local pin first, else the header ref unless the cache marks it unpinned), for cache updates and header reloads alike. On any other profile the header ref is authoritative, because the appview sets `viewer.pinned` only for the post's author.

#### Scenario: Someone else's pinned post

- **WHEN** another account's profile loads with a pinned ref, and the cache reports that post with `isPinned = false`
- **THEN** the slot still shows that post

#### Scenario: Refresh before the appview indexes a local pin

- **WHEN** the viewer pins `B`, then refreshes and the header still reports `A`
- **THEN** the slot keeps showing `B`

### Requirement: A just-pinned post is revealed

When the slot resolves a pin the viewer just made, the VM SHALL emit `ProfileEffect.RevealPinnedPost`, and the Posts tab scrolls the slot to just below the top bar. A header load or reload MUST NOT reveal. The slot is keyed by the post's URI and animated with `animateItem()`.

#### Scenario: Pin a post further down the profile

- **WHEN** the viewer pins a post while the pinned slot is scrolled off-screen
- **THEN** the list scrolls so the "Pinned" label sits just below the top bar, showing the new post

#### Scenario: Opening the profile

- **WHEN** the viewer opens their profile with an existing pin
- **THEN** no reveal is emitted
