package net.kikin.nubecita.feature.chats.impl.worker

/**
 * One DM notification to post, already resolved to display strings by
 * [DmPollRunner]. [convoId] keys the per-conversation notification (a later
 * message updates rather than stacks — design D4); [deepLinkUri] is the
 * convo-addressed tap target (`nubecita://chat/convo/{convoId}`) resolved by
 * [DmPollRunner] so groups and 1:1s both open the conversation directly
 * (nubecita-g1ph).
 */
internal data class DmNotification(
    val convoId: String,
    val deepLinkUri: String,
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
