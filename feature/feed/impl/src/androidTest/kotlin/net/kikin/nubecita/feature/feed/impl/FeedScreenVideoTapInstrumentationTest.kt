package net.kikin.nubecita.feature.feed.impl

import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import net.kikin.nubecita.core.testing.android.HiltTestActivity
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.feed.impl.testing.FakeFeedRepository
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * Instrumented coverage for `nubecita-zak.8` — pins the user-tap path
 * that zak.5 landed: tapping the autoplay video region on a feed
 * `PostCard` routes to `onNavigateToVideoPlayer(postUri)` with the
 * post's AT URI.
 *
 * The fullscreen route itself lives in `:feature:videoplayer:impl`
 * (separate Kotlin module → its `internal` screen isn't visible here),
 * so this test stops at the navigation handoff: it captures the
 * `onNavigateToVideoPlayer` callback off `FeedScreen` and asserts the
 * URI argument. The other side of the handoff — the videoplayer VM's
 * `Resolving → Ready` transition + SharedVideoPlayer mode flip — is
 * covered by `:core:video`'s unit tests (zak.1) and the videoplayer
 * VM unit tests (zak.4); this test bolts the user-facing tap onto
 * those.
 *
 * Hilt graph: `FakeFeedRepository` swaps the real repository via
 * `TestFeedRepositoryModule`, so the timeline is deterministic and
 * never hits the network. The post carries an `EmbedUi.Video` with an
 * `example.com` playlist URL — ExoPlayer will fail the HLS load
 * on-device, but the tap-routing the test asserts doesn't depend on
 * successful playback.
 */
@HiltAndroidTest
class FeedScreenVideoTapInstrumentationTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<HiltTestActivity>()

    @Inject
    internal lateinit var feedRepository: net.kikin.nubecita.feature.feed.impl.data.FeedRepository

    @Before
    fun setUp() {
        hiltRule.inject()
        // Swap the default three-text-post timeline for one with a
        // single video post so the video card is unambiguous in the
        // semantics tree.
        (feedRepository as FakeFeedRepository).page =
            FakeFeedRepository.singleVideoPostTimeline(
                postUri = VIDEO_POST_URI,
                playlistUrl = "https://example.com/test-stream.m3u8",
                posterUrl = null,
                altText = VIDEO_ALT_TEXT,
            )
    }

    @Test
    fun videoCardTap_dispatchesOnNavigateToVideoPlayer_withPostUri() {
        val capturedUris = mutableListOf<String>()

        composeTestRule.setContent {
            NubecitaTheme {
                FeedScreen(
                    onNavigateToVideoPlayer = { uri -> capturedUris += uri },
                )
            }
        }

        // FeedScreen fires FeedEvent.Load on first composition; wait for
        // the timeline to render. The video poster (gradient-fallback
        // branch since posterUrl is null) carries the altText as its
        // `contentDescription`, so we key the wait on that label.
        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            composeTestRule
                .onAllNodes(hasContentDescription(VIDEO_ALT_TEXT))
                .fetchSemanticsNodes()
                .isNotEmpty()
        }

        // Tap the first node carrying the video altText. The PostCard's
        // outer `clickable { callbacks.onTap }` would normally route to
        // PostDetail, but PostCardVideoEmbed installs its own inner
        // clickable on the video region that absorbs the tap and routes
        // to `onVideoTap → FeedEvent.OnVideoTapped → onNavigateToVideoPlayer`.
        // Hitting a node inside the video region (the poster / gradient
        // fallback Box) lets that inner clickable fire instead of the
        // outer card tap.
        composeTestRule
            .onAllNodes(hasContentDescription(VIDEO_ALT_TEXT))
            .onFirst()
            .performClick()

        composeTestRule.waitUntil(timeoutMillis = WAIT_TIMEOUT_MILLIS) {
            capturedUris.isNotEmpty()
        }

        assertEquals(listOf(VIDEO_POST_URI), capturedUris)
    }

    private companion object {
        const val WAIT_TIMEOUT_MILLIS = 5_000L
        const val VIDEO_POST_URI = "at://did:plc:video/app.bsky.feed.post/v1"
        const val VIDEO_ALT_TEXT = "Test video alt text for zak.8"
    }
}
