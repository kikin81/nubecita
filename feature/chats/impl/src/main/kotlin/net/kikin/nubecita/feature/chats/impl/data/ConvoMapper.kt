package net.kikin.nubecita.feature.chats.impl.data

import io.github.kikin81.atproto.chat.bsky.convo.ConvoView
import io.github.kikin81.atproto.chat.bsky.convo.ConvoViewLastMessageUnion
import io.github.kikin81.atproto.chat.bsky.convo.DeletedMessageView
import io.github.kikin81.atproto.chat.bsky.convo.MessageView
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
 * @param viewerDid The current authenticated user's DID, used to pick
 *   the "other member" out of the convo's members list and to determine
 *   whether the last message was sent by the viewer.
 * @param now The current time, injected so tests can assert relative-
 *   timestamp rendering deterministically.
 */
internal fun ConvoView.toConvoListItemUi(
    viewerDid: String,
    now: Instant,
): ConvoListItemUi {
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
        timestampRelative = lastMessage?.sentAt()?.let { sent -> relativeTimestamp(sent, now) } ?: "",
    )
}

/**
 * Deterministic hue in `0..359` derived from `did + first char of
 * handle`. Used as the fallback gradient input when no avatar is set.
 *
 * Inline copy of the helper currently living in
 * `:feature:profile:impl/data/AuthorProfileMapper.kt`.
 * Per the spec's YAGNI clause we keep the duplicate copy until a third
 * consumer warrants extraction to `:designsystem`. The two copies MUST
 * stay byte-identical until extraction — diverging would re-paint
 * avatars differently for the same DID.
 *
 * `Math.floorMod` is used (not `abs % 360`) because
 * `abs(Int.MIN_VALUE)` is still `Int.MIN_VALUE` — `floorMod` returns
 * a non-negative result for every input.
 */
internal fun avatarHueFor(
    did: String,
    handle: String,
): Int {
    val seed = did + (handle.firstOrNull()?.toString() ?: "")
    return Math.floorMod(seed.hashCode(), 360)
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

/**
 * Renders a wire timestamp into the row's relative-time label:
 *
 * - `< 1 min ago` → `"now"`
 * - `< 1 hour ago` → `"{N}m"`
 * - same calendar day → `"{N}h"`
 * - previous calendar day → `"Yesterday"`
 * - within the last 7 days → short weekday name (e.g. `"Sun"`)
 * - older → `"MMM d"` (e.g. `"Apr 25"`)
 *
 * Calendar comparisons use the system default zone — locally relative,
 * not UTC-relative.
 */
private fun relativeTimestamp(
    sent: Instant,
    now: Instant,
): String {
    val deltaMin = (now - sent).inWholeMinutes
    if (deltaMin < 1) return "now"
    if (deltaMin < 60) return "${deltaMin}m"

    val zone = ZoneId.systemDefault()
    val sentZoned = ZonedDateTime.ofInstant(java.time.Instant.parse(sent.toString()), zone)
    val nowZoned = ZonedDateTime.ofInstant(java.time.Instant.parse(now.toString()), zone)
    val sentDate = sentZoned.toLocalDate()
    val nowDate = nowZoned.toLocalDate()

    if (sentDate == nowDate) {
        val deltaHr = (now - sent).inWholeHours
        return "${deltaHr}h"
    }
    if (sentDate == nowDate.minusDays(1)) return "Yesterday"
    if (sentDate.isAfter(nowDate.minusDays(7))) {
        return sentZoned.format(WEEKDAY_FORMATTER)
    }
    return sentZoned.format(MONTH_DAY_FORMATTER)
}

private val WEEKDAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

private val MONTH_DAY_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d", Locale.getDefault())
