package net.kikin.nubecita.feature.search.impl

import java.io.IOException

/**
 * UI-resolvable error categories surfaced by the Search Posts tab.
 * The screen maps each variant to a stringResource call when rendering
 * — the VM stays Android-resource-free. Mirrors the [FeedError] shape
 * in `:feature:feed:impl`.
 *
 * `RateLimited` is reserved for an eventual atproto-kotlin 429 typed
 * exception; until the SDK exposes one, the mapping in
 * [toSearchPostsError] falls through to [Unknown]. When the SDK surface
 * lands, extend the `when` branch — no contract change at the VM/UI
 * boundary.
 */
internal sealed interface SearchPostsError {
    /** Underlying network or transport failure. */
    data object Network : SearchPostsError

    /** Bluesky / atproto rate-limit response (HTTP 429). */
    data object RateLimited : SearchPostsError

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    data class Unknown(
        val cause: String?,
    ) : SearchPostsError
}

internal fun Throwable.toSearchPostsError(): SearchPostsError =
    when (this) {
        is IOException -> SearchPostsError.Network
        // Future: add a branch for the SDK's typed 429 exception when it
        // exists (file an upstream issue against kikin81/atproto-kotlin
        // if it doesn't; we have a reference memory for that pattern).
        else -> SearchPostsError.Unknown(cause = message)
    }
