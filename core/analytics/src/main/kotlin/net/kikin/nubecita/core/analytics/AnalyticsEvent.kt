package net.kikin.nubecita.core.analytics

import net.kikin.nubecita.core.analytics.AnalyticsValue.BoolVal
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

/** Fired once when the paywall is presented (`PaywallViewModel.init`). */
data object PaywallViewed : AnalyticsEvent {
    override val name: String = "paywall_viewed"
    override val params: Map<String, AnalyticsValue> = emptyMap()
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
