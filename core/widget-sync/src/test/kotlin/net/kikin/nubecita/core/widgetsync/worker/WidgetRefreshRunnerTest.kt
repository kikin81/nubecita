package net.kikin.nubecita.core.widgetsync.worker

import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feedcache.FeedType
import net.kikin.nubecita.core.widgetsync.WidgetImagePrefetcher
import net.kikin.nubecita.core.widgetsync.WidgetUpdater
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class WidgetRefreshRunnerTest {
    @Test
    fun `signed out is a no-op success without refreshing`() =
        runTest {
            val repo = mockk<FeedRepository>()
            val updater = RecordingUpdater()
            val runner = runner(repo, session = SessionState.SignedOut, updater = updater)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 0) { repo.refresh(any()) }
            coVerify(exactly = 0) { repo.trimToCap(any(), any()) }
            assertEquals(0, updater.calls)
        }

    @Test
    fun `foregrounded suppresses refresh and updater (D-B4)`() =
        runTest {
            val repo = mockk<FeedRepository>()
            val updater = RecordingUpdater()
            val runner = runner(repo, foregrounded = true, updater = updater)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 0) { repo.refresh(any()) }
            coVerify(exactly = 0) { repo.trimToCap(any(), any()) }
            assertEquals(0, updater.calls)
        }

    @Test
    fun `backgrounded signed-in refreshes both feeds, trims each, updates once`() =
        runTest {
            val repo = mockk<FeedRepository>()
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any()) } just Runs
            val updater = RecordingUpdater()
            val prefetcher = RecordingPrefetcher()
            val runner = runner(repo, updater = updater, prefetcher = prefetcher)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())

            // Both MVP feeds (Following + Discover) for the signed-in DID.
            coVerify(exactly = 1) { repo.refresh(FeedKey.following(VIEWER)) }
            coVerify(exactly = 1) { repo.refresh(match { it.feedType == FeedType.DISCOVER && it.accountDid == VIEWER }) }
            coVerify(exactly = 1) { repo.trimToCap(FeedKey.following(VIEWER), any()) }
            coVerify(exactly = 1) { repo.trimToCap(match { it.feedType == FeedType.DISCOVER }, any()) }
            assertEquals(1, updater.calls)
            // D-C4: each succeeded feed is prefetched once (off-scroll).
            assertEquals(listOf(FeedType.FOLLOWING, FeedType.DISCOVER), prefetcher.prefetched.map { it.feedType })
        }

    @Test
    fun `every feed failing retries and does not update`() =
        runTest {
            val repo = mockk<FeedRepository>()
            coEvery { repo.refresh(any()) } returns Result.failure(IOException("down"))
            val updater = RecordingUpdater()
            val prefetcher = RecordingPrefetcher()
            val runner = runner(repo, updater = updater, prefetcher = prefetcher)

            assertEquals(WidgetRefreshRunner.Outcome.RETRY, runner.run())
            coVerify(exactly = 0) { repo.trimToCap(any(), any()) }
            assertEquals(0, updater.calls)
            // No feed succeeded → nothing prefetched.
            assertEquals(emptyList<FeedKey>(), prefetcher.prefetched)
        }

    @Test
    fun `partial success returns SUCCESS, trims only the succeeded feed, updates once`() =
        runTest {
            val repo = mockk<FeedRepository>()
            // Following succeeds, Discover fails.
            coEvery { repo.refresh(FeedKey.following(VIEWER)) } returns Result.success(false)
            coEvery { repo.refresh(match { it.feedType == FeedType.DISCOVER }) } returns Result.failure(IOException("down"))
            coEvery { repo.trimToCap(any(), any()) } just Runs
            val updater = RecordingUpdater()
            val prefetcher = RecordingPrefetcher()
            val runner = runner(repo, updater = updater, prefetcher = prefetcher)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())

            // Only the succeeded feed is trimmed; the failed one is left for the
            // next periodic run (D-B7: a succeeded feed is never re-fetched by a retry).
            coVerify(exactly = 1) { repo.trimToCap(FeedKey.following(VIEWER), any()) }
            coVerify(exactly = 0) { repo.trimToCap(match { it.feedType == FeedType.DISCOVER }, any()) }
            assertEquals(1, updater.calls)
            // Only the succeeded feed is prefetched (D-C4): prefetch is gated on refresh.
            assertEquals(listOf(FeedType.FOLLOWING), prefetcher.prefetched.map { it.feedType })
        }

    @Test
    fun `a feed that throws is isolated and does not abort the other feed`() =
        runTest {
            val repo = mockk<FeedRepository>()
            // Following throws an UNEXPECTED exception (not a Result.failure);
            // Discover still succeeds.
            coEvery { repo.refresh(FeedKey.following(VIEWER)) } throws IllegalStateException("db constraint")
            coEvery { repo.refresh(match { it.feedType == FeedType.DISCOVER }) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any()) } just Runs
            val updater = RecordingUpdater()
            val runner = runner(repo, updater = updater)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            // The throw didn't abort the loop — Discover was still refreshed + trimmed.
            coVerify(exactly = 1) { repo.trimToCap(match { it.feedType == FeedType.DISCOVER }, any()) }
            coVerify(exactly = 0) { repo.trimToCap(FeedKey.following(VIEWER), any()) }
            assertEquals(1, updater.calls)
        }

    @Test
    fun `a widget-updater failure still returns SUCCESS (no network retry)`() =
        runTest {
            val repo = mockk<FeedRepository>()
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any()) } just Runs
            val updater =
                object : WidgetUpdater {
                    override suspend fun updateFeedWidgets() = throw IllegalStateException("glance IPC")
                }
            val runner = runner(repo, updater = updater)

            // Cache already refreshed — a widget-render failure must not trigger a
            // network-heavy WorkManager retry.
            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
        }

    @Test
    fun `a session refresh failure returns SUCCESS without refreshing`() =
        runTest {
            val repo = mockk<FeedRepository>()
            val updater = RecordingUpdater()
            val throwingProvider =
                object : SessionStateProvider {
                    override val state: StateFlow<SessionState> = MutableStateFlow(SessionState.Loading)

                    override suspend fun refresh(): Unit = throw IllegalStateException("session store corrupt")
                }
            val runner =
                WidgetRefreshRunner(
                    repository = repo,
                    sessionStateProvider = throwingProvider,
                    foreground = FakeForeground(foregrounded = false),
                    widgetUpdater = updater,
                    imagePrefetcher = RecordingPrefetcher(),
                )

            // A storage error during session hydration must not crash the worker
            // or trigger a retry loop — return SUCCESS and do no work.
            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 0) { repo.refresh(any()) }
            assertEquals(0, updater.calls)
        }

    @Test
    fun `a prefetch failure is isolated and does not fail the refresh (D-C4)`() =
        runTest {
            val repo = mockk<FeedRepository>()
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any()) } just Runs
            val updater = RecordingUpdater()
            // Following's image prefetch throws; Discover's succeeds.
            val prefetcher =
                object : WidgetImagePrefetcher {
                    val prefetched = mutableListOf<FeedKey>()

                    override suspend fun prefetch(feedKey: FeedKey) {
                        if (feedKey.feedType == FeedType.FOLLOWING) throw IllegalStateException("decode oom")
                        prefetched += feedKey
                    }
                }
            val runner = runner(repo, updater = updater, prefetcher = prefetcher)

            // The prefetch throw fails only Following's images: the refresh already
            // succeeded, so the worker still returns SUCCESS, both feeds are still
            // refreshed + trimmed, Discover is still prefetched, and the updater runs.
            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 1) { repo.trimToCap(FeedKey.following(VIEWER), any()) }
            coVerify(exactly = 1) { repo.trimToCap(match { it.feedType == FeedType.DISCOVER }, any()) }
            assertEquals(listOf(FeedType.DISCOVER), prefetcher.prefetched.map { it.feedType })
            assertEquals(1, updater.calls)
        }

    private companion object {
        const val VIEWER = "did:plc:viewer"
    }

    private fun runner(
        repo: FeedRepository,
        session: SessionState = SessionState.SignedIn(handle = "me.bsky.social", did = VIEWER),
        foregrounded: Boolean = false,
        updater: WidgetUpdater = RecordingUpdater(),
        prefetcher: WidgetImagePrefetcher = RecordingPrefetcher(),
    ): WidgetRefreshRunner =
        WidgetRefreshRunner(
            repository = repo,
            sessionStateProvider = FakeSessionStateProvider(session),
            foreground = FakeForeground(foregrounded),
            widgetUpdater = updater,
            imagePrefetcher = prefetcher,
        )

    private class RecordingUpdater : WidgetUpdater {
        var calls = 0

        override suspend fun updateFeedWidgets() {
            calls++
        }
    }

    private class RecordingPrefetcher : WidgetImagePrefetcher {
        val prefetched = mutableListOf<FeedKey>()

        override suspend fun prefetch(feedKey: FeedKey) {
            prefetched += feedKey
        }
    }

    private class FakeForeground(
        private val foregrounded: Boolean,
    ) : AppForegroundSignal {
        override fun isForegrounded(): Boolean = foregrounded
    }

    private class FakeSessionStateProvider(
        state: SessionState,
    ) : SessionStateProvider {
        override val state: StateFlow<SessionState> = MutableStateFlow(state)

        override suspend fun refresh() = Unit
    }
}
