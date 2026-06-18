package net.kikin.nubecita.core.analytics

import net.kikin.nubecita.core.analytics.AnalyticsValue.BoolVal
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
        val event = CreatePost(hasMedia = true, isReply = false, isQuote = true)
        assertEquals("create_post", event.name)
        assertEquals(
            mapOf(
                "has_media" to BoolVal(true),
                "is_reply" to BoolVal(false),
                "is_quote" to BoolVal(true),
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
        assertEquals("paywall_viewed", PaywallViewed.name)
        assertEquals(emptyMap<String, AnalyticsValue>(), PaywallViewed.params)

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
    fun `is_pro user property is a bare boolean`() {
        assertEquals("is_pro", IsPro(true).name)
        assertEquals("true", IsPro(true).value)
        assertEquals("false", IsPro(false).value)
    }

    @Test
    fun `all paywall events and the is_pro property pass GA4 validation`() {
        val events =
            listOf(
                PaywallViewed,
                PaywallPlanSelected(PaywallPlan.Annual),
                PaywallCheckoutStarted(PaywallPlan.Annual),
                PaywallPurchaseCancelled,
                PaywallPurchaseError,
                PaywallRestore(RestoreOutcome.Restored),
            )
        events.forEach { AnalyticsValidator.requireValid(it) }
        AnalyticsValidator.requireValid(IsPro(true))
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
