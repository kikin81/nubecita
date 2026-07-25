## 1. SDK — `getServiceAuth` (blocks everything else)

- [ ] 1.1 Add `generator/lexicons/com/atproto/server/getServiceAuth.json` to `../atproto-kotlin`, faithful to upstream (`aud`, `lxm`, `exp` params; `token` output). Regenerate and confirm `ServerService.getServiceAuth` appears in `models/build/generated/`. Test: the generator's existing snapshot/smoke suite covers the new type.
- [ ] 1.2 Publish `:models` to mavenLocal via the signing-disabled init script; verify nubecita resolves it with `-PuseMavenLocal=true` and a one-line throwaway call compiles. No test — this is the dev-loop gate for tasks 2.x.
- [ ] 1.3 Cut an `atproto-kotlin` release containing 1.1 and bump the `atproto` version in `gradle/libs.versions.toml`, dropping `-PuseMavenLocal=true`. Test: `./gradlew testDebugUnitTest` green against the released pin.

## 2. `:core:video-upload` module scaffold

- [ ] 2.1 Create the module: `build.gradle.kts` applying `nubecita.android.library` + `nubecita.android.hilt`, namespace, and an **empty `consumer-rules.pro`** (CI Build/Lint fail at the consumer-proguard merge without it; local `assembleProductionDebug` does not catch it). Register in `settings.gradle.kts`. Test: `./gradlew :core:video-upload:assembleProductionDebug`.
- [ ] 2.2 Add `androidx.media3:media3-transformer` to the version catalog (reusing the existing `media3` version ref) and wire it into the module. Test: `./gradlew :app:checkSortDependencies`.
- [ ] 2.3 Define the public surface — `VideoUploadRepository`, `VideoUploadState`, `VideoUploadError` — plus the Hilt module binding an unscoped implementation. Test: `VideoUploadStateTest` asserting the sealed hierarchy is exhaustive and `Ready`/`Failed` are the only terminal variants.

## 3. Pipeline stages

- [ ] 3.1 Limits probe: call `getUploadLimits` against a `video.bsky.app`-scoped `XrpcClient` built with a `defaultRequest` block attaching `Authorization: Bearer <serviceAuthToken>` — the endpoint is documented "for the authenticated user" and `XrpcClient` takes credentials from its `HttpClient`, not per call; it must NOT reuse the PDS client, whose DPoP OAuth credentials are wrong for this host. Map `canUpload == false` to `Failed(NotPermitted(message))`. Test: `VideoUploadLimitsTest` with Ktor `MockEngine` — permitted proceeds, rejected terminates and never invokes the transcoder (assert via a spy transcoder).
- [ ] 3.2 Source probe: `MediaMetadataRetriever` for width / height / rotation / duration, with the 90-and-270 width-height swap, and **omit** the aspect ratio when either dimension is non-positive or unreadable (`aspectRatio` is `AtField.Missing`-able; a substituted 1:1 letterboxes in every client). Test: `VideoAspectRatioTest`, table-driven over rotations 0/90/180/270 including the portrait case and the zero/absent-dimension case.
- [ ] 3.3 Compression: Media3 `Transformer` with `targetBitrate = min(DEFAULT_BITRATE, (SIZE_BUDGET_BYTES * 8) / durationSeconds)` for positive durations and `DEFAULT_BITRATE` otherwise (MediaMetadataRetriever returns null for a corrupt container — guard the divisor), 1080px longest edge, H.264/AAC, progress mapped through `asUploadProgress()`. Verify the encoded file against the cap **after** transcoding and fail with `CompressionFailed` if it exceeds — the fallback path abandons the computed bound, so the check makes it enforced rather than assumed. Test: `VideoBitrateTest` — long clip gets a lower bitrate than short, short clip capped at the default, non-positive duration falls back without dividing, and an oversized encode fails before any upload.
- [ ] 3.4 Service auth: `getServiceAuth(aud = "did:web:<pdsHost>", lxm = "com.atproto.repo.uploadBlob", exp = now + 1800)` against the PDS client. Test: `ServiceAuthRequestTest` asserting all three parameter values — these are the counter-intuitive ones (`aud` is the PDS, `lxm` is the blob method).
- [ ] 3.5 Upload leg: raw Ktor `POST` to `video.bsky.app/xrpc/app.bsky.video.uploadVideo?did=…&name=…` with bearer auth, `video/mp4`, explicit `Content-Length`, and `onUpload` → `Uploading(progress)`. Test: `VideoUploadRequestTest` with `MockEngine` asserting host, query params, `Authorization` header shape, absence of a DPoP proof, and monotonic progress.
- [ ] 3.6 Job polling: `getJobStatus` until a blob appears; unknown states treated as in-progress; explicit failure → `ProcessingFailed(message)`. Test: `VideoJobPollingTest` — resolves to blob, surfaces a failure message, and does **not** fail on an unrecognised state string.
- [ ] 3.7 Assemble the stages into `upload(uri)` and verify cancellation tears down the transcoder and the in-flight request. Test: `VideoUploadPipelineTest` — full happy-path ordering, terminal-state exclusivity, and cancellation mid-`Uploading`.

