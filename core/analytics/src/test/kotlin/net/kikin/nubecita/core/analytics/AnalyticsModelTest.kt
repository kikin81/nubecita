package net.kikin.nubecita.core.analytics

import net.kikin.nubecita.core.analytics.AnalyticsValue.BoolVal
import net.kikin.nubecita.core.analytics.AnalyticsValue.LongVal
import net.kikin.nubecita.core.analytics.AnalyticsValue.Str
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Pins each v1 [AnalyticsEvent] / [UserProperty] / [AnalyticsScreen] to its
 * exact neutral wire name + params. A typo in a wire name (which would split
 * the metric in GA4) fails here. Every param value is an enum-derived [Str] or
 * a [BoolVal] — never a free-form string — which is the structural PII defense.
 */
class AnalyticsModelTest {
    @Test
    fun `login maps to recommended event with oauth method`() {
        val event = Login()
        assertEquals("login", event.name)
        assertEquals(mapOf("method" to Str("oauth")), event.params)
    }

    @Test
    fun `login_error carries reason and stage but never the handle`() {
        val event = LoginFailed(reason = LoginErrorReason.Network, stage = LoginStage.Complete)
        assertEquals("login_error", event.name)
        assertEquals(
            mapOf(
                "reason" to Str("network"),
                "stage" to Str("complete"),
            ),
            event.params,
        )
        assertEquals("handle_not_found", LoginErrorReason.HandleNotFound.wire)
        assertEquals("unexpected", LoginErrorReason.Unexpected.wire)
        assertEquals("begin", LoginStage.Begin.wire)
        AnalyticsValidator.requireValid(event)
    }

    @Test
    fun `view_feed carries the feed type enum`() {
        val event = ViewFeed(FeedType.Following)
        assertEquals("view_feed", event.name)
        assertEquals(mapOf("feed_type" to Str("following")), event.params)
    }

    @Test
    fun `interact_post carries action and source surface`() {
        val event = InteractPost(action = PostAction.Like, surface = PostSurface.Feed)
        assertEquals("interact_post", event.name)
        assertEquals(
            mapOf(
                "action_type" to Str("like"),
                "source_surface" to Str("feed"),
            ),
            event.params,
        )
    }

    @Test
    fun `create_post carries only structural booleans`() {
        val event = CreatePost(hasMedia = true, isReply = false, isQuote = true, hasExternal = true)
        assertEquals("create_post", event.name)
        assertEquals(
            mapOf(
                "has_media" to BoolVal(true),
                "is_reply" to BoolVal(false),
                "is_quote" to BoolVal(true),
                "has_external" to BoolVal(true),
            ),
            event.params,
        )
    }

    @Test
    fun `search_perform carries scope and from_recent but never the query`() {
        val event = SearchPerform(scope = SearchScope.People, fromRecent = true)
        assertEquals("search_perform", event.name)
        assertEquals(
            mapOf(
                "search_scope" to Str("people"),
                "from_recent" to BoolVal(true),
            ),
            event.params,
        )
    }

    @Test
    fun `theme user property maps to wire name and value`() {
        val property = Theme(ThemePreference.Dark)
        assertEquals("theme_preference", property.name)
        assertEquals("dark", property.value)
    }

    @Test
    fun `self hosted user property is a bare boolean, never the host`() {
        assertEquals("is_self_hosted", SelfHosted(true).name)
        assertEquals("true", SelfHosted(true).value)
        assertEquals("false", SelfHosted(false).value)
    }

    @Test
    fun `notifications enabled user property maps to wire name and value`() {
        assertEquals("notifications_enabled", NotificationsEnabled(true).name)
        assertEquals("true", NotificationsEnabled(true).value)
    }

    @Test
    fun `paywall funnel events map to their wire names and params`() {
        assertEquals("paywall_viewed", PaywallViewed(PaywallSource.Pip).name)
        assertEquals(mapOf("source_surface" to Str("pip")), PaywallViewed(PaywallSource.Pip).params)
        assertEquals("settings", PaywallSource.Settings.wire)
        assertEquals("supporter_badge", PaywallSource.SupporterBadge.wire)

        assertEquals("paywall_plan_selected", PaywallPlanSelected(PaywallPlan.Annual).name)
        assertEquals(mapOf("plan" to Str("annual")), PaywallPlanSelected(PaywallPlan.Annual).params)

        assertEquals("paywall_checkout_started", PaywallCheckoutStarted(PaywallPlan.Monthly).name)
        assertEquals(mapOf("plan" to Str("monthly")), PaywallCheckoutStarted(PaywallPlan.Monthly).params)

        assertEquals("paywall_purchase_cancelled", PaywallPurchaseCancelled.name)
        assertEquals("paywall_purchase_error", PaywallPurchaseError.name)

        assertEquals("paywall_restore", PaywallRestore(RestoreOutcome.Restored).name)
        assertEquals(mapOf("outcome" to Str("restored")), PaywallRestore(RestoreOutcome.Restored).params)
        assertEquals(mapOf("outcome" to Str("nothing")), PaywallRestore(RestoreOutcome.Nothing).params)
        assertEquals(mapOf("outcome" to Str("error")), PaywallRestore(RestoreOutcome.Error).params)
    }

    @Test
    fun `video_play carries surface and autoplay but never the clip`() {
        val event = VideoPlay(surface = VideoSurface.Feed, autoplay = true)
        assertEquals("video_play", event.name)
        assertEquals(
            mapOf(
                "source_surface" to Str("feed"),
                "autoplay" to BoolVal(true),
            ),
            event.params,
        )
        assertEquals("video_player", VideoSurface.VideoPlayer.wire)
    }

