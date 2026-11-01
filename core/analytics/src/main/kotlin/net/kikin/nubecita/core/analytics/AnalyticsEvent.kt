package net.kikin.nubecita.core.analytics

import net.kikin.nubecita.core.analytics.AnalyticsValue.BoolVal
import net.kikin.nubecita.core.analytics.AnalyticsValue.LongVal
import net.kikin.nubecita.core.analytics.AnalyticsValue.Str

/**
 * A typed, PII-free analytics event.
 *
 * Each variant exposes a neutral wire [name] and a [params] map of
 * [AnalyticsValue]s derived from typed enum / boolean fields. There is no
 * raw-string constructor, so call sites physically cannot attach post text,
 * handles, DIDs, URIs, or query strings.
 *
 * The v1 set (epic nubecita-049f) is the core engagement loop:
 * read → interact → compose → search → auth.
 */
sealed interface AnalyticsEvent {
    /** GA4 wire event name (snake_case, ≤40 chars, no reserved prefix). */
    val name: String

    /** Neutral params keyed by GA4 param name; empty when the event has none. */
    val params: Map<String, AnalyticsValue>
}

/** How a session was authenticated. AT Protocol is OAuth-only today. */
enum class LoginMethod(
    val wire: String,
) {
    OAuth("oauth"),
}

/**
 * GA4-recommended `login` event. Fired on OAuth completion (`LoginViewModel`).
 */
data class Login(
    val method: LoginMethod = LoginMethod.OAuth,
) : AnalyticsEvent {
    override val name: String = "login"
    override val params: Map<String, AnalyticsValue> = mapOf("method" to Str(method.wire))
}

/** Why a login attempt failed. Bucketed — never carries the handle. */
enum class LoginErrorReason(
    val wire: String,
) {
    /** The submitted handle didn't resolve to a DID (typo / wrong server). */
    HandleNotFound("handle_not_found"),

    /** Offline / DNS / socket timeout / upstream reachability during the flow. */
    Network("network"),

    /**
     * The PDS / auth-server OAuth metadata is malformed or incomplete (missing
     * endpoint, unsupported DID method, empty `authorization_servers`) — an
     * upstream server-config problem retrying won't fix. Split out from
     * [Unexpected] so funnel reports separate "their server is misconfigured"
     * from "something genuinely unexpected threw".
     */
    OauthConfig("oauth_config"),

    /** Any other unclassified failure (unexpected throw). */
    Unexpected("unexpected"),
}

/** Which step of the OAuth flow failed. */
enum class LoginStage(
    val wire: String,
) {
    /** `beginLogin` — handle resolution + authorization-URL construction. */
    Begin("begin"),

    /** `completeLogin` — redirect handling + token exchange. */
    Complete("complete"),
}

/**
 * Fired when a login attempt fails (`LoginViewModel`), so funnel reports can
 * track failure *rates* by reason/stage alongside the `login` success event.
 * Blank-handle (pure client-side validation) is not a real attempt and is not
 * reported. PII-free: the reason is a bucketed enum, never the handle.
 */
data class LoginFailed(
    val reason: LoginErrorReason,
    val stage: LoginStage,
) : AnalyticsEvent {
    override val name: String = "login_error"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "reason" to Str(reason.wire),
            "stage" to Str(stage.wire),
        )
}

/** Which redirect transport an OAuth callback arrives on. */
enum class OAuthRedirectKind(
    val wire: String,
) {
    /** Verified Android App Link (`https://nubecita.app/oauth-redirect`). */
    AppLink("applink"),

    /** Legacy custom scheme (`app.nubecita:/oauth-redirect`). */
    CustomScheme("custom_scheme"),
}

/**
 * Fired when the OAuth authorization URL is handed to the Custom Tab from the
 * sign-in path (`LoginViewModel.submitLogin`) — NOT the "create account" link,
 * which opens a generic page. Together with [LoginRedirectReturned] this makes
 * the begin → launched → returned → success/error funnel measurable, so the
 * silent drop between launching the browser and a redirect coming back (the
 * App Link / custom-scheme return failing) is finally visible.
 */
