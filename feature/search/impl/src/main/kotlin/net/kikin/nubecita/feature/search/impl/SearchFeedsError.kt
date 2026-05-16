package net.kikin.nubecita.feature.search.impl

import java.io.IOException

/**
 * UI-resolvable error categories surfaced by the Search Feeds tab.
 * Mirrors [SearchActorsError] / [SearchPostsError]. Atproto-kotlin
 * doesn't expose a typed RateLimited (429) exception today; the branch
 * is reserved for that eventual SDK surface — when it lands, extend
 * the `when` below; no contract change at the VM/UI boundary.
 */
internal sealed interface SearchFeedsError {
    /** Underlying network or transport failure. */
    data object Network : SearchFeedsError

    /** Bluesky / atproto rate-limit response (HTTP 429). */
    data object RateLimited : SearchFeedsError

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    data class Unknown(
        val cause: String?,
    ) : SearchFeedsError
}

internal fun Throwable.toSearchFeedsError(): SearchFeedsError =
    when (this) {
        is IOException -> SearchFeedsError.Network
        else -> SearchFeedsError.Unknown(cause = message)
    }
