package net.kikin.nubecita.feature.bookmarks.impl

import java.io.IOException

/**
 * UI-resolvable error categories surfaced by the Bookmarks list. The
 * screen maps each variant to a `stringResource` call when rendering —
 * the ViewModel stays Android-resource-free. Mirrors the
 * [net.kikin.nubecita.feature.search.impl.SearchPostsError] shape.
 */
internal sealed interface BookmarksError {
    /** Underlying network or transport failure. */
    data object Network : BookmarksError

    /** Anything else (server 5xx, decode failure, unexpected throwable). */
    data class Unknown(
        val cause: String?,
    ) : BookmarksError
}

internal fun Throwable.toBookmarksError(): BookmarksError =
    when (this) {
        is IOException -> BookmarksError.Network
        else -> BookmarksError.Unknown(cause = message)
    }
