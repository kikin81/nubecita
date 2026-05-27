package net.kikin.nubecita.core.push

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.annotation.DrawableRes
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat

/**
 * Constructs [android.app.Notification] instances from [PushPayload]s. The
 * Phase-2 FCM service ([NubecitaFcmService]) calls [build] for each push
 * that survives the dispatcher's filter chain, and [buildSummary] for the
 * per-reason group summary that collapses many individual notifications in
 * the shade.
 *
 * **Channel selection** — the channel ID matches the gateway's wire-reason
 * string; see [NotificationChannelInstaller.channelIdFor].
 *
 * **Tap intent** — [deepLinkFor] selects `payload.subject` over
 * `payload.uri` when both are set (so a `like` lands the user on the post,
 * not on the like record) and translates the chosen AT-URI to a
 * `nubecita://profile/...` string via [AtUriToDeepLink]. The
 * [PendingIntent] is built with `FLAG_IMMUTABLE` (Android 12+ requirement)
 * and a request code = [notifyIdFor] so re-publishing the same logical
 * notification updates the prior PendingIntent in place rather than
 * accumulating stale ones.
 *
 * **Grouping + summary** — every individual notification carries
 * `setGroup(nubecita:${reason})`; the per-reason summary carries
 * `setGroupSummary(true)` and a stable [summaryNotifyIdFor] ID so a fresh
 * summary `notify` updates the previous one in place rather than stacking.
 *
 * **smallIcon** — provided by the consumer (typically the app's launcher
 * foreground or a dedicated push icon resource), since `:core:push` has no
 * branding of its own.
 */
