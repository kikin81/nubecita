package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import net.kikin.nubecita.feature.chats.impl.MessageUi

// The sealed [ConvoRowUi] has no shared `copy` (each variant's is its own), so
// the cache patches go through these per-shared-field copy extensions that fan
// out the `when` once instead of at every patch call site.

internal fun ConvoRowUi.withMuted(muted: Boolean): ConvoRowUi =
    when (this) {
        is ConvoRowUi.Direct -> copy(muted = muted)
        is ConvoRowUi.Group -> copy(muted = muted)
    }

internal fun ConvoRowUi.withUnread(unreadCount: Int): ConvoRowUi =
    when (this) {
        is ConvoRowUi.Direct -> copy(unreadCount = unreadCount)
        is ConvoRowUi.Group -> copy(unreadCount = unreadCount)
    }

internal fun ConvoRowUi.withLastMessage(
    snippet: String?,
    fromViewer: Boolean,
    isAttachment: Boolean,
    sentAt: kotlin.time.Instant?,
): ConvoRowUi =
    when (this) {
        is ConvoRowUi.Direct ->
            copy(
                lastMessageSnippet = snippet,
                lastMessageFromViewer = fromViewer,
                lastMessageIsAttachment = isAttachment,
                sentAt = sentAt,
            )
        is ConvoRowUi.Group ->
            copy(
                lastMessageSnippet = snippet,
                lastMessageFromViewer = fromViewer,
                lastMessageIsAttachment = isAttachment,
                sentAt = sentAt,
            )
    }

/**
 * Reflect a just-sent [message] in the cached convo list: update the matching
 * convo's last-message preview/timestamp (as an outgoing message) and move it to
 * the top, so the inbox shows the send live without a refetch.
 *
 * Pure function over the cache snapshot — the single source of truth behind the
 * inbox-freshness fix, unit-tested directly:
 * - `null` cache (inbox never loaded) → `null` (no-op).
 * - convo not in the list → returned unchanged (the next refresh surfaces it).
 * - convo present → patched copy hoisted to index 0, others kept in order.
 */
internal fun patchConvosOnSend(
    current: ImmutableList<ConvoRowUi>?,
    convoId: String,
    message: MessageUi,
): ImmutableList<ConvoRowUi>? {
    if (current == null) return null
    val index = current.indexOfFirst { it.convoId == convoId }
    if (index < 0) return current
    val patched =
        current[index].withLastMessage(
            snippet = message.text,
            fromViewer = true,
            isAttachment = false,
            sentAt = message.sentAt,
        )
    return (listOf(patched) + current.filterIndexed { i, _ -> i != index }).toImmutableList()
}

/**
 * Reflect "the viewer left this convo" in the cached list: drop the matching row.
 * Applied to both caches (a convo may be accepted or a request).
 * - `null` cache → `null` (no-op).
 * - convo absent → same instance returned (no copy, no emission).
 * - convo present → copy without it, order otherwise preserved.
 */
internal fun patchConvosOnLeave(
    current: ImmutableList<ConvoRowUi>?,
    convoId: String,
): ImmutableList<ConvoRowUi>? {
    if (current == null) return null
    if (current.none { it.convoId == convoId }) return current
    return current.filterNot { it.convoId == convoId }.toImmutableList()
}

/**
 * Reflect a mute/unmute in the cached list: set the matching convo's
 * [ConvoRowUi.muted] flag. No reorder.
 * - `null` cache → `null`.
 * - convo absent, or flag already [muted] → same instance returned (no emission).
 */
internal fun patchConvosOnMute(
    current: ImmutableList<ConvoRowUi>?,
    convoId: String,
    muted: Boolean,
): ImmutableList<ConvoRowUi>? {
    if (current == null) return null
    val index = current.indexOfFirst { it.convoId == convoId }
    if (index < 0 || current[index].muted == muted) return current
    return current.mapIndexed { i, convo -> if (i == index) convo.withMuted(muted) else convo }.toImmutableList()
}

/**
 * Hoist [convo] to the front of the accepted cache, removing any existing row
 * with the same id first (so re-accepting can't duplicate it).
 * - `null` cache (inbox never loaded) → `null` (no-op).
 *
 * The accept-side half of the move; pairs with [patchConvosOnLeave] (the
 * request-side removal) so the real repository can perform the move as two
 * independent atomic `MutableStateFlow.update` steps rather than a
 * snapshot-both-then-assign-both (which races concurrent send / mark-read /
 * poll patches). [patchConvosOnAccept] is retained for the single-threaded
 * test fakes.
 */
internal fun patchConvosPrepend(
    accepted: ImmutableList<ConvoRowUi>?,
    convo: ConvoRowUi,
): ImmutableList<ConvoRowUi>? {
    if (accepted == null) return null
    return (listOf(convo) + accepted.filterNot { it.convoId == convo.convoId }).toImmutableList()
}

/**
 * Reflect "the viewer accepted this request" by moving it from the request cache
 * into the accepted cache. Returns the new `(accepted, requests)` pair: the moved
 * convo is hoisted to the front of accepted. If it isn't in [requests] (already
 * gone), both lists are returned unchanged.
 */
internal fun patchConvosOnAccept(
    accepted: ImmutableList<ConvoRowUi>?,
    requests: ImmutableList<ConvoRowUi>?,
    convoId: String,
): Pair<ImmutableList<ConvoRowUi>?, ImmutableList<ConvoRowUi>?> {
    val moved = requests?.firstOrNull { it.convoId == convoId } ?: return accepted to requests
    val newRequests = requests.filterNot { it.convoId == convoId }.toImmutableList()
    val newAccepted =
        if (accepted == null) null else (listOf(moved) + accepted.filterNot { it.convoId == convoId }).toImmutableList()
    return newAccepted to newRequests
}

/**
 * Reflect "the viewer opened this convo" in the cached list: zero the matching
 * convo's [ConvoRowUi.unreadCount] in place so the in-row badge and the
 * aggregate bottom-nav badge flip to read immediately, without waiting for the
 * next `listConvos` refresh.
 *
 * Pure counterpart to [patchConvosOnSend], unit-tested directly. Unlike a send,
 * reading does **not** reorder — the convo keeps its position:
 * - `null` cache (inbox never loaded) → `null` (no-op).
 * - convo not in the list → returned unchanged.
 * - convo present → copy with `unreadCount = 0`, all other rows untouched.
 * - already-read convo → same instance returned (no copy, no emission).
 */
internal fun patchConvosOnRead(
    current: ImmutableList<ConvoRowUi>?,
    convoId: String,
): ImmutableList<ConvoRowUi>? {
    if (current == null) return null
    val index = current.indexOfFirst { it.convoId == convoId }
    // Already-read (or absent) → return the same instance: no list copy and no
    // downstream StateFlow emission / recomposition for a no-op open.
    if (index < 0 || current[index].unreadCount == 0) return current
    return current
        .mapIndexed { i, convo -> if (i == index) convo.withUnread(0) else convo }
        .toImmutableList()
}
