package net.kikin.nubecita.core.posting

import net.kikin.nubecita.core.image.EncodedImage

/**
 * Boundary to the external-link preview service (CardyB). Owns both compose-time
 * metadata resolution and post-time thumbnail download, so the rest of the app
 * never talks to the service directly.
 *
 * The provider is swappable by reimplementing this interface — nothing CardyB-
 * specific leaks past it.
 */
interface ExternalLinkMetadataRepository {
    /**
     * Resolve [url] into a [LinkPreview], or `null` when there is no usable card
     * (service error, network/timeout failure, or no title). A blank description
     * is NOT a failure. [LinkPreview.uri] is the service's redirect-resolved URL,
     * falling back to [url] when the service omits it.
     */
    suspend fun fetch(url: String): LinkPreview?

    /**
     * Download the preview thumbnail at [imageUrl] as an [EncodedImage] (raw bytes
     * + response `Content-Type`), size-guarded against OOM. Returns `null` on
     * failure or when the image exceeds the blob size limit. Best-effort: callers
     * post the card without a thumbnail when this returns `null`.
     */
    suspend fun downloadThumb(imageUrl: String): EncodedImage?
}
