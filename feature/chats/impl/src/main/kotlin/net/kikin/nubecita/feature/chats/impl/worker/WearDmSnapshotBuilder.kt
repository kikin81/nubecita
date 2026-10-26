package net.kikin.nubecita.feature.chats.impl.worker

import net.kikin.nubecita.core.wearcontract.WearConvo
import net.kikin.nubecita.core.wearcontract.WearDmSnapshot
import net.kikin.nubecita.core.wearcontract.WearMessage
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import net.kikin.nubecita.feature.chats.impl.MessageUi

/** How many conversations the watch keeps, and how many messages per conversation. */
internal const val WEAR_DM_CONVOS = 10
internal const val WEAR_DM_MESSAGES = 20

/**
 * The conversations the watch shows: accepted ones (not requests), most recent
 * first, capped at [WEAR_DM_CONVOS].
 */
internal fun List<ConvoRowUi>.forWatch(): List<ConvoRowUi> =
    filterNot { it.isRequest }
        .sortedByDescending { it.sentAt }
        .take(WEAR_DM_CONVOS)

/**
 * The conversations whose messages need fetching this poll: those [changed]
 * (the poll's `getLog` saw new messages in them) and those the previous
 * snapshot doesn't have yet. With no previous snapshot that's every shown
 * conversation: the one-time seed after pairing.
 */
internal fun convosToFetch(
    shown: List<ConvoRowUi>,
    changed: Set<String>,
    previous: WearDmSnapshot?,
): List<String> {
    val known = previous?.convos?.mapTo(HashSet()) { it.convoId }.orEmpty()
    return shown.map { it.convoId }.filter { it in changed || it !in known }
}

/**
 * Builds the snapshot from the poll's conversation list, the messages fetched
 * this run ([fetched], newest first as the lexicon pages them), and the
 * [previous] snapshot, whose messages are kept for every conversation that
 * wasn't fetched.
 */
internal fun buildWearDmSnapshot(
    accountDid: String,
    nowMs: Long,
    shown: List<ConvoRowUi>,
    fetched: Map<String, List<MessageUi>>,
    previous: WearDmSnapshot?,
): WearDmSnapshot {
    val previousById = previous?.convos?.associateBy { it.convoId }.orEmpty()
    val convos =
        shown.map { convo ->
            val messages = fetched[convo.convoId]?.toWear(convo) ?: previousById[convo.convoId]?.messages.orEmpty()
            WearConvo(
                convoId = convo.convoId,
                title =
                    when (convo) {
                        is ConvoRowUi.Direct -> convo.displayName?.takeIf { it.isNotBlank() } ?: convo.otherUserHandle
                        is ConvoRowUi.Group -> convo.name
                    },
                isGroup = convo is ConvoRowUi.Group,
                unreadCount = convo.unreadCount,
                muted = convo.muted,
                lastMessageAtMs = convo.sentAt?.toEpochMilliseconds(),
                messages = messages,
            )
        }
    return WearDmSnapshot(
        publishedAtMs = nowMs,
        accountDid = accountDid,
        unreadCount = convos.filterNot { it.muted }.sumOf { it.unreadCount },
        convos = convos,
    )
}

// Newest-first page → the latest WEAR_DM_MESSAGES, oldest first.
private fun List<MessageUi>.toWear(convo: ConvoRowUi): List<WearMessage> {
    val names = (convo as? ConvoRowUi.Group)?.members?.associate { it.did to (it.displayName.ifBlank { it.handle }) }.orEmpty()
    return take(WEAR_DM_MESSAGES).asReversed().map { message ->
        WearMessage(
            id = message.id,
            fromViewer = message.isOutgoing,
            senderName = if (message.isOutgoing) null else names[message.senderDid],
            text = if (message.isDeleted) "" else message.text,
            sentAtMs = message.sentAt.toEpochMilliseconds(),
            isDeleted = message.isDeleted,
            hasAttachment = message.embed != null,
        )
    }
}
