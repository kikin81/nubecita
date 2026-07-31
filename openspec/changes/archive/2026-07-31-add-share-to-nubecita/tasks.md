# Tasks — Share to Nubecita

Four vertical slices, each a branch + PR. A→B ships text/URL sharing end-to-end; C adds the image; D is validation. Bead ids are cross-linked once the epic is cut.

## Slice A — Composer prefill params (text) `[bead: nubecita-9xoz.1]`

- [ ] A1 Extend `ComposerRoute` (`:feature:composer:api`) with `sharedText: String? = null` and `sharedImageUri: String? = null` (defaults preserve existing call sites). Keep it a `@Serializable NavKey`.
- [ ] A2 `ComposerViewModel` (`:impl`): when `route.sharedText != null` and no `mentionHandle`, seed `TextFieldState(initialText = route.sharedText)`. Confirm the existing `ExternalLinkDetector`/`TextLinkScanner` `snapshotFlow` picks up a seeded URL and produces the link card (no new scanning code).
- [ ] A3 Unit tests (`:impl`): VM seeds text from `sharedText`; a seeded URL resolves to an `externalLink` card via the existing scanner; `mentionHandle` still wins if both set (or define precedence explicitly).
- [ ] A4 Screenshot fixture: composer prefilled with a URL (link card) and with plain text.
- [ ] A5 `:feature:composer:impl:testDebugUnitTest` + `validateDebugScreenshotTest` green; existing composer tests untouched.

## Slice B — `ACTION_SEND` entry point (text/URL end-to-end) `[bead: nubecita-9xoz.2]`

- [ ] B1 `ShareIntentParser` (`:core:posting`, pure): `Intent` → `SharedContent` sum type (Text / Url / Image / UrlWithImage / Invalid). Scheme allowlist (`http`/`https` only), length cap, reject own-authority `content://`. No IO.
- [ ] B2 Unit tests (`:core:posting`): accept http/https; reject `javascript:`/`file:`/`content:`/`intent:`; length cap; plain-text vs URL; `EXTRA_SUBJECT` ignored for text seeding; screenshot+URL parses both; empty/malformed extras → Invalid.
- [ ] B3 Manifest (`:app`): add an `ACTION_SEND` intent-filter to `MainActivity` for **`text/plain` only** in this slice (`category.DEFAULT`). The `image/*` filter is deliberately deferred to Slice C so that if B merges independently, Nubecita never advertises itself as an image share target before image handling exists.
- [ ] B4 `MainActivity.handleIntent`: new `ACTION_SEND`/`ACTION_SEND_MULTIPLE`(reject multiple in v1) branch **before** the `uri == null` FCM early-return. Parse via `ShareIntentParser`; on Text/Url → `deepLinkRouter.publish(ComposerRoute(sharedText = …))`; then **strip the share extras in place** — remove `EXTRA_TEXT`/`EXTRA_STREAM` + clear `clipData` on the existing intent (mutate, matching the existing `intent.data = null` convention; do NOT `setIntent(Intent())`). Re-run the same branch from `onNewIntent`.
- [ ] B5 New user-facing strings (error copy) + **es-419 + pt-BR translations in the same commit** (repo `MissingTranslation` guard; run the touched module's own lint).
- [ ] B6 `:app:assembleProductionDebug` links; unit tests green. Bench smoke: `adb shell am start -n net.kikin.nubecita/.MainActivity -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT "https://example.com"` → composer opens carded.

## Slice C — Single image `[bead: nubecita-9xoz.3]`

- [ ] C0 Manifest (`:app`): add the `image/*` (single) `ACTION_SEND` intent-filter to `MainActivity` — deferred from Slice B so the image share-target advertisement lands together with image handling.
- [ ] C1 `SharedMediaStore` (`:core:posting`): `copyIn(uri): Uri?` (**null when rejected or failed** — oversize, non-image, or IO error) — main-safe by contract via `withContext(ioDispatcher)` using an **injected `@IoDispatcher`** (not hardcoded `Dispatchers.IO`); bounded read with hard byte cap (reject over), `ContentResolver.getType` **+ magic-byte header sniff** (drop non-images), `runCatching` around all IO **rethrowing `CancellationException`** (`if (it is CancellationException) throw it`); writes to `filesDir/composer_shares/<uuid>.<ext>`. Plus `delete(uri)` and `sweepOrphans(maxAge)`.
- [ ] C2 Unit tests (`:core:posting`): byte-cap rejection; declared-image-but-non-image-bytes rejection; IO failure → null/failure (no crash); `sweepOrphans` age boundary; own-authority reject already covered in B.
- [ ] C3 `MainActivity` branch: for Image / UrlWithImage → `SharedMediaStore.copyIn` off-main-thread → `ComposerRoute(sharedText = url?, sharedImageUri = copiedUri)`. "images win": UrlWithImage keeps the URL as text, attaches the image (no card).
- [ ] C4 `ComposerViewModel`: from `sharedImageUri`, build `ComposerAttachment(appOwnedUri, verifiedMime)` and dispatch `ComposerEvent.AddAttachments`. Read the **app-owned** copy at upload, never the original `content://`.
- [ ] C5 Lifecycle cleanup wiring: delete the copy on (a) publish success, (b) `ViewModel.onCleared()` if not published, (c) attachment removed in-composer; call `SharedMediaStore.sweepOrphans()` at app startup. Verify the composer VM is scoped to the nav entry so `onCleared` == dismiss.
- [ ] C6 Graceful missing-file: a dangling `sharedImageUri` on restore degrades to "image unavailable" (uses the C1 `runCatching`), never crashes.
- [ ] C7 Unit tests (`:impl`): AddAttachments dispatched from `sharedImageUri`; "images win" with both; cleanup invoked on publish / onCleared / remove. Screenshot fixture: composer with a single shared image attached.

## Slice D — Validation `[bead: nubecita-9xoz.7]`

- [ ] D1 Instrumented test (`run-instrumented` label): `ACTION_SEND` `text/plain` intent → composer opens prefilled (pattern: `LoginScreenInstrumentationTest`). Optionally an image-share case.
- [ ] D2 On-device bench smoke on a real device + the fold: share a URL from Chrome and a photo from Gallery → composer opens correctly in both postures (phone full-screen, tablet dialog). Screenshot evidence.
- [ ] D3 Security spot-checks: `adb` a `file://`/`javascript:` `EXTRA_TEXT` → rejected (composer opens empty or not at all, no crash); an oversized stream → rejected, no OOM; a `content://` pointing at our own authority → rejected.
- [ ] D4 Run the compose-expert gate over the composer diff (adds `@Composable`-adjacent prefill wiring).

## Cross-cutting

- [ ] X1 Confirm no regression to existing `MainActivity` intent handling (ACTION_VIEW deep links, OAuth redirect, FCM tap) — the new branch precedes only the `uri == null` path.
- [ ] X2 `openspec validate --strict` passes; archive the change after the epic's slices merge.
