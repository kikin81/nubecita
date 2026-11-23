package net.kikin.nubecita.feature.starterpack.impl

import java.io.IOException

/** UI-resolvable error categories for the starter-pack screen. */
internal sealed interface StarterPackError {
    /** Network or transport failure. */
    data object Network : StarterPackError

    /** Anything else: a bad pack URI, a server error, a response that did not decode. */
    data object Unknown : StarterPackError
}

internal fun Throwable.toStarterPackError(): StarterPackError =
    when (this) {
        is IOException -> StarterPackError.Network
        else -> StarterPackError.Unknown
    }
