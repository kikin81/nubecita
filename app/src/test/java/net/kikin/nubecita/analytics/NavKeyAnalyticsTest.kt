package net.kikin.nubecita.analytics

import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.Main
import net.kikin.nubecita.Splash
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.feature.bookmarks.api.Bookmarks
import net.kikin.nubecita.feature.chats.api.AddGroupMembers
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.ChatSettings
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.api.GroupDetails
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview
import net.kikin.nubecita.feature.chats.api.GroupJoinRequests
import net.kikin.nubecita.feature.chats.api.ManageJoinLink
import net.kikin.nubecita.feature.chats.api.NewChat
import net.kikin.nubecita.feature.chats.api.NewGroup
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feed.api.FeedView
import net.kikin.nubecita.feature.feeds.api.Feeds
import net.kikin.nubecita.feature.login.api.Login
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.moderation.api.Block
import net.kikin.nubecita.feature.moderation.api.BlockedAccounts
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.moderation.api.ReportSubject
import net.kikin.nubecita.feature.moderation.api.SafetyAndReporting
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import net.kikin.nubecita.feature.paywall.api.PaywallRoute
import net.kikin.nubecita.feature.paywall.api.PaywallSuccessRoute
import net.kikin.nubecita.feature.postdetail.api.PostDeepLinkKey
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Hashtag
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.settings.api.About
import net.kikin.nubecita.feature.settings.api.AboutLicenses
import net.kikin.nubecita.feature.settings.api.Appearance
import net.kikin.nubecita.feature.settings.api.ContentFilters
import net.kikin.nubecita.feature.settings.api.FeedPreferences
import net.kikin.nubecita.feature.settings.api.MediaAndAnimations
import net.kikin.nubecita.feature.settings.api.Moderation
import net.kikin.nubecita.feature.settings.api.Settings
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import net.kikin.nubecita.feature.videos.api.VideoFeed
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.lang.reflect.Modifier
import java.util.jar.JarFile

/**
 * Pins [toAnalyticsScreenOrNull] for every route in the app, and fails when a
 * route exists that this table doesn't mention.
 *
 * `NavKey` is not sealed across modules, so the mapping needs an `else ->
 * null` and a brand-new route can't fail the build. The Hashtag screen shipped
 * untracked exactly that way. [everyNavKeyOnTheClasspath_hasAScreenViewDecision]
 * closes the gap: it scans the runtime classpath for every concrete `NavKey`
 * and requires each one to appear in [expected], either with its screen or as
 * deliberately `null`.
 */
class NavKeyAnalyticsTest {
    /** One sample instance per route → the screen it must report (`null` = deliberately untracked). */
    private val expected: Map<NavKey, AnalyticsScreen?> =
        mapOf(
            // Outer shell. Splash/Main are containers; MainShell tracks the real screen.
            Splash to null,
            Main to null,
            Login to AnalyticsScreen.Login,
            Onboarding to AnalyticsScreen.Onboarding,
            // Top-level tabs.
            Feed to AnalyticsScreen.Feed,
            Search to AnalyticsScreen.Search,
            NotificationsTab to AnalyticsScreen.Notifications,
            Chats to AnalyticsScreen.Chats,
            // Sub-routes.
            Profile(handle = "alice.bsky.social") to AnalyticsScreen.Profile,
            PostDetailRoute(postUri = "at://x") to AnalyticsScreen.PostDetail,
            ComposerRoute() to AnalyticsScreen.Composer,
            EditProfile() to AnalyticsScreen.EditProfile,
            Settings to AnalyticsScreen.Settings,
            Bookmarks to AnalyticsScreen.Bookmarks,
            Chat(otherUserDid = "did:plc:x") to AnalyticsScreen.ChatThread,
            MediaViewerRoute(postUri = "at://x", imageIndex = 0) to AnalyticsScreen.MediaViewer,
            VideoFeed() to AnalyticsScreen.VideoFeed,
            FeedView(feedUri = "at://feed") to AnalyticsScreen.FeedView,
            Feeds to AnalyticsScreen.Feeds,
            Hashtag(tag = "kotlin") to AnalyticsScreen.Hashtag,
            PaywallRoute() to AnalyticsScreen.Paywall,
            BlockedAccounts to AnalyticsScreen.BlockedAccounts,
            SafetyAndReporting to AnalyticsScreen.SafetyAndReporting,
            // Settings sub-pages.
            Appearance to AnalyticsScreen.SettingsAppearance,
            ContentFilters to AnalyticsScreen.SettingsContentFilters,
            FeedPreferences to AnalyticsScreen.SettingsFeedPreferences,
            MediaAndAnimations to AnalyticsScreen.SettingsMediaAndAnimations,
            Moderation to AnalyticsScreen.SettingsModeration,
            About to AnalyticsScreen.About,
            AboutLicenses to AnalyticsScreen.AboutLicenses,
            // Group chats.
            NewGroup to AnalyticsScreen.NewGroup,
            GroupDetails(convoId = "c") to AnalyticsScreen.GroupDetails,
            AddGroupMembers(convoId = "c") to AnalyticsScreen.GroupAddMembers,
            GroupJoinRequests(convoId = "c") to AnalyticsScreen.GroupJoinRequests,
            ManageJoinLink(convoId = "c") to AnalyticsScreen.GroupJoinLink,
            GroupJoinPreview(code = "x") to AnalyticsScreen.GroupJoinPreview,
            ChatSettings to AnalyticsScreen.ChatSettings,
            // Deliberately untracked.
            NewChat to null, // recipient picker, not a destination screen
            VideoPlayerRoute(postUri = "at://x") to null, // video_play covers fullscreen playback
            PostDeepLinkKey(handle = "alice.bsky.social", rkey = "3lkbabcdefghi") to null, // transport only
            PaywallSuccessRoute to null, // celebration (nubecita-ykpc)
            Report(subject = ReportSubject.Account(did = "did:plc:x")) to null, // dialog
            Block(did = "did:plc:x", handle = "alice.bsky.social") to null, // dialog
        )

