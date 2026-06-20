package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.ConvoView
import io.github.kikin81.atproto.chat.bsky.convo.ConvoViewLastMessageUnion
import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.GroupConvo
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.feature.chats.impl.ChatHeader
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import kotlin.time.Instant

/**
 * Sentinel snippet emitted when the convo's last message is a
 * [DeletedMessageView]. The UI layer detects this exact value and
 * renders the `chats_row_deleted_placeholder` string in italic.
 *
 * This sentinel is what populates [ConvoRowUi.lastMessageSnippet]
 * when the last message has been deleted — the UI layer checks for this
 * exact constant and renders the localized placeholder instead.
 *
 * String constant (not enum / sealed sum) because [ConvoRowUi]
 * is `@Immutable` and we want to keep its surface primitive-only.
 */
internal const val DELETED_MESSAGE_SNIPPET: String = "__deleted__"

/**
 * Maps a wire [ConvoView] to the UI-ready, kind-aware [ConvoRowUi]: a
 * [GroupConvo] becomes a [ConvoRowUi.Group] (name + all members for the
 * facepile), anything else becomes a [ConvoRowUi.Direct] keyed on the
 * single other member.
 *
 * Boundary contract: this file is the only place in `:feature:chats:impl`
 * that touches `io.github.kikin81.atproto.chat.bsky.convo.*` runtime
 * types. Everything downstream sees [ConvoRowUi].
 *
 * The raw `sentAt: Instant?` is propagated unchanged; relative-time
 * rendering happens in the UI layer via `rememberChatRelativeTimeText`
 * so labels stay localized and tick live as time passes. See
 * nubecita-nn3.3.
 *
 * @param viewerDid The current authenticated user's DID, used to pick
 *   the "other member" of a direct convo and to determine whether the
 *   last message was sent by the viewer.
 */
fun ConvoView.toConvoRowUi(viewerDid: String): ConvoRowUi {
    val snippet = lastMessage?.snippet()
    val fromViewer = lastMessage?.senderDid() == viewerDid
    val attachment = lastMessage.isAttachmentOnly()
    val at = lastMessage?.sentAt()
    // Wire `unreadCount` is a Long; UI counts are small (badge caps at
    // 99+), so narrow to Int, clamped non-negative defensively.
    val unread = unreadCount.toInt().coerceAtLeast(0)
    return when (val k = kind) {
        is GroupConvo ->
            ConvoRowUi.Group(
                convoId = id,
                name = k.name,
                members = members.map { it.toAuthorUi() }.toImmutableList(),
                lastMessageSnippet = snippet,
                lastMessageFromViewer = fromViewer,
                lastMessageIsAttachment = attachment,
                sentAt = at,
                unreadCount = unread,
                muted = muted,
            )
        else -> {
            val other =
                members.firstOrNull { it.did.raw != viewerDid }
                    ?: members.firstOrNull()
                    ?: error("ConvoView.members is empty — protocol violation; direct convos always have 2 members")
            ConvoRowUi.Direct(
                convoId = id,
                otherUserDid = other.did.raw,
                otherUserHandle = other.handle.raw,
                displayName = other.displayName?.takeUnless { it.isBlank() },
                avatarUrl = other.avatar?.raw,
                lastMessageSnippet = snippet,
                lastMessageFromViewer = fromViewer,
                lastMessageIsAttachment = attachment,
                sentAt = at,
                unreadCount = unread,
                muted = muted,
            )
        }
    }
}

/**
 * Builds the thread header from a loaded convo. Group → name + all members;
 * anything else (direct, or an unknown future kind) → the single other member.
 */
internal fun ConvoView.toChatHeader(viewerDid: String): ChatHeader =
    when (val k = kind) {
        is GroupConvo ->
            ChatHeader.Group(
                name = k.name,
                members = members.map { it.toAuthorUi() }.toImmutableList(),
            )
        else -> {
            val other = members.firstOrNull { it.did.raw != viewerDid } ?: members.firstOrNull()
            ChatHeader.Direct(
                did = other?.did?.raw.orEmpty(),
                handle = other?.handle?.raw.orEmpty(),
                displayName = other?.displayName?.takeUnless { it.isBlank() },
                avatarUrl = other?.avatar?.raw,
            )
        }
    }

/**
 * Lightweight Phase-1 send gate from the loaded convo. FAIL-OPEN: we only disable
 * the composer when we are confident the viewer can't post, because the real
 * fallback for a wrongly-enabled composer is the send-error path. So:
 *  - membership: the viewer must be a member; if members is empty (shouldn't happen)
 *    treat as a member (fail-open).
 *  - lock: only a GroupConvo whose lockStatus is explicitly "locked" blocks posting.
 *    Any other/unknown lockStatus value leaves posting enabled (send-error fallback).
 */
internal fun ConvoView.canViewerPost(viewerDid: String): Boolean {
    val isMember = members.isEmpty() || members.any { it.did.raw == viewerDid }
    val locked = (kind as? GroupConvo)?.lockStatus == GROUP_LOCK_STATUS_LOCKED
    return isMember && !locked
}

// chat.bsky lexicon GroupConvo.lockStatus sentinel for a locked group (admins-only posting).
// Opaque String in the SDK; this is the conservative known-locked value — see canViewerPost.
internal const val GROUP_LOCK_STATUS_LOCKED: String = "locked"

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