data object LoginRedirectLaunched : AnalyticsEvent {
    override val name: String = "login_redirect_launched"
    override val params: Map<String, AnalyticsValue> = emptyMap()
}

/**
 * Fired when an OAuth redirect returns to the app (before token exchange).
 * [redirect] distinguishes the verified App Link from the legacy custom scheme,
 * so return-rate by transport is comparable — directly informs the App Link
 * redirect migration.
 */
data class LoginRedirectReturned(
    val redirect: OAuthRedirectKind,
) : AnalyticsEvent {
    override val name: String = "login_redirect_returned"
    override val params: Map<String, AnalyticsValue> = mapOf("redirect_kind" to Str(redirect.wire))
}

/**
 * Why the persisted OAuth session was cleared. Bucketed from the `clear()`
 * call stack — never carries tokens, handles, or DIDs.
 */
enum class SessionClearReason(
    val wire: String,
) {
    /** The auth server rejected the refresh token (`invalid_grant`) — the SDK wiped the session. */
    InvalidGrant("invalid_grant"),

    /** The user signed out from Settings. */
    UserSignOut("user_sign_out"),

    /** Cleared from an unrecognized call site. */
    Unknown("unknown"),
}

/**
 * Fired whenever the persisted OAuth session is cleared (epic nubecita-09xt).
 * [daysSinceLogin] separates legitimate ~14-day public-client session expiry
 * from premature, spurious logouts; omitted when no login timestamp exists
 * (e.g. logins that predate this event).
 *
 * The age is sent **bucketed as a string**, not as the raw number. GA4 custom
 * dimensions are text-only, so the numeric form reported `(not set)` on every
 * event since the dimension was registered (nubecita-z2z6) — the one signal
 * this event exists to provide was never actually queryable. Buckets also suit
 * the question better than a raw count: what matters is which side of the
 * ~2-week public-client expiry a clear falls on, not whether it was day 4 or 5.
 */
data class SessionCleared(
    val reason: SessionClearReason,
    val daysSinceLogin: Long?,
) : AnalyticsEvent {
    override val name: String = "session_cleared"
    override val params: Map<String, AnalyticsValue> =
        buildMap {
            put("reason", Str(reason.wire))
            if (daysSinceLogin != null) put("days_since_login", Str(bucketDaysSinceLogin(daysSinceLogin)))
        }
}

/**
 * Buckets a session age (in whole days) for the `days_since_login` dimension.
 *
 * Boundaries are chosen around the ~2-week public-client refresh-token lifetime:
 * `0` and `1` are unambiguously premature losses, `14_plus` is consistent with
 * natural expiry, and the middle buckets show where the distribution actually
 * sits. Negative input is clamped — the caller already clamps a future login
 * timestamp to zero, and this keeps a clock change from minting a junk bucket.
 */
internal fun bucketDaysSinceLogin(days: Long): String =
    when {
        days <= 0L -> "0"
        days == 1L -> "1"
        days < 7L -> "2_6"
        days < 14L -> "7_13"
        else -> "14_plus"
    }

/** Which storage layer failed while reading the persisted session. */
enum class SessionReadErrorCause(
    val wire: String,
) {
    /** Disk / DataStore IO failure. */
    Io("io"),

    /** Tink / Android Keystore failure (AEAD decrypt, key invalidation, Keystore unavailable). */
    Security("security"),

    /** The decrypted payload failed to deserialize. */
    Serialization("serialization"),
}

/**
 * Fired when reading the persisted session fails and degrades to "no session"
 * (epic nubecita-09xt). Today this is indistinguishable from being signed out
 * at the routing layer, so a transient read failure presents as a spurious
 * logout — this event measures how often that actually happens in the wild.
 */
data class SessionReadError(
    val cause: SessionReadErrorCause,
) : AnalyticsEvent {
    override val name: String = "session_read_error"
    override val params: Map<String, AnalyticsValue> = mapOf("cause" to Str(cause.wire))
}

/**
 * Fired when every bounded retry of a failed session read is exhausted and the
 * user is routed to Login with their session still on disk (epic
 * nubecita-09xt) — the user-impacting subset of [SessionReadError], which
 * counts raw per-attempt incidence.
 */
