package net.kikin.nubecita.feature.chats.impl.worker

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import dagger.hilt.android.qualifiers.ApplicationContext
import net.kikin.nubecita.feature.chats.impl.R
import net.kikin.nubecita.feature.chats.impl.data.DELETED_MESSAGE_SNIPPET
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

            notifications.groupBy { it.convoId }.forEach { (convoId, items) ->
                manager.notifyIfPermitted(ChatNotificationIds.notifyId(convoId), buildConvoNotification(items))
            }
            manager.notifyIfPermitted(ChatNotificationIds.SUMMARY_ID, buildSummary())
        }

        private fun ensureChannel(manager: NotificationManagerCompat) {
            manager.createNotificationChannel(
                NotificationChannelCompat
                    .Builder(ChatNotificationIds.CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_HIGH)
                    .setName(context.getString(R.string.chats_messages_channel_name))
                    .setDescription(context.getString(R.string.chats_messages_channel_description))
                    .build(),
            )
        }

        /**
         * One [items] list is all the new messages for a single convo this run;
         * they share a sender (the convo's other user), so the title is taken
         * from the first item. Each message becomes a `MessagingStyle.Message`.
         */
        private fun buildConvoNotification(items: List<DmNotification>): android.app.Notification {
            val first = items.first()
            val sender = Person.Builder().setName(first.title).build()
            val style = NotificationCompat.MessagingStyle(Person.Builder().setName("").build())
            items.forEach { item ->
                style.addMessage(item.displayBody(), item.timestampMillis, sender)
            }
            return NotificationCompat
                .Builder(context, ChatNotificationIds.CHANNEL_ID)
                .setSmallIcon(smallIconRes)
                .setStyle(style)
                .setAutoCancel(true)
                .setGroup(ChatNotificationIds.GROUP_KEY)
                // Children alert, summary stays silent — avoids a double
                // sound/vibration on the high-importance channel.
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
                .setContentIntent(tapIntent(first.otherUserDid, ChatNotificationIds.notifyId(first.convoId)))
                .addAction(replyAction(first.convoId))
                .build()
        }

        /**
         * Inline Direct Reply action (nubecita-1fy.17): a [RemoteInput] whose typed
         * text is delivered to [DmReplyReceiver], which sends it via the chat repo.
         * The PendingIntent is MUTABLE so the system can fill in the reply results.
         */
        private fun replyAction(convoId: String): NotificationCompat.Action {
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
                    DmReplyReceiver.intent(context, convoId, notifyId),
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
    }
