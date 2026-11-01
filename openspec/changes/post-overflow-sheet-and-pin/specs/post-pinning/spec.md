## ADDED Requirements

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

Unpin SHALL clear `pinnedPost` only if the record's current `pinnedPost.uri` equals the unpinned post's URI. Otherwise it writes nothing and reports success.

#### Scenario: Unpin of a no-longer-pinned post is a no-op

- **WHEN** Unpin is tapped on post `A`, but the record's `pinnedPost.uri` is post `B`
- **THEN** no `putRecord` is issued, the call succeeds, and `A` shows `isPinned = false`

### Requirement: Pin state updates optimistically across every surface

On `PinPost` for post `B`, `PostInteractionsCache` SHALL immediately set `isPinned = true` on `B`, and `false` on any other cached post that was pinned. Feed, Post detail, Profile and Videos all reflect this. Flipped values survive stale server refreshes, under the same rule as `isLikedByViewer`.

#### Scenario: Moving the pin from A to B

- **WHEN** post `A` is pinned and the user pins post `B`
- **THEN** `B` shows "Unpin from profile" and `A` shows "Pin to your profile" on every loaded surface, before the write returns

### Requirement: Pin failures roll back, and successes confirm

On a failed pin or unpin, the handler MUST restore every flag it changed and emit an error snackbar. On success, it emits "Post pinned" or "Post unpinned". Repeat taps while a pin write is in flight are ignored.

#### Scenario: Failed pin rolls back both posts

- **WHEN** the pin write for `B` fails while `A` was pinned
- **THEN** `A` is pinned again, `B` is not pinned, and an error snackbar is emitted

### Requirement: The profile's pinned slot follows pin changes without a refresh

`ProfileViewModel` SHALL update `ProfileState.pinnedPost` from `PostInteractionsCache` state. When a post by the profile's DID becomes pinned, it becomes `pinnedPost`; when the pinned post becomes unpinned, `pinnedPost` clears. This MUST NOT introduce an app-wide event stream; in particular, `ownProfileUpdates` stays inside `:feature:profile:impl`.

#### Scenario: Pin from Feed shows on Profile

- **WHEN** the user pins their own post from the Feed, then opens their profile without pulling to refresh
- **THEN** that post renders in the pinned slot at the top of the Posts tab