data class SessionReadErrorTerminal(
    val cause: SessionReadErrorCause,
) : AnalyticsEvent {
    override val name: String = "session_read_error_terminal"
    override val params: Map<String, AnalyticsValue> = mapOf("cause" to Str(cause.wire))
}

/**
 * Fired when the Tink keyset backing the encrypted session store failed to
 * load and was destructively regenerated (epic nubecita-09xt). Regeneration
 * makes any previously persisted session ciphertext undecryptable, so every
 * fire is a guaranteed silent logout for a signed-in user.
 */
data object AuthKeysetRegenerated : AnalyticsEvent {
    override val name: String = "auth_keyset_regenerated"
    override val params: Map<String, AnalyticsValue> = emptyMap()
}

/**
 * Which *kind* of feed was opened — never which feed.
 *
 * This is a category, not a feed identity. Every custom generator feed a user
 * follows ("Cats", "Tech News", and hundreds more) collapses to [Custom]; every
 * list feed collapses to [List]. The high-cardinality, identifying value — the
 * feed URI (`at://did:plc:…/app.bsky.feed.generator/cats`, which embeds a DID
 * and an often-identifying name) — is exactly the PII this enum exists to drop.
 *
 * Bucketing into a handful of kinds is intentional, not a scaling limit: it
 * keeps `feed_type` PII-free and within GA4's param-cardinality budget (raw
 * URIs would be sampled into "(other)" and unusable in reports anyway).
 * Per-feed analytics, if ever wanted, belongs on a separate bounded surface —
 * not a free-cardinality GA4 param.
 */
enum class FeedType(
    val wire: String,
) {
    Following("following"),
    Discover("discover"),
    Custom("custom"),
    List("list"),
    Video("video"),
}

/** Fired when a feed surface loads (feed tab + search "feeds" results). */
data class ViewFeed(
    val feedType: FeedType,
) : AnalyticsEvent {
    override val name: String = "view_feed"
    override val params: Map<String, AnalyticsValue> = mapOf("feed_type" to Str(feedType.wire))
}

/** The interaction performed on a post. */
enum class PostAction(
    val wire: String,
) {
    Like("like"),
    Unlike("unlike"),
    Repost("repost"),
    Unrepost("unrepost"),
    Quote("quote"),
    Reply("reply"),
    Bookmark("bookmark"),
    Unbookmark("unbookmark"),
}

/** The screen surface the interaction originated from (no instance identity). */
enum class PostSurface(
    val wire: String,
) {
    Feed("feed"),
    PostDetail("post_detail"),
    Profile("profile"),
    Search("search"),
    FeedView("feed_view"),
    Explore("explore"),
    Bookmarks("bookmarks"),
    Videos("videos"),

    /** The Wear OS app, relayed through the phone (`:core:wear-sync`). */
    Watch("watch"),
}

/** Fired at like/repost/quote/reply call sites. */
data class InteractPost(
    val action: PostAction,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name: String = "interact_post"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "action_type" to Str(action.wire),
            "source_surface" to Str(surface.wire),
        )
}

/** A user-to-user interaction (room for mute/block later). */
enum class ActorAction(
    val wire: String,
) {
    Follow("follow"),
    Unfollow("unfollow"),
}

/** Fired at follow/unfollow call sites (Profile today). Generic — mirrors [InteractPost]. */
data class InteractActor(
    val action: ActorAction,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name: String = "interact_actor"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "action_type" to Str(action.wire),
            "source_surface" to Str(surface.wire),
        )
}

/** How a post was shared. */
enum class ShareMethod(
    val wire: String,
) {
    ShareSheet("share_sheet"),
    CopyLink("copy_link"),
}

/**
 * GA4-recommended `share` event (PII-free: carries no item_id). Named `Share`
 * (not `SharePost`) to match the wire name and avoid shadowing the existing
 * `*Effect.SharePost` UI effects.
 */
data class Share(
    val method: ShareMethod,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name: String = "share"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "method" to Str(method.wire),
            "content_type" to Str("post"),
            "source_surface" to Str(surface.wire),
        )
}

