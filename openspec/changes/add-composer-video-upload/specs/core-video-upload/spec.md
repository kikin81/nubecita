## ADDED Requirements

### Requirement: `VideoUploadRepository` exposes the pipeline as an observable state machine

The system SHALL expose `VideoUploadRepository` in `:core:video-upload` declaring cold flow `upload(uri: Uri): Flow<VideoUploadState>` terminating in `Ready` or `Failed`. `VideoUploadState` SHALL be a sealed interface with variants `CheckingLimits`, `Compressing(progress: Float)`, `Uploading(progress: Float)`, `Processing(progress: Float)`, `Ready(blob: Blob, aspectRatio: AspectRatio)`, and `Failed(error: VideoUploadError)`. Progress values SHALL be in `0f..1f`.

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

The system SHALL call `app.bsky.video.getUploadLimits` and evaluate `canUpload` before starting compression. When `canUpload` is `false`, the pipeline SHALL terminate with `Failed(VideoUploadError.NotPermitted(message))` carrying the server-supplied message and SHALL NOT invoke the transcoder, avoiding unnecessary re-encoding on unverified or quota-exhausted accounts.

#### Scenario: Rejected account never transcodes

- **WHEN** `getUploadLimits` returns `canUpload = false` with a message about email verification
- **THEN** the pipeline emits `CheckingLimits` then `Failed(NotPermitted)` carrying that message, and the transcoder is never invoked

#### Scenario: Permitted account proceeds to compression

- **WHEN** `getUploadLimits` returns `canUpload = true`
- **THEN** the pipeline proceeds to `Compressing`

### Requirement: Compression bounds output size as a function of clip duration

The system SHALL re-encode clips with `Transformer` targeting H.264/AAC with longest edge capped at 1080px. For positive duration, target bitrate SHALL be `min(defaultBitrate, (SIZE_BUDGET_BYTES * 8) / durationSeconds)` below 100MB cap; unreadable duration falls back to `defaultBitrate`. Transcoded files exceeding the service cap SHALL terminate with `CompressionFailed`.

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

The system SHALL derive `AspectRatio` from `METADATA_KEY_VIDEO_WIDTH` and `METADATA_KEY_VIDEO_HEIGHT`, swapping width and height when rotation is 90 or 270 degrees. When dimensions are non-positive or unreadable, the system SHALL omit the aspect ratio rather than publishing placeholders.

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

The system SHALL obtain a service-auth token via `com.atproto.server.getServiceAuth` against the user's PDS (`aud = did:web:<pds-host>`, `lxm = com.atproto.repo.uploadBlob`, `exp = 30m`) and POST compressed bytes to `https://video.bsky.app/xrpc/app.bsky.video.uploadVideo` with parameters `did` and `name`, `Authorization: Bearer <token>`, `Content-Type: video/mp4`, and explicit `Content-Length`. Requests SHALL NOT route through the shared `XrpcClient`.

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

After upload, the system SHALL poll `app.bsky.video.getJobStatus` with `jobId` until receiving a non-null `blob`, mapping intermediate states to `Processing(progress)`. Unrecognized states SHALL be treated as in-progress. Job failure SHALL terminate with `Failed(VideoUploadError.ProcessingFailed(message))`.

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
