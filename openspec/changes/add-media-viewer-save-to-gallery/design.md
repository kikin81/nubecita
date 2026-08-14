## Context

The fullscreen media viewer (`:feature:mediaviewer:impl`) renders a `HorizontalPager` of `ImageUi` with fading chrome, an alt-text sheet, pinch-zoom and swipe-down dismiss. It has no save action, and nothing anywhere in the repository writes to `MediaStore`.

Three pieces of existing state shape this design:

- **`:core:image` already owns image I/O** — `ImagePicker`, `ImageByteSource`, `ImageEncoder`, `CropImage`, and a `ContentResolver`-backed implementation, wired through its own `ImageModule`. It is the inbound half of exactly this concern.
- **Coil is a Hilt-provided singleton with a configured disk cache** (`:app/data/CoilModule.kt`, `NubecitaApplication` implements `SingletonImageLoader.Factory`). By the time a user can press save, the viewer has already put the fullsize bytes on disk.
- **`minSdk` is 28.** `MediaStore` writes to shared storage need no permission from API 29, but API 28 requires `WRITE_EXTERNAL_STORAGE`.

The permission question dominated the design. The application's merged production manifest currently declares `ACCESS_ADSERVICES_AD_ID`, `ACCESS_ADSERVICES_ATTRIBUTION`, `ACCESS_NETWORK_STATE`, `FOREGROUND_SERVICE`, `INTERNET`, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED` and `WAKE_LOCK` — **not one of them dangerous**. `WRITE_EXTERNAL_STORAGE` would be the first, and it lands in the "Photos and media" group that privacy-conscious users specifically look for.

## Goals / Non-Goals

**Goals:**

- Let a viewer save the image they are looking at into their device gallery.
- Add **zero** Android permissions.
- Do not re-download or re-encode an image the app already holds.
- Keep platform-version branching out of the ViewModel and the composable.

**Non-Goals:**

- Saving every image in a multi-image post at once.
- Video download.
- A second entry point (post overflow menu, long-press in feed).
- Any Pro entitlement gate.
- A Storage Access Framework fallback for API 28.

## Decisions

### D1: The capability lives in `:core:image`, not a new module and not the feature

`ImageSaver` is the outbound counterpart to `ImagePicker` and belongs beside it. `DefaultImageSaver` goes in `internal/`, bound in the existing `ImageModule`.

*Alternatives considered.* A new `:core:media-save` module — rejected as ceremony for one interface, and it would need its own `consumer-rules.pro` (a known CI trap in this repo: the file is required or the Build/Lint jobs fail, and a local `assembleDebug` does not catch it). Implementing inline in `:feature:mediaviewer:impl` — rejected because it puts `MediaStore` plumbing in a feature module, leaves no seam for a fake in ViewModel tests, and would have to be extracted the first time a second surface wants to save.

### D2: Bytes come from Coil's disk cache

Coil's `diskCacheKey` defaults to the data string, so the fullsize URL opens the exact snapshot the viewer displayed. Copying that file preserves the original encoding — no decode/re-encode — which keeps the saved image byte-identical to what the server sent and avoids the CPU and battery cost of a round trip through a `Bitmap`. If the snapshot is absent (the user pressed save before the load finished), execute the `ImageRequest` first, then read the snapshot.

*Alternatives considered.* `DownloadManager` — rejected on three counts: it re-downloads an image already on disk (battery is an explicit project priority), it writes to `Downloads` rather than `Pictures` so it does not reliably appear in the gallery, and it surrenders control of filename and album. Decoding to a `Bitmap` and calling `compress` — rejected as it re-encodes, degrading quality and burning CPU for no benefit.

### D3: API 28 is gated out via `ImageSaver.isSupported`, and no permission is declared

Measured before deciding. GA4, `platform = Android`, 90-day window:

| Android | API | Active users | Share |
|---|---:|---:|---:|
| 17 | 36 | 125 | 1.7% |
| 16 | 35 | 1,191 | 16.4% |
| 15 | 34 | 1,769 | 24.4% |
| 14 | 33 | 729 | 10.0% |
| 13 | 32 | 498 | 6.9% |
| 12 | 31 | 692 | 9.5% |
| 11 | 30 | 1,948 | 26.8% |
| 10 | 29 | 230 | 3.2% |
| **9** | **28** | **76** | **1.0%** |

**99.0% of users are on API 29+ and need no permission.** Declaring `WRITE_EXTERNAL_STORAGE` would put a Storage entry on the store listing seen by 100% of prospective users, to serve 1.0%. It could not be confirmed whether `android:maxSdkVersion="28"` removes a declaration from Play's listing display; that uncertainty is entirely downside, so the design avoids needing the permission at all.

Exposing this as `isSupported` on the interface — rather than a `Build.VERSION` check at the call site — keeps platform branching out of both the ViewModel and the composable, and makes the gated-out path testable with a plain fake instead of a Robolectric SDK override.

*Alternatives considered.* Declaring the permission with `maxSdkVersion="28"` — rejected per above. Raising `minSdk` to 29 — rejected as **strictly worse for the same users**: it removes all 76 from the application entirely rather than from one button. A SAF `ACTION_CREATE_DOCUMENT` fallback — rejected for now: it would serve those 76 with no permission, but adds a second save path reachable by 1% of users, which will be neither exercised nor meaningfully tested and will rot silently. It remains a clean follow-up if an Android 9 user actually asks.

### D4: `Result<Uri>` with typed exceptions

Mirrors `:core:posts`'s `PostRepositoryExceptions.kt`, the established pattern for distinguishable repository failures. The UI needs to tell "couldn't fetch" from "couldn't write", which a bare boolean cannot express.

### D5: The outcome effect carries a type, not a string

`MediaViewerContract`'s existing KDoc commits to the ViewModel staying free of Android resources — the screen maps each error variant to a `stringResource`. The new `ShowSaveOutcome` effect follows that precedent exactly, so the presenter stays unit-testable without a `Context`.

### D6: `IS_PENDING` during the write

Standard scoped-storage practice, and it is what makes the "a partially written image is never visible" requirement true rather than aspirational: the gallery cannot index the row until the flag clears.

## Risks / Trade-offs

- **Coil's disk cache key is a default, not a contract** → if a future `ImageRequest` sets a custom `diskCacheKey`, the snapshot lookup silently misses and every save takes the slow fetch path. Mitigation: the fetch fallback keeps it *correct*, only slower; the instrumented test covers the cache-hit path explicitly so a regression shows up as a failing assertion rather than a silent perf cliff.
- **Adding a glyph to the icon font is the riskiest mechanical step** → the font is a Material Symbols subset, and regenerating the whole font to add one icon is a known way to break unrelated glyphs. Mitigation: follow the codepoint recipe with `scripts/update_material_symbols.sh`, and diff the rendered result rather than trusting a green build.
- **Screenshot baselines will shift** → the chrome gains a button. Mitigation: regenerate, then commit only genuinely changed images; on macOS most baselines come back with 1/255 antialiasing noise, and CI's `screenshot` job is the authority.
- **1.0% of users get no save action, with no in-app explanation** → accepted deliberately. The alternative costs every prospective user a permission line. Android 9's share only shrinks, so this decays on its own.
- **`MediaStore` behaviour varies by OEM** → some skins index `Pictures/<app>` albums differently. Mitigation: the instrumented test asserts the row and its content type; exact album presentation is the gallery app's business.

## Migration Plan

Additive. No schema, no persisted state, no API change — nothing to migrate and nothing to roll back beyond reverting the change. The feature is inert until a user presses the new control.

## Open Questions

None blocking. Two deferred by choice, both recorded above: whether to add a SAF fallback for API 28 (revisit only on real user demand), and whether a second entry point in the post overflow menu is wanted (would force the multi-image disambiguation this design avoids).