/** The action taken on a feed (pin/unpin to the tab bar). */
enum class FeedAction(
    val wire: String,
) {
    Pin("pin"),
    Unpin("unpin"),
}

/**
 * Fired when the user pins or unpins a feed. [surface] distinguishes the
 * originating screen: [PostSurface.FeedView] when acting from the feed's own
 * tab (Component B), [PostSurface.Explore] when acting from the Discover
 * surface (Component C).
 */
data class InteractFeed(
    val action: FeedAction,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name: String = "interact_feed"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "feed_action" to Str(action.wire),
            "source_surface" to Str(surface.wire),
        )
}

/**
 * Fired on a successful `DefaultPostingRepository.createPost`. Carries only
 * structural booleans — never the post body, language, or attachment URIs.
 *
 * [hasExternal] is true only when an `app.bsky.embed.external` link card
 * actually shipped on the post; images win the media slot, so a post with both
 * images and a link reports `hasMedia = true, hasExternal = false`. Lets us
 * measure link-card adoption separately from image posts. Surfaces in GA4
 * reports only once `has_external` is registered as a custom dimension
 * (forward-only) — see nubecita-049f.10.
 */
data class CreatePost(
    val hasMedia: Boolean,
    val isReply: Boolean,
    val isQuote: Boolean,
    val hasExternal: Boolean,
) : AnalyticsEvent {
    override val name: String = "create_post"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "has_media" to BoolVal(hasMedia),
            "is_reply" to BoolVal(isReply),
            "is_quote" to BoolVal(isQuote),
            "has_external" to BoolVal(hasExternal),
        )
}

/** Which search tab/sort the query ran against — the query text is never sent. */
enum class SearchScope(
    val wire: String,
) {
    Top("top"),
    Latest("latest"),
    People("people"),
    Feeds("feeds"),
}

/**
 * Fired on search submit (`SearchViewModel`). Replaces GA4's `search` event so
 * the `search_term` param can never be attached. [fromRecent] distinguishes a
 * tap on a saved recent search from a freshly typed one.
 */
data class SearchPerform(
    val scope: SearchScope,
    val fromRecent: Boolean,
) : AnalyticsEvent {
    override val name: String = "search_perform"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "search_scope" to Str(scope.wire),
            "from_recent" to BoolVal(fromRecent),
        )
}

/**
 * Which Nubecita Pro plan a paywall event refers to. Mirrors
 * `:data:models`'s `SubscriptionPlanId` but kept as a local enum so
 * `:core:analytics` stays dependency-free (no `:data:models` coupling); the
 * paywall VM maps one onto the other at the log call site.
 */
enum class PaywallPlan(
    val wire: String,
) {
    Monthly("monthly"),
    Annual("annual"),
    Lifetime("lifetime"),
}

/** Terminal outcome of a user-initiated Restore-purchases tap. */
enum class RestoreOutcome(
    val wire: String,
) {
    /** Restore found an active subscription — Pro granted. */
    Restored("restored"),

    /** Restore completed but the Play account owns no Pro. */
    Nothing("nothing"),

    /** The restore call itself failed (network / provider). */
    Error("error"),
}

/*
 * Paywall funnel (epic nubecita-q5ge, child .13). The *terminal* purchase /
 * revenue / renewal / refund events come from RevenueCat's server-side GA4
 * integration — these client events deliberately cover only the funnel UP TO
 * checkout plus the non-converting outcomes, so the conversion isn't
 * double-counted. All params are bucketed enums; no price, sku, or account id.
 */

/**
 * Which upsell surface presented the paywall — the entry point, not the plan.
 * Lets funnel reports separate the PiP pop-out upsell (the primary monetization
 * path — a non-Pro tap on the fullscreen-video PiP button) from the Settings
 * "Nubecita Pro" row and the Supporter badge. [Other] is the forward-safe
 * default for any future caller that hasn't been tagged yet.
 */
enum class PaywallSource(
    val wire: String,
) {
    Pip("pip"),
    Settings("settings"),
    SupporterBadge("supporter_badge"),
    Other("other"),
}

