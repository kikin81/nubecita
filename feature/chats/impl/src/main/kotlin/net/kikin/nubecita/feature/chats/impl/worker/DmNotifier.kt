package net.kikin.nubecita.feature.chats.impl.worker

import timber.log.Timber
import javax.inject.Inject

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
)

/**
 * Posts local DM notifications. Seam between the worker's pure orchestration
 * ([DmPollRunner]) and the Android notification machinery.
 *
 * The real `MessagingStyle` + "Messages" channel + deep-link implementation
 * lands in the next task group (§5); until then [LoggingDmNotifier] is bound so
 * the graph is complete. This is safe because the worker is not yet scheduled
 * (§7) — it never runs in production, so nothing is silently dropped.
 */
internal interface DmNotifier {
    fun notify(notifications: List<DmNotification>)
}

internal class LoggingDmNotifier
    @Inject
    constructor() : DmNotifier {
        override fun notify(notifications: List<DmNotification>) {
            notifications.forEach {
                Timber.tag(TAG).d("would post DM notification: convo=%s title=%s", it.convoId, it.title)
            }
        }

        private companion object {
            const val TAG = "DmNotifier"
        }
    }