## 4. Composer state and reducer

- [ ] 4.1 Add `ComposerVideo(uri, alt, uploadState)` and `ComposerState.video: ComposerVideo?`; add the `VideoPicked` / `RemoveVideo` / `RetryVideoUpload` / `SetVideoAlt` events. Test: `ComposerVideoStateTest` covering each event's state transition.
- [ ] 4.2 Widen the picker to `PickVisualMedia.ImageAndVideo` in `core/image/.../ImagePicker.kt` and route the result by MIME type in the reducer, including the mixed-multi-select rule (keep the video, drop the images) and a `ComposerEffect` explaining the drop — a user who picks six items and sees one appear cannot tell a constraint from a bug. Test: extend `ComposerViewModelTest` — video-only, image-only, mixed picker results, and the effect emission.
- [ ] 4.3 Implement mutual exclusion: attaching a video clears photos / KLIPY / link card and disables their entry points; removing it restores link-card auto-detection; a second video replaces the first. Test: `ComposerVideoExclusionTest`, one case per scenario in the modified "One embed per post" requirement.
- [ ] 4.4 Wire the eager upload — start on `VideoPicked` in `viewModelScope`, mirror emissions into state, cancel on remove and on discard, restart on retry. Test: `ComposerVideoUploadLifecycleTest` with a fake repository driving a `MutableStateFlow`.
- [ ] 4.5 Gate submission on `Ready`; keep Post disabled during any in-flight stage and on `Failed`. Test: extend `ComposerViewModelTest` submit-gate cases for each `VideoUploadState`.

## 5. Composer UI

- [ ] 5.1 Video attachment card with thumbnail, per-stage progress indicator, and a remove affordance. Test: `ComposerVideoCardScreenshotTest` — one baseline per stage (`CheckingLimits`, `Compressing`, `Uploading`, `Processing`, `Ready`, `Failed`).
- [ ] 5.2 Failure presentation with an actionable message per `VideoUploadError` variant plus a retry affordance. Test: covered by the `Failed` baseline in 5.1 plus a `ComposerVideoErrorMessageTest` mapping each error variant to its string resource.
- [ ] 5.3 Open the existing alt editor in single-item mode for video; blank alt does not block submission. Test: extend the composer screenshot suite with a video-alt-editor baseline; unit-test the no-gate rule.
- [ ] 5.4 Add all new strings with `values-b+es+419` and `values-pt-rBR` entries **in the same commit**. Test: `./gradlew :feature:composer:impl:lintProductionDebug` (the module's own lint — `:app:lintProductionDebug` misses `MissingTranslation`).

## 6. Posting

- [ ] 6.1 Add `video: UploadedVideo?` to `ComposerEmbedIntent` and a video branch at the top of `resolveEmbed` (`DefaultPostingRepository.kt:471`), including the `recordWithMedia` form when a quote is present. Test: extend `DefaultPostingRepositoryTest` — video-only emits `app.bsky.embed.video`; video-plus-quote emits `recordWithMedia`; video outranks images and external.
- [ ] 6.2 Thread the ready blob, alt, and aspect ratio from `ComposerState.video` through `createPost`. Test: extend `ComposerViewModelTest` asserting the submitted intent carries the video.

## 7. Bench, verification, and rollout

- [ ] 7.1 Add a bench-flavor `FakeVideoUploadRepository` and register it, so the composer works in the offline bench build. Test: `./gradlew :app:assembleBenchDebug` compiles (bench-only exhaustive `when`s over the new sealed types are a known CI-only failure mode).
- [ ] 7.2 Device pass on the Pixel Fold (`37201FDHS002UN`), both postures: pick a real recording, watch every stage, publish, and confirm the post renders with correct orientation. Separately verify a portrait recording is not letterboxed, and capture the observed duration ceiling to fill the open question in `design.md`.
- [ ] 7.3 Device pass on failure paths: airplane-mode mid-upload (`Network`), remove-during-upload (cancellation), and retry-after-failure. Record the codec and configuration logged on any `CompressionFailed`.
- [ ] 7.4 Fill in `DEFAULT_BITRATE` / `SIZE_BUDGET_BYTES` / the duration ceiling from 7.2–7.3 measurements and update `design.md`'s Open Questions section with the observed values and date.
- [ ] 7.5 Full gate: `./gradlew spotlessCheck lint :app:checkSortDependencies testDebugUnitTest` plus the composer screenshot validation. Verify `spotlessApply` cold (`--no-build-cache --rerun-tasks --no-daemon`) if any `atproto-kotlin` source was touched in task group 1.