/**
 * Fired once when the paywall is presented (top of the funnel). [source]
 * attributes the view to its entry point via `source_surface`, so PiP-driven
 * paywall views are separable from Settings / Supporter-badge ones — the join
 * that makes the PiP → paywall → checkout funnel measurable end-to-end.
 */
data class PaywallViewed(
    val source: PaywallSource,
) : AnalyticsEvent {
    override val name: String = "paywall_viewed"
    override val params: Map<String, AnalyticsValue> = mapOf("source_surface" to Str(source.wire))
}

/** Fired when the user actively switches the selected plan. */
data class PaywallPlanSelected(
    val plan: PaywallPlan,
) : AnalyticsEvent {
    override val name: String = "paywall_plan_selected"
    override val params: Map<String, AnalyticsValue> = mapOf("plan" to Str(plan.wire))
}

/** Fired the moment the purchase is initiated and control hands to the Play sheet. */
data class PaywallCheckoutStarted(
    val plan: PaywallPlan,
) : AnalyticsEvent {
    override val name: String = "paywall_checkout_started"
    override val params: Map<String, AnalyticsValue> = mapOf("plan" to Str(plan.wire))
}

/** Fired when the user backs out of the Play purchase sheet (RevenueCat never sees this). */
data object PaywallPurchaseCancelled : AnalyticsEvent {
    override val name: String = "paywall_purchase_cancelled"
    override val params: Map<String, AnalyticsValue> = emptyMap()
}

/** Fired when the purchase fails (billing unavailable / provider error / unexpected throw). */
data object PaywallPurchaseError : AnalyticsEvent {
    override val name: String = "paywall_purchase_error"
    override val params: Map<String, AnalyticsValue> = emptyMap()
}

/** Fired with the outcome of a user-initiated Restore. */
data class PaywallRestore(
    val outcome: RestoreOutcome,
) : AnalyticsEvent {
    override val name: String = "paywall_restore"
    override val params: Map<String, AnalyticsValue> = mapOf("outcome" to Str(outcome.wire))
}

/**
 * The surface a video started playing on — a category, never the clip URI.
 * Derived from the shared player's `PlaybackMode` at the single play choke-point
 * ([FeedPreview] silent autoplay → [Feed]; fullscreen → [VideoPlayer]).
 * [MediaViewer]/[PostDetail] are reserved for future per-surface fire sites.
 */
enum class VideoSurface(
    val wire: String,
) {
    Feed("feed"),
    VideoPlayer("video_player"),
    MediaViewer("media_viewer"),
    PostDetail("post_detail"),
    VerticalFeed("vertical_feed"),
}

/**
 * Fired the first time playback starts for a bound clip (deduped per media item,
 * not per play/pause). [autoplay] separates silent in-feed autoplay from a
 * deliberate open, so "did anyone actually choose to watch" is measurable rather
 * than drowned by autoplay. PII-free: no clip URI, no duration.
 */
data class VideoPlay(
    val surface: VideoSurface,
    val autoplay: Boolean,
) : AnalyticsEvent {
    override val name: String = "video_play"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "source_surface" to Str(surface.wire),
            "autoplay" to BoolVal(autoplay),
        )
}

/**
 * Per-playback quality summary, emitted when a clip's playback session ends
 * (media item changes or the player is released) from a Media3
 * `PlaybackStatsListener`. [timeToFirstFrameMs] is the join time (buffering
 * before the first frame — the swipe-to-video latency the vertical feed
 * optimizes); rebuffer + play-time capture stall vs. watch time. PII-free: no
 * clip URI, no bitrate, no position. "Started/stopped" is implicit — an event
 * is only emitted for a session that played.
 */
data class VideoPlaybackStats(
    val surface: VideoSurface,
    val timeToFirstFrameMs: Long,
    val rebufferCount: Long,
    val rebufferMs: Long,
    val playMs: Long,
) : AnalyticsEvent {
    override val name: String = "video_playback_stats"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "source_surface" to Str(surface.wire),
            "ttff_ms" to LongVal(timeToFirstFrameMs),
            "rebuffer_count" to LongVal(rebufferCount),
            "rebuffer_ms" to LongVal(rebufferMs),
            "play_ms" to LongVal(playMs),
        )
}

