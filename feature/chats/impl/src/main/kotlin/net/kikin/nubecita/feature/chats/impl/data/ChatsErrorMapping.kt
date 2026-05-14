package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.feature.chats.impl.ChatError
import net.kikin.nubecita.feature.chats.impl.ChatsError
import java.io.IOException
import java.util.Locale

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

/**
 * `getConvoForMembers` returns a typed `ConvoNotFound` error when the peer DID
 * has no shared convo and one can't be auto-opened. The appview surfaces this
 * via an XrpcError whose message contains the literal token `ConvoNotFound`
 * (case-insensitive after `lowercase(Locale.ROOT)` here).
 */
private const val CONVO_NOT_FOUND_MARKER = "convonotfound"

/**
 * Maps a thrown error from the convo-list path (`listConvos`) to a screen-facing
 * [ChatsError] variant. Predates [toChatError]; kept for the existing
 * convo-list ViewModel.
 */
internal fun Throwable.toChatsError(): ChatsError =
    when (this) {
        is IOException -> ChatsError.Network
        is NoSessionException -> ChatsError.Unknown("not-signed-in")
        is XrpcError -> {
            // Locale.ROOT — protocol/error-code matching must be locale-independent.
            // Default-locale lowercase() flips I↔ı in Turkish, which would break
            // future markers that contain those letters.
            val msg = message.orEmpty().lowercase(Locale.ROOT)
            if (NOT_ENROLLED_MARKER in msg) ChatsError.NotEnrolled else ChatsError.Unknown(javaClass.simpleName)
        }
        else -> ChatsError.Unknown(javaClass.simpleName)
    }

/**
 * Maps a thrown error from the thread path (`resolveConvo` or `getMessages`) to a
 * screen-facing [ChatError] variant. Distinct from [toChatsError] because the
 * thread screen recognises `ConvoNotFound` (returned by `getConvoForMembers`
 * when the peer DID has no shared convo and one can't be auto-opened).
 */
internal fun Throwable.toChatError(): ChatError =
    when (this) {
        is IOException -> ChatError.Network
        is NoSessionException -> ChatError.Unknown("not-signed-in")
        is XrpcError -> {
            val msg = message.orEmpty().lowercase(Locale.ROOT)
            when {
                CONVO_NOT_FOUND_MARKER in msg -> ChatError.ConvoNotFound
                NOT_ENROLLED_MARKER in msg -> ChatError.NotEnrolled
                else -> ChatError.Unknown(javaClass.simpleName)
            }
        }
        else -> ChatError.Unknown(javaClass.simpleName)
    }
