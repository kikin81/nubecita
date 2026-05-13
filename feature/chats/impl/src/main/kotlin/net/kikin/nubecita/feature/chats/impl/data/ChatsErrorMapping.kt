package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.feature.chats.impl.ChatsError
import java.io.IOException

/**
 * The `chat.bsky.convo.*` endpoints surface a specific error when the
 * authenticated user hasn't opted into direct messages on Bluesky.
 * The error code as returned by the appview is `"InvalidRequest"` with
 * message text containing the substring `"not enrolled"`. We match
 * on the message substring because the appview's error code is
 * generic — multiple distinct conditions all return InvalidRequest.
 *
 * If Bluesky changes the wording, this match needs to update; we treat
 * any miss as a generic `Unknown` error, which is a safe failure mode
 * (Retry button still rendered).
 */
private const val NOT_ENROLLED_MARKER = "not enrolled"

internal fun Throwable.toChatsError(): ChatsError =
    when (this) {
        is IOException -> ChatsError.Network
        is NoSessionException -> ChatsError.Unknown("not-signed-in")
        is XrpcError -> {
            val msg = message.orEmpty().lowercase()
            if (NOT_ENROLLED_MARKER in msg) ChatsError.NotEnrolled else ChatsError.Unknown(javaClass.simpleName)
        }
        else -> ChatsError.Unknown(javaClass.simpleName)
    }