    @Test
    fun `pip_attempt carries the entered vs upsell outcome`() {
        assertEquals("pip_attempt", PipAttempt(PipOutcome.Entered).name)
        assertEquals(mapOf("pip_outcome" to Str("entered")), PipAttempt(PipOutcome.Entered).params)
        assertEquals(mapOf("pip_outcome" to Str("upsell")), PipAttempt(PipOutcome.Upsell).params)
    }

    @Test
    fun `is_pro user property is a bare boolean`() {
        assertEquals("is_pro", IsPro(true).name)
        assertEquals("true", IsPro(true).value)
        assertEquals("false", IsPro(false).value)
    }

    @Test
    fun `all paywall events and the is_pro property pass GA4 validation`() {
        val events =
            listOf(
                PaywallViewed(PaywallSource.Pip),
                PaywallPlanSelected(PaywallPlan.Annual),
                PaywallCheckoutStarted(PaywallPlan.Annual),
                PaywallPurchaseCancelled,
                PaywallPurchaseError,
                PaywallRestore(RestoreOutcome.Restored),
                VideoPlay(VideoSurface.Feed, autoplay = true),
                PipAttempt(PipOutcome.Upsell),
            )
        events.forEach { AnalyticsValidator.requireValid(it) }
        AnalyticsValidator.requireValid(IsPro(true))
    }

    @Test
    fun `interact_actor carries action and source surface`() {
        val event = InteractActor(action = ActorAction.Follow, surface = PostSurface.Profile)
        assertEquals("interact_actor", event.name)
        assertEquals(
            mapOf(
                "action_type" to Str("follow"),
                "source_surface" to Str("profile"),
            ),
            event.params,
        )
        assertEquals("unfollow", ActorAction.Unfollow.wire)
    }

    @Test
    fun `share carries method content_type and source surface`() {
        val event = Share(method = ShareMethod.ShareSheet, surface = PostSurface.Feed)
        assertEquals("share", event.name)
        assertEquals(
            mapOf(
                "method" to Str("share_sheet"),
                "content_type" to Str("post"),
                "source_surface" to Str("feed"),
            ),
            event.params,
        )
        assertEquals("copy_link", ShareMethod.CopyLink.wire)
    }

    @Test
    fun `interact_feed carries feed_action and source_surface`() {
        val pinFeedView = InteractFeed(action = FeedAction.Pin, surface = PostSurface.FeedView)
        assertEquals("interact_feed", pinFeedView.name)
        assertEquals(
            mapOf(
                "feed_action" to Str("pin"),
                "source_surface" to Str("feed_view"),
            ),
            pinFeedView.params,
        )

        val unpinExplore = InteractFeed(action = FeedAction.Unpin, surface = PostSurface.Explore)
        assertEquals("interact_feed", unpinExplore.name)
        assertEquals(
            mapOf(
                "feed_action" to Str("unpin"),
                "source_surface" to Str("explore"),
            ),
            unpinExplore.params,
        )

        assertEquals("pin", FeedAction.Pin.wire)
        assertEquals("unpin", FeedAction.Unpin.wire)
        assertEquals("feed_view", PostSurface.FeedView.wire)
        assertEquals("explore", PostSurface.Explore.wire)
        AnalyticsValidator.requireValid(pinFeedView)
        AnalyticsValidator.requireValid(unpinExplore)
    }

    @Test
    fun `session_cleared carries bucketed reason and days_since_login`() {
        val event = SessionCleared(reason = SessionClearReason.InvalidGrant, daysSinceLogin = 3)
        assertEquals("session_cleared", event.name)
        assertEquals(
            mapOf(
                "reason" to Str("invalid_grant"),
                "days_since_login" to LongVal(3),
            ),
            event.params,
        )
        assertEquals("user_sign_out", SessionClearReason.UserSignOut.wire)
        assertEquals("unknown", SessionClearReason.Unknown.wire)
        AnalyticsValidator.requireValid(event)
    }

    @Test
    fun `session_cleared omits days_since_login when no login timestamp exists`() {
        val event = SessionCleared(reason = SessionClearReason.Unknown, daysSinceLogin = null)
        assertEquals(mapOf("reason" to Str("unknown")), event.params)
        AnalyticsValidator.requireValid(event)
    }

    @Test
    fun `session_read_error carries the bucketed cause class only`() {
        val event = SessionReadError(cause = SessionReadErrorCause.Security)
        assertEquals("session_read_error", event.name)
        assertEquals(mapOf("cause" to Str("security")), event.params)
        assertEquals("io", SessionReadErrorCause.Io.wire)
        assertEquals("serialization", SessionReadErrorCause.Serialization.wire)
        AnalyticsValidator.requireValid(event)
    }

    @Test
    fun `session_read_error_terminal carries the bucketed cause class only`() {
        val event = SessionReadErrorTerminal(cause = SessionReadErrorCause.Security)
        assertEquals("session_read_error_terminal", event.name)
        assertEquals(mapOf("cause" to Str("security")), event.params)
        AnalyticsValidator.requireValid(event)
    }

    @Test
    fun `auth_keyset_regenerated has no params`() {
        assertEquals("auth_keyset_regenerated", AuthKeysetRegenerated.name)
        assertEquals(emptyMap<String, AnalyticsValue>(), AuthKeysetRegenerated.params)
        AnalyticsValidator.requireValid(AuthKeysetRegenerated)
    }

    @Test
    fun `every screen exposes a snake_case route name`() {
        val snakeCase = Regex("^[a-z][a-z0-9_]*$")
        AnalyticsScreen.entries.forEach { screen ->
            assert(snakeCase.matches(screen.screenName)) {
                "${screen.name} has non-snake_case screenName '${screen.screenName}'"
            }
        }
    }
}
