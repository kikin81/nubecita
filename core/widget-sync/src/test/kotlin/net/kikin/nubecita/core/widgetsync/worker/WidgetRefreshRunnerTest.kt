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
import net.kikin.nubecita.core.feedcache.CatchUpOutcome
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.core.feedcache.FeedType
import net.kikin.nubecita.core.wearsync.WearFeedSync
import net.kikin.nubecita.core.widgetsync.PlacedWidgets
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
            coVerify(exactly = 0) { repo.catchUp(any()) }
            coVerify(exactly = 0) { repo.refresh(any()) }
            coVerify(exactly = 0) { repo.trimToCap(any(), any(), any(), any()) }
            assertEquals(0, updater.calls)
        }

    @Test
    fun `foregrounded suppresses refresh and updater (D-B4)`() =
        runTest {
            val repo = mockk<FeedRepository>()
            val updater = RecordingUpdater()
            val runner = runner(repo, foregrounded = true, updater = updater)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 0) { repo.catchUp(any()) }
            coVerify(exactly = 0) { repo.refresh(any()) }
            coVerify(exactly = 0) { repo.trimToCap(any(), any(), any(), any()) }
            assertEquals(0, updater.calls)
        }

    @Test
    fun `no placed widget and no watch skips refresh entirely (nubecita-kvh0)`() =
        runTest {
            val repo = mockk<FeedRepository>()
            val updater = RecordingUpdater()
            val watch = RecordingWearFeedSync(hasWatch = false)
            val runner =
                runner(
                    repo,
                    updater = updater,
                    wearFeedSync = watch,
                    placedWidgets = FakePlacedWidgets(placed = false),
                )

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 0) { repo.refresh(any()) }
            coVerify(exactly = 0) { repo.trimToCap(any(), any(), any(), any()) }
            assertEquals(0, updater.calls)
            assertEquals(0, watch.feedKeyCalls, "the run never reached the watch sync")
            assertEquals(emptyList<String>(), watch.published, "no watch to publish to")
        }

    @Test
    fun `no placed widget but a watch refreshes only the watch feeds, and never retries`() =
        runTest {
            val cats = FeedKey(VIEWER, FeedType.CUSTOM, "at://cats")
            val repo = mockk<FeedRepository>()
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val updater = RecordingUpdater()
            val watch = RecordingWearFeedSync(extraFeeds = listOf(cats))
            val runner =
                runner(
                    repo,
                    updater = updater,
                    wearFeedSync = watch,
                    placedWidgets = FakePlacedWidgets(placed = false),
                )

            // An empty widget feed list must not read as "every feed failed".
            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 1) { repo.refresh(cats) }
            coVerify(exactly = 0) { repo.refresh(FeedKey.following(VIEWER)) }
            assertEquals(0, updater.calls, "no widget to re-render")
            assertEquals(listOf(VIEWER), watch.published)
        }

    @Test
    fun `a failing placed-widget check refreshes rather than freezing a live widget`() =
        runTest {
            val repo = mockk<FeedRepository>()
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val runner =
                runner(
                    repo,
                    wearFeedSync = RecordingWearFeedSync(hasWatch = false),
                    placedWidgets = FakePlacedWidgets(fail = true),
                )

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 1) { repo.refresh(FeedKey.following(VIEWER)) }
        }

    @Test
    fun `a failing watch check runs rather than skipping`() =
        runTest {
            val repo = mockk<FeedRepository>()
            val watch = RecordingWearFeedSync(failHasWatch = true)
            val runner =
                runner(
                    repo,
                    wearFeedSync = watch,
                    placedWidgets = FakePlacedWidgets(placed = false),
                )

            // No widget is placed, so there are no widget feeds to fetch — what
            // must NOT happen is the gate concluding "no consumer" from a watch
            // check it could not answer.
            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            assertEquals(1, watch.feedKeyCalls, "unknown watch state must not skip the run")
        }

    @Test
    fun `backgrounded signed-in refreshes both feeds, trims each, updates once`() =
        runTest {
            val repo = mockk<FeedRepository>()
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
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
    fun `a warm partition is added to, never cleared`() =
        runTest {
            // The regression this epic exists to prevent (D10). refresh() is
            // clearPartition + insert in one transaction, and the worker runs
            // on a period while the app is backgrounded — the one window in
            // which a reader's saved position matters and the foreground guard
            // does not apply. A destructive write here deletes their scrollback
            // and the row their anchor points at, between sittings.
            val repo = mockk<FeedRepository>()
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.CAUGHT_UP)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val updater = RecordingUpdater()
            val prefetcher = RecordingPrefetcher()
            val runner = runner(repo, updater = updater, prefetcher = prefetcher)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())

            coVerify(exactly = 0) { repo.refresh(any()) }
            // Everything downstream of the write is unchanged: both feeds
            // trimmed, both prefetched, one re-render.
            coVerify(exactly = 1) { repo.trimToCap(FeedKey.following(VIEWER), any()) }
            coVerify(exactly = 1) { repo.trimToCap(match { it.feedType == FeedType.DISCOVER }, any()) }
            assertEquals(listOf(FeedType.FOLLOWING, FeedType.DISCOVER), prefetcher.prefetched.map { it.feedType })
            assertEquals(1, updater.calls)
        }

    @Test
    fun `a half-filled catch-up still counts as fresh`() =
        runTest {
            // The widget reads the partition's head, and the head is current as
            // soon as the newest slice lands. The rest of the range is the
            // app's to fill when the reader scrolls up to it.
            val repo = mockk<FeedRepository>()
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.MORE_PENDING)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val updater = RecordingUpdater()
            val runner = runner(repo, updater = updater)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 0) { repo.refresh(any()) }
            assertEquals(1, updater.calls)
        }

    @Test
    fun `a capped catch-up counts as fresh, with no destructive refresh`() =
        runTest {
            // The fill reached its budget and left a gap, but the head is the
            // newest page it fetched. Refreshing here would throw away the
            // reader's place to "fix" a gap the app already marks.
            val repo = mockk<FeedRepository>()
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.CAUGHT_UP_WITH_GAP)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val updater = RecordingUpdater()
            val runner = runner(repo, updater = updater)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 0) { repo.refresh(any()) }
            assertEquals(1, updater.calls)
        }

    @Test
    fun `a cold partition still loads from the top`() =
        runTest {
            // Nothing to prepend above, so the destructive path is the only one
            // that can populate it — and it is what records the boundary cursor
            // every later catch-up needs.
            val repo = mockk<FeedRepository>()
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val runner = runner(repo)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 1) { repo.refresh(FeedKey.following(VIEWER)) }
        }

    @Test
    fun `the two paths are decided per feed`() =
        runTest {
            // A generator feed's XRPC method takes no `since`, so no bounded
            // prepend is possible there and no reader position is persisted for
            // it. Following catches up; Discover is replaced. Both in one run.
            val repo = mockk<FeedRepository>()
            coEvery { repo.catchUp(FeedKey.following(VIEWER)) } returns Result.success(CatchUpOutcome.CAUGHT_UP)
            coEvery {
                repo.catchUp(match { it.feedType == FeedType.DISCOVER })
            } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val runner = runner(repo)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())

            coVerify(exactly = 0) { repo.refresh(FeedKey.following(VIEWER)) }
            coVerify(exactly = 1) { repo.refresh(match { it.feedType == FeedType.DISCOVER }) }
            coVerify(exactly = 1) { repo.trimToCap(FeedKey.following(VIEWER), any()) }
        }

    @Test
    fun `a failed catch-up does not fall back to a destructive clear`() =
        runTest {
            // The failure is a network failure, and the destructive path hits
            // the same network. Retrying it here would double this run's
            // requests on a flaky connection and, worse, clear a partition the
            // reader is still in because a prepend happened to fail.
            val repo = mockk<FeedRepository>()
            coEvery { repo.catchUp(any()) } returns Result.failure(IOException("down"))
            val updater = RecordingUpdater()
            val runner = runner(repo, updater = updater)

            // Every feed failed, so the run retries — unchanged from before.
            assertEquals(WidgetRefreshRunner.Outcome.RETRY, runner.run())
            coVerify(exactly = 0) { repo.refresh(any()) }
            coVerify(exactly = 0) { repo.trimToCap(any(), any(), any(), any()) }
            assertEquals(0, updater.calls)
        }

    @Test
    fun `a watch feed is caught up on too`() =
        runTest {
            // A watch feed is a cached partition like any other, and the app's
            // reader may be sitting in it.
            val cats = FeedKey(VIEWER, FeedType.CUSTOM, "at://cats")
            val repo = mockk<FeedRepository>()
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.CAUGHT_UP)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val watch = RecordingWearFeedSync(extraFeeds = listOf(cats))
            val runner = runner(repo, wearFeedSync = watch)

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner.run())
            coVerify(exactly = 1) { repo.catchUp(cats) }
            coVerify(exactly = 0) { repo.refresh(any()) }
            assertEquals(listOf(VIEWER), watch.published)
        }

    @Test
    fun `every feed failing retries and does not update`() =
        runTest {
            val repo = mockk<FeedRepository>()
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.failure(IOException("down"))
            val updater = RecordingUpdater()
            val prefetcher = RecordingPrefetcher()
            val runner = runner(repo, updater = updater, prefetcher = prefetcher)

            assertEquals(WidgetRefreshRunner.Outcome.RETRY, runner.run())
            coVerify(exactly = 0) { repo.trimToCap(any(), any(), any(), any()) }
            assertEquals(0, updater.calls)
            // No feed succeeded → nothing prefetched.
            assertEquals(emptyList<FeedKey>(), prefetcher.prefetched)
        }

    @Test
    fun `partial success returns SUCCESS, trims only the succeeded feed, updates once`() =
        runTest {
            val repo = mockk<FeedRepository>()
            // Following succeeds, Discover fails.
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(FeedKey.following(VIEWER)) } returns Result.success(false)
            coEvery { repo.refresh(match { it.feedType == FeedType.DISCOVER }) } returns Result.failure(IOException("down"))
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
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
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(FeedKey.following(VIEWER)) } throws IllegalStateException("db constraint")
            coEvery { repo.refresh(match { it.feedType == FeedType.DISCOVER }) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
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
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
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
                    placedWidgets = FakePlacedWidgets(),
                    widgetUpdater = updater,
                    imagePrefetcher = RecordingPrefetcher(),
                    wearFeedSync = RecordingWearFeedSync(),
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
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
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

    @Test
    fun `with a watch, its extra feeds are refreshed once and then published`() =
        runTest {
            val repo = mockk<FeedRepository>()
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val cats = FeedKey(VIEWER, FeedType.CUSTOM, "at://did:plc:x/app.bsky.feed.generator/cats")
            // Following is already refreshed for the widget, so it must not be fetched twice.
            val watch = RecordingWearFeedSync(extraFeeds = listOf(FeedKey.following(VIEWER), cats))

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, runner(repo, wearFeedSync = watch).run())

            coVerify(exactly = 1) { repo.refresh(FeedKey.following(VIEWER)) }
            coVerify(exactly = 1) { repo.refresh(cats) }
            coVerify(exactly = 1) { repo.trimToCap(cats, any()) }
            assertEquals(listOf(VIEWER), watch.published)
        }

    @Test
    fun `a watch publish failure does not change the outcome`() =
        runTest {
            val repo = mockk<FeedRepository>()
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.success(false)
            coEvery { repo.trimToCap(any(), any(), any(), any()) } just Runs
            val updater = RecordingUpdater()

            val outcome = runner(repo, updater = updater, wearFeedSync = RecordingWearFeedSync(failPublish = true)).run()

            assertEquals(WidgetRefreshRunner.Outcome.SUCCESS, outcome)
            assertEquals(1, updater.calls)
        }

    @Test
    fun `when every widget feed failed, the watch is not synced`() =
        runTest {
            val repo = mockk<FeedRepository>()
            // These partitions cannot be caught up on — the worker's
            // destructive fallback is what is under test here.
            coEvery { repo.catchUp(any()) } returns Result.success(CatchUpOutcome.NOT_POSSIBLE)
            coEvery { repo.refresh(any()) } returns Result.failure(java.io.IOException("offline"))
            val watch = RecordingWearFeedSync()

            assertEquals(WidgetRefreshRunner.Outcome.RETRY, runner(repo, wearFeedSync = watch).run())

            assertEquals(emptyList<String>(), watch.published)
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
        wearFeedSync: WearFeedSync = RecordingWearFeedSync(),
        placedWidgets: PlacedWidgets = FakePlacedWidgets(),
    ): WidgetRefreshRunner =
        WidgetRefreshRunner(
            repository = repo,
            sessionStateProvider = FakeSessionStateProvider(session),
            foreground = FakeForeground(foregrounded),
            placedWidgets = placedWidgets,
            widgetUpdater = updater,
            imagePrefetcher = prefetcher,
            wearFeedSync = wearFeedSync,
        )

    /**
     * A widget is placed by default, so every test that does not say otherwise
     * exercises the refresh path rather than the consumer gate.
     */
    private class FakePlacedWidgets(
        private val placed: Boolean = true,
        private val fail: Boolean = false,
    ) : PlacedWidgets {
        override suspend fun anyPlaced(): Boolean {
            if (fail) error("AppWidgetManager unavailable")
            return placed
        }
    }

    /** No watch by default (empty extra feeds); records what the runner asked of it. */
    private class RecordingWearFeedSync(
        private val extraFeeds: List<FeedKey> = emptyList(),
        private val failPublish: Boolean = false,
        private val hasWatch: Boolean = extraFeeds.isNotEmpty(),
        private val failHasWatch: Boolean = false,
    ) : WearFeedSync {
        val published = mutableListOf<String>()

        /** Non-zero iff the consumer gate let the run through to the watch sync. */
        var feedKeyCalls = 0
            private set

        override suspend fun hasWatch(): Boolean {
            if (failHasWatch) error("Data Layer unavailable")
            return hasWatch
        }

        override suspend fun feedKeysToRefresh(accountDid: String): List<FeedKey> {
            feedKeyCalls++
            return extraFeeds
        }

        override suspend fun publish(
            accountDid: String,
            urgent: Boolean,
        ) {
            if (failPublish) error("Data Layer unavailable")
            published += accountDid
        }
    }

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
