package net.kikin.nubecita.core.posting

import android.net.Uri

/**
 * Owns the lifecycle of images shared into Nubecita from another app. A shared
 * `content://` arrives under a transient, per-URI read grant that expires with
 * the launching intent, so it cannot be attached directly — it is copied into
 * app-private storage (`filesDir/composer_shares/`) first, and the composer
 * only ever reads the app-owned copy.
 *
 * All operations are `suspend` and run off the main thread. Every method fails
 * closed: a rejected/oversize/unverifiable source, an expired grant, or a
 * missing copy yields `null` / a no-op, never a crash (share-target design
 * Decisions 5–6).
 */
interface SharedMediaStore {
    /**
     * Copy a transient shared [source] `content://` into app-private storage and
     * return the app-owned `file://` [Uri], or `null` if the source is rejected
     * (points at our own provider, declares/contains non-image bytes, exceeds the
     * byte ceiling) or the read fails. Callable from the `MainActivity` share
     * branch; the returned URI survives process death and is safe to serialize
     * onto the composer route.
     */
    suspend fun copyIn(source: Uri): Uri?

    /**
     * Resolve a previously-[copyIn]'d app-owned URI string (as carried on
     * `ComposerRoute.sharedImageUri`) into a [ComposerAttachment], or `null` if
     * the copy is gone or unreadable — the graceful "image unavailable" path, so
     * a stale route degrades to a text-only composer instead of crashing.
     */
    suspend fun attachmentFor(sharedImageUri: String): ComposerAttachment?

    /**
     * Delete an app-owned copy once it is no longer referenced (attachment
     * removed, post published, composer discarded). A no-op if the URI is not one
     * of ours or is already gone.
     */
    suspend fun delete(uri: Uri)

    /**
     * Delete orphaned copies older than the retention window. The startup
     * backstop that bounds the directory when process death skips the in-session
     * cleanup triggers.
     */
    suspend fun sweepOrphans()
}
