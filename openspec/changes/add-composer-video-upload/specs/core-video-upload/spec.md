## ADDED Requirements

### Requirement: `VideoUploadRepository` exposes the pipeline as an observable state machine

The system SHALL expose `net.kikin.nubecita.core.videoupload.VideoUploadRepository` as a Kotlin interface in the `:core:video-upload` capability, declaring a single function `upload(uri: Uri): Flow<VideoUploadState>`. The returned flow SHALL be cold — collection starts the pipeline, cancellation of the collecting coroutine aborts it — and SHALL emit a strictly non-decreasing sequence of stages terminating in exactly one of `Ready` or `Failed`. No consumer SHALL be required to know that the pipeline spans three hosts and two auth schemes.

`VideoUploadState` SHALL be a sealed interface with the variants `CheckingLimits`, `Compressing(progress: Float)`, `Uploading(progress: Float)`, `Processing(progress: Float)`, `Ready(blob: Blob, aspectRatio: AspectRatio)`, and `Failed(error: VideoUploadError)`. All `progress` values SHALL be in `0f..1f`.

#### Scenario: Happy path emits every stage in order

- **WHEN** `upload(uri)` is collected for a clip that passes limits, compresses, uploads, and completes server-side processing
- **THEN** the emitted stages are `CheckingLimits`, then one or more `Compressing`, then one or more `Uploading`, then zero or more `Processing`, then exactly one `Ready` carrying a non-null blob and aspect ratio, and the flow completes

#### Scenario: Terminal states are exclusive and final

- **WHEN** the pipeline emits `Ready` or `Failed`
- **THEN** no further state is emitted and the flow completes normally

#### Scenario: Cancelling collection aborts the pipeline

- **WHEN** the collecting coroutine is cancelled during `Compressing` or `Uploading`
- **THEN** the transcode and any in-flight HTTP request are cancelled, and no further state is emitted

### Requirement: Upload limits are checked before any transcoding work begins

The system SHALL call `app.bsky.video.getUploadLimits` and evaluate `canUpload` **before** starting compression. When `canUpload` is `false`, the pipeline SHALL terminate with `Failed(VideoUploadError.NotPermitted(message))` carrying the server-supplied `message` verbatim, and SHALL NOT invoke the transcoder.

This ordering is normative, not incidental: transcoding is the most expensive stage in both battery and thermal budget, and the two real rejection causes — an unverified account email and an exhausted daily quota — are both knowable before a single frame is re-encoded.

#### Scenario: Rejected account never transcodes

- **WHEN** `getUploadLimits` returns `canUpload = false` with a message about email verification
- **THEN** the pipeline emits `CheckingLimits` then `Failed(NotPermitted)` carrying that message, and the transcoder is never invoked

#### Scenario: Permitted account proceeds to compression

- **WHEN** `getUploadLimits` returns `canUpload = true`
- **THEN** the pipeline proceeds to `Compressing`

### Requirement: Compression bounds output size as a function of clip duration

The system SHALL re-encode the source clip with `androidx.media3.transformer.Transformer` before upload, targeting H.264 video and AAC audio with the longest edge capped at 1080 px. For a strictly positive `durationSeconds`, the target video bitrate SHALL be computed as `min(defaultBitrate, (SIZE_BUDGET_BYTES * 8) / durationSeconds)` where `SIZE_BUDGET_BYTES` is a margin below the 100 MB service cap, so that output size is bounded by construction rather than by assumption.

When the duration is non-positive or cannot be read — `MediaMetadataRetriever` returns null for a corrupt container — the system SHALL fall back to `defaultBitrate` rather than dividing.

Because that fallback abandons the computed bound, the system SHALL additionally verify the encoded file against the service cap after transcoding and terminate with `CompressionFailed` if it exceeds it, rather than beginning an upload the service will reject. This check applies on every path, so the size bound is enforced rather than merely computed.

A fixed bitrate SHALL NOT be used: at any bitrate high enough to look acceptable on a short clip, a clip near the duration limit would exceed the cap.

#### Scenario: Long clip gets a proportionally lower bitrate

- **WHEN** two clips of the same source resolution are compressed, one 15 seconds and one 3 minutes
- **THEN** the 3-minute clip is encoded at a strictly lower target bitrate than the 15-second clip

#### Scenario: Short clip is not over-compressed

- **WHEN** a clip is short enough that the duration-derived bitrate exceeds the default
- **THEN** the default bitrate is used, not the higher duration-derived value

#### Scenario: Compressed output respects the service cap

- **WHEN** any clip within the accepted duration limit completes compression
- **THEN** the produced file is no larger than the service's 100 MB cap

#### Scenario: Unreadable duration falls back instead of dividing

- **WHEN** the source's duration metadata is absent, zero, or negative
- **THEN** the target bitrate is `defaultBitrate` and no division is performed

#### Scenario: An oversized encode fails instead of uploading

- **WHEN** the transcoded file exceeds the service cap, whichever bitrate path produced it
- **THEN** the pipeline terminates with `Failed(CompressionFailed)` and no upload is attempted

### Requirement: Aspect ratio accounts for container rotation metadata

The system SHALL derive the `AspectRatio` written to `app.bsky.embed.video` from the source's `METADATA_KEY_VIDEO_WIDTH` and `METADATA_KEY_VIDEO_HEIGHT`, and SHALL swap width and height when `METADATA_KEY_VIDEO_ROTATION` is `90` or `270`.

