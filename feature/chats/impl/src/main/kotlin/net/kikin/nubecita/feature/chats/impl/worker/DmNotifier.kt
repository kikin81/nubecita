package net.kikin.nubecita.feature.chats.impl.worker

/**
 * One DM notification to post, already resolved to display strings by
 * [DmPollRunner]. [convoId] keys the per-conversation notification (a later
 * message updates rather than stacks — design D4); [otherUserDid] is carried
 * for the tap deep-link target (§5.2).
 */
internal data class DmNotification(
    val convoId: String,
    val otherUserDid: String,
    val title: String,
    val body: String,
    val timestampMillis: Long,
)

/**
 * Posts local DM notifications. Seam between the worker's pure orchestration
 * ([DmPollRunner]) and the Android notification machinery
 * ([MessagingStyleDmNotifier]).
 */
internal interface DmNotifier {
    fun notify(notifications: List<DmNotification>)
}
