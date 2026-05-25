package net.kikin.nubecita.core.push

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Idempotently installs the ten per-reason notification channels Nubecita
 * uses to surface push payloads. Channel IDs match the gateway's wire-reason
 * strings — see [PushPayload.Reason] and [channelIdFor] — so a future
 * gateway change that adds a reason variant maps cleanly through both
 * surfaces.
 *
 * Importance is three-tier per `design.md`:
 * - HIGH for `reply`, `mention`, `quote`, `verified`, `unverified` —
 *   direct interactions and security-relevant verifications peep.
 * - DEFAULT for `follow` — visible but not heads-up.
 * - LOW for `like`, `like-via-repost`, `repost`, `repost-via-repost` —
 *   passive engagement, shade-only, no sound.
 *
 * Android's `NotificationManager.createNotificationChannel` is a no-op
 * when a channel with the same ID + identical configuration already exists,
 * so [install] can run on every cold start without churn. Channels stay
 * stable across the app's lifetime — importance is the user's per-reason
 * tuning surface in system Settings > Apps > Nubecita > Notifications.
 *
 * Channel deletion is intentionally not supported here: importance changes
 * to existing channels after first install do NOT propagate (Android caches
 * per-channel user prefs across reinstalls), so any future re-importance
 * migration would need a new channel ID + an explicit migration prompt.
 * Out of scope for v1.
 */
class NotificationChannelInstaller {
    fun install(context: Context) {
        NotificationManagerCompat
            .from(context)
            .createNotificationChannelsCompat(CHANNELS.map { it.toChannel(context) })
    }

    private data class ChannelSpec(
        val id: String,
        val importance: Int,
        @StringRes val nameRes: Int,
        @StringRes val descriptionRes: Int,
    ) {
        fun toChannel(context: Context): NotificationChannelCompat =
            NotificationChannelCompat
                .Builder(id, importance)
                .setName(context.getString(nameRes))
                .setDescription(context.getString(descriptionRes))
                .build()
    }

    companion object {
        // Wire-reason channel IDs. Public so PushNotificationBuilder can
        // look them up via channelIdFor(reason) without re-deriving the
        // wire-string mapping.
        const val CHANNEL_LIKE = "like"
        const val CHANNEL_LIKE_VIA_REPOST = "like-via-repost"
        const val CHANNEL_REPOST = "repost"
        const val CHANNEL_REPOST_VIA_REPOST = "repost-via-repost"
        const val CHANNEL_REPLY = "reply"
        const val CHANNEL_MENTION = "mention"
        const val CHANNEL_QUOTE = "quote"
        const val CHANNEL_FOLLOW = "follow"
        const val CHANNEL_VERIFIED = "verified"
        const val CHANNEL_UNVERIFIED = "unverified"

        private val CHANNELS: List<ChannelSpec> =
            listOf(
                ChannelSpec(
                    id = CHANNEL_REPLY,
                    importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                    nameRes = R.string.push_channel_reply_name,
                    descriptionRes = R.string.push_channel_reply_description,
                ),
                ChannelSpec(
                    id = CHANNEL_MENTION,
                    importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                    nameRes = R.string.push_channel_mention_name,
                    descriptionRes = R.string.push_channel_mention_description,
                ),
                ChannelSpec(
                    id = CHANNEL_QUOTE,
                    importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                    nameRes = R.string.push_channel_quote_name,
                    descriptionRes = R.string.push_channel_quote_description,
                ),
                ChannelSpec(
                    id = CHANNEL_VERIFIED,
                    importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                    nameRes = R.string.push_channel_verified_name,
                    descriptionRes = R.string.push_channel_verified_description,
                ),
                ChannelSpec(
                    id = CHANNEL_UNVERIFIED,
                    importance = NotificationManagerCompat.IMPORTANCE_HIGH,
                    nameRes = R.string.push_channel_unverified_name,
                    descriptionRes = R.string.push_channel_unverified_description,
                ),
                ChannelSpec(
                    id = CHANNEL_FOLLOW,
                    importance = NotificationManagerCompat.IMPORTANCE_DEFAULT,
                    nameRes = R.string.push_channel_follow_name,
                    descriptionRes = R.string.push_channel_follow_description,
                ),
                ChannelSpec(
                    id = CHANNEL_LIKE,
                    importance = NotificationManagerCompat.IMPORTANCE_LOW,
                    nameRes = R.string.push_channel_like_name,
                    descriptionRes = R.string.push_channel_like_description,
                ),
                ChannelSpec(
                    id = CHANNEL_LIKE_VIA_REPOST,
                    importance = NotificationManagerCompat.IMPORTANCE_LOW,
                    nameRes = R.string.push_channel_like_via_repost_name,
                    descriptionRes = R.string.push_channel_like_via_repost_description,
                ),
                ChannelSpec(
                    id = CHANNEL_REPOST,
                    importance = NotificationManagerCompat.IMPORTANCE_LOW,
                    nameRes = R.string.push_channel_repost_name,
                    descriptionRes = R.string.push_channel_repost_description,
                ),
                ChannelSpec(
                    id = CHANNEL_REPOST_VIA_REPOST,
                    importance = NotificationManagerCompat.IMPORTANCE_LOW,
                    nameRes = R.string.push_channel_repost_via_repost_name,
                    descriptionRes = R.string.push_channel_repost_via_repost_description,
                ),
            )

        /** Resolve the [NotificationChannel] ID Nubecita uses for [reason]. */
        fun channelIdFor(reason: PushPayload.Reason): String =
            when (reason) {
                PushPayload.Reason.Like -> CHANNEL_LIKE
                PushPayload.Reason.LikeViaRepost -> CHANNEL_LIKE_VIA_REPOST
                PushPayload.Reason.Repost -> CHANNEL_REPOST
                PushPayload.Reason.RepostViaRepost -> CHANNEL_REPOST_VIA_REPOST
                PushPayload.Reason.Reply -> CHANNEL_REPLY
                PushPayload.Reason.Mention -> CHANNEL_MENTION
                PushPayload.Reason.Quote -> CHANNEL_QUOTE
                PushPayload.Reason.Follow -> CHANNEL_FOLLOW
                PushPayload.Reason.Verified -> CHANNEL_VERIFIED
                PushPayload.Reason.Unverified -> CHANNEL_UNVERIFIED
            }
    }
}
