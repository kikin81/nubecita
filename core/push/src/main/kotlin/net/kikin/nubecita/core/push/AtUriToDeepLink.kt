package net.kikin.nubecita.core.push

/**
 * Translates a push payload's AT-URI to the `nubecita://profile/...`
 * deep-link string that [PushNotificationBuilder] sets on the
 * notification's tap intent.
 *
 * `at://` is not a registered URI scheme on Android — no manifest filter
 * matches it, so a `PendingIntent` carrying a raw AT-URI would land
 * `MainActivity` with `Intent.data` the deep-link router can't resolve.
 * This helper translates to the equivalent `nubecita://profile/...` form,
 * which `MainActivity`'s existing matcher set already routes through the
 * `nubecita.app` profile + post deep-link keys.
 *
 * Returns `null` for any input the helper cannot translate confidently —
 * the caller posts the notification with no tap intent so a tap is a no-op
 * rather than crashing or routing to the wrong place.
 *
 * Translation rules (see `design.md`'s "Tap intent" decision):
 *
 * - **Post** (`at://{did}/app.bsky.feed.post/{rkey}`, the `subject` URI on
 *   like / repost / reply / quote / *-via-repost) →
 *   `nubecita://profile/{did}/post/{rkey}`.
 * - **Follow** (`at://{follower-did}/app.bsky.graph.follow/{rkey}`, the
 *   `uri` on the follow reason) → `nubecita://profile/{did}`. The follower's
 *   profile is the tap target; the follow record's rkey is discarded.
 * - **Verification** (`at://{verifier-did}/app.bsky.graph.verification/{rkey}`,
 *   the `uri` on verified / unverified) → `nubecita://profile/{recipientDid}`.
 *   The authority of the AT-URI is the verifier — the actor who issued the
 *   record — but the tap target is the recipient, the user whose status
 *   changed. The helper requires the caller to pass `recipientDid` for this
 *   case; if null, the helper returns `null` (preferring a no-op tap to
 *   routing the user to the verifier's profile every time).
 *
 * The existing `nubecita://profile/{handle}/post/{rkey}` matchers accept
 * DIDs in the `{handle}` slot — `isValidActor` documents "AT Protocol handle
 * / DID grammar" and `PostDeepLinkKey`'s KDoc confirms it — so no matcher
 * changes are required.
 */
object AtUriToDeepLink {
    /**
     * @param atUri the AT-URI from the push payload (typically `subject` for
     *   post-shaped reasons, `uri` for follow / verification).
     * @param recipientDid the recipient DID — required for verification
     *   payloads to route to the verified account rather than the verifier.
     * @return the `nubecita://profile/...` deep-link string, or `null` for
     *   any malformed input or untranslatable collection NSID.
     */
    fun toNubecitaDeepLink(
        atUri: String,
        recipientDid: String?,
    ): String? {
        if (!atUri.startsWith(AT_SCHEME_PREFIX)) return null
        val withoutScheme = atUri.removePrefix(AT_SCHEME_PREFIX)
        // Expect exactly three segments: authority / collection / rkey.
        val segments = withoutScheme.split('/')
        if (segments.size != 3) return null
        val (authority, collection, rkey) = segments
        if (authority.isEmpty() || collection.isEmpty() || rkey.isEmpty()) return null
        return when (collection) {
            COLLECTION_POST -> "nubecita://profile/$authority/post/$rkey"
            COLLECTION_FOLLOW -> "nubecita://profile/$authority"
            COLLECTION_VERIFICATION -> recipientDid?.let { "nubecita://profile/$it" }
            else -> null
        }
    }

    private const val AT_SCHEME_PREFIX = "at://"
    private const val COLLECTION_POST = "app.bsky.feed.post"
    private const val COLLECTION_FOLLOW = "app.bsky.graph.follow"
    private const val COLLECTION_VERIFICATION = "app.bsky.graph.verification"
}
