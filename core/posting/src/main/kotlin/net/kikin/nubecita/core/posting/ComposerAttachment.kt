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
 * The lexicon caps `app.bsky.embed.images` at 4 entries; the picker
 * is configured with `maxItems` set to the remaining capacity, the
 * composer's reducer caps at 4 defensively, and the repository does
 * not re-validate (the call site is the source of truth).
 */
data class ComposerAttachment(
    val uri: Uri,
    val mimeType: String,
)
