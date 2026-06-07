package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.ConvoView
import io.github.kikin81.atproto.chat.bsky.convo.ConvoViewLastMessageUnion
import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import net.kikin.nubecita.core.profile.avatarHueFor
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import kotlin.time.Instant

/**
 * Sentinel snippet emitted when the convo's last message is a
 * [DeletedMessageView]. The UI layer detects this exact value and
 * renders the `chats_row_deleted_placeholder` string in italic.
 *
 * This sentinel is what populates [ConvoListItemUi.lastMessageSnippet]
 * when the last message has been deleted — the UI layer checks for this
 * exact constant and renders the localized placeholder instead.
 *
 * String constant (not enum / sealed sum) because [ConvoListItemUi]
 * is `@Immutable` and we want to keep its surface primitive-only.
 */
internal const val DELETED_MESSAGE_SNIPPET: String = "__deleted__"

/**
 * Maps a wire [ConvoView] to the UI-ready [ConvoListItemUi].
 *
 * Boundary contract: this file is the only place in `:feature:chats:impl`
 * that touches `io.github.kikin81.atproto.chat.bsky.convo.*` runtime
 * types. Everything downstream sees [ConvoListItemUi] with primitive
 * fields.
 *
 * The raw `sentAt: Instant?` is propagated unchanged; relative-time
 * rendering happens in the UI layer via `rememberChatRelativeTimeText`
 * so labels stay localized and tick live as time passes. See
 * nubecita-nn3.3.
 *
 * @param viewerDid The current authenticated user's DID, used to pick
 *   the "other member" out of the convo's members list and to determine
 *   whether the last message was sent by the viewer.
 */
fun ConvoView.toConvoListItemUi(viewerDid: String): ConvoListItemUi {
    val other =
        members.firstOrNull { it.did.raw != viewerDid }
            ?: members.firstOrNull()
            ?: error("ConvoView.members is empty — protocol violation; direct convos always have 2 members")
    return ConvoListItemUi(
        convoId = id,
        otherUserDid = other.did.raw,
        otherUserHandle = other.handle.raw,
        displayName = other.displayName?.takeUnless { it.isBlank() },
        avatarUrl = other.avatar?.raw,
        avatarHue = avatarHueFor(did = other.did.raw, handle = other.handle.raw),
        lastMessageSnippet = lastMessage?.snippet(),
        lastMessageFromViewer = lastMessage?.senderDid() == viewerDid,
        lastMessageIsAttachment = lastMessage.isAttachmentOnly(),
        sentAt = lastMessage?.sentAt(),
        // Wire `unreadCount` is a Long; UI counts are small (badge caps at
        // 99+), so narrow to Int, clamped non-negative defensively.
        unreadCount = unreadCount.toInt().coerceAtLeast(0),
        muted = muted,
    )
}

private fun ConvoViewLastMessageUnion.snippet(): String? =
    when (this) {
        is MessageView -> text
        is DeletedMessageView -> DELETED_MESSAGE_SNIPPET
        else -> null // SystemMessageView + Unknown — surface no snippet for the MVP.
    }

private fun ConvoViewLastMessageUnion.senderDid(): String? =
    when (this) {
        is MessageView -> sender.did.raw
        is DeletedMessageView -> sender.did.raw
        else -> null
    }

@Suppress("UnusedReceiverParameter")
private fun ConvoViewLastMessageUnion?.isAttachmentOnly(): Boolean {
    // V1: there's no MessageView path that's exclusively an attachment without text in the
    // current Bluesky chat schema — `text` is non-nullable. When attachment-only support lands
    // in the lexicon, extend this. Until then: always false.
    return false
}

private fun ConvoViewLastMessageUnion.sentAt(): Instant? =
    when (this) {
        is MessageView -> Instant.parse(sentAt.raw)
        is DeletedMessageView -> Instant.parse(sentAt.raw)
        else -> null
    }