class PushNotificationBuilder(
    @DrawableRes private val smallIconRes: Int,
) {
    fun build(
        payload: PushPayload,
        context: Context,
    ): android.app.Notification {
        val channelId = NotificationChannelInstaller.channelIdFor(payload.reason)
        val title = titleFor(payload, context)
        return NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(smallIconRes)
            .setContentTitle(title)
            .setAutoCancel(true)
            .setGroup(groupKeyFor(payload.reason))
            .apply {
                tapPendingIntentFor(payload, context)?.let { setContentIntent(it) }
            }.build()
    }

    fun buildSummary(
        reason: PushPayload.Reason,
        count: Int,
        context: Context,
    ): android.app.Notification {
        val channelId = NotificationChannelInstaller.channelIdFor(reason)
        val text = context.resources.getQuantityString(summaryPluralResFor(reason), count, count)
        return NotificationCompat
            .Builder(context, channelId)
            .setSmallIcon(smallIconRes)
            .setContentTitle(text)
            .setGroup(groupKeyFor(reason))
            .setGroupSummary(true)
            .setStyle(NotificationCompat.InboxStyle().setSummaryText(text))
            .build()
    }

    private fun titleFor(
        payload: PushPayload,
        context: Context,
    ): CharSequence {
        val actorName = payload.actorDisplayName?.takeIf { it.isNotBlank() } ?: payload.actorHandle ?: payload.actorDid
        val titleRes = titleResFor(payload.reason)
        return runCatching { context.getString(titleRes, actorName) }
            .getOrElse { context.getString(R.string.push_title_default) }
    }

    private fun tapPendingIntentFor(
        payload: PushPayload,
        context: Context,
    ): PendingIntent? {
        val spec = tapIntentSpecFor(payload, context.packageName) ?: return null
        val intent =
            Intent(Intent.ACTION_VIEW, Uri.parse(spec.deepLinkUri)).apply {
                // Constrain to our app so the OS routes to MainActivity's
                // nubecita://profile filter without surfacing a chooser.
                setPackage(spec.packageName)
                addFlags(spec.flags)
            }
        return PendingIntent.getActivity(
            context,
            notifyIdFor(payload),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        /**
         * Notify-ID for an individual push. Distinct per [PushPayload.uri]
         * so multiple events (e.g. five different likes from the same actor
         * on different posts) stack in the shade rather than overwriting
         * each other.
         */
        fun notifyIdFor(payload: PushPayload): Int = payload.uri.hashCode()

        /**
         * Notify-ID for the per-reason group summary. Stable across calls
         * for the same [reason] so a subsequent `notify(summaryId,
         * newSummary)` updates the prior summary in place. The
         * `"nubecita-summary:"` prefix guarantees disjointness from any
         * plausible individual [notifyIdFor].
         */
        fun summaryNotifyIdFor(reason: PushPayload.Reason): Int = ("nubecita-summary:" + wireFor(reason)).hashCode()

        /** Group key shared by an individual notification and its per-reason summary. */
        fun groupKeyFor(reason: PushPayload.Reason): String = "nubecita:" + wireFor(reason)

        /**
         * Resolves the tap-intent destination URI for [payload]:
         * `payload.subject` when set (e.g. the post being liked or replied
         * to), falling back to `payload.uri` for `follow` and `verified` /
         * `unverified` which carry no subject. The chosen AT-URI is
         * translated to a `nubecita://profile/...` deep-link string via
         * [AtUriToDeepLink].
         *
         * Returns `null` for malformed input — the caller posts the
         * notification with no tap intent so a tap is a no-op rather than
         * routing wrong.
         */
        fun deepLinkFor(payload: PushPayload): String? =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = payload.subject ?: payload.uri,
                recipientDid = payload.recipientDid,
            )

        /**
         * Flag composition for the tap intent. Load-bearing:
         * `FLAG_ACTIVITY_NEW_TASK` is required because the PendingIntent fires
         * from the system notification dispatch context (not an Activity);
         * `FLAG_ACTIVITY_CLEAR_TASK` is required for the receiving
         * `MainActivity` which is `launchMode="singleTask"`. Without
         * `CLEAR_TASK`, if a task for the activity already exists (the user
         * opened the app from the launcher), Android silently delivers the
         * task's BASE intent (`ACTION_MAIN`, `data = null`) on rebuild and the
         * deep-link URI never reaches `handleIntent`. Verified on Pixel 10
         * Pro XL during the nubecita-veqm Phase 4 smoke — taps landed on Feed
         * instead of PostDetail until both flags were set together.
         * Documented at developer.android.com/develop/ui/views/notifications/build-notification
         * §"Add a direct entry point".
         */
        internal const val TAP_INTENT_FLAGS: Int =
            Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK

        /**
         * Pre-Intent shape of the tap target. Extracted from
         * [tapPendingIntentFor] so unit tests can assert the URI / package /
         * flags shape without needing a working `Uri.parse` / `Intent.setPackage`
         * (both throw "Method not mocked" in the AGP unit-test Android stubs).
         *
         * Returns `null` when [deepLinkFor] returns `null` (untranslatable
         * payload) — caller posts the notification with no tap intent so a tap
         * is a no-op rather than routing wrong.
         */
        internal fun tapIntentSpecFor(
            payload: PushPayload,
            packageName: String,
        ): TapIntentSpec? = deepLinkFor(payload)?.let { TapIntentSpec(it, packageName, TAP_INTENT_FLAGS) }

        /**
         * Data shape of the tap-intent fields the notification builder writes
         * onto the constructed [Intent]. Internal for testability — production
         * code never sees this; [tapPendingIntentFor] constructs a real
         * [Intent] from the spec and hands it to [PendingIntent.getActivity].
         */
        internal data class TapIntentSpec(
            val deepLinkUri: String,
            val packageName: String,
            val flags: Int,
        )

        @StringRes
        private fun titleResFor(reason: PushPayload.Reason): Int =
            when (reason) {
                PushPayload.Reason.Like -> R.string.push_reason_like
                PushPayload.Reason.LikeViaRepost -> R.string.push_reason_like_via_repost
                PushPayload.Reason.Repost -> R.string.push_reason_repost
                PushPayload.Reason.RepostViaRepost -> R.string.push_reason_repost_via_repost
                PushPayload.Reason.Reply -> R.string.push_reason_reply
                PushPayload.Reason.Mention -> R.string.push_reason_mention
                PushPayload.Reason.Quote -> R.string.push_reason_quote
                PushPayload.Reason.Follow -> R.string.push_reason_follow
                PushPayload.Reason.Verified -> R.string.push_reason_verified
                PushPayload.Reason.Unverified -> R.string.push_reason_unverified
            }

        @PluralsRes
        private fun summaryPluralResFor(reason: PushPayload.Reason): Int =
            when (reason) {
                PushPayload.Reason.Like -> R.plurals.push_summary_like
                PushPayload.Reason.LikeViaRepost -> R.plurals.push_summary_like_via_repost
                PushPayload.Reason.Repost -> R.plurals.push_summary_repost
                PushPayload.Reason.RepostViaRepost -> R.plurals.push_summary_repost_via_repost
                PushPayload.Reason.Reply -> R.plurals.push_summary_reply
                PushPayload.Reason.Mention -> R.plurals.push_summary_mention
                PushPayload.Reason.Quote -> R.plurals.push_summary_quote
                PushPayload.Reason.Follow -> R.plurals.push_summary_follow
                PushPayload.Reason.Verified -> R.plurals.push_summary_verified
                PushPayload.Reason.Unverified -> R.plurals.push_summary_unverified
            }

        private fun wireFor(reason: PushPayload.Reason): String = NotificationChannelInstaller.channelIdFor(reason)
    }
}
