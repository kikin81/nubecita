package net.kikin.nubecita.analytics

import net.kikin.nubecita.Main
import net.kikin.nubecita.Splash
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.api.NewChat
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.login.api.Login
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import net.kikin.nubecita.feature.paywall.api.PaywallSuccessRoute
import net.kikin.nubecita.feature.postdetail.api.PostDeepLinkKey
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.settings.api.Settings
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * Pins [toAnalyticsScreenOrNull] for every current route. This is the
 * coverage net for the deliberate `else -> null` in the mapping: because a
 * cross-module sealed `NavKey` set is impossible (see the function's KDoc), a
 * brand-new route can't fail the build — but adding one without a branch
 * leaves it untracked, which this test surfaces (a new route should arrive
 * with a new assertion here).
 *
 * Routes that resolve to a screen are asserted by enum; routes that are
 * intentionally untracked are asserted `null`.
 */
class NavKeyAnalyticsTest {
    @Test
    fun tracked_outerShell_routesMapToScreens() {
        assertEquals(AnalyticsScreen.Login, Login.toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.Onboarding, Onboarding.toAnalyticsScreenOrNull())
    }

    @Test
    fun tracked_topLevelTabs_mapToScreens() {
        assertEquals(AnalyticsScreen.Feed, Feed.toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.Search, Search.toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.Notifications, NotificationsTab.toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.Chats, Chats.toAnalyticsScreenOrNull())
    }

    @Test
    fun tracked_subRoutes_mapToScreens() {
        assertEquals(AnalyticsScreen.PostDetail, PostDetailRoute(postUri = "at://x").toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.Composer, ComposerRoute().toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.EditProfile, EditProfile().toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.Settings, Settings.toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.ChatThread, Chat(otherUserDid = "did:plc:x").toAnalyticsScreenOrNull())
        assertEquals(
            AnalyticsScreen.MediaViewer,
            MediaViewerRoute(postUri = "at://x", imageIndex = 0).toAnalyticsScreenOrNull(),
        )
    }

    @Test
    fun profile_ownAndOther_bothMapToProfile_droppingTheHandle() {
        assertEquals(AnalyticsScreen.Profile, Profile(handle = null).toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.Profile, Profile(handle = "alice.bsky.social").toAnalyticsScreenOrNull())
    }

    @Test
    fun untracked_containerAndTransientRoutes_returnNull() {
        assertNull(Splash.toAnalyticsScreenOrNull())
        assertNull(Main.toAnalyticsScreenOrNull())
    }

    @Test
    fun untracked_byDesignRoutes_returnNull() {
        assertNull(NewChat.toAnalyticsScreenOrNull())
        assertNull(VideoPlayerRoute(postUri = "at://x").toAnalyticsScreenOrNull())
        assertNull(PostDeepLinkKey(handle = "alice.bsky.social", rkey = "3lkbabcdefghi").toAnalyticsScreenOrNull())
        assertNull(PaywallSuccessRoute.toAnalyticsScreenOrNull()) // celebration, intentionally untracked (nubecita-ykpc)
    }
}
