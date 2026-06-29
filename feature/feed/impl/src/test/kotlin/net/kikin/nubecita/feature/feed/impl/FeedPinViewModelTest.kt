package net.kikin.nubecita.feature.feed.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.FeedAction
import net.kikin.nubecita.core.analytics.InteractFeed
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsResult
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

private const val FEED_URI = "at://did:plc:test/app.bsky.feed.generator/test-feed"

/**
 * Unit tests for [FeedPinViewModel].
 *
 * TDD approach: tests are written against the behaviour contract (isPinned
 * reflects the repository, togglePin calls the right repo method and logs
 * analytics, failure emits an error effect), exercised through a
 * [FakePinnedFeedsRepositoryForPin] that lets each test drive the outcome.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedPinViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val analytics = RecordingAnalyticsClient()

    // -------------------------------------------------------------------------
    // isPinned reflects observePinnedFeeds
    // -------------------------------------------------------------------------

    @Test
    fun `isPinned is false when feedUri is absent from pinned feeds`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePinnedFeedsRepositoryForPin(pinned = false)
            val vm = FeedPinViewModel(feedUri = FEED_URI, pinnedFeedsRepository = repo, analytics = analytics)

            advanceUntilIdle()

            assertFalse(vm.uiState.value.isPinned)
        }

    @Test
    fun `isPinned is true when feedUri is present in pinned feeds`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePinnedFeedsRepositoryForPin(pinned = true)
            val vm = FeedPinViewModel(feedUri = FEED_URI, pinnedFeedsRepository = repo, analytics = analytics)

            advanceUntilIdle()

            assertTrue(vm.uiState.value.isPinned)
        }

    @Test
    fun `isPinned updates reactively when pinned feeds change`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePinnedFeedsRepositoryForPin(pinned = false)
            val vm = FeedPinViewModel(feedUri = FEED_URI, pinnedFeedsRepository = repo, analytics = analytics)

            advanceUntilIdle()
            assertFalse(vm.uiState.value.isPinned)

            // Simulate the repository emitting the feed as pinned.
            repo.setPinned(true)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.isPinned)
        }

    // -------------------------------------------------------------------------
    // togglePin when unpinned → pinFeed + logs Pin
    // -------------------------------------------------------------------------

    @Test
    fun `togglePin when unpinned calls pinFeed and logs InteractFeed Pin`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePinnedFeedsRepositoryForPin(pinned = false)
            val vm = FeedPinViewModel(feedUri = FEED_URI, pinnedFeedsRepository = repo, analytics = analytics)

            advanceUntilIdle()
            vm.handleEvent(FeedPinEvent.TogglePin)
            advanceUntilIdle()

            assertEquals(1, repo.pinCalls)
            assertEquals(0, repo.unpinCalls)
            assertEquals(
                listOf(InteractFeed(FeedAction.Pin, PostSurface.FeedView)),
                analytics.events,
            )
        }

    // -------------------------------------------------------------------------
    // togglePin when pinned → unpinFeed + logs Unpin
    // -------------------------------------------------------------------------

    @Test
    fun `togglePin when pinned calls unpinFeed and logs InteractFeed Unpin`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakePinnedFeedsRepositoryForPin(pinned = true)
            val vm = FeedPinViewModel(feedUri = FEED_URI, pinnedFeedsRepository = repo, analytics = analytics)

            advanceUntilIdle()
            vm.handleEvent(FeedPinEvent.TogglePin)
            advanceUntilIdle()

            assertEquals(0, repo.pinCalls)
            assertEquals(1, repo.unpinCalls)
            assertEquals(
                listOf(InteractFeed(FeedAction.Unpin, PostSurface.FeedView)),
                analytics.events,
            )
        }

    // -------------------------------------------------------------------------
    // failure → error effect, no crash
    // -------------------------------------------------------------------------

    @Test
    fun `togglePin failure emits ShowError effect and does not crash`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakePinnedFeedsRepositoryForPin(
                    pinned = false,
                    pinResult = Result.failure(RuntimeException("network error")),
                )
            val vm = FeedPinViewModel(feedUri = FEED_URI, pinnedFeedsRepository = repo, analytics = analytics)

            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(FeedPinEvent.TogglePin)
                advanceUntilIdle()

                val effect = awaitItem()
                assertEquals(FeedPinEffect.ShowError, effect)
            }
            // No analytics logged on failure.
            assertTrue(analytics.events.isEmpty())
        }

    @Test
    fun `togglePin unpin failure emits ShowError effect and does not crash`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakePinnedFeedsRepositoryForPin(
                    pinned = true,
                    unpinResult = Result.failure(RuntimeException("network error")),
                )
            val vm = FeedPinViewModel(feedUri = FEED_URI, pinnedFeedsRepository = repo, analytics = analytics)

            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(FeedPinEvent.TogglePin)
                advanceUntilIdle()

                val effect = awaitItem()
                assertEquals(FeedPinEffect.ShowError, effect)
            }
            // No analytics logged on failure.
            assertTrue(analytics.events.isEmpty())
        }

    // -------------------------------------------------------------------------
    // Rapid-tap guard — second in-flight tap is dropped
    // -------------------------------------------------------------------------

    @Test
    fun `rapid double togglePin results in a single repo call and single analytics event`() =
        runTest(mainDispatcher.dispatcher) {
            // pinDelayMs keeps the first coroutine suspended so the second tap
            // arrives while the job is still active — proving the Job guard fires.
            val repo =
                FakePinnedFeedsRepositoryForPin(
                    pinned = false,
                    pinDelayMs = 100,
                )
            val vm = FeedPinViewModel(feedUri = FEED_URI, pinnedFeedsRepository = repo, analytics = analytics)

            advanceUntilIdle()

            // First tap — launches the coroutine which suspends at delay(100).
            vm.handleEvent(FeedPinEvent.TogglePin)
            // Second tap arrives immediately while the coroutine is still in flight.
            vm.handleEvent(FeedPinEvent.TogglePin)

            // Let the first coroutine finish (delay elapses + analytics logged).
            advanceUntilIdle()

            assertEquals(1, repo.pinCalls)
            assertEquals(0, repo.unpinCalls)
            assertEquals(1, analytics.events.size)
            assertEquals(
                listOf(InteractFeed(FeedAction.Pin, PostSurface.FeedView)),
                analytics.events,
            )
        }
}

/**
 * Minimal [PinnedFeedsRepository] fake for [FeedPinViewModelTest].
 *
 * [pinned] controls the initial [observePinnedFeeds] emission. Call
 * [setPinned] to push a new value mid-test. [pinResult] / [unpinResult]
 * let individual tests inject failures without Mockito/MockK.
 * [pinDelayMs] introduces a virtual-time delay inside [pinFeed] so the
 * rapid-tap test can verify that the in-flight Job guard fires before the
 * coroutine completes (requires [delay] to be a real suspension point).
 */
