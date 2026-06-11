package net.kikin.nubecita.feature.widgets.impl.image

import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-disk store for the widgets' pre-decoded post thumbnails (D-C5).
 *
 * Layout: `<noBackupFilesDir>/widget_thumbs/<account>/<postKey>.jpg`, where
 * `<account>` is the (sanitized) account DID and `<postKey>` is a hash of the
 * post AT-URI (`PostUi.id`) — AT-URIs contain `/` and `:` so they can't be file
 * names directly. The widget's Glance state holds only the small file path; the
 * bitmap bytes live here, off the prefs.
 *
 * **Lives under `noBackupFilesDir`, not `cacheDir` or `filesDir`:** unlike
 * `cacheDir`, the OS won't evict it under storage pressure (which would silently
 * drop decoded thumbnails and leave empty placeholders until the next refresh);
 * unlike plain `filesDir`, it's excluded from cloud backup / device transfer, so
 * this rebuildable user-media cache doesn't bloat backups or persist off-device.
 * We bound growth ourselves via [evict] / [clearAccount].
 *
 * The image analogue of `:core:feed-cache`'s `trimToCap`: [evict] bounds this
 * directory to the posts currently in a feed's head, and [clearAccount] drops
 * an account's whole tree on logout. Pure file I/O (no Android `Bitmap`), so
 * it's JVM-unit-testable against a temp dir — the bitmap compression itself
 * lives in [ThumbnailDecoder].
 *
 * Constructed with the widget-thumbnails root; the Hilt graph supplies
 * `<noBackupFilesDir>/widget_thumbs` via the secondary `@Inject` constructor.
 */
@Singleton
class WidgetThumbnailStore(
    private val root: File,
) {
    @Inject
    constructor(
        @ApplicationContext context: android.content.Context,
    ) : this(File(context.noBackupFilesDir, ROOT_DIR_NAME))

    /**
     * The thumbnail [File] for ([accountDid], [postId]), creating the account
     * directory if needed. The file itself may or may not exist yet — callers
     * write the decoded bitmap into it.
     */
    fun thumbnailFile(
        accountDid: String,
        postId: String,
    ): File {
        val dir = accountDir(accountDid).apply { mkdirs() }
        return File(dir, fileName(postId))
    }

    /** Whether a thumbnail file currently exists for ([accountDid], [postId]). */
    fun hasThumbnail(
        accountDid: String,
        postId: String,
    ): Boolean = File(accountDir(accountDid), fileName(postId)).exists()

    /**
     * Evict every thumbnail for [accountDid] whose post is **not** in
     * [keepPostIds] — i.e. posts that have scrolled out of the feed's head.
     * Bounds the cache to ~`head(n)` thumbnails per feed.
     */
    fun evict(
        accountDid: String,
        keepPostIds: Set<String>,
    ) {
        val keepFiles = keepPostIds.mapTo(HashSet(keepPostIds.size)) { fileName(it) }
        accountDir(accountDid).listFiles()?.forEach { file ->
            if (file.name !in keepFiles) file.delete()
        }
    }

    /** Drop all of [accountDid]'s thumbnails (logout / account removal). */
    fun clearAccount(accountDid: String) {
        accountDir(accountDid).deleteRecursively()
    }

    private fun accountDir(accountDid: String): File = File(root, sanitize(accountDid))

    private fun fileName(postId: String): String = "${sha256Hex(postId)}.jpg"

    private companion object {
        const val ROOT_DIR_NAME = "widget_thumbs"

        /** Keep DID directory names file-system safe without losing uniqueness. */
        fun sanitize(value: String): String = value.replace(UNSAFE_CHARS, "_")

        val UNSAFE_CHARS = Regex("[^A-Za-z0-9._-]")

        fun sha256Hex(value: String): String =
            MessageDigest
                .getInstance("SHA-256")
                .digest(value.toByteArray(Charsets.UTF_8))
                .joinToString(separator = "") { "%02x".format(Locale.US, it) }
    }
}
