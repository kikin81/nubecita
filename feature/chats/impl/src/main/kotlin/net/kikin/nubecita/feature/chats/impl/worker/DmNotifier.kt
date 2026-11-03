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
    /**
     * Whether a notification posted right now could actually reach the user.
     *
     * [DmPollRunner] asks this BEFORE any network so a poll whose only possible
     * output would be dropped never runs (nubecita-kvh0). It MUST be no stricter
     * than [notify]'s own check, or a run that would have notified gets skipped.
     */
    fun canPost(): Boolean

    fun notify(notifications: List<DmNotification>)
}
