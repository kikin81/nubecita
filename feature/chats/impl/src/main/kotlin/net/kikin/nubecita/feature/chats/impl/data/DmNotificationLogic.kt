package net.kikin.nubecita.feature.chats.impl.data

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

/**
 * Upper bound on DM notifications produced from a single background poll. A
 * pathological backlog (many convos at once) must not spam the shade; excess
 * events are dropped while the cursor still advances past the whole page, so
 * they are not re-processed on the next run. Per-convo coalescing (§5) further
 * collapses multiple messages in one convo into a single notification.
 */
internal const val MAX_DM_NOTIFICATIONS_PER_RUN: Int = 25

/**
 * Result of [toDmNotifyPlan]: the inbound message-create events to notify and
 * the cursor to persist. [advancedCursor] is the page's next cursor regardless
 * of how many events were notified or capped — see the cap rationale above.
 */
internal data class DmNotifyPlan(
    val toNotify: ImmutableList<ChatLogEvent>,
    val advancedCursor: String?,
)

/**
 * Pure detection step (design D3): selects which of this page's create-message
 * events should produce a notification.
 *
 * Keeps only events that are:
 * - **inbound** (`senderDid != viewerDid`), and
 * - in a conversation that is **still unread** server-side ([unreadConvoIds]) —
 *   the read-state filter that prevents re-notifying a thread the user already
 *   opened in the foreground (which cleared its unread via `updateRead`).
 *
 * The page's events are already "after the stored cursor" (getLog was called
 * with it), so no cursor comparison is needed here; [advancedCursor] carries
 * the page's next cursor through for the caller to persist.
 */
internal fun ChatLogPage.toDmNotifyPlan(
    viewerDid: String,
    unreadConvoIds: Set<String>,
): DmNotifyPlan {
    val toNotify =
        events
            .asSequence()
            .filter { it.senderDid != viewerDid }
            .filter { it.convoId in unreadConvoIds }
            .take(MAX_DM_NOTIFICATIONS_PER_RUN)
            .toImmutableList()
    return DmNotifyPlan(toNotify = toNotify, advancedCursor = nextCursor)
}

/**
 * Notification title + body for a single inbound event. [title] is the sender's
 * display name, falling back to the handle when it's null/blank (mirrors
 * [ConvoMapper]'s identity rule). [body] is the message text, or the
 * [DELETED_MESSAGE_SNIPPET] sentinel for a deleted create — the notification
 * layer (§5) localizes the sentinel just as the inbox row does.
 */
internal data class DmNotificationContent(
    val title: String,
    val body: String,
)

internal fun ChatLogEvent.toDmNotificationContent(
    senderDisplayName: String?,
    senderHandle: String,
): DmNotificationContent =
    DmNotificationContent(
        title = senderDisplayName?.takeUnless { it.isBlank() } ?: senderHandle,
        body = if (isDeleted) DELETED_MESSAGE_SNIPPET else text,
    )
