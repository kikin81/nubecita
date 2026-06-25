package net.kikin.nubecita.feature.chats.impl.worker

import android.content.Context
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat
import net.kikin.nubecita.feature.chats.impl.R

/**
 * Idempotently installs the single "Messages" notification channel (design D4)
 * that [MessagingStyleDmNotifier] posts background DM notifications under.
 *
 * Created **eagerly at startup** (via the production `AppInitializer`
 * multibinding) so the channel appears in *Settings > Apps > Nubecita >
 * Notifications* immediately on first launch — letting the user see and tune it
 * before any DM arrives, exactly like the ten push channels installed by
 * `:core:push`'s `NotificationChannelInstaller`. Before nubecita-29rw the
 * channel was only created lazily inside [MessagingStyleDmNotifier.notify], so a
 * fresh install / Clear Data left it missing until the next new-message poll.
 *
 * `createNotificationChannel` is a no-op when a channel with the same id +
 * identical configuration already exists, so [install] is safe to run on every
 * cold start and the notifier keeps calling [channel] as an idempotent fallback.
 */
class MessagesNotificationChannelInstaller {
    fun install(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(channel(context))
    }

    companion object {
        /** Heads-up importance — direct messages are time-sensitive (design D4). */
        const val IMPORTANCE: Int = NotificationManagerCompat.IMPORTANCE_HIGH

        /**
         * Single source of truth for the "Messages" channel spec, shared by
         * [install] and [MessagingStyleDmNotifier]'s lazy fallback so the
         * name / description / importance can never drift between the two.
         */
        internal fun channel(context: Context): NotificationChannelCompat =
            NotificationChannelCompat
                .Builder(ChatNotificationIds.CHANNEL_ID, IMPORTANCE)
                .setName(context.getString(R.string.chats_messages_channel_name))
                .setDescription(context.getString(R.string.chats_messages_channel_description))
                .build()
    }
}
