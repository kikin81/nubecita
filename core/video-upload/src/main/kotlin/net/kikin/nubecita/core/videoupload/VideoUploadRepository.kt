package net.kikin.nubecita.core.videoupload

import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Publishes a local video to Bluesky and yields the blob that
 * `app.bsky.embed.video` needs.
 *
 * Bluesky video does **not** go through `com.atproto.repo.uploadBlob`. The
 * pipeline mints a service-auth token against the user's PDS, uploads to a
 * separate host, polls an asynchronous transcode job, and only then has a blob.
 * All of that is hidden behind this one function: callers observe
 * [VideoUploadState] and nothing else.
 */
interface VideoUploadRepository {
    /**
     * Start publishing the video at [uri].
     *
     * The returned flow is **cold** — collection starts the work, and
     * cancelling the collecting coroutine aborts the transcode and any
     * in-flight request. Nothing is uploaded until someone collects.
     *
     * Emits a non-decreasing sequence of stages terminating in exactly one
     * [VideoUploadState.Ready] or [VideoUploadState.Failed], after which the
     * flow completes. It does not throw for expected failures; those arrive as
     * [VideoUploadState.Failed] so a caller can render them without a
     * try/catch around collection.
     *
     * No cleanup call is issued for an abandoned upload — the server-side job
     * expires on its own, and inventing a cancel request would add a failure
     * mode without removing one.
     */
    fun upload(uri: Uri): Flow<VideoUploadState>
}