/**
 * A fatal playback error. [errorCode] is the Media3 `PlaybackException` error
 * code (e.g. 4001 decoder-init, 2001 network) — numeric, so segmentation stays
 * PII-free (no message, no clip URI).
 */
data class VideoPlaybackError(
    val surface: VideoSurface,
    val errorCode: Long,
) : AnalyticsEvent {
    override val name: String = "video_playback_error"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "source_surface" to Str(surface.wire),
            "error_code" to LongVal(errorCode),
        )
}

/**
 * How the vertical video feed resolved the post the user actually tapped
 * (`VideoFeed.startPostUri`).
 *
 * The feed's own pages are a **second, independent** fetch of a live feed, so
 * they need not contain the tapped post — device-confirmed in nubecita-zdv8.16,
 * where two `getFeed(thevids)` calls minutes apart returned disjoint heads.
 * This event makes that miss countable instead of silent.
 */
enum class VideoSeekOutcome(
    val wire: String,
) {
    /** Found in a page the feed loaded itself — the normal path. */
    Resolved("resolved"),

    /** Absent from every loaded page, hydrated by URI and pinned to the top. */
    Recovered("recovered"),

    /** Absent AND unhydratable — the feed opened on a post the user did not tap. */
    FellBackToTop("fell_back_to_top"),
}

/**
 * Fired once per vertical-feed open that carried a tapped post URI.
 * [pagesWalked] is how many pages the seek consumed looking for it. PII-free:
 * an outcome category and a count, never the post URI.
 */
data class VideoFeedSeek(
    val outcome: VideoSeekOutcome,
    val pagesWalked: Long,
) : AnalyticsEvent {
    override val name: String = "video_feed_seek"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "outcome" to Str(outcome.wire),
            "pages_walked" to LongVal(pagesWalked),
        )
}

/** What happened when the user reached for picture-in-picture. */
enum class PipOutcome(
    val wire: String,
) {
    /** Pro (and device-capable): entered PiP. */
    Entered("entered"),

    /** Non-Pro: routed to the paywall instead — the PiP upsell. */
    Upsell("upsell"),
}

/**
 * Fired on every tap of the fullscreen-video PiP button (`VideoPlayerScreen`).
 * PiP is the primary paywall entry point, so this is the top of the monetization
 * funnel: [PipOutcome.Upsell] counts non-Pro users sent to the paywall, making
 * "reach for PiP → fall off" visible for the first time.
 */
data class PipAttempt(
    val outcome: PipOutcome,
) : AnalyticsEvent {
    override val name: String = "pip_attempt"
    override val params: Map<String, AnalyticsValue> = mapOf("pip_outcome" to Str(outcome.wire))
}

/**
 * The entry point whose request triggered an OAuth token refresh. Background
 * entry points tag themselves; anything untagged ran from the app's own
 * foreground surfaces or from a caller not yet tagged.
 */
enum class TokenRefreshCaller(
    val wire: String,
) {
    /** `DmPollWorker`'s 15-minute background poll. */
    DmPoll("dm_poll"),

    /** `WidgetRefreshWorker`'s 15-minute background feed refresh. */
    Widget("widget"),

    /** Push (un)registration after an FCM token rotation. */
    PushRegistration("push_registration"),

    /** No tag on the calling coroutine. */
    Untagged("untagged"),
}

/** How a single POST of a refresh token to the token endpoint ended. */
enum class TokenRefreshOutcome(
    val wire: String,
) {
    /** HTTP 200: the token rotated. */
    Ok("ok"),

    /** `use_dpop_nonce`: the routine nonce challenge; the SDK retries once. */
    NonceChallenge("nonce_challenge"),

    /** `invalid_grant`: the server rejected the refresh token and the session is gone. */
    InvalidGrant("invalid_grant"),

    /** Any other 4xx. */
    Rejected("rejected"),

    /** 5xx, or a non-4xx status other than 200. */
    ServerError("server_error"),

    /** Failed before reaching the server (DNS, connect): the token was provably NOT consumed. */
    NotSent("not_sent"),

    /**
     * Failed after the request was sent (timeout, reset): the server may have
     * consumed the token and rotated it. The client keeps the old token, and
     * the next refresh replays it — which the AT Proto auth server answers by
     * deleting the whole session. The precursor of a spurious logout.
     */
    LostResponse("lost_response"),
}

