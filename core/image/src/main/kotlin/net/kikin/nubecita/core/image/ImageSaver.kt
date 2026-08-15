package net.kikin.nubecita.core.image

import android.net.Uri

/**
 * Writes a remote image into the device's shared photo gallery — the
 * outbound counterpart to [ImagePicker].
 *
 * Saved images land in a `Nubecita` album under the device's standard
 * pictures directory.
 *
 * ## Why [isSupported] lives on this interface
 *
 * Writing to the shared gallery needs no permission from API 29, but API 28
 * requires `WRITE_EXTERNAL_STORAGE`. The app declares **no** storage
 * permission — that would put its first *dangerous* permission on the Play
 * listing, visible to every prospective user, to serve a measured 1.0% of
 * active users (see the `add-media-viewer-save-to-gallery` change's design,
 * decision D3). API 28 is therefore gated out rather than served.
 *
 * Exposing that gate as a property here — instead of a `Build.VERSION` check
 * at each call site — keeps platform branching out of ViewModels and
 * composables entirely, and lets the gated-out path be tested with a plain
 * fake rather than a Robolectric SDK override.
 *
 * **Do not "fix" this by adding the permission.** Read D3 first.
 */
interface ImageSaver {
    /**
     * Whether this device can save to the shared gallery without the app
     * declaring a storage permission. `false` on API 28.
     *
     * Callers should hide their save affordance entirely when this is
     * `false` — a control that can never work is worse than no control.
     */
    val isSupported: Boolean

    /**
     * Copies the image at [url] into the shared photo gallery.
     *
     * Bytes are read from the shared image cache when available, so an image
     * the user is already looking at is not downloaded a second time, and are
     * written through unchanged — the saved file is byte-identical to what
     * the server served, with no decode/re-encode step.
     *
     * Returns the saved item's [Uri] on success. On failure the exception is
     * one of [ImageRetrievalException], [ImageStorageException] or
     * [ImageSaveUnsupportedException], so callers can tell the causes apart
     * when choosing what to tell the user.
     */
    suspend fun saveToGallery(url: String): Result<Uri>
}
