package net.kikin.nubecita.navigation

import android.content.Context
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.api.ChatSettings
import net.kikin.nubecita.feature.chats.api.Chats
import net.kikin.nubecita.feature.chats.api.NewChat
import net.kikin.nubecita.feature.composer.api.ComposerRoute
import net.kikin.nubecita.feature.feed.api.Feed
import net.kikin.nubecita.feature.feeds.api.Feeds
import net.kikin.nubecita.feature.login.api.Login
import net.kikin.nubecita.feature.mediaviewer.api.MediaViewerRoute
import net.kikin.nubecita.feature.moderation.api.Block
import net.kikin.nubecita.feature.moderation.api.BlockedAccounts
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import net.kikin.nubecita.feature.paywall.api.PaywallRoute
import net.kikin.nubecita.feature.paywall.api.PaywallSuccessRoute
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.search.api.Search
import net.kikin.nubecita.feature.settings.api.About
import net.kikin.nubecita.feature.settings.api.AboutLicenses
import net.kikin.nubecita.feature.settings.api.ContentFilters
import net.kikin.nubecita.feature.settings.api.Moderation
import net.kikin.nubecita.feature.settings.api.Settings
import net.kikin.nubecita.feature.videoplayer.api.VideoPlayerRoute
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression guard for the "feature `:impl` module not aggregated in `:app`"
 * crash class (`nubecita-33cb`): a `@MainShell` / `@OuterShell`
 * [androidx.navigation3.runtime.NavKey] is pushed at runtime but no
 * `EntryProviderInstaller` registered an `entry<>` for it (because the module
 * that provides the installer isn't on `:app`'s dependency graph), so the inner
 * `NavDisplay`'s `entryProvider` fallback throws `IllegalStateException: Unknown
 * screen <key>` and the app crashes.
 *
 * This builds the REAL aggregated entry providers from the actual `:app` Hilt
 * graph (via [NavigationEntryPoint], the same multibindings `MainShell` /
 * `Navigation` consume) and asserts every known route resolves to an entry. It
 * runs under `HiltTestApplication`, so it needs no signed-in session and runs on
 * the production-flavor connected job — i.e. it guards the routes regardless of
 * flavor. Drop a feature impl from `app/build.gradle.kts` and the matching
 * route(s) below fail here instead of crashing a user.
 *
 * Adding a new `@MainShell` / `@OuterShell` route? Add it to the relevant list.
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class MainShellRouteCoverageTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    private lateinit var entryPoint: NavigationEntryPoint

    @Before
    fun setUp() {
        hiltRule.inject()
        val context = ApplicationProvider.getApplicationContext<Context>()
        entryPoint = EntryPointAccessors.fromApplication(context, NavigationEntryPoint::class.java)
    }

    @Test
    fun everyMainShellRouteResolvesToAnEntry() {
        assertRoutesResolve(
            installers = entryPoint.mainShellEntryProviderInstallers(),
            routes = MAIN_SHELL_ROUTES,
            shell = "@MainShell",
        )
    }

    @Test
    fun everyOuterShellRouteResolvesToAnEntry() {
        assertRoutesResolve(
            installers = entryPoint.outerEntryProviderInstallers(),
            routes = OUTER_SHELL_ROUTES,
            shell = "@OuterShell",
        )
    }

    private fun assertRoutesResolve(
        installers: Set<EntryProviderInstaller>,
        routes: List<NavKey>,
        shell: String,
    ) {
        // Build the same aggregated provider MainShell / Navigation build. Resolving
        // a key returns its NavEntry without composing it; an unregistered key hits
        // the entryProvider fallback and throws.
        val provider = entryProvider { installers.forEach { it() } }
        val unresolved =
            routes.filter { route ->
                // Catch Exception (the entryProvider fallback throws
                // IllegalStateException for an unregistered key), NOT Throwable —
                // a JVM Error (NoClassDefFoundError / LinkageError) signals a real
                // classpath problem that should fail loudly, not be reported as an
                // "unresolved route".
                try {
                    provider(route)
                    false
                } catch (
                    @Suppress("SwallowedException") e: Exception,
                ) {
                    true
                }
            }
        assertEquals(
            "$shell routes with no registered entry<> — the feature impl that " +
                "installs them is likely missing from :app's dependencies (see nubecita-33cb): $unresolved",
            emptyList<NavKey>(),
            unresolved,
        )
    }

    private companion object {
        // Dummy args for parameterized keys — the entryProvider resolves by type,
        // so the values are irrelevant to entry lookup.
        val MAIN_SHELL_ROUTES: List<NavKey> =
            listOf(
                Feed,
                Search,
                NotificationsTab,
                Chats,
                Chat(otherUserDid = "did:plc:test"),
                ChatSettings,
                NewChat,
                Profile(handle = null),
                Settings,
                Moderation,
                ContentFilters,
                BlockedAccounts,
                About,
                AboutLicenses,
                PostDetailRoute(postUri = "at://did:plc:test/app.bsky.feed.post/test"),
                ComposerRoute(),
                PaywallRoute(),
                PaywallSuccessRoute,
                Report.forAccount(did = "did:plc:test"),
                Block.forAccount(did = "did:plc:test", handle = "test.bsky.social"),
                Feeds,
            )

        val OUTER_SHELL_ROUTES: List<NavKey> =
            listOf(
                Login,
                Onboarding,
                MediaViewerRoute(postUri = "at://did:plc:test/app.bsky.feed.post/test", imageIndex = 0),
                VideoPlayerRoute(postUri = "at://did:plc:test/app.bsky.feed.post/test"),
            )
    }
}