/**
 * Fired once per refresh-token POST (`TokenRefreshMonitor` in `:core:auth`).
 * Each POST is one chance to lose the response and, with it, the session, so
 * the rate per [caller] — split by [foreground] — is what decides whether
 * background refreshes need gating (nubecita-bhqv, nubecita-z2gt).
 */
data class TokenRefresh(
    val caller: TokenRefreshCaller,
    val outcome: TokenRefreshOutcome,
    val foreground: Boolean,
) : AnalyticsEvent {
    override val name: String = "token_refresh"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "refresh_caller" to Str(caller.wire),
            "outcome" to Str(outcome.wire),
            "foreground" to BoolVal(foreground),
        )
}

/** Where a tapped notification came from. */
enum class NotificationSource(
    val wire: String,
) {
    /** A push from the gateway (our own notification or Firebase's auto-display). */
    Push("push"),

    /** A local notification posted by the background DM poll. */
    DmPoll("dm_poll"),

    /** A row in the in-app Notifications tab. */
    InApp("in_app"),
}

/** What a tapped notification was about, bucketed so the param stays a small closed set. */
enum class NotificationType(
    val wire: String,
) {
    Like("like"),
    Repost("repost"),
    Reply("reply"),
    Mention("mention"),
    Quote("quote"),
    Follow("follow"),
    Verification("verification"),
    Message("message"),
    Other("other"),
}

/**
 * Fired when the user taps a notification — a push, a DM-poll notification,
 * or a row in the Notifications tab. The app posts its own notifications, so
 * Firebase's automatic `notification_open` (a reserved name) only sees the
 * rare auto-displayed ones; this is the real re-engagement signal
 * (nubecita-ccvy.2).
 */
data class NotificationTap(
    val source: NotificationSource,
    val type: NotificationType,
) : AnalyticsEvent {
    override val name: String = "notification_tap"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "notification_source" to Str(source.wire),
            "notification_type" to Str(type.wire),
        )
}

/** Whether a conversation is one-to-one or a group. */
enum class ConvoKind(
    val wire: String,
) {
    Direct("direct"),
    Group("group"),
}

/** How a DM send ended. */
enum class DmSendOutcome(
    val wire: String,
) {
    Sent("sent"),
    Failed("failed"),
}

/**
 * Fired when a direct message send settles (nubecita-ccvy.3). [kind] is
 * omitted when the conversation header hasn't loaded yet. No text, ids or
 * recipients are ever attached.
 */
data class DmSend(
    val kind: ConvoKind?,
    val outcome: DmSendOutcome,
    val isReply: Boolean,
    val isRetry: Boolean,
) : AnalyticsEvent {
    override val name: String = "dm_send"
    override val params: Map<String, AnalyticsValue> =
        buildMap {
            if (kind != null) put("convo_kind", Str(kind.wire))
            put("outcome", Str(outcome.wire))
            put("is_reply", BoolVal(isReply))
            put("is_retry", BoolVal(isRetry))
        }
}

/** Adding or removing an emoji reaction on a message. */
enum class ReactionAction(
    val wire: String,
) {
    Add("add"),
    Remove("remove"),
}

/** Fired when a DM reaction toggle succeeds (nubecita-ccvy.3). The emoji is not sent. */
data class DmReaction(
    val action: ReactionAction,
) : AnalyticsEvent {
    override val name: String = "dm_reaction"
    override val params: Map<String, AnalyticsValue> = mapOf("action_type" to Str(action.wire))
}

/** Fired when a group chat is created (nubecita-ccvy.3). */
data object DmGroupCreate : AnalyticsEvent {
    override val name: String = "dm_group_create"
    override val params: Map<String, AnalyticsValue> = emptyMap()
}

