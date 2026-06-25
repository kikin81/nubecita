package net.kikin.nubecita.feature.chats.impl.worker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import dagger.hilt.android.EntryPointAccessors

/**
 * Receives the inline Direct Reply typed into a DM notification (nubecita-1fy.17)
 * and hands it to [DmReplyHandler]. Declared in the manifest (not runtime-
 * registered) so it works even when the app process was killed between the
 * notification posting and the reply.
 *
 * Hilt can't `@Inject` a manifest receiver's constructor, so the handler is
 * pulled from the application graph via [DmReplyEntryPoint] — the house pattern
 * for non-`@AndroidEntryPoint` entry points (mirrors `WidgetEntryPoint`).
 */
internal class DmReplyReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val text =
            RemoteInput
                .getResultsFromIntent(intent)
                ?.getCharSequence(KEY_REPLY_TEXT)
                ?.toString()
                .orEmpty()
        val convoId = intent.getStringExtra(EXTRA_CONVO_ID).orEmpty()
        val notifyId = intent.getIntExtra(EXTRA_NOTIFY_ID, 0)
        if (text.isBlank() || convoId.isBlank()) return

        val handler =
            EntryPointAccessors
                .fromApplication(context.applicationContext, DmReplyEntryPoint::class.java)
                .dmReplyHandler()
        handler.handle(convoId, text, notifyId, goAsync())
    }

    companion object {
        /** RemoteInput result key for the typed reply text. */
        const val KEY_REPLY_TEXT = "nubecita.dm.reply.text"
        private const val EXTRA_CONVO_ID = "nubecita.dm.reply.convoId"
        private const val EXTRA_NOTIFY_ID = "nubecita.dm.reply.notifyId"

        /** The explicit, same-package intent the notification's reply [android.app.PendingIntent] targets. */
        fun intent(
            context: Context,
            convoId: String,
            notifyId: Int,
        ): Intent =
            Intent(context, DmReplyReceiver::class.java).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_CONVO_ID, convoId)
                putExtra(EXTRA_NOTIFY_ID, notifyId)
            }
    }
}
