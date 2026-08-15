package net.kikin.nubecita.core.image

/**
 * Sentinel exception surfaced by [ImageSaver] when the image bytes could not
 * be obtained — absent from the image cache and the network fetch also failed.
 *
 * Distinct from [ImageStorageException] so the caller can tell the user their
 * connection is the problem rather than their storage.
 *
 * Exposed as a public type (rather than matched on `simpleName`) so consumers
 * can `catch` / pattern-match without a stringly-typed check — the latter
 * breaks under R8 minification. Mirrors `:core:posts`'s
 * `PostRepositoryExceptions`.
 */
class ImageRetrievalException(
    url: String,
    cause: Throwable? = null,
) : RuntimeException("could not obtain image bytes for ${url.substringAfterLast('/')}", cause)

/**
 * Sentinel exception surfaced by [ImageSaver] when the bytes were obtained but
 * the gallery write failed — no space, a revoked volume, or a provider refusal.
 */
class ImageStorageException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

/**
 * Sentinel exception surfaced by [ImageSaver.saveToGallery] when called on a
 * device where [ImageSaver.isSupported] is `false`.
 *
 * Callers are expected to hide their save affordance rather than reach this;
 * it exists so a programming error fails loudly instead of appearing to the
 * user as a generic storage failure, and so the gate can never be satisfied
 * by silently requesting a permission the app does not declare.
 */
class ImageSaveUnsupportedException : RuntimeException("saving to the shared gallery is not supported on this platform version")
