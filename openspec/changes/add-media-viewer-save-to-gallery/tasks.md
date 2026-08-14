## 1. `:core:image` — the save capability

- [ ] 1.1 Add Coil to `:core:image`'s `build.gradle.kts` (`platform(libs.coil.bom)` + the core artifact); the module has no Coil dependency today. Run the **root** `checkSortDependencies` afterwards, not the `:app:`-scoped one.
- [ ] 1.2 Add `ImageSaver` to `:core:image` — `val isSupported: Boolean` and `suspend fun saveToGallery(url: String, altText: String?): Result<Uri>`. KDoc why `isSupported` exists on the interface (keeps `Build.VERSION` out of the VM and composable, and makes the gated path fakeable).
- [ ] 1.3 Add typed save exceptions mirroring `:core:posts`'s `PostRepositoryExceptions.kt` — at minimum a retrieval failure, a storage failure, and an unsupported-platform failure.
- [ ] 1.4 Add a pure content-type sniffer that reads the leading magic bytes and returns MIME + file extension for JPEG, PNG and WebP, with a defined fallback for unrecognised bytes.
- [ ] 1.5 Add pure filename generation for the saved entry (stable, collision-tolerant — `MediaStore` de-duplicates, so this must not attempt its own uniqueness scheme).
- [ ] 1.6 Implement `internal/DefaultImageSaver`: read the Coil disk-cache snapshot for the URL; on a miss, execute the `ImageRequest` and re-read; stream the bytes into a `MediaStore.Images` row with `RELATIVE_PATH = Pictures/Nubecita` and `IS_PENDING = 1`, clearing the flag only after a complete write. Write `altText` to `DESCRIPTION` when present. Run on `@IoDispatcher`. **Do not decode/re-encode.**
- [ ] 1.7 Implement `isSupported` as the API-29+ check, with a comment recording the store-listing rationale and the 1.0% measurement from `design.md` D3 — so a future reader does not "fix" it by adding the permission.
- [ ] 1.8 Bind `ImageSaver` → `DefaultImageSaver` in the existing `ImageModule`.

## 2. `:designsystem` — the download glyph

- [ ] 2.1 Pick the Material Symbols codepoint for the download/save glyph and add the corresponding `NubecitaIconName` entry.
- [ ] 2.2 Re-subset the icon font via `scripts/update_material_symbols.sh` for that codepoint only. **Do not regenerate the whole font** — that is the documented way this step breaks unrelated glyphs.
- [ ] 2.3 Verify the glyph renders (not a tofu box) and that no pre-existing icon changed, by diffing the rendered output rather than trusting a green build.

## 3. `:feature:mediaviewer:impl` — contract and presenter

- [ ] 3.1 Add `:core:image` to the module's `build.gradle.kts`.
- [ ] 3.2 Extend `MediaViewerContract`: `OnSaveClick` event; `isSaving: Boolean` and `canSave: Boolean` on `MediaViewerLoadStatus.Loaded`; `ShowSaveOutcome` effect carrying a **typed** outcome (no user-facing string — the contract's existing KDoc commits to the VM staying Android-resource-free).
- [ ] 3.3 Inject `ImageSaver` into `MediaViewerViewModel`; set `canSave` from `isSupported` when entering `Loaded`.
- [ ] 3.4 Handle `OnSaveClick`: ignore when `isSaving` is already true; set `isSaving`; call `saveToGallery` with the **current page's** `fullsizeUrl` and `altText`; map the `Result` to a typed outcome effect; clear `isSaving` on both success and failure.

## 4. `:feature:mediaviewer:impl` — screen

- [ ] 4.1 Add the save `IconButton` to the top chrome so it fades with the existing `AnimatedVisibility`, and omit it entirely when `canSave` is false (absent, not disabled).
- [ ] 4.2 Add a `SnackbarHost` inside the chrome `Box` — the screen has no `Scaffold` today — and collect `ShowSaveOutcome`, mapping each typed outcome to a `stringResource`.
- [ ] 4.3 Render the in-flight state with the brand progress indicator. A raw indeterminate spinner is rejected by the `check_progress_indicators.sh` pre-commit hook.
- [ ] 4.4 Add the new strings to `values/strings.xml` **and** to `values-es-r419/` and `values-pt-rBR/` in the same commit, then run this module's own lint (`:app` lint does not catch `MissingTranslation` here).

## 5. Tests

- [ ] 5.1 JVM unit tests for the content-type sniffer (JPEG/PNG/WebP plus the unrecognised fallback) and for filename generation.
- [ ] 5.2 Instrumented `androidTest` for `DefaultImageSaver`, mirroring `:core:posting`'s `DefaultSharedMediaStoreTest`: asserts the row lands under `Pictures/Nubecita`, that saved bytes are byte-identical to the source, that the recorded content type matches the sniffed type, and that `altText` reaches `DESCRIPTION`.
- [ ] 5.3 Instrumented coverage for the cache-hit path specifically — assert no network request is issued when the image is already cached, so the D2 optimisation cannot silently regress into the fetch fallback.
- [ ] 5.4 ViewModel tests with a fake `ImageSaver`: `isSaving` toggles and clears on both outcomes; a second `OnSaveClick` during a save starts no second save; the correct outcome effect is emitted per `Result`; `canSave` is false when the fake reports unsupported.
- [ ] 5.5 Verify the tests discriminate — mutate the current-page selection to always save index 0 and confirm a test fails. Both modules are **unflavored**: use plain `testDebugUnitTest` / `lintDebug`.

## 6. Baselines and gate

- [ ] 6.1 Regenerate `:feature:mediaviewer:impl` screenshot baselines and commit **only** the genuinely changed images; `git checkout --` the antialiasing-noise churn. Local `validate*ScreenshotTest` is not a usable gate on macOS — CI's `screenshot` job is the authority.
- [ ] 6.2 Run the gate: `:core:image` and `:feature:mediaviewer:impl` tests and lint, root `spotlessCheck`, root `checkSortDependencies`.
- [ ] 6.3 Confirm the merged manifest still declares **no** storage or media permission — this is the change's central promise, so assert it rather than assume it.
- [ ] 6.4 Device pass on the Pixel Fold (`37201FDHS002UN`): save an image, confirm it appears in the device gallery under Nubecita, and confirm the alt text landed as the description.
