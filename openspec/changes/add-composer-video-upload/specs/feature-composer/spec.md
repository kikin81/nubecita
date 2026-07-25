## ADDED Requirements

### Requirement: A picked video uploads eagerly, not on submit

Selecting a video SHALL start `VideoUploadRepository.upload(uri)` immediately and mirror its emissions into `ComposerState.video`, so that by the time the user finishes typing the blob is usually already available and submission is instant. Submission SHALL NOT be the trigger for compression or upload.

The upload job SHALL be owned by `ComposerViewModel` and scoped to `viewModelScope`, and SHALL be cancelled when the video is removed or the composer is discarded. No server-side cleanup call SHALL be made for an abandoned upload — the job expires on its own, and inventing a cancel call would add a failure mode without removing one.

#### Scenario: Picking a video starts the pipeline

- **WHEN** the user picks a video from the system picker
- **THEN** `ComposerState.video` becomes non-null and its upload state advances without any further user action

#### Scenario: Removing the video cancels the upload

- **WHEN** an upload is in `Compressing` or `Uploading` and the user removes the video
- **THEN** the job is cancelled and `ComposerState.video` becomes null

#### Scenario: Discarding the composer cancels the upload

- **WHEN** an upload is in progress and the user confirms discard
- **THEN** the job is cancelled

#### Scenario: A completed upload makes submission immediate

- **WHEN** the video reached `Ready` while the user was still typing and the user then submits
- **THEN** the post is created without re-uploading the video

### Requirement: `ComposerState` carries the video slot as a flat field

`ComposerState` SHALL carry `video: ComposerVideo?`, where `ComposerVideo` holds the source `uri`, the `alt` text, and the current `VideoUploadState`. Consistent with the composer's flat-state rule, the pipeline stage SHALL be read directly by Composables and SHALL NOT be wrapped in a generic remote-data type.

#### Scenario: Absent video is null, not an empty sentinel

- **WHEN** no video is attached
- **THEN** `state.video` is `null`

#### Scenario: Progress is projected onto state

- **WHEN** the repository emits `Uploading(0.4f)`
- **THEN** `state.video.uploadState` is `Uploading(0.4f)`

### Requirement: Submission is gated on the video reaching a terminal success

While `state.video` is non-null and its upload state is not `Ready`, the Post action SHALL be disabled. When the upload state is `Failed`, the Post action SHALL remain disabled and the composer SHALL surface the error with a retry affordance.

A post SHALL never be created that silently drops an attached video: if the user attached one, either it ships or the post does not.

#### Scenario: In-flight upload blocks posting

- **WHEN** `state.video.uploadState` is `Compressing`, `Uploading`, or `Processing`
- **THEN** the Post action is disabled

#### Scenario: Failed upload blocks posting and offers retry

- **WHEN** `state.video.uploadState` is `Failed`
- **THEN** the Post action is disabled and a retry affordance is shown alongside the error message

#### Scenario: Ready upload unblocks posting

- **WHEN** `state.video.uploadState` is `Ready`
- **THEN** the Post action follows the normal enablement rules

#### Scenario: Retry restarts the pipeline

- **WHEN** the user taps retry after a `Failed` upload
- **THEN** the pipeline restarts from the limits check for the same source URI

### Requirement: Video alt text reuses the existing alt editor

The composer SHALL allow an accessibility description for an attached video, edited through the same alt-editor layer used for images, opened in a single-item mode. The value SHALL be written to `app.bsky.embed.video`'s `alt` field on submit.

Unlike the gallery rule, blank video alt text SHALL NOT block submission — the gallery gate exists because a 5-plus-image post is unreadable without descriptions, and that reasoning does not transfer to a single video.

#### Scenario: Alt text reaches the wire record

- **WHEN** a video with alt text "sunset over the bay" is posted
- **THEN** the created record's `app.bsky.embed.video` carries `alt = "sunset over the bay"`

#### Scenario: Blank video alt does not block submission

- **WHEN** a video is attached with empty alt text and the upload is `Ready`
- **THEN** the Post action is enabled

### Requirement: A submitted video outranks every other media embed

When a post is submitted with an attached video whose upload reached `Ready`, the wire embed SHALL be `app.bsky.embed.video` carrying the blob, the alt text, and the aspect ratio. When a quoted post is also present, the embed SHALL be `app.bsky.embed.recordWithMedia` with the video as its media.

Video SHALL take priority over images, gallery, and external cards in the media slot. The mutual-exclusion rules make the conflict unreachable through the UI; the resolver states the precedence anyway so that no reachable state can silently drop the most expensive attachment the user provided.

#### Scenario: Video-only post emits a video embed

- **WHEN** a post with a ready video and no other media is submitted
- **THEN** the wire embed is `app.bsky.embed.video`

#### Scenario: Video plus quote emits recordWithMedia

- **WHEN** a post has a ready video and a quoted post
- **THEN** the wire embed is `app.bsky.embed.recordWithMedia` whose media is the video

## MODIFIED Requirements

### Requirement: Image attachments cap at 4 and use the system photo picker