When either dimension is non-positive or unreadable, the system SHALL **omit** the aspect ratio rather than publishing a placeholder. `app.bsky.embed.video`'s `aspectRatio` is optional (`AtField.Missing`), so omission is representable — and a substituted 1:1 would be a silent lie that every client renders, letterboxing the video exactly as an unrotated value would. An absent ratio lets each client fall back to its own measurement; a wrong one does not.

Portrait phone recordings are commonly stored as landscape frames plus a 90-degree rotation flag. Reporting the unrotated dimensions would make every such video render letterboxed in every AT Protocol client, not only in Nubecita.

#### Scenario: Portrait recording reports portrait dimensions

- **WHEN** the source reports width 1920, height 1080, rotation 90
- **THEN** the emitted aspect ratio is width 1080, height 1920

#### Scenario: Landscape recording is unchanged

- **WHEN** the source reports width 1920, height 1080, rotation 0
- **THEN** the emitted aspect ratio is width 1920, height 1080

#### Scenario: Unreadable dimensions omit the ratio rather than substituting one

- **WHEN** either reported dimension is zero, negative, or absent
- **THEN** no aspect ratio is emitted and `app.bsky.embed.video` is written without the field

### Requirement: The upload leg uses service auth against the video service host

The system SHALL obtain a service-auth token via `com.atproto.server.getServiceAuth` against the user's PDS with `aud` set to `did:web:<pds-host>`, `lxm` set to `com.atproto.repo.uploadBlob`, and `exp` set to 30 minutes ahead. It SHALL then `POST` the compressed bytes to `https://video.bsky.app/xrpc/app.bsky.video.uploadVideo` with the query parameters `did` and `name`, the header `Authorization: Bearer <serviceAuthToken>`, `Content-Type: video/mp4`, and an explicit `Content-Length`.

The `lxm` value SHALL be `com.atproto.repo.uploadBlob` and not `app.bsky.video.uploadVideo`; the `aud` SHALL be the user's PDS and not the video service. Both are counter-intuitive and both are required by the service.

This request SHALL NOT be routed through the shared `XrpcClient`: that client is bound to a single PDS `baseUrl` and installs DPoP-bound OAuth credentials, neither of which applies to this host, and it exposes no upload-progress callback.

#### Scenario: Service auth is requested with the documented parameters

- **WHEN** the pipeline reaches the upload stage
- **THEN** `getServiceAuth` is called with `aud = "did:web:<pds-host>"`, `lxm = "com.atproto.repo.uploadBlob"`, and an `exp` approximately 1800 seconds in the future

#### Scenario: Upload targets the video service with a plain bearer token

- **WHEN** the compressed bytes are uploaded
- **THEN** the request goes to `video.bsky.app`, carries `Authorization: Bearer <serviceAuthToken>` with no DPoP proof, and includes `did` and `name` query parameters

#### Scenario: Upload progress is reported continuously

- **WHEN** bytes are being transmitted
- **THEN** `Uploading(progress)` is emitted repeatedly with a non-decreasing fraction of bytes sent

### Requirement: Job status is polled until the blob is available

After a successful upload the system SHALL poll `app.bsky.video.getJobStatus` with the returned `jobId` until the response carries a non-null `blob`, mapping intermediate responses to `Processing(progress)` from the job's `progress` field. A job state indicating failure SHALL terminate the pipeline with `Failed(VideoUploadError.ProcessingFailed(message))` carrying the job's `error` or `message`.

Because the lexicon documents that any unrecognized state means the job is still running, the implementation SHALL treat unknown states as in-progress rather than as failures.

#### Scenario: Polling resolves to a blob

- **WHEN** `getJobStatus` returns a completed job carrying a blob
- **THEN** the pipeline emits `Ready` with that blob

#### Scenario: Failed job surfaces the server message

- **WHEN** `getJobStatus` returns a failed job with an error message
- **THEN** the pipeline emits `Failed(ProcessingFailed)` carrying that message

#### Scenario: Unknown job state is treated as in-progress

- **WHEN** `getJobStatus` returns a state string the client does not recognize and no blob
- **THEN** the pipeline continues polling and does not fail

### Requirement: Failure modes are distinguishable by the caller

`VideoUploadError` SHALL be a sealed interface distinguishing at minimum `NotPermitted` (limits or account state, carrying the server message), `TooLong` (source exceeds the accepted duration), `CompressionFailed`, `UploadFailed`, `ProcessingFailed`, and `Network`. Callers SHALL be able to select an actionable message and decide retryability without inspecting strings.

#### Scenario: Caller distinguishes a quota rejection from a network drop

- **WHEN** the pipeline fails because `canUpload` was false, versus because the socket closed mid-upload
- **THEN** the emitted `Failed` carries `NotPermitted` in the first case and `Network` in the second

### Requirement: The module ships the standard library-module scaffolding

`:core:video-upload` SHALL apply the `nubecita.android.library` and `nubecita.android.hilt` convention plugins, declare its namespace, and ship an empty `consumer-rules.pro`. The repository SHALL be bound via Hilt as an unscoped binding.

An absent `consumer-rules.pro` fails the CI Build and Lint jobs at the consumer-proguard merge step while passing a local `assembleProductionDebug`, so it is stated here as a requirement rather than left to the build.

#### Scenario: Module exposes only its interface

- **WHEN** a consumer module depends on `:core:video-upload`
- **THEN** `VideoUploadRepository`, `VideoUploadState`, and `VideoUploadError` are visible, and the Ktor client, transcoder, and polling loop are not
