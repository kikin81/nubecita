## Why

Nubecita plays video everywhere — feed embeds, the media viewer, and a whole vertical
video feed — but it cannot post one. A user who records a clip has to leave for the
official app to share it, which undercuts the "this is my Bluesky client" proposition
more than any missing read surface would.

The gap is not a small one to close honestly: Bluesky video does not go through
`com.atproto.repo.uploadBlob`. It requires a service-auth token, an upload to a separate
host, an asynchronous server-side transcode job, and a client-side compression step
without which most phone recordings exceed the 100MB cap before they are a minute long.

## What Changes

- **New `:core:video-upload` module** owning the full pipeline as a single observable
  state machine: check limits → compress → upload → poll job → emit blob.
- **Client-side compression** via `androidx.media3:media3-transformer`, with the target
  bitrate derived from clip duration so the output size is structurally bounded rather
  than hoped-for.
- **Eager upload on selection.** Picking a video starts the pipeline immediately and
  shows progress inside the composer, so submitting is usually instant. Removing the
  video or discarding the composer cancels it.
- **Composer gains a video slot**, mutually exclusive with images, GIF, and link cards
  (the lexicon permits exactly one video and forbids mixing it with other media).
- **`app.bsky.embed.video`** becomes the highest-priority branch in the posting
  repository's embed resolver, carrying `video`, `alt`, and `aspectRatio`.
- **Alt text for video**, reusing the composer's existing alt editor in single-item mode.
- **SDK dependency**: `com.atproto.server.getServiceAuth` must be added to
  `atproto-kotlin` — it is the one lexicon the pipeline needs that the SDK does not
  currently generate.

### Non-goals

- **Captions / subtitles.** `app.bsky.embed.video` accepts a `captions` array of WebVTT
  files; shipping a caption authoring or import flow is a separate change.
- **In-app trimming.** The user picks a clip as recorded; range selection is deferred.
- **Recording from within the composer.** The system picker is the only entry point.
- **Video in DMs, profile headers, or anywhere outside the post composer.**
- **Resumable / chunked upload.** A dropped connection restarts the upload leg; the
  service-auth token's 30-minute lifetime makes a plain retry sufficient.
- **Multi-video posts.** Forbidden by the lexicon; not a deferral, a permanent bound.

### Baseline deviations

- **New non-Compose dependency**: `androidx.media3:media3-transformer` (Media3 is already
  pinned at 1.10.1 for playback; this adds the transcoding artifact from the same BOM
  line). Justified in `design.md` — without it the feature rejects most real recordings.
- **Raw Ktor call outside the atproto SDK.** The `uploadVideo` leg targets a different
  host with a different auth scheme than every other call in the app, and needs Ktor's
  `onUpload` progress callback, which `XrpcClient.procedure` does not expose. This is a
  deliberate, documented exception to "all networking goes through the SDK", scoped to
  one function in one module.
- **Cross-repo change.** Slice 1 lands in `../atproto-kotlin` and must be released (or
  consumed via mavenLocal) before the rest can compile.

Everything else stays on the MVI / Compose / Hilt baseline: the pipeline is a repository
behind an interface, the composer holds its state in `ComposerState`, and errors route
through `UiEffect`.

## Capabilities

### New Capabilities
- `core-video-upload`: the video publishing pipeline — upload-limit probing, client-side
  compression, service-auth acquisition, upload to the Bluesky video service, job-status
  polling, cancellation, and the terminal blob + aspect-ratio result. Owns every failure
  mode the network and the transcoder can produce.

### Modified Capabilities
- `feature-composer`: gains a video attachment slot with its own progress and alt-text
  states; the "One embed per post" mutual-exclusion rule extends to cover video; the
  system picker requirement widens from images-only to image-or-video; and the submitted
  embed gains an `app.bsky.embed.video` branch that outranks images, gallery, and
  external cards.

Note: the embed-resolution requirements live in `feature-composer`, not `core-posting` —
the `core-posting` spec currently covers only `ActorTypeaheadRepository` (its Purpose is
still an unreconciled `TBD` from `add-composer-mention-typeahead`). The `:core:posting`
*module* changes, but no `core-posting` *requirement* does, so no delta is written there.

## Impact

**New code**
- `:core:video-upload` — new Android library module (needs an empty `consumer-rules.pro`,
  per the convention-plugin `consumerProguardFiles` declaration).

**Modified code**
- `core/posting/.../internal/DefaultPostingRepository.kt` — `resolveEmbed` (currently
  line 471) and `createPost`.
- `core/posting/.../internal/ComposerEmbedIntent.kt` — new `video` field.
- `core/image/.../ImagePicker.kt` — `ImageAndVideo` picker mode.
- `feature/composer/impl` — state, events, reducer, attachment row, alt editor, submit gate.

**Dependencies**
- `androidx.media3:media3-transformer` added to the version catalog.
- `atproto-kotlin` bumped to a release containing `com.atproto.server.getServiceAuth`.

**External services**
- First traffic to `video.bsky.app` (upload, job status, upload limits).

**Test surface**
- `:core:video-upload` JVM tests over the state machine with Ktor `MockEngine`.
- Composer unit tests for the mutual-exclusion rules and submit gating.
- New composer screenshot fixtures for each pipeline stage.
- A bench-flavor fake, or the composer breaks in the offline bench build.

**Localization**
- New user-facing strings need `values-b+es+419` and `values-pt-rBR` entries in the same
  commit, or the module's own lint fails on `MissingTranslation`.

**Known collision**
- The drafts epic (`nubecita-4ok`) must decide what a saved draft holding a partially
  uploaded video means. Flagged here; resolved in whichever change lands second.
