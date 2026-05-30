package net.kikin.nubecita.core.image.internal

import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import net.kikin.nubecita.core.image.ImageByteSource
import javax.inject.Inject

/**
 * Production [ImageByteSource] backed by the application
 * `ContentResolver`. Used for `content://` URIs returned by
 * `ActivityResultContracts.PickVisualMedia`.
 */
internal class ContentResolverImageByteSource
    @Inject
    constructor(
        @param:ApplicationContext private val context: android.content.Context,
    ) : ImageByteSource {
        override suspend fun read(uri: Uri): ByteArray =
            requireNotNull(context.contentResolver.openInputStream(uri)) {
                // Redacted message — content URIs (e.g. `content://media/external/.../12345`)
                // can carry user-identifying segments and may end up in
                // Crashlytics or logcat if this exception escapes. The
                // URI scheme alone is sufficient diagnostic signal; the
                // image index is added by the caller for traceability.
                "ContentResolver returned null InputStream for ${uri.scheme}://[redacted]"
            }.use { it.readBytes() }
    }
