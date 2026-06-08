package net.kikin.nubecita.feature.chats.impl.worker

/**
 * Pure helpers for the DM notification surface (channel/group keys, stable
 * notification ids, tap deep-link URI). Extracted so they're JVM-unit-testable
 * without the Android notification machinery in [MessagingStyleDmNotifier].
 */
internal object ChatNotificationIds {
    /** The "Messages" channel id (design D4). */
    const val CHANNEL_ID = "messages"

    /** Group key shared by per-convo notifications and their summary. */
    const val GROUP_KEY = "nubecita:dm"

    /**
     * Stable per-conversation notify id, so a later message in the same convo
     * UPDATES its notification rather than stacking (design D4). Disjoint from
     * [SUMMARY_ID] via the distinct key prefixes.
     */
    fun notifyId(convoId: String): Int = "nubecita:dm:$convoId".hashCode()

    /** Stable id for the group summary notification. */
    val SUMMARY_ID: Int = "nubecita:dm:summary".hashCode()

    /**
     * Tap target: `nubecita://chat/{otherUserDid}`, matched by the
     * `@IntoSet` [net.kikin.nubecita.feature.chats.impl.di.ChatDeepLinkModule]
     * matcher → `Chat(otherUserDid)`.
     */
    fun deepLinkUri(otherUserDid: String): String = "nubecita://chat/$otherUserDid"
}