private class FakePinnedFeedsRepositoryForPin(
    pinned: Boolean,
    private val pinResult: Result<Unit> = Result.success(Unit),
    private val unpinResult: Result<Unit> = Result.success(Unit),
    private val pinDelayMs: Long = 0,
) : PinnedFeedsRepository {
    var pinCalls = 0
    var unpinCalls = 0

    private val flow =
        MutableStateFlow(
            PinnedFeedsResult(
                feeds =
                    if (pinned) {
                        persistentListOf(
                            PinnedFeedUi(
                                id = FEED_URI,
                                uri = FEED_URI,
                                kind = FeedKind.Generator,
                                displayName = "Test Feed",
                                avatarUrl = null,
                            ),
                        )
                    } else {
                        persistentListOf()
                    },
                usedFallback = false,
            ),
        )

    fun setPinned(pinned: Boolean) {
        flow.value =
            PinnedFeedsResult(
                feeds =
                    if (pinned) {
                        persistentListOf(
                            PinnedFeedUi(
                                id = FEED_URI,
                                uri = FEED_URI,
                                kind = FeedKind.Generator,
                                displayName = "Test Feed",
                                avatarUrl = null,
                            ),
                        )
                    } else {
                        persistentListOf()
                    },
                usedFallback = false,
            )
    }

    override fun observePinnedFeeds() = flow.asStateFlow()

    override suspend fun refresh() = Result.success(Unit)

    override fun validateSelectedFeedUri(
        uri: String?,
        pinned: List<PinnedFeedUi>,
    ): String = uri ?: PinnedFeedsRepository.FOLLOWING_FEED_URI

    override suspend fun pinFeed(uri: String): Result<Unit> {
        if (pinDelayMs > 0) delay(pinDelayMs)
        pinCalls++
        return pinResult
    }

    override suspend fun unpinFeed(uri: String): Result<Unit> {
        unpinCalls++
        return unpinResult
    }
}
