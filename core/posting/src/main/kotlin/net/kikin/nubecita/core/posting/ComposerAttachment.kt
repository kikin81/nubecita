package net.kikin.nubecita.core.posting

import android.net.Uri

/**
 * One image attachment selected by the composer's photo picker.
 *
 * - [uri] is the system content URI returned by
 *   `ActivityResultContracts.PickVisualMedia` — typically a
 *   `content://` URI that the repository resolves through
 *   [android.content.ContentResolver.openInputStream] at upload time.
 * - [mimeType] is the picker-resolved MIME (`image/jpeg`,
 *   `image/png`, `image/webp`, etc.) used as the
 *   `inputContentType` argument to the SDK's `RepoService.uploadBlob(...)`.
 *
 * - [alt] is the per-image accessibility description. Empty by default
 *   (matching the pre-alt-editor behavior); the composer's alt-text editor
 *   populates it, and the gallery required-alt gate is enforced at the
 *   composer, not here.
 *
 * The lexicon caps `app.bsky.embed.images` at 4 entries and
 * `app.bsky.embed.gallery` at a soft 10; the picker's `maxItems` and the
 * composer's reducer enforce the cap. The repository does not re-validate the
 * count, but it DOES pick the wire embed by count — `app.bsky.embed.images`
 * for ≤4 (max interop), `app.bsky.embed.gallery` for ≥5.
 */
data class ComposerAttachment(
    val uri: Uri,
    val mimeType: String,
    val alt: String = "",
)