/** How a request to join a group via its invite link ended. */
enum class GroupJoinOutcome(
    val wire: String,
) {
    /** Joined immediately (open group). */
    Joined("joined"),

    /** The group needs approval; a join request was sent. */
    Requested("requested"),
}

/** Fired when a group join from an invite link succeeds (nubecita-ccvy.3). */
data class DmGroupJoin(
    val outcome: GroupJoinOutcome,
) : AnalyticsEvent {
    override val name: String = "dm_group_join"
    override val params: Map<String, AnalyticsValue> = mapOf("outcome" to Str(outcome.wire))
}

/** A post overflow-menu choice, or the confirmed delete that follows one. */
enum class PostMenuAction(
    val wire: String,
) {
    ReportPost("report_post"),
    ReportAuthor("report_author"),
    MuteAuthor("mute_author"),
    UnmuteAuthor("unmute_author"),
    BlockAuthor("block_author"),
    UnblockAuthor("unblock_author"),
    MuteThread("mute_thread"),
    UnmuteThread("unmute_thread"),
    CopyText("copy_text"),
    Translate("translate"),
    Delete("delete"),
    PinPost("pin_post"),
    UnpinPost("unpin_post"),

    /** The user confirmed the delete dialog — the post is actually being deleted. */
    DeleteConfirmed("delete_confirmed"),
}

/**
 * Fired when the user picks an item from a post's overflow menu, plus once
 * more when a delete is confirmed (nubecita-ccvy.4). Report and block open a
 * dialog, so for those this measures intent, not a completed report or block.
 */
data class PostMenuSelect(
    val action: PostMenuAction,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name: String = "post_menu_select"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "action_type" to Str(action.wire),
            "source_surface" to Str(surface.wire),
        )
}

/** What kind of link inside post text was tapped. */
enum class PostTextTarget(
    val wire: String,
) {
    Hashtag("hashtag"),
    Cashtag("cashtag"),
    Mention("mention"),
    Link("link"),
}

/**
 * Fired when the user taps a hashtag, cashtag, mention or link inside post
 * text (nubecita-ccvy.4). Only the kind is sent, never the tag, handle or URL.
 */
data class PostTextTap(
    val target: PostTextTarget,
    val surface: PostSurface,
) : AnalyticsEvent {
    override val name: String = "post_text_tap"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "text_target" to Str(target.wire),
            "source_surface" to Str(surface.wire),
        )
}

/**
 * Fired on the phone when the watch app opens and asks for a fresh feed
 * (`:core:wear-sync`'s sync request). Counting users with this event gives
 * watch DAU/WAU without any analytics SDK on the watch itself.
 */
data object WatchAppOpen : AnalyticsEvent {
    override val name: String = "watch_app_open"
    override val params: Map<String, AnalyticsValue> = emptyMap()
}

/** An action the watch asked the phone to run. */
enum class WatchActionType(
    val wire: String,
) {
    Like("like"),
    Unlike("unlike"),
    Repost("repost"),
    Unrepost("unrepost"),
    Bookmark("bookmark"),
    Unbookmark("unbookmark"),
    DmReply("dm_reply"),
    DmReact("dm_react"),
    CreatePost("create_post"),

    /** The payload couldn't be read (a newer contract, or malformed). */
    Unknown("unknown"),
}

/** How the phone answered a watch action — the reason the watch is told. */
enum class WatchActionOutcome(
    val wire: String,
) {
    Ok("ok"),
    Account("account"),
    Unsupported("unsupported"),
    Expired("expired"),
    Error("error"),
}

/**
 * Fired once per action the watch sends, whatever the outcome, so watch
 * reliability is measurable. Successful post actions are also logged as
 * [InteractPost] with [PostSurface.Watch], so they sit beside phone
 * interactions; a successful watch post is also a [CreatePost]. No post URI,
 * conversation, text or emoji is ever attached.
 */
data class WatchAction(
    val type: WatchActionType,
    val outcome: WatchActionOutcome,
) : AnalyticsEvent {
    override val name: String = "watch_action"
    override val params: Map<String, AnalyticsValue> =
        mapOf(
            "action_type" to Str(type.wire),
            "outcome" to Str(outcome.wire),
        )
}
