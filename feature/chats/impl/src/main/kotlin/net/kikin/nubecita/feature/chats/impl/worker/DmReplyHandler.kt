package net.kikin.nubecita.feature.chats.impl.worker

import android.content.BroadcastReceiver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import timber.log.Timber
import javax.inject.Inject

/**
 * Sends an inline notification reply (nubecita-1fy.17). Resolved by
 * [DmReplyReceiver] via [DmReplyEntryPoint] and run off the main thread on the
 * application scope so the send survives the receiver returning.
 *
 * On success the per-convo notification is re-posted with the reply appended as a
 * "you" message ([MessagingStyleDmNotifier.appendSentReply]); on a blank / failed /
 * timed-out send it is re-posted unchanged to clear the RemoteInput "sending…"
 * spinner. The send is bounded by [SEND_TIMEOUT_MS] (the broadcast's goAsync() has
 * a ~10s limit before an ANR), and exceptions are caught so an uncaught failure on
 * the application scope can't crash the app (CancellationException is rethrown).
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

        /** Receiver entry point: send asynchronously, update the notification, and always finish the broadcast. */
        fun handle(
            convoId: String,
            otherUserDid: String,
            text: String,
            pendingResult: BroadcastReceiver.PendingResult?,
        ) {
            val trimmed = text.trim()
            scope.launch {
                try {
                    val sent = withTimeoutOrNull(SEND_TIMEOUT_MS) { trySend(convoId, trimmed) } ?: false
                    if (sent) {
                        notifier.appendSentReply(convoId, otherUserDid, trimmed)
                    } else {
                        // Blank / failed / timed-out: re-post unchanged so the spinner stops.
                        notifier.clearReplySpinner(convoId, otherUserDid)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Timber.tag(LOG_TAG).e(e, "inline reply handling failed for convo %s", convoId)
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        private companion object {
            const val LOG_TAG = "DmPoll"

            /** Bound the send under the broadcast's ~10s goAsync() limit (avoids an ANR). */
            const val SEND_TIMEOUT_MS = 8_000L
        }
    }
