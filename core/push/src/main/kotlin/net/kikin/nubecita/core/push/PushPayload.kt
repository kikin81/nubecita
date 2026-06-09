package net.kikin.nubecita.core.push

/**
 * Typed projection of an FCM `data` map sent by the
 * [DracoBlue/atproto-push-gateway](https://github.com/DracoBlue/atproto-push-gateway)
 * v1.2.0 deployment at `https://push.nubecita.app`. The seven string fields
 * mirror the gateway's `notification.data` payload.
 *
 * Always-required: [reason], [uri], [actorDid], [recipientDid]. The remaining
 * fields are reason-conditional and held as nullable strings to keep the
 * dispatcher's parse path defensive without a per-reason variant.
 *
 * [bodyText] is the notifying post's text, pre-truncated server-side and sent
 * only for `reply` / `mention` / `quote` by the
 * [gateway](https://github.com/kikin81/atproto-push-gateway). It is nullable
 * because engagement reasons (`like` / `repost` / `follow` / verification)
 * carry no post text, and because pre-enrichment gateway deployments omit the
 * field entirely — a missing `bodyText` renders the title-only notification.
 *
 * Parsing is via [parse]; the constructor is intentionally direct so tests can
 * build fixtures without touching the wire-string mapping.
 */
data class PushPayload(
    val reason: Reason,
    val uri: String,
    val subject: String?,
    val actorDid: String,
    val actorHandle: String?,
    val actorDisplayName: String?,
    val recipientDid: String,
    val bodyText: String? = null,
) {
    sealed interface Reason {
        object Like : Reason

        object LikeViaRepost : Reason

        object Repost : Reason

        object RepostViaRepost : Reason

        object Reply : Reason

        object Mention : Reason

        object Quote : Reason

        object Follow : Reason

        object Verified : Reason

        object Unverified : Reason
    }

    companion object {
        fun parse(data: Map<String, String>): PushPayload? {
            val reason =
                when (data["reason"]) {
                    "like" -> Reason.Like
                    "like-via-repost" -> Reason.LikeViaRepost
                    "repost" -> Reason.Repost
                    "repost-via-repost" -> Reason.RepostViaRepost
                    "reply" -> Reason.Reply
                    "mention" -> Reason.Mention
                    "quote" -> Reason.Quote
                    "follow" -> Reason.Follow
                    "verified" -> Reason.Verified
                    "unverified" -> Reason.Unverified
                    else -> return null
                }
            return PushPayload(
                reason = reason,
                uri = data["uri"] ?: return null,
                subject = data["subject"],
                actorDid = data["actorDid"] ?: return null,
                actorHandle = data["actorHandle"],
                actorDisplayName = data["actorDisplayName"],
                recipientDid = data["recipientDid"] ?: return null,
                bodyText = data["bodyText"],
            )
        }
    }
}
