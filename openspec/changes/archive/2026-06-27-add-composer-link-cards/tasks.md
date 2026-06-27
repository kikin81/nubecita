# Tasks: Composer paste-a-link external embeds

Tracked by `nubecita-gfli`. Slices are PR-sized; land in order.

## 1. CardyB metadata repository (`:core:posting`)
- [x] 1.1 Add `LinkPreview(uri, title, description, imageUrl)` internal domain model.
- [x] 1.2 Add `ExternalLinkMetadataRepository` interface: `fetch(url): LinkPreview?`, `downloadThumb(imageUrl): ByteArray?`.
- [x] 1.3 `CardyBExternalLinkMetadataRepository` impl using the singleton Ktor `HttpClient` from `:core:auth` DI; short timeout; **URL-encode** the `url` param; `null` only on `error`/network/timeout/no-title; blank description allowed; default missing title/description to `""`. `LinkPreview.uri` ← CardyB's returned **`url`** (redirect-resolved final destination), falling back to the typed URL if absent. Verify redirect-following live against a real shortener (`bit.ly`).
- [x] 1.4 `downloadThumb(imageUrl): EncodedImage?` — capture response `Content-Type`; **size guard** (reject via `Content-Length`, cap streamed read at the ~1 MB blob limit) to avoid OOM.
- [x] 1.5 Hilt binding for the repository.
- [x] 1.6 Unit tests with Ktor `MockEngine`: success → `LinkPreview`; `error`/no-title → null; blank description → card with `""`; **resolved `url` ≠ typed → `LinkPreview.uri` is the resolved one**; missing `url` → falls back to typed; network fail → null; `downloadThumb` success / oversize-rejected / fail.

## 2. URL detection + shared scanner (`:feature:composer:impl`)
- [x] 2.1 `ExternalLinkDetector` (pure): first `http(s)` URL excluding Bluesky quote-links + an exclude set; unit tests.
- [x] 2.2 `TextLinkScanner<T>` helper (detect / alreadyHandled / onDetected + memoized attempts); unit tests.
- [x] 2.3 Refactor existing quote-link detection onto `TextLinkScanner` (behavior-preserving); confirm existing quote tests pass.

## 3. Composer state + detection wiring (`:feature:composer:impl`)
- [x] 3.1 `ExternalLinkStatus` sealed type + `ComposerState.externalLink` field.
- [x] 3.2 Wire an external `TextLinkScanner` into the existing `snapshotFlow` collector; `alreadyHandled` = images present || card loaded.
- [x] 3.3 `launchExternalFetch` as a tracked `Job` → Loading→Loaded / silent Idle on failure (memoized). Re-check `Loading(url)` + no-images before → Loaded; cancel the Job when images are added or the card is cleared (race guard).
- [x] 3.4 `RemoveExternalLink` (manual dismiss) → Idle + **memoize**; image-induced auto-clear → Idle **without** memoizing (so removing images restores the card).
- [x] 3.5 VM unit tests (drive `snapshotFlow` via `Snapshot.sendApplyNotifications()` + `runCurrent()`): detect→Loading→Loaded; quote-link excluded; images suppress; adding images clears; **removing images restores**; manual dismiss memoizes; late fetch after images attached does NOT show a card; failure silent.

## 4. Composer card UI (`:feature:composer:impl`)
- [x] 4.1 `ComposerLinkCard` composable: spinner while Loading; thumb/title/description/domain + dismiss `X` while Loaded (Coil loads the CardyB image URL).
- [x] 4.2 Host it in `ComposerScreenContent` below the text (mutually exclusive with the attachment row per state).
- [x] 4.3 `@Preview` + screenshot fixtures (Loaded + Loading); regenerate baselines via the `update-baselines` CI label.

## 5. Embed resolution + thumbnail (`:core:posting`)
- [x] 5.1 `ComposerEmbedIntent.external: PreparedExternal?` (uri/title/description/thumbImageUrl).
- [x] 5.2 `resolveEmbed` external arm per the truth table: external-only → external; external+quote → recordWithMedia; external+images → dropped.
- [x] 5.3 Best-effort thumbnail: `downloadThumb` (EncodedImage) → reuse `encoder.encodeForUpload(bytes, mimeType)` (compresses oversize) → `uploadBlob(encoded.bytes, encoded.mimeType)` → `external.thumb`; any failure → `Missing`, post proceeds.
- [x] 5.4 Unit tests: external-only; external+quote → recordWithMedia; external+images → dropped; thumb success → Defined; thumb failure → Missing + post proceeds.

## 6. Verification
- [x] 6.1 `spotlessApply`, `:core:posting:testDebugUnitTest`, `:feature:composer:impl:testProductionDebugUnitTest` (or the module's flavor), `lintDebug`, `:app:assembleProductionDebug`.
- [x] 6.2 Compose review gate (compose-touched) → compose-expert.
- [x] 6.3 On-device smoke on a bench build (paste URL → card → post) via the android CLI.
