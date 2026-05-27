package net.kikin.nubecita.feature.notifications.impl

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.common.navigation.LocalMainShellNavState
import net.kikin.nubecita.core.common.navigation.rememberMainShellNavState
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.notifications.api.NotificationsTab
import net.kikin.nubecita.feature.notifications.impl.testing.FakeNotificationsRepository
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Smoke instrumentation test for [NotificationsScreen]. Verifies that
 * the screen renders the page returned by its
 * [net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository]
 * dependency, that filter chips are tappable, and that an aggregated
 * row's headline reads as the mapper produced it.
 *
 * The repository is faked via Hilt's `@TestInstallIn` replacement of
 * `NotificationsRepositoryModule` (see
 * [net.kikin.nubecita.feature.notifications.impl.testing.TestNotificationsRepositoryModule])
 * so the test never touches the real network or auth stack. Same shape
 * the Feed instrumentation tests use.
 *
 * `LocalMainShellNavState` has no default value and throws when read
 * from a non-shell-hosted Composable — we provide a real instance via
 * `rememberMainShellNavState(startRoute = NotificationsTab,
 * topLevelRoutes = …)` so the screen's tab-exit detection collector +
 * `mainShellNavState.add(target)` call site both function. The single-
 * tab list means no actual nav happens during the test; we only assert
 * on the rendered content.
 *
 * Filed under nubecita-1fy.1.10. The smoke covers happy paths:
 * - Initial render: rows from the fake page appear.
 * - Filter chip switch: tapping "Mentions" triggers a refetch through
 *   the VM (assertion sits on the chip being selected; the fake repo
 *   returns the same page so visible rows don't change).
 */
@HiltAndroidTest
internal class NotificationsScreenInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    // Hilt populates this with the same @Singleton instance the screen's
    // VM sees. The @Inject binding for the concrete type co-exists with
    // the @Binds-as-NotificationsRepository binding because both point at
    // the same @Singleton @Inject constructor — no second instance.
    @Inject
    lateinit var fakeRepository: FakeNotificationsRepository

    @Test
    fun notificationsScreen_rendersFakedPage() {
        hiltRule.inject()
        composeTestRule.setContent {
            val mainShellNavState =
                rememberMainShellNavState(
                    startRoute = NotificationsTab,
                    topLevelRoutes = listOf(NotificationsTab),
                )
            CompositionLocalProvider(LocalMainShellNavState provides mainShellNavState) {
                NubecitaTheme {
                    NotificationsScreen()
                }
            }
        }

        // The VM fires its first fetch from init; the load bounces through
        // viewModelScope -> StateFlow -> recomposition. waitUntil is the
        // recommended replacement for IdlingResource on StateFlow-driven UI
        // (Android docs: "Alternatives to Idling Resources in Compose tests").
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodesWithText(LIKED_YOUR_POST, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Aggregated likes row (3 actors): "Alice Chen and 2 others liked your post"
        composeTestRule
            .onNodeWithText(LIKED_YOUR_POST, substring = true)
            .assertIsDisplayed()

        // Single reply row: "Alice Chen replied to your post"
        composeTestRule
            .onNode(hasText(REPLIED_TO_YOUR_POST, substring = true))
            .assertIsDisplayed()

        // Follow row: "Alice Chen and 1 other followed you" (2 actors)
        composeTestRule
            .onNode(hasText(FOLLOWED_YOU, substring = true))
            .assertIsDisplayed()
    }

    @Test
    fun notificationsScreen_filterChipTap_triggersRefetch() {
        hiltRule.inject()
        composeTestRule.setContent {
            val mainShellNavState =
                rememberMainShellNavState(
                    startRoute = NotificationsTab,
                    topLevelRoutes = listOf(NotificationsTab),
                )
            CompositionLocalProvider(LocalMainShellNavState provides mainShellNavState) {
                NubecitaTheme {
                    NotificationsScreen()
                }
            }
        }

        // Wait for the initial page to settle so the chip strip is rendered
        // over Loaded state.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodesWithText(LIKED_YOUR_POST, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Capture the call count BEFORE the tap so the post-tap assertion
        // doesn't trip on the initial-load fetch that already fired with
        // NotificationFilter.All.
        val callCountBeforeTap = fakeRepository.fetchPageCalls.size

        // Tap the Mentions chip. The VM's filter-switch reducer should
        // fire fetchPage(filter = Mentions, cursor = null) through the
        // fake repo.
        composeTestRule
            .onNodeWithText(MENTIONS_LABEL)
            .performClick()

        // Wait for the recorded call rather than for visible-text settle —
        // the fake returns the same default page for every filter, so a
        // text-based wait would also pass if the tap were a no-op. Polling
        // fetchPageCalls is the assertion the test is actually claiming.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            fakeRepository.fetchPageCalls
                .drop(callCountBeforeTap)
                .any { it.filter == NotificationFilter.Mentions }
        }

        assertTrue(
            "expected a fetchPage call with filter=Mentions after chip tap; recorded calls were " +
                fakeRepository.fetchPageCalls.joinToString(),
            fakeRepository.fetchPageCalls
                .drop(callCountBeforeTap)
                .any { it.filter == NotificationFilter.Mentions },
        )
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L

        // Substring matches against the localized headline copies. The
        // fixture actors are named "Alice Chen", "Bob Diaz", … so the
        // headlines render as "Alice Chen and 2 others liked your post"
        // etc. The substring is the verb phrase that's stable across
        // every actor permutation.
        const val LIKED_YOUR_POST = "liked your post"
        const val REPLIED_TO_YOUR_POST = "replied to your post"
        const val FOLLOWED_YOU = "followed you"

        // Chip-row label; English copy from R.string.notifications_filter_mentions.
        const val MENTIONS_LABEL = "Mentions"
    }
}