    @Test
    fun everyRoute_mapsToItsExpectedScreen() {
        expected.forEach { (route, screen) ->
            assertEquals(screen, route.toAnalyticsScreenOrNull(), "screen for ${route::class.simpleName}")
        }
    }

    @Test
    fun profile_ownAndOther_bothMapToProfile_droppingTheHandle() {
        assertEquals(AnalyticsScreen.Profile, Profile(handle = null).toAnalyticsScreenOrNull())
        assertEquals(AnalyticsScreen.Profile, Profile(handle = "alice.bsky.social").toAnalyticsScreenOrNull())
    }

    @Test
    fun everyNavKeyOnTheClasspath_hasAScreenViewDecision() {
        val routes = navKeyClassNamesOnClasspath()
        // Guard against a vacuous pass: a scan that found nothing would "cover" everything.
        assertTrue(Feed::class.java.name in routes && Main::class.java.name in routes) {
            "classpath scan found no feature or :app routes — the scan itself is broken: $routes"
        }

        val decided = expected.keys.map { it::class.java.name }.toSet()
        val undecided = routes - decided
        assertTrue(undecided.isEmpty()) {
            "Route(s) with no screen_view decision: $undecided. Map each in " +
                "NavKeyAnalytics.toAnalyticsScreenOrNull (or return null on purpose) and add it to `expected` here."
        }
    }

    /** Every concrete `NavKey` under `net.kikin.nubecita` in the app's runtime (non-test) classpath. */
    private fun navKeyClassNamesOnClasspath(): Set<String> {
        val loader = javaClass.classLoader
        return System
            .getProperty("java.class.path")
            .split(File.pathSeparator)
            .filterNot { TEST_OUTPUT.containsMatchIn(it) }
            .flatMap { classNames(File(it)) }
            .mapNotNull { name -> runCatching { Class.forName(name, false, loader) }.getOrNull() }
            .filter { NavKey::class.java.isAssignableFrom(it) && !it.isInterface && !Modifier.isAbstract(it.modifiers) }
            .map { it.name }
            .toSet()
    }

    private fun classNames(entry: File): List<String> =
        when {
            entry.isDirectory ->
                File(entry, PACKAGE_PATH)
                    .takeIf { it.isDirectory }
                    ?.walkTopDown()
                    ?.filter { it.isFile && it.name.endsWith(".class") }
                    ?.map { it.relativeTo(entry).invariantSeparatorsPath }
                    ?.toList()
                    .orEmpty()
            entry.isFile && entry.name.endsWith(".jar") ->
                JarFile(entry).use { jar ->
                    jar
                        .entries()
                        .asSequence()
                        .map { it.name }
                        .filter { it.startsWith(PACKAGE_PATH) && it.endsWith(".class") }
                        .toList()
                }
            else -> emptyList()
        }.filterNot { '$' in it } // top-level routes only; skips companions and nested types
            .map { it.removeSuffix(".class").replace('/', '.') }

    private companion object {
        const val PACKAGE_PATH = "net/kikin/nubecita/"

        // Test output dirs/jars hold test-only fake routes; production routes only.
        val TEST_OUTPUT = Regex("(?i)unittest|/test/|test-classes")
    }
}
