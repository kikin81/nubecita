## ADDED Requirements

### Requirement: Gallery embed UI model
The system SHALL represent an `app.bsky.embed.gallery#view` as a dedicated `EmbedUi.Gallery` UI model (and `QuotedEmbedUi.Gallery` in the quoted/record-with-media context), reusing the existing per-image `ImageUi` type. The model SHALL be `@Immutable` and hold its images as an `ImmutableList<ImageUi>`.

#### Scenario: Gallery view becomes a distinct UI model
- **WHEN** a post's embed is a gallery view
- **THEN** it is represented as `EmbedUi.Gallery`, not `EmbedUi.Images` and not `EmbedUi.Unsupported`

#### Scenario: Each gallery image carries display fields
- **WHEN** a gallery image is mapped to `ImageUi`
- **THEN** it carries `fullsizeUrl`, `thumbUrl`, `altText` (null when blank), and `aspectRatio` (width ÷ height)

### Requirement: Gallery view mapping
The system SHALL map `GalleryView` to `EmbedUi.Gallery` in the top-level `PostViewEmbedUnion` path, and map `GalleryView` appearing in `RecordWithMediaViewMediaUnion` to `QuotedEmbedUi.Gallery`. The mapper SHALL unwrap each `GalleryViewItemsUnion` to its `GalleryViewImage`, reading `thumbnail`, `fullsize`, `alt`, and the non-null `aspectRatio`. Unknown item-union variants SHALL be skipped without failing the post.

#### Scenario: Top-level gallery no longer unsupported
- **WHEN** a post carries an `app.bsky.embed.gallery#view`
- **THEN** the mapper produces `EmbedUi.Gallery` and the post no longer renders an "unsupported embed" chip

#### Scenario: Quoted gallery maps via the record-with-media path
- **WHEN** a quoted post's media is a gallery view (`RecordWithMediaViewMediaUnion`)
- **THEN** the mapper produces `QuotedEmbedUi.Gallery`

#### Scenario: Unknown gallery item variant is tolerated
- **WHEN** a gallery view contains an unrecognized item-union variant
- **THEN** that item is skipped and the remaining images still render

### Requirement: Gallery rendering in the post card
The system SHALL render `EmbedUi.Gallery` through the existing multi-image carousel used by `EmbedUi.Images`. Rendering of `EmbedUi.Images` SHALL remain unchanged.

#### Scenario: Gallery renders as a carousel
- **WHEN** a post card displays a gallery of 5–10 images
- **THEN** the images render in the horizontal multi-image carousel

#### Scenario: Existing images rendering is unchanged
- **WHEN** a post card displays an `app.bsky.embed.images` embed of 1–4 images
- **THEN** it renders exactly as before this change (no visual change, no new baseline)

### Requirement: Gallery in the full-screen lightbox
The full-screen media viewer SHALL accept `EmbedUi.Gallery` in addition to `EmbedUi.Images`, paging across all images in the gallery, and SHALL open at the tapped image's index.

#### Scenario: Tapping a gallery image opens the lightbox
- **WHEN** the user taps the image at index N in a gallery
- **THEN** the lightbox opens at index N and can page across all images in the gallery

#### Scenario: Out-of-range index is clamped
- **WHEN** the lightbox is opened with an index outside the gallery bounds
- **THEN** the index is clamped into range and a valid image is shown

### Requirement: Composer supports up to ten images
The composer SHALL allow up to 10 image attachments (raised from 4). The image picker SHALL offer at most `10 − current attachment count` additional images, and adding beyond 10 SHALL be prevented.

#### Scenario: Picker cap reflects remaining slots
- **WHEN** the composer already holds 6 images
- **THEN** the picker allows selecting at most 4 more

#### Scenario: Cannot exceed ten images
- **WHEN** the user attempts to add images that would exceed 10 total
- **THEN** the excess images are not added and the total stays at 10