The system SHALL allow up to 4 image attachments per composition. Attachments MUST be added via `androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia` configured with `maxItems = 4 - state.attachments.size` so the picker reflects the *remaining* capacity rather than the absolute cap. When the remaining capacity is `1`, the screen SHALL fall back to single-pick (`ActivityResultContracts.PickVisualMedia`) because `PickMultipleVisualMedia` rejects `maxItems < 2`. Because `rememberLauncherForActivityResult` captures the contract at registration time, the launcher Composable MUST be wrapped in a `key(remainingCapacity) { … }` block so the registration is refreshed when capacity changes. The "Add image" affordance MUST be hidden or disabled when `state.attachments.size == 4`. The reducer MUST defensively cap at 4 even if the picker returns more URIs. Removing an attachment MUST be possible from the attachment chip strip.

The picker's media-type filter SHALL be `PickVisualMedia.ImageAndVideo` rather than images-only, so a single entry point serves both media kinds. The reducer SHALL route the picker result by MIME type: `video/*` URIs populate the video slot (at most one, subject to the mutual-exclusion rule), and all other URIs populate `attachments` under the existing cap. When a multi-select returns a video alongside images, the reducer SHALL accept the first video and drop the images, because the two cannot coexist in one post.

#### Scenario: Picker invocation respects the cap

- **WHEN** `state.attachments.size == 2` and the user taps "Add image"
- **THEN** the launched picker is configured with `maxItems = 2` (remaining capacity), not the absolute cap of 4

#### Scenario: Reducer enforces the cap defensively

- **WHEN** `state.attachments.size == 3` and an `AddAttachments` event arrives carrying 3 image URIs
- **THEN** the next state has `attachments.size == 4` (one new URI accepted, two dropped)

#### Scenario: Add-image affordance disabled at the cap

- **WHEN** `state.attachments.size == 4`
- **THEN** the rendered "Add image" affordance has `enabled == false`

#### Scenario: Attachment removal mutates state

- **WHEN** `state.attachments` contains three items and a `RemoveAttachment(index = 1)` event is dispatched
- **THEN** the next state has `attachments.size == 2` and item at original index 1 is absent

#### Scenario: Picker offers both images and video

- **WHEN** the media picker is launched from an empty composer
- **THEN** it is configured with `PickVisualMedia.ImageAndVideo`

#### Scenario: A picked video routes to the video slot

- **WHEN** the picker returns a single URI whose MIME type is `video/mp4`
- **THEN** `state.video` becomes non-null and `state.attachments` is unchanged

#### Scenario: A mixed multi-select keeps the video and drops the images

- **WHEN** the picker returns one video URI and two image URIs
- **THEN** `state.video` is populated from the video URI and `state.attachments` remains empty

### Requirement: One embed per post

A post carries exactly one media embed, so the composer SHALL treat a picked KLIPY item (GIF or sticker) as mutually exclusive with photo attachments, SHALL have it replace an auto-detected link-card embed (they share the single external-embed slot), and MAY combine it with a quote (published as `app.bsky.embed.recordWithMedia`).

An attached video SHALL be mutually exclusive with photo attachments, with a KLIPY item, and with an auto-detected link card, and SHALL be limited to one per post — `app.bsky.embed.video` accepts a single blob and the lexicon provides no multi-video form. Attaching a video SHALL clear any existing photos, KLIPY item, or link card; while a video is attached, the photo, KLIPY, and link-card entry points SHALL be disabled and no link card SHALL be auto-detected. Removing the video SHALL restore link-card auto-detection for a URL still present in the text, mirroring the existing images-cleared-the-card behavior. A video MAY combine with a quote.

#### Scenario: Photos block the KLIPY entry point
- **WHEN** the composer already has photo attachments
- **THEN** the KLIPY picker entry point is disabled

#### Scenario: An attached KLIPY item blocks adding photos
- **WHEN** the composer already has a KLIPY item attached
- **THEN** adding photos is blocked

#### Scenario: A picked KLIPY item replaces an auto-detected link card
- **WHEN** a link-card embed is showing and the user picks a KLIPY item
- **THEN** the link card is cleared and the KLIPY item takes the external-embed slot

#### Scenario: A KLIPY item may coexist with a quote
- **WHEN** the composer has a quote attached and the user picks a KLIPY item
- **THEN** both are kept, and the post is published as record-with-media

#### Scenario: Attaching a video clears other media
- **WHEN** the composer has photo attachments or a KLIPY item or a link card, and the user attaches a video
- **THEN** the photos, KLIPY item, and link card are cleared and only the video remains

#### Scenario: An attached video blocks the other media entry points
- **WHEN** the composer has a video attached
- **THEN** the photo picker, KLIPY picker, and link-card auto-detection are all disabled

#### Scenario: Only one video may be attached
- **WHEN** the composer has a video attached and the picker returns another video
- **THEN** the newly picked video replaces the existing one rather than being added alongside it

#### Scenario: Removing the video restores link-card detection
- **WHEN** a link card was cleared by attaching a video, and the user removes the video while the URL is still in the text
- **THEN** the card is re-detected and restored automatically

#### Scenario: A video may coexist with a quote
- **WHEN** the composer has a quote attached and the user attaches a video
- **THEN** both are kept, and the post is published as record-with-media
