# Profile editing (display name, bio, avatar, banner)

- **bd epic:** nubecita-qr1q (children `.1`–`.7`)
- **Date:** 2026-05-30
- **Status:** design approved (brainstorm), ready to file + spike

## Problem

A new account (and any account) cannot edit its profile — display name, bio, avatar photo, or
banner/background image. Surfaced by a friend who installed the app, created a fresh account, and
found no way to set a photo, bio, or background. The own-profile **Edit** button already exists
(`ProfileVerbsRow`, own-profile only) but is stubbed: `ProfileEvent.EditTapped` →
`ProfileEffect.ShowComingSoon`. This epic builds the destination and the write path.

## Scope

**In:** edit display name, bio (`description`), avatar, and banner on your own profile, with a
real in-app crop for the images. Own-profile gating already works (`Profile.handle == null` →
`ownProfile`).

**Out:** handle/username editing (a separate DNS/custom-domain flow); pinned post, self-labels, and
other `app.bsky.actor.profile` fields (preserved on write, not edited); editing other users.

## Key decisions (from brainstorm)

1. **Fields:** the standard 4 — display name, bio, avatar, banner.
2. **Crop:** full crop in this epic — avatar 1:1 (circle), banner 3:1. **Build our own**, no
   third-party crop library and **not telephoto**. A **fixed-frame** crop: a fixed frame (circle /
   3:1 rect) stays centered and the user pans/zooms the image behind it, constrained so the image
   always covers the frame. Gestures are **hand-rolled on Compose foundation** (`detectTransformGestures`
   + `graphicsLayer`), so crop depends only on stable first-party Compose APIs (pinned via our BOM)
   — a telephoto change can never break it — and owning the frame lets us use an **exact 3:1** banner.
   Clean-room: informed by standard graphics techniques, not copied from any library.
3. **Home for crop:** a new shared **`:core:image`** Compose library module (parallels `:core:video`),
   so crop is reusable later (e.g. post-photo uploads) and does **not** live in `:feature:profile`.
4. **Consolidation:** relocate the existing image **picker** (`rememberComposerImagePicker`),
   **encoder** (`BitmapAttachmentEncoder`), and **byte source** (`AttachmentByteSource`) from
   `:core:posting`/composer into `:core:image`; `:core:posting` depends on `:core:image` (correct
   direction). Composer call sites migrate.
