package net.kikin.nubecita.core.wearcontract

import kotlinx.serialization.Serializable

/**
 * The account's recent conversations, published non-urgently at
 * [WearPaths.dms] after each DM poll while a watch is paired (W4). The watch
 * shows it offline; it updates on the next poll, with no on-demand refresh.
 */
@Serializable
data class WearDmSnapshot(
    override val schemaVersion: Int = WearSchema.CURRENT,
    val publishedAtMs: Long,
    val accountDid: String,
    /** Unread messages across [convos], for the list header and the complication. */
    val unreadCount: Int = 0,
    /** Most recent activity first. */
    val convos: List<WearConvo>,
) : WearPayload

/** One conversation and its latest messages. */
@Serializable
data class WearConvo(
    val convoId: String,
    /** The other person's name (or handle) for a direct chat; the group's name otherwise. */
    val title: String,
    val isGroup: Boolean = false,
    val unreadCount: Int = 0,
    val muted: Boolean = false,
    val lastMessageAtMs: Long? = null,
    /** Oldest first, so the thread reads top to bottom. */
    val messages: List<WearMessage> = emptyList(),
)

@Serializable
data class WearMessage(
    val id: String,
    val fromViewer: Boolean,
    /** The sender's name in a group chat; null in a direct chat or for the viewer's own messages. */
    val senderName: String? = null,
    /** Empty for a deleted message. */
    val text: String,
    val sentAtMs: Long,
    val isDeleted: Boolean = false,
    /** The message carries an embed the watch doesn't show (a post, an image). */
    val hasAttachment: Boolean = false,
)