### Requirement: Automatic promotion and demotion between images and gallery
When building the post embed, the system SHALL emit `app.bsky.embed.images` for 1–4 images and `app.bsky.embed.gallery` for 5 or more images (the composer caps attachments at 10). Zero attachments SHALL emit no image embed. The posting branch SHALL treat the gallery case as "5 or more" rather than a closed 1–10 range so an unexpected count never silently drops the embed. The embed kind SHALL be derived solely from the attachment count, with no persisted mode flag. Crossing the boundary by adding or removing images SHALL change the emitted embed accordingly. Per-attachment alt text and computed aspect ratio SHALL be preserved across promotion and demotion.

#### Scenario: Four images emit an images embed
- **WHEN** a post is created with 4 images
- **THEN** the wire embed is `app.bsky.embed.images`

#### Scenario: Five images emit a gallery embed
- **WHEN** a post is created with 5 images
- **THEN** the wire embed is `app.bsky.embed.gallery`

#### Scenario: Demotion preserves per-image alt text
- **WHEN** a 5-image gallery with alt text on each image is reduced to 4 images
- **THEN** the post emits an images embed and the remaining images keep their alt text

### Requirement: Per-image alt-text editor
The composer SHALL provide a per-image alt-text editor reachable by tapping an attachment thumbnail, presented as an adaptive route (full-screen on compact width, centered dialog on medium/expanded width). Setting alt text SHALL persist it on that attachment, and a thumbnail with non-blank alt text SHALL display an "ALT" indicator.

#### Scenario: Editing alt text persists it on the attachment
- **WHEN** the user opens the alt-text editor for an attachment and saves non-blank text
- **THEN** that attachment carries the alt text and its thumbnail shows the "ALT" indicator

#### Scenario: Editor is adaptive by width
- **WHEN** the alt-text editor is opened on a compact-width device
- **THEN** it is presented full-screen; on medium/expanded width it is presented as a centered dialog

### Requirement: Required alt text for galleries
The system SHALL prevent posting a gallery (more than 4 images) until every image has non-blank alt text, surfacing a hint identifying the unmet requirement. Posts with 4 or fewer images SHALL NOT require alt text.

#### Scenario: Gallery post blocked until all images described
- **WHEN** the composer holds more than 4 images and at least one has blank alt text
- **THEN** the submit action is disabled and a hint indicates alt text is required for all gallery images

#### Scenario: Gallery post allowed once all images described
- **WHEN** every image in a gallery of more than 4 images has non-blank alt text
- **THEN** the submit action is enabled

#### Scenario: Four-or-fewer images do not require alt text
- **WHEN** the composer holds 4 or fewer images, some with blank alt text
- **THEN** the submit action is not blocked on alt text

#### Scenario: Demotion lifts the alt requirement
- **WHEN** a gallery with a blank-alt image is reduced to 4 or fewer images
- **THEN** the submit action is no longer blocked on alt text

### Requirement: Reorder attachments
The composer SHALL allow reordering image attachments by drag, and the post SHALL be created with images in the user-defined order.

#### Scenario: Dragging changes post order
- **WHEN** the user drags an attachment from position 1 to position 3
- **THEN** the created post's embed lists that image in position 3

### Requirement: Per-image aspect ratio
The system SHALL compute each image's aspect ratio (width and height) via a bounds-only decode when the image is picked, carry it on the attachment, and include it on every `gallery#image`. The system SHALL also include the computed aspect ratio on each `images#image` (previously omitted). The computed ratio SHALL be correct regardless of any upload re-encode/downscale (aspect ratio is scale-invariant).

#### Scenario: Gallery images carry aspect ratio
- **WHEN** a gallery is uploaded
- **THEN** every gallery image record includes a non-null aspect ratio computed from the image

#### Scenario: Images embed also carries aspect ratio
- **WHEN** an images embed of 1–4 images is uploaded
- **THEN** every image record includes the computed aspect ratio