5. **Write path:** `getRecord` → merge → `putRecord(swapRecord)` on `app.bsky.actor.profile`/`self`,
   with a **create-if-missing** path for brand-new accounts (the friend's case). The codebase has
   only ever used `createRecord`/`deleteRecord`, so SDK support for `getRecord`/`putRecord` is the
   **first spike**.

## Architecture

### Module map
- **`:core:image`** *(new, `nubecita.android.library.compose`)* — owns our **hand-rolled** crop
  composable (`CropImage(sourceUri, shape, onCropped)`-style API) built on Compose foundation only;
  plus the relocated picker, encoder, byte source. No third-party crop/viewer dependency. Pure image
  capability, no profile/posting knowledge.
- **`:core:posting`** — drops the relocated utilities; depends on `:core:image`. Composer unchanged
  behaviorally.
- **`:feature:profile:api`** — new `EditProfile` NavKey (`data object`).
- **`:feature:profile:impl`** — new `EditProfileScreen` (+ stateless content + `EditProfileViewModel`)
  registered as a `@MainShell` entry; the profile-record **write repository**
  (`updateProfile(...)`, mirroring the existing `DefaultProfileRepository` follow/unfollow writes);
  unstub `EditTapped` → `NavigateTo(EditProfile)`.

### Write path (correctness core)
`updateProfile(displayName, description, avatar: ImageChange, banner: ImageChange)` where each image
is *unchanged / replaced(bytes,mime) / removed*:
1. `getRecord(collection=app.bsky.actor.profile, repo=selfDid, rkey=self)` → `(record, cid)`.
2. **404 → create-if-missing:** no record yet → start from an empty profile record (new accounts).
3. Upload only **changed** images via `uploadBlob` → `Blob`; **unchanged** reuse the fetched blob
   ref; **removed** set the field to null.
4. Merge `displayName`/`description`/`avatar`/`banner` onto the fetched record, **preserving** all
   other fields (pinnedPost, labels, createdAt, …).
5. `putRecord(..., swapRecord=cid)` (omit swap on the create path). Optimistic concurrency: a stale
   `cid` (profile changed elsewhere) fails → surface "profile changed, reload and retry".

### Crop (`:core:image`)
Our own **fixed-frame** crop, hand-rolled — no third-party crop/viewer library. A fixed frame
(circle for avatar, exact 3:1 rect for banner) stays centered; the user pans/zooms the image behind
it, constrained so the image always covers the frame. Pipeline: decode the picked Uri to a
downscaled `Bitmap` (reuse the relocated encoder / `ImageDecoder`) → render it under a
`Modifier.graphicsLayer { scale; translation }` clipped to the frame, with a scrim + frame overlay
(circle mask for avatar) → drive `(scale, offset)` from Compose foundation's `detectTransformGestures`
(pan + pinch) with bounds clamping → on confirm, invert the transform to the source rect, extract at
full resolution (`Bitmap.createBitmap` / `BitmapRegionDecoder`), and run it through the encoder to
fit Bluesky's ~1 MB blob cap. Handles EXIF + bounds. Output: `(bytes, mime)`. Depends only on stable
first-party Compose — no telephoto coupling — and we wrote it, so there's no third-party attribution.

### Edit screen UX
Full-screen `EditProfile` sub-route (`@MainShell`, pushed via `navState.add`, like Settings), wired
to the now-unstubbed Edit button. Form: banner (tap → pick → crop), avatar (tap → pick → crop),
display-name field, multi-line bio field. Save shows a loading state; back with unsaved changes
prompts a discard confirm; on success, pop and refetch the profile header. Pre-filled from the
current `ProfileHeaderUi`.

## Validation / limits
- Display name ≤ **64 graphemes**, bio ≤ **256 graphemes** (`app.bsky.actor.profile` lexicon) —
  reuse the composer's grapheme counter.
- Images encoded under the ~1 MB blob cap by the existing encoder ladder (dimension + quality).

## Error handling
- `putRecord` swap conflict → "profile changed elsewhere; reload and retry" (no silent overwrite).
- Blob upload failure → inline error, keep the form populated (no data loss).
- No session / auth failure → standard signed-out handling.
- Oversize/odd image → encoder ladder downscales; last-resort min-quality (existing behavior).

## Testing
- **Write repo (unit, MockEngine):** getRecord→putRecord happy path; create-if-missing on 404;
  swap-conflict surfaced; changed-vs-unchanged-vs-removed image handling; field preservation.
- **Crop (unit):** frame→source-rect math across zoom/pan + aspect ratios; EXIF; bounds clamping.
- **Edit VM (unit):** grapheme limits, dirty-state tracking, save success/failure effects.
- **Screenshot:** edit form (empty + populated), crop screen (avatar circle, banner wide).

## Epic breakdown (children)
1. **Spike — SDK write path.** Confirm the atproto SDK exposes `getRecord` + `putRecord(swapRecord)`
   for `app.bsky.actor.profile`/`self`, plus the create-if-missing (404) path. *(ready first;
   de-risks the epic; if absent → SDK work precedes the rest.)*
2. **`:core:image` module.** Create the Compose library module; relocate picker + encoder + byte
   source from `:core:posting`; `:core:posting` depends on it; migrate composer call sites + tests.
3. **Hand-rolled crop.** Build the fixed-frame `CropImage(sourceUri, shape, onCropped)` in
   `:core:image` on Compose foundation (`detectTransformGestures` + `graphicsLayer`): Uri → downscaled
   bitmap → pan/zoom-behind-frame (avatar circle / banner 3:1) → invert transform → full-res extract →
   encoder → `(bytes, mime)`. No third-party crop/viewer dep; clean-room. *(needs `:core:image`.)*
4. **Profile-write repository.** `updateProfile(...)`: getRecord/merge/putRecord + uploadBlob +
   create-if-missing; unit-tested. *(needs spike 1.)*
5. **Edit screen — text first.** `EditProfile` route + display-name/bio form + grapheme limits, wired
   to the Edit button, save via putRecord (no image change). Ships text editing as a vertical slice.
   *(needs repo 4.)*
6. **Avatar + banner editing.** Wire pick → crop → preview → upload into the form; full putRecord with
   blobs. *(needs crop 3 + edit screen 5.)*
7. **Polish + tests.** Unsaved-changes guard, error/empty states, refetch-on-success, screenshot
   tests. *(needs 6.)*

## Open risk
The SDK `getRecord`/`putRecord` availability (spike 1) gates the write repository. Everything else
(crop, module, UI) is independent and can proceed in parallel with the spike.
