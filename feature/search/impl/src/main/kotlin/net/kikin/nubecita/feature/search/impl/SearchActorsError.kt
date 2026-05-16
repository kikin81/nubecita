package net.kikin.nubecita.feature.search.impl

import java.io.IOException

/**
 * UI-resolvable error categories surfaced by the Search People tab.
 * Mirrors the [SearchPostsError] shape — same lifecycle, same mapping
 * extension pattern. Atproto-kotlin doesn't expose a typed
 * RateLimited (429) exception today; the branch is reserved for that
 * eventual SDK surface — when it lands, extend the `when` below; no
 * contract change at the VM/UI boundary.
 */
internal sealed interface SearchActorsError {
    /** Underlying network or transport failure. */
    data object Network : SearchActorsError

    /** Bluesky / atproto rate-limit response (HTTP 429). */
    data object RateLimited : SearchActorsError

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    data class Unknown(
        val cause: String?,
    ) : SearchActorsError
}

internal fun Throwable.toSearchActorsError(): SearchActorsError =
    when (this) {
        is IOException -> SearchActorsError.Network
        // Future: SDK-typed 429 exception. File against
        // kikin81/atproto-kotlin (per the project's existing convention)
        // when needed.
        else -> SearchActorsError.Unknown(cause = message)
    }
