package net.kikin.nubecita.feature.chats.impl.worker

import android.content.BroadcastReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Sends an inline notification reply (nubecita-1fy.17). Resolved by
 * [DmReplyReceiver] via [DmReplyEntryPoint] and run off the main thread on the
 * application scope so the send survives the receiver returning.
 *
 * On a successful send the per-convo notification is re-posted with the reply
 * appended as a "you" message ([MessagingStyleDmNotifier.appendSentReply]), so the
 * thread shows the reply landed. A failed/blank send leaves the notification in
 * place so the user can retry from the thread.
 */
internal class DmReplyHandler
    @Inject
    constructor(
        private val chatRepository: ChatRepository,
        private val notifier: MessagingStyleDmNotifier,
        @param:ApplicationScope private val scope: CoroutineScope,
    ) {
        /**
         * Trim and send [text] to [convoId]; a blank reply (or convo) is a no-op.
         * Returns true iff the message was sent. Pure-ish — JVM-unit-tested.
         */
        suspend fun trySend(
            convoId: String,
            text: String,
        ): Boolean {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || convoId.isBlank()) return false
            return chatRepository
                .sendMessage(convoId, trimmed)
                .onFailure { Timber.tag(LOG_TAG).w(it, "inline reply send failed for convo %s", convoId) }
                .isSuccess
        }

        /** Receiver entry point: send asynchronously, append the reply on success, always finish the broadcast. */
        fun handle(
            convoId: String,
            otherUserDid: String,
            text: String,
            pendingResult: BroadcastReceiver.PendingResult?,
        ) {
            val trimmed = text.trim()
            scope.launch {
                try {
                    if (trySend(convoId, trimmed)) {
                        notifier.appendSentReply(convoId, otherUserDid, trimmed)
                    }
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        private companion object {
            const val LOG_TAG = "DmPoll"
        }
    }
