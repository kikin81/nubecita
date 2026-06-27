# Tasks: Composer paste-a-link external embeds

Tracked by `nubecita-gfli`. Slices are PR-sized; land in order.

## 1. CardyB metadata repository (`:core:posting`)
- [ ] 1.1 Add `LinkPreview(uri, title, description, imageUrl)` internal domain model.
- [ ] 1.2 Add `ExternalLinkMetadataRepository` interface: `fetch(url): LinkPreview?`, `downloadThumb(imageUrl): ByteArray?`.
- [ ] 1.3 `CardyBExternalLinkMetadataRepository` impl using the singleton Ktor `HttpClient` from `:core:auth` DI; short timeout; map `{title,description,image,error}` (blank/`error`/network → null).
- [ ] 1.4 Hilt binding for the repository.
- [ ] 1.5 Unit tests with Ktor `MockEngine`: success → `LinkPreview`; blank/`error` → null; network fail → null; `downloadThumb` success/fail.

## 2. URL detection + shared scanner (`:feature:composer:impl`)
- [ ] 2.1 `ExternalLinkDetector` (pure): first `http(s)` URL excluding Bluesky quote-links + an exclude set; unit tests.
- [ ] 2.2 `TextLinkScanner<T>` helper (detect / alreadyHandled / onDetected + memoized attempts); unit tests.
- [ ] 2.3 Refactor existing quote-link detection onto `TextLinkScanner` (behavior-preserving); confirm existing quote tests pass.

## 3. Composer state + detection wiring (`:feature:composer:impl`)
- [ ] 3.1 `ExternalLinkStatus` sealed type + `ComposerState.externalLink` field.
- [ ] 3.2 Wire an external `TextLinkScanner` into the existing `snapshotFlow` collector; `alreadyHandled` = images present || card loaded.
- [ ] 3.3 `launchExternalFetch` → Loading→Loaded / silent Idle on failure (memoized).
- [ ] 3.4 `RemoveExternalLink` event → Idle (memoized); clear card when images are added.
- [ ] 3.5 VM unit tests (drive `snapshotFlow` via `Snapshot.sendApplyNotifications()` + `runCurrent()`): detect→Loading→Loaded; quote-link excluded; images suppress; adding images clears; dismiss memoizes; failure silent.

## 4. Composer card UI (`:feature:composer:impl`)
- [ ] 4.1 `ComposerLinkCard` composable: spinner while Loading; thumb/title/description/domain + dismiss `X` while Loaded (Coil loads the CardyB image URL).
- [ ] 4.2 Host it in `ComposerScreenContent` below the text (mutually exclusive with the attachment row per state).
- [ ] 4.3 `@Preview` + screenshot fixtures (Loaded + Loading); regenerate baselines via the `update-baselines` CI label.

## 5. Embed resolution + thumbnail (`:core:posting`)
- [ ] 5.1 `ComposerEmbedIntent.external: PreparedExternal?` (uri/title/description/thumbImageUrl).
- [ ] 5.2 `resolveEmbed` external arm per the truth table: external-only → external; external+quote → recordWithMedia; external+images → dropped.
- [ ] 5.3 Best-effort thumbnail: `downloadThumb` → size-guarded `uploadBlob` → `external.thumb`; any failure → `Missing`, post proceeds.
- [ ] 5.4 Unit tests: external-only; external+quote → recordWithMedia; external+images → dropped; thumb success → Defined; thumb failure → Missing + post proceeds.

## 6. Verification
- [ ] 6.1 `spotlessApply`, `:core:posting:testDebugUnitTest`, `:feature:composer:impl:testProductionDebugUnitTest` (or the module's flavor), `lintDebug`, `:app:assembleProductionDebug`.
- [ ] 6.2 Compose review gate (compose-touched) → compose-expert.
- [ ] 6.3 On-device smoke on a bench build (paste URL → card → post) via the android CLI.
