## Why

A viewer who opens an image fullscreen has no way to keep it. Every mainstream Bluesky client offers save-to-gallery, and its absence is the kind of gap that reads as unfinished rather than deliberate.

The reason to do it now — and to spec it — is that the obvious implementation carries a hidden cost that is easy to pay by accident: writing to shared storage on `minSdk 28` normally means declaring `WRITE_EXTERNAL_STORAGE`, which would put the first **dangerous** permission on a Play listing that currently declares none. That is a one-way door on the store listing, so the decision belongs in a spec rather than in a commit message.

## What Changes

- `:core:image` gains an `ImageSaver` capability — the outbound counterpart to its existing `ImagePicker` — that writes a remote image into the device's shared photo gallery.
- The fullscreen media viewer gains a save affordance in its top chrome, acting on the currently-paged image.
- The viewer gains a snackbar host (it has no `Scaffold` today) to report save outcomes.
- The design system icon font gains a download glyph; it currently has 51 icons and none of them fit.
- **No new Android permission is declared.** Devices that would require one are gated out of the affordance instead (see `image-save-to-gallery` spec).

Not breaking. No existing behaviour changes; the viewer's load/paging/zoom/dismiss requirements are untouched.

## Capabilities

### New Capabilities

- `image-save-to-gallery`: writing a remote image into the device's shared photo gallery — byte sourcing, MediaStore placement, metadata, failure reporting, and the platform-support gate that keeps the app's permission surface empty.

### Modified Capabilities

- `feature-mediaviewer`: adds a requirement that the viewer exposes a save affordance for the current image, reports its outcome, and hides the affordance where the platform cannot support it. Existing requirements are unchanged.

## Impact

**Code**

- `:core:image` — new `ImageSaver` interface, `internal/DefaultImageSaver`, typed save exceptions, binding in the existing `ImageModule`. Gains a Coil dependency (it has none today).
- `:feature:mediaviewer:impl` — new event, two state fields, one effect, a chrome icon button, a snackbar host. Gains a `:core:image` dependency.
- `:designsystem` — one new `NubecitaIconName` entry plus a re-subset Material Symbols font.

**Tests**

- New instrumented test in `:core:image` (`MediaStore` is not meaningfully fakeable on the JVM), mirroring `:core:posting`'s `DefaultSharedMediaStoreTest`.
- New JVM unit tests for the pure helpers; new ViewModel tests against a fake `ImageSaver`.
- `:feature:mediaviewer:impl` screenshot baselines shift because the chrome gains a button.

**Explicitly out of scope**

Save-all from a multi-image post, video download, an overflow-menu entry point, a Pro gate, and a Storage Access Framework fallback for the gated-out platform. Each is a clean follow-up.

**No impact**

Play Data safety declaration is unaffected — writing to the user's own device is neither collection nor sharing. `minSdk` stays at 28.
