## Why

Bluesky shipped an official multi-image embed, `app.bsky.embed.gallery` (up to 10 images), live end-to-end since social-app v1.123.0. Nubecita has no gallery support today, so two things are broken for users:

1. **Render gap (visible now):** a gallery post that someone already published renders as an "unsupported embed" chip — its images are completely missing, even though the text shows. Galleries route through `PostViewEmbedUnion.Unknown → EmbedUi.Unsupported` today.
2. **Authoring ceiling:** the composer caps at 4 images and only ever emits `app.bsky.embed.images`, so Nubecita users can't post 5–10 image posts at all.

The governing interop constraint: gallery has **no graceful fallback** — on any client without gallery support a gallery post shows its images entirely missing. So authoring must mirror social-app exactly: emit `app.bsky.embed.images` for ≤4 images (maximum interop) and only `app.bsky.embed.gallery` for 5–10.

## What Changes

- **Render galleries** (Phase 1, independently shippable): map `gallery#view` → a new `EmbedUi.Gallery` (and the quoted/record-with-media variant), reusing the existing `ImageUi` shape and the existing multi-image carousel. Removes the "unsupported embed" chip for galleries.
- **Lightbox**: the full-screen media viewer accepts galleries (not just `EmbedUi.Images`), paging across all N images.
- **Author galleries** (Phase 2): raise the composer cap from 4 → 10 images; auto-**promote** images→gallery when the count crosses 4 and auto-**demote** back when a removal brings it ≤4; build the correct wire embed per count.
- **Per-image alt-text editor**: a full-screen adaptive route (full-screen phone / dialog tablet) to set alt text per attachment, with an "ALT" badge on described thumbnails. New: there is no alt-text UI today (alt is hardcoded `""`).
- **Required alt for galleries**: posting a gallery (>4 images) is gated until every image has non-empty alt text. Images (≤4) stay optional — no regression to the existing image-post flow.
- **Drag-to-reorder** attachments in the composer.
- **Per-image aspect ratio** is computed at upload time (required by the gallery wire type) and also populated on the `images` path as a free feed-layout improvement.

## Capabilities

### New Capabilities
- `gallery-embed`: end-to-end support for `app.bsky.embed.gallery` — mapping the gallery view union to a UI model, rendering it in the post card and lightbox, and authoring galleries in the composer (10-image cap, promote/demote, required alt, reorder, aspect-ratio computation).

### Modified Capabilities
<!-- No existing capability's REQUIREMENTS change. Gallery render reuses ImageUi/carousel additively; the composer/posting changes are net-new behavior captured under gallery-embed. The aspectRatio-on-images-path improvement is documented as a gallery-embed requirement to keep the contract in one place. -->

## Impact

- **`:data:models`** — add `EmbedUi.Gallery` + `QuotedEmbedUi.Gallery` (reuse `ImageUi`); gallery fixtures.
- **`:core:feed-mapping`** — map `GalleryView` in the top-level `PostViewEmbedUnion` path and the `RecordWithMediaViewMediaUnion` (quoted) path.
- **`:designsystem`** — `PostCard` dispatches `EmbedUi.Gallery` to the existing carousel; new gallery `@Preview` + screenshot tests.
- **`:feature:mediaviewer`** — widen the viewer's embed type-guard to accept `EmbedUi.Gallery`.
- **`:feature:composer`** — 10-image picker cap, promote/demote, drag-reorder, alt-text editor route, required-alt gate.
- **`:core:image`** — new `ImageDimensionDecoder` (bounds-only `inJustDecodeBounds` decode of raw bytes) + Hilt binding, so the upload path can read intrinsic dimensions off the main thread without relying on the encoder.
- **`:core:posting`** — `ComposerAttachment` gains `alt`; `uploadOne` decodes dimensions and carries `UploadedImage` (blob + alt + dimensions); `resolveEmbed` branches Images(1–4)/Gallery(≥5, capped at 10 by the composer) with per-image `alt` + `aspectRatio` written to records.
- **SDK**: `io.github.kikin81.atproto` 9.6.0 (already on `main`) — uses `GalleryView` / `GalleryViewImage` / `Gallery` / `GalleryImage` / `PostEmbedUnion.Gallery`. No dependency bump.
- **Deviation from baseline**: none — stays on MVI / Compose / Hilt / Coil. The composer's alt-edit route uses the established `adaptiveDialog()` scene-strategy pattern; hosting it from the composer's current hand-rolled overlay is an implementation risk to resolve (see design.md).

## Non-goals

- `app.bsky.embed.getEmbedExternalView` (enhanced external link cards) — tracked separately as `nubecita-kpqb`.
- Migrating the composer off its hand-rolled launcher/overlay to the scene-strategy pattern (`nubecita-11st`).
- Making alt text required for `app.bsky.embed.images` (≤4) posts — would regress today's image flow.
- Raising the gallery cap beyond the soft limit of 10 (the lexicon hard ceiling is 20).
