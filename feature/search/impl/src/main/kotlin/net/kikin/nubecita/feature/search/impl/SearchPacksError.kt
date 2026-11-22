package net.kikin.nubecita.feature.search.impl

import java.io.IOException

/** UI-resolvable error categories for the Search Packs tab. Mirrors [SearchFeedsError]. */
internal sealed interface SearchPacksError {
    /** Underlying network or transport failure. */
    data object Network : SearchPacksError

    /** Bluesky / atproto rate-limit response (HTTP 429). */
    data object RateLimited : SearchPacksError

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    data class Unknown(
        val cause: String?,
    ) : SearchPacksError
}

internal fun Throwable.toSearchPacksError(): SearchPacksError =
    when (this) {
        is IOException -> SearchPacksError.Network
        else -> SearchPacksError.Unknown(cause = message)
    }
