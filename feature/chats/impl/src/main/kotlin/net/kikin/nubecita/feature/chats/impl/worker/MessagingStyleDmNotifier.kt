package net.kikin.nubecita.feature.chats.impl.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import net.kikin.nubecita.feature.chats.impl.R
import net.kikin.nubecita.feature.chats.impl.data.DELETED_MESSAGE_SNIPPET
import timber.log.Timber
import javax.inject.Inject

/**
 * Real [DmNotifier] (design D4/D7): posts one `MessagingStyle` notification per
 * conversation under the "Messages" channel, grouped with a summary, tapping
 * through to the convo via a `nubecita://chat/{otherUserDid}` deep link.
 *
 * Per-convo stable ids ([ChatNotificationIds.notifyId]) mean a later message
 * updates the existing notification rather than stacking. `MessagingStyle` also
 * keeps the door open for inline Direct Reply (`nubecita-1fy.17`).
 *
 * Best-effort: if the user has notifications disabled we skip silently — no
 * nags (battery rule). The channel is created lazily and idempotently.
 */
internal class MessagingStyleDmNotifier
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        @param:DmNotificationSmallIcon @param:DrawableRes private val smallIconRes: Int,
    ) : DmNotifier {
        override fun notify(notifications: List<DmNotification>) {
            if (notifications.isEmpty()) return
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            ensureChannel(manager)

            // Best-effort (design D4): one convo that fails to build/post must not
            // abort the others — and, critically, must not throw out of notify(). The
            // caller ([DmPollRunner]) advances the poll cursor only after this returns;
            // a throw here becomes a silent worker failure that freezes the cursor, so
            // every later run re-detects the same backlog and re-throws. Isolate + log.
            notifications.groupBy { it.convoId }.forEach { (convoId, items) ->
                try {
                    manager.notifyIfPermitted(ChatNotificationIds.notifyId(convoId), buildConvoNotification(items))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    Timber.tag(LOG_TAG).e(e, "failed to post DM notification for convo %s", convoId)
                }
            }
            try {
                manager.notifyIfPermitted(ChatNotificationIds.SUMMARY_ID, buildSummary())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                Timber.tag(LOG_TAG).e(e, "failed to post DM notification summary")
            }
        }

        // Idempotent fallback: the channel is normally installed eagerly at
        // startup (MessagesNotificationChannelInstaller via the production
        // AppInitializer set), but keep creating it here too so a notification
        // never posts to a missing channel. Shares the one channel spec so the
        // two call sites can't drift (nubecita-29rw).
        private fun ensureChannel(manager: NotificationManagerCompat) {
            manager.createNotificationChannel(MessagesNotificationChannelInstaller.channel(context))
        }

        /**
         * One [items] list is all the new messages for a single convo this run;
         * they share a sender (the convo's other user), so the title is taken
         * from the first item. Each message becomes a `MessagingStyle.Message`.
         */
        private fun buildConvoNotification(items: List<DmNotification>): android.app.Notification {
            val first = items.first()
            val notifyId = ChatNotificationIds.notifyId(first.convoId)
            val manager = NotificationManagerCompat.from(context)
            val existing = manager.activeNotifications.firstOrNull { it.id == notifyId }?.notification
            val style =
                existing?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
                    ?: NotificationCompat.MessagingStyle(selfPerson())

            val sender = Person.Builder().setName(first.title).build()
            items.forEach { item ->
                style.addMessage(item.displayBody(), item.timestampMillis, sender)
            }
            return convoNotification(style, first.convoId, first.otherUserDid, alert = true)
        }

        /**
         * Inline reply history (nubecita-1fy.17): after the viewer sends a reply from
         * the notification, re-post the convo notification with [replyText] appended as
         * a "you" message — so the thread shows the reply landed instead of vanishing.
         * Extracts the live `MessagingStyle` (preserving the incoming messages) and
         * appends; falls back to a fresh style if the notification was already dismissed.
         * Called off the main thread from [DmReplyHandler]; re-post does not re-alert.
         */
        fun appendSentReply(
            convoId: String,
            otherUserDid: String,
            replyText: String,
        ) = repostConvo(convoId, otherUserDid, appendReply = replyText)

        /**
         * Re-post the convo notification unchanged to clear the RemoteInput "sending…"
         * spinner after a blank or failed reply — the spinner spins until the
         * notification is next updated. No-op if it was already dismissed.
         */
        fun clearReplySpinner(
            convoId: String,
            otherUserDid: String,
        ) = repostConvo(convoId, otherUserDid, appendReply = null)

        private fun repostConvo(
            convoId: String,
            otherUserDid: String,
            appendReply: String?,
        ) {
            val manager = NotificationManagerCompat.from(context)
            if (!manager.areNotificationsEnabled()) return
            val notifyId = ChatNotificationIds.notifyId(convoId)
            val existing = manager.activeNotifications.firstOrNull { it.id == notifyId }?.notification
            // Nothing to clear if the notification is already gone (no spinner to stop).
            if (appendReply == null && existing == null) return
            ensureChannel(manager)
            val style =
                existing?.let { NotificationCompat.MessagingStyle.extractMessagingStyleFromNotification(it) }
                    ?: NotificationCompat.MessagingStyle(selfPerson())
            // A null sender attributes the message to the MessagingStyle's user — "you".
            appendReply?.let { style.addMessage(it, System.currentTimeMillis(), null as Person?) }
            manager.notifyIfPermitted(notifyId, convoNotification(style, convoId, otherUserDid, alert = false))
        }

        // MessagingStyle's "user" is the viewer. androidx.core 1.19's MessagingStyle
        // ctor throws IllegalArgumentException("User's name must not be empty") on a
        // blank name (it tolerated "" before), and the name also labels the inline
        // self-reply "you …" messages (nubecita-1fy.17) — so give it a real localized name.
        private fun selfPerson(): Person = Person.Builder().setName(context.getString(R.string.chats_notification_self_name)).build()

        /** Shared builder for a per-convo notification; [alert] = false on a self-reply re-post (no re-sound). */
        private fun convoNotification(
            style: NotificationCompat.MessagingStyle,
            convoId: String,
            otherUserDid: String,
            alert: Boolean,
        ): android.app.Notification =
            NotificationCompat
                .Builder(context, ChatNotificationIds.CHANNEL_ID)
                .setSmallIcon(smallIconRes)
                .setStyle(style)
                .setAutoCancel(true)
                .setGroup(ChatNotificationIds.GROUP_KEY)
                // Children alert, summary stays silent — avoids a double
                // sound/vibration on the high-importance channel.
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                // Re-posting the viewer's own reply must not re-alert.
                .setOnlyAlertOnce(!alert)
                .setContentIntent(tapIntent(otherUserDid, ChatNotificationIds.notifyId(convoId)))
                .addAction(replyAction(convoId, otherUserDid))
                .build()

        /**
         * Inline Direct Reply action (nubecita-1fy.17): a [RemoteInput] whose typed
         * text is delivered to [DmReplyReceiver], which sends it via the chat repo.
         * The PendingIntent is MUTABLE so the system can fill in the reply results.
         */
        private fun replyAction(
            convoId: String,
            otherUserDid: String,
        ): NotificationCompat.Action {
            val remoteInput =
                RemoteInput
                    .Builder(DmReplyReceiver.KEY_REPLY_TEXT)
                    .setLabel(context.getString(R.string.chats_notification_reply))
                    .build()
            val notifyId = ChatNotificationIds.notifyId(convoId)
            val pendingIntent =
                PendingIntent.getBroadcast(
                    context,
                    notifyId,
                    DmReplyReceiver.intent(context, convoId, otherUserDid),
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            return NotificationCompat.Action
                .Builder(smallIconRes, context.getString(R.string.chats_notification_reply), pendingIntent)
                .addRemoteInput(remoteInput)
                .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                .setAllowGeneratedReplies(true)
                .setShowsUserInterface(false)
                .build()
        }

        private fun buildSummary(): android.app.Notification =
            NotificationCompat
                .Builder(context, ChatNotificationIds.CHANNEL_ID)
                .setSmallIcon(smallIconRes)
                .setGroup(ChatNotificationIds.GROUP_KEY)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .build()

        private fun tapIntent(
            otherUserDid: String,
            requestCode: Int,
        ): PendingIntent {
            val intent =
                Intent(Intent.ACTION_VIEW, Uri.parse(ChatNotificationIds.deepLinkUri(otherUserDid))).apply {
                    setPackage(context.packageName)
                    // NEW_TASK + CLEAR_TASK so the singleTask MainActivity receives
                    // the deep-link data even when a task already exists (mirrors
                    // PushNotificationBuilder.TAP_INTENT_FLAGS).
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
                }
            return PendingIntent.getActivity(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }

        private fun DmNotification.displayBody(): String = if (body == DELETED_MESSAGE_SNIPPET) context.getString(R.string.chats_row_deleted_placeholder) else body

        @Suppress("MissingPermission") // areNotificationsEnabled() gates the whole post; POST_NOTIFICATIONS denial is a silent no-op (best-effort).
        private fun NotificationManagerCompat.notifyIfPermitted(
            id: Int,
            notification: android.app.Notification,
        ) {
            notify(id, notification)
        }

        private companion object {
            const val LOG_TAG = "DmPoll"
        }
    }
