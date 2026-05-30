package net.kikin.nubecita.core.image

import android.net.Uri

/**
 * One image selected via the system photo picker ([rememberImagePicker]).
 *
 * - [uri] is the system content URI returned by
 *   `ActivityResultContracts.PickVisualMedia` — typically a
 *   `content://` URI that callers resolve through an
 *   [ImageByteSource] (backed by
 *   [android.content.ContentResolver.openInputStream]) at read time.
 * - [mimeType] is the picker-resolved MIME (`image/jpeg`,
 *   `image/png`, `image/webp`, etc.). Callers forward it as the
 *   `inputContentType` argument to the SDK's `RepoService.uploadBlob(...)`.
 *
 * This is a neutral, domain-agnostic picker result: the composer maps
 * it onto its own `ComposerAttachment`, and the profile editor will
 * feed it straight into the crop pipeline. `:core:image` carries no
 * posting/profile knowledge.
 */
data class PickedImage(
    val uri: Uri,
    val mimeType: String,
)
