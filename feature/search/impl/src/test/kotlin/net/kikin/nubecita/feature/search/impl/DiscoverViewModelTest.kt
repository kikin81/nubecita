package net.kikin.nubecita.feature.search.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.ActorAction
import net.kikin.nubecita.core.analytics.FeedAction
import net.kikin.nubecita.core.analytics.InteractActor
import net.kikin.nubecita.core.analytics.InteractFeed
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.postinteractions.FollowOverlay
import net.kikin.nubecita.core.postinteractions.FollowToggle
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.data.models.StarterPackUi
import net.kikin.nubecita.data.models.StarterPackUiFixtures
import net.kikin.nubecita.feature.search.impl.data.ExploreContent
import net.kikin.nubecita.feature.search.impl.data.ExploreRepository
import net.kikin.nubecita.feature.search.impl.data.FeedPreviewPostUi
import net.kikin.nubecita.feature.search.impl.data.SuggestedAccountUi
import net.kikin.nubecita.feature.search.impl.data.SuggestedFeedUi
import net.kikin.nubecita.feature.search.impl.data.TrendUi
import net.kikin.nubecita.feature.search.impl.data.signedIn
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Unit tests for [DiscoverViewModel].
 *
 * Harness: [MainDispatcherExtension] installs a [StandardTestDispatcher] as
 * `Dispatchers.Main` so [viewModelScope] coroutines are under test-scheduler
 * control. All tests call `advanceUntilIdle()` to flush coroutines.
 *
 * Fakes:
 * - [FakeExploreRepository] — configurable success/failure results for
 *   account, feed and preview calls.
 * - [FakeFollowRepository] — configurable follow/unfollow outcomes + call logs.
 * - [FakePinnedFeedsRepository] — [MutableStateFlow]-backed pinned-feeds
 *   emission with configurable pin/unpin results + call logs.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Suppress("LargeClass")
internal class DiscoverViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val analytics = RecordingAnalyticsClient()

    private fun buildVm(
        suggestionsRepo: FakeExploreRepository = FakeExploreRepository(),
        followRepo: FakeFollowRepository = FakeFollowRepository(),
        pinnedRepo: FakePinnedFeedsRepository = FakePinnedFeedsRepository(),
    ): DiscoverViewModel {
        val sessions =
            object : SessionStateProvider {
                override val state = MutableStateFlow<SessionState>(signedIn("did:plc:me"))

                override suspend fun refresh() = Unit
            }
        val overlay = FollowOverlay(CoroutineScope(SupervisorJob() + mainDispatcher.dispatcher), sessions)
        return DiscoverViewModel(suggestionsRepo, FollowToggle(followRepo, overlay, sessions, CoroutineScope(SupervisorJob() + mainDispatcher.dispatcher)), overlay, pinnedRepo, analytics)
    }

    // -------------------------------------------------------------------------
    // Section loading — OnAppear
    // -------------------------------------------------------------------------

    @Test
    fun `onAppear loads both sections concurrently and both are Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(listOf(accountFixture())),
                    feedsResult = Result.success(listOf(feedFixture())),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.accountsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.feedsStatus)
            assertEquals(1, suggestionsRepo.accountCallCount)
            assertEquals(1, suggestionsRepo.feedCallCount)
        }

    @Test
    fun `onAppear with loaded section skips re-fetch`() =
        runTest(mainDispatcher.dispatcher) {
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(listOf(accountFixture())),
                    feedsResult = Result.success(listOf(feedFixture())),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()
            val callsAfterFirst = suggestionsRepo.accountCallCount

            // Second appear — Loaded section must not trigger another fetch
            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(callsAfterFirst, suggestionsRepo.accountCallCount, "accounts must not refetch when Loaded")
        }

    @Test
    fun `onAppear with error accounts section retries on re-appear`() =
        runTest(mainDispatcher.dispatcher) {
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.failure(java.io.IOException("network")),
                    feedsResult = Result.success(listOf(feedFixture())),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(DiscoverSectionStatus.Error, vm.uiState.value.accountsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.feedsStatus)

            // Fix the error and re-appear
            suggestionsRepo.accountsResult = Result.success(listOf(accountFixture()))
            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.accountsStatus)
            assertEquals(2, suggestionsRepo.accountCallCount, "accounts must retry after error")
        }

    @Test
    fun `onAppear with empty accounts section retries on re-appear`() =
        runTest(mainDispatcher.dispatcher) {
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feedFixture())),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(DiscoverSectionStatus.Empty, vm.uiState.value.accountsStatus)

            // Provide data and re-appear
            suggestionsRepo.accountsResult = Result.success(listOf(accountFixture()))
            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.accountsStatus)
        }

    @Test
    fun `onRefresh unconditionally reloads both sections`() =
        runTest(mainDispatcher.dispatcher) {
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(listOf(accountFixture())),
                    feedsResult = Result.success(listOf(feedFixture())),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            val accountCallsAfterAppear = suggestionsRepo.accountCallCount
            val feedCallsAfterAppear = suggestionsRepo.feedCallCount

            vm.handleEvent(DiscoverEvent.OnRefresh)
            advanceUntilIdle()

            assertEquals(accountCallsAfterAppear + 1, suggestionsRepo.accountCallCount)
            assertEquals(feedCallsAfterAppear + 1, suggestionsRepo.feedCallCount)
        }

    @Test
    fun `independent section failure does not mask other section`() =
        runTest(mainDispatcher.dispatcher) {
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.failure(java.io.IOException("network")),
                    feedsResult = Result.success(listOf(feedFixture())),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(DiscoverSectionStatus.Error, vm.uiState.value.accountsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.feedsStatus)
            assertTrue(
                vm.uiState.value.feeds
                    .isNotEmpty(),
            )
        }

    // -------------------------------------------------------------------------
    // Follow toggle
    // -------------------------------------------------------------------------

    @Test
    fun `follow optimistically flips isFollowing then commits followUri and logs InteractActor`() =
        runTest(mainDispatcher.dispatcher) {
            val did = "did:plc:alice"
            val newFollowUri = "at://did:plc:me/app.bsky.graph.follow/new"
            val account = accountFixture(did = did, isFollowing = false)
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(listOf(account)),
                    feedsResult = Result.success(emptyList()),
                )
            val followRepo = FakeFollowRepository(followResult = Result.success(newFollowUri))
            val vm = buildVm(suggestionsRepo = suggestionsRepo, followRepo = followRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.handleEvent(DiscoverEvent.OnFollowTapped(did))

            // Optimistic flip must be visible before coroutine runs
            val optimisticAccount =
                vm.uiState.value.accounts
                    .find { it.did == did }
            assertTrue(optimisticAccount?.isFollowing == true, "optimistic isFollowing should be true")

            advanceUntilIdle()

            val finalAccount =
                vm.uiState.value.accounts
                    .find { it.did == did }
            assertTrue(finalAccount?.isFollowing == true)
            assertEquals(newFollowUri, finalAccount?.followUri)
            assertEquals(
                listOf(InteractActor(ActorAction.Follow, PostSurface.Explore)),
                analytics.events,
            )
            assertEquals(listOf(did), followRepo.followCallDids)
        }

    @Test
    fun `follow failure rolls back isFollowing and emits FollowFailed`() =
        runTest(mainDispatcher.dispatcher) {
            val did = "did:plc:alice"
            val account = accountFixture(did = did, isFollowing = false)
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(listOf(account)),
                    feedsResult = Result.success(emptyList()),
                )
            val followRepo = FakeFollowRepository(followResult = Result.failure(java.io.IOException("net")))
            val vm = buildVm(suggestionsRepo = suggestionsRepo, followRepo = followRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnFollowTapped(did))
                advanceUntilIdle()

                val effect = awaitItem()
                assertEquals(DiscoverEffect.FollowFailed(wasUnfollow = false), effect)
                cancelAndIgnoreRemainingEvents()
            }

            val finalAccount =
                vm.uiState.value.accounts
                    .find { it.did == did }
            assertFalse(finalAccount?.isFollowing ?: true, "isFollowing must be rolled back")
            assertNull(finalAccount?.followUri)
            assertTrue(analytics.events.isEmpty(), "analytics must not fire on failure")
        }

    @Test
    fun `unfollow optimistically clears isFollowing then calls unfollow and logs InteractActor`() =
        runTest(mainDispatcher.dispatcher) {
            val did = "did:plc:alice"
            val followUri = "at://did:plc:me/app.bsky.graph.follow/existing"
            val account = accountFixture(did = did, isFollowing = true, followUri = followUri)
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(listOf(account)),
                    feedsResult = Result.success(emptyList()),
                )
            val followRepo = FakeFollowRepository(unfollowResult = Result.success(Unit))
            val vm = buildVm(suggestionsRepo = suggestionsRepo, followRepo = followRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.handleEvent(DiscoverEvent.OnFollowTapped(did))

            // Optimistic flip must be visible immediately
            val optimisticAccount =
                vm.uiState.value.accounts
                    .find { it.did == did }
            assertFalse(optimisticAccount?.isFollowing ?: true, "optimistic isFollowing should be false")

            advanceUntilIdle()

            val finalAccount =
                vm.uiState.value.accounts
                    .find { it.did == did }
            assertFalse(finalAccount?.isFollowing ?: true)
            assertNull(finalAccount?.followUri)
            assertEquals(
                listOf(InteractActor(ActorAction.Unfollow, PostSurface.Explore)),
                analytics.events,
            )
            assertEquals(listOf(followUri), followRepo.unfollowCallUris)
        }

    @Test
    fun `unfollow failure rolls back isFollowing and emits FollowFailed`() =
        runTest(mainDispatcher.dispatcher) {
            val did = "did:plc:alice"
            val followUri = "at://did:plc:me/app.bsky.graph.follow/existing"
            val account = accountFixture(did = did, isFollowing = true, followUri = followUri)
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(listOf(account)),
                    feedsResult = Result.success(emptyList()),
                )
            val followRepo = FakeFollowRepository(unfollowResult = Result.failure(java.io.IOException("net")))
            val vm = buildVm(suggestionsRepo = suggestionsRepo, followRepo = followRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnFollowTapped(did))
                advanceUntilIdle()

                val effect = awaitItem()
                assertEquals(DiscoverEffect.FollowFailed(wasUnfollow = true), effect)
                cancelAndIgnoreRemainingEvents()
            }

            val finalAccount =
                vm.uiState.value.accounts
                    .find { it.did == did }
            assertTrue(finalAccount?.isFollowing ?: false, "isFollowing must be rolled back to true")
            assertEquals(followUri, finalAccount?.followUri)
            assertTrue(analytics.events.isEmpty(), "analytics must not fire on failure")
        }

    @Test
    fun `follow concurrent tap on same did is dropped single flight`() =
        runTest(mainDispatcher.dispatcher) {
            val did = "did:plc:alice"
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(listOf(accountFixture(did = did, isFollowing = false))),
                    feedsResult = Result.success(emptyList()),
                )
            val followRepo =
                FakeFollowRepository(
                    followResult = Result.success("at://did:plc:me/app.bsky.graph.follow/new"),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo, followRepo = followRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            // Fire twice before advanceUntilIdle — second must be dropped
            vm.handleEvent(DiscoverEvent.OnFollowTapped(did))
            vm.handleEvent(DiscoverEvent.OnFollowTapped(did))
            advanceUntilIdle()

            assertEquals(1, followRepo.followCallDids.size, "follow must be called exactly once")
            assertEquals(1, analytics.events.size, "analytics must fire exactly once")
        }

    // -------------------------------------------------------------------------
    // Pin toggle
    // -------------------------------------------------------------------------

    @Test
    fun `pin optimistically flips isPinned then calls pinFeed and logs InteractFeed Pin`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val feed = feedFixture(uri = uri, isPinned = false)
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feed)),
                )
            val pinnedRepo = FakePinnedFeedsRepository(pinResult = Result.success(Unit))
            val vm = buildVm(suggestionsRepo = suggestionsRepo, pinnedRepo = pinnedRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.handleEvent(DiscoverEvent.OnPinTapped(uri))

            // Optimistic flip must be immediate
            val optimisticFeed =
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
            assertTrue(optimisticFeed?.feed?.isPinned == true, "optimistic isPinned should be true")

            advanceUntilIdle()

            assertEquals(
                listOf(InteractFeed(FeedAction.Pin, PostSurface.Explore)),
                analytics.events,
            )
            assertEquals(listOf(uri), pinnedRepo.pinCallUris)
        }

    @Test
    fun `pin failure rolls back isPinned on current state and emits PinFailed`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val feed = feedFixture(uri = uri, isPinned = false)
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feed)),
                )
            val pinnedRepo =
                FakePinnedFeedsRepository(
                    pinResult = Result.failure(java.io.IOException("net")),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo, pinnedRepo = pinnedRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnPinTapped(uri))
                advanceUntilIdle()

                val effect = awaitItem()
                assertEquals(DiscoverEffect.PinFailed(wasUnpin = false), effect)
                cancelAndIgnoreRemainingEvents()
            }

            val finalFeed =
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
            assertFalse(finalFeed?.feed?.isPinned ?: true, "isPinned must be rolled back to false")
            assertTrue(analytics.events.isEmpty(), "analytics must not fire on failure")
        }

    @Test
    fun `unpin optimistically clears isPinned then calls unpinFeed and logs InteractFeed Unpin`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val feed = feedFixture(uri = uri, isPinned = false) // API value; repo is source of truth
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feed)),
                )
            val pinnedRepo = FakePinnedFeedsRepository(unpinResult = Result.success(Unit))
            // Seed the pinned repo so the observer seeds isPinned=true on load
            pinnedRepo.setPinnedUris(setOf(uri))
            val vm = buildVm(suggestionsRepo = suggestionsRepo, pinnedRepo = pinnedRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.handleEvent(DiscoverEvent.OnPinTapped(uri))

            // Optimistic flip must be immediate
            val optimistic =
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
            assertFalse(optimistic?.feed?.isPinned ?: true, "optimistic isPinned should be false")

            advanceUntilIdle()

            assertEquals(
                listOf(InteractFeed(FeedAction.Unpin, PostSurface.Explore)),
                analytics.events,
            )
            assertEquals(listOf(uri), pinnedRepo.unpinCallUris)
        }

    @Test
    fun `unpin failure rolls back isPinned and emits PinFailed`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val feed = feedFixture(uri = uri, isPinned = false) // API value; repo is source of truth
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feed)),
                )
            val pinnedRepo =
                FakePinnedFeedsRepository(
                    unpinResult = Result.failure(java.io.IOException("net")),
                )
            // Seed the pinned repo so isPinned=true on load, exercising the unpin path
            pinnedRepo.setPinnedUris(setOf(uri))
            val vm = buildVm(suggestionsRepo = suggestionsRepo, pinnedRepo = pinnedRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnPinTapped(uri))
                advanceUntilIdle()

                val effect = awaitItem()
                assertEquals(DiscoverEffect.PinFailed(wasUnpin = true), effect)
                cancelAndIgnoreRemainingEvents()
            }

            val finalFeed =
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
            assertTrue(finalFeed?.feed?.isPinned ?: false, "isPinned must be rolled back to true")
            assertTrue(analytics.events.isEmpty(), "analytics must not fire on failure")
        }

    @Test
    fun `pin concurrent tap on same uri is dropped single flight`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feedFixture(uri = uri, isPinned = false))),
                )
            val pinnedRepo = FakePinnedFeedsRepository(pinResult = Result.success(Unit))
            val vm = buildVm(suggestionsRepo = suggestionsRepo, pinnedRepo = pinnedRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.handleEvent(DiscoverEvent.OnPinTapped(uri))
            vm.handleEvent(DiscoverEvent.OnPinTapped(uri)) // concurrent — must be dropped
            advanceUntilIdle()

            assertEquals(1, pinnedRepo.pinCallUris.size, "pinFeed must be called exactly once")
            assertEquals(1, analytics.events.size, "analytics must fire exactly once")
        }

    // -------------------------------------------------------------------------
    // Lazy preview
    // -------------------------------------------------------------------------

    @Test
    fun `feedCardVisible triggers preview fetch and updates previewStatus to Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val previewPost =
                FeedPreviewPostUi(
                    authorHandle = "author.bsky.social",
                    authorAvatarUrl = null,
                    text = "Test post",
                    thumbnailUrl = null,
                )
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feedFixture(uri = uri))),
                    previewResult = Result.success(listOf(previewPost)),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.handleEvent(DiscoverEvent.OnFeedCardVisible(uri))
            advanceUntilIdle()

            val feedUi =
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
            assertEquals(FeedPreviewStatus.Loaded, feedUi?.previewStatus)
            assertEquals(listOf(previewPost), feedUi?.preview?.toList())
        }

    @Test
    fun `feedCardVisible second call for same uri does not refetch`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feedFixture(uri = uri))),
                    previewResult =
                        Result.success(
                            listOf(
                                FeedPreviewPostUi("a.bsky.social", null, "post", null),
                            ),
                        ),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            // First call — should fetch
            vm.handleEvent(DiscoverEvent.OnFeedCardVisible(uri))
            advanceUntilIdle()

            val callsAfterFirst = suggestionsRepo.previewCallUris.size

            // Second call — must NOT refetch
            vm.handleEvent(DiscoverEvent.OnFeedCardVisible(uri))
            advanceUntilIdle()

            assertEquals(callsAfterFirst, suggestionsRepo.previewCallUris.size, "preview must not refetch")
        }

    @Test
    fun `onRefresh preserves loaded preview and previewStatus for matching uris`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val previewPost =
                FeedPreviewPostUi(
                    authorHandle = "author.bsky.social",
                    authorAvatarUrl = null,
                    text = "Preview post",
                    thumbnailUrl = null,
                )
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feedFixture(uri = uri))),
                    previewResult = Result.success(listOf(previewPost)),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            // Initial load + preview fetch
            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()
            vm.handleEvent(DiscoverEvent.OnFeedCardVisible(uri))
            advanceUntilIdle()

            val afterPreview =
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
            assertEquals(FeedPreviewStatus.Loaded, afterPreview?.previewStatus)
            assertEquals(listOf(previewPost), afterPreview?.preview?.toList())

            // Refresh — same feed URIs returned by the repo
            vm.handleEvent(DiscoverEvent.OnRefresh)
            advanceUntilIdle()

            val afterRefresh =
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
            assertEquals(
                FeedPreviewStatus.Loaded,
                afterRefresh?.previewStatus,
                "refresh must not reset previewStatus to Idle for known URIs",
            )
            assertEquals(
                listOf(previewPost),
                afterRefresh?.preview?.toList(),
                "refresh must preserve previously-loaded preview posts",
            )
        }

    @Test
    fun `feedCardVisible failure sets previewStatus to Error and subsequent call is no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feedFixture(uri = uri))),
                    previewResult = Result.failure(java.io.IOException("net")),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.handleEvent(DiscoverEvent.OnFeedCardVisible(uri))
            advanceUntilIdle()

            val feedUi =
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
            assertEquals(FeedPreviewStatus.Error, feedUi?.previewStatus)

            val callsAfterError = suggestionsRepo.previewCallUris.size
            // Repeat — must be no-op (Error is not Idle)
            vm.handleEvent(DiscoverEvent.OnFeedCardVisible(uri))
            advanceUntilIdle()

            assertEquals(callsAfterError, suggestionsRepo.previewCallUris.size)
        }

    @Test
    fun `feedCardVisible concurrent calls for same uri before fetch completes fetches only once`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feedFixture(uri = uri))),
                    previewResult = Result.success(listOf(FeedPreviewPostUi("a.bsky.social", null, "post", null))),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            // Fire twice synchronously before the fetch coroutine executes — activePreviewJobs guard
            // must drop the second call (mirrors the rapid-pin-tap dedup pattern).
            vm.handleEvent(DiscoverEvent.OnFeedCardVisible(uri))
            vm.handleEvent(DiscoverEvent.OnFeedCardVisible(uri))
            advanceUntilIdle()

            assertEquals(1, suggestionsRepo.previewCallUris.size, "getFeedPreview must be called exactly once")
            assertEquals(
                FeedPreviewStatus.Loaded,
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
                    ?.previewStatus,
            )
        }

    @Test
    fun `onRefresh cancels in-flight section load and does not issue concurrent redundant requests`() =
        runTest(mainDispatcher.dispatcher) {
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(listOf(accountFixture())),
                    feedsResult = Result.success(listOf(feedFixture())),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            // Schedule onAppear load but do NOT advance — coroutines are queued, not yet executing.
            vm.handleEvent(DiscoverEvent.OnAppear)

            // onRefresh cancels the queued-but-unstarted onAppear jobs and starts new ones.
            vm.handleEvent(DiscoverEvent.OnRefresh)
            advanceUntilIdle()

            // Only the refresh jobs ran; the appear jobs were cancelled before they could execute.
            assertEquals(1, suggestionsRepo.accountCallCount, "getSuggestedAccounts must be called exactly once")
            assertEquals(1, suggestionsRepo.feedCallCount, "getSuggestedFeeds must be called exactly once")
            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.accountsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.feedsStatus)
        }

    // -------------------------------------------------------------------------
    // Account dismissal
    // -------------------------------------------------------------------------

    @Test
    fun `onAccountDismissed removes account from list`() =
        runTest(mainDispatcher.dispatcher) {
            val keepDid = "did:plc:keep"
            val dismissDid = "did:plc:dismiss"
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult =
                        Result.success(
                            listOf(
                                accountFixture(did = keepDid),
                                accountFixture(did = dismissDid),
                            ),
                        ),
                    feedsResult = Result.success(emptyList()),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(2, vm.uiState.value.accounts.size)

            vm.handleEvent(DiscoverEvent.OnAccountDismissed(dismissDid))

            assertEquals(1, vm.uiState.value.accounts.size)
            assertEquals(
                keepDid,
                vm.uiState.value.accounts
                    .single()
                    .did,
            )
        }

    @Test
    fun `dismissed account is excluded from subsequent refresh`() =
        runTest(mainDispatcher.dispatcher) {
            val keepDid = "did:plc:keep"
            val dismissDid = "did:plc:dismiss"
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult =
                        Result.success(
                            listOf(
                                accountFixture(did = keepDid),
                                accountFixture(did = dismissDid),
                            ),
                        ),
                    feedsResult = Result.success(emptyList()),
                )
            val vm = buildVm(suggestionsRepo = suggestionsRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            vm.handleEvent(DiscoverEvent.OnAccountDismissed(dismissDid))

            // Refresh brings back all accounts from the server, but dismissed one is filtered
            vm.handleEvent(DiscoverEvent.OnRefresh)
            advanceUntilIdle()

            assertTrue(
                vm.uiState.value.accounts
                    .none { it.did == dismissDid },
                "dismissed did must stay hidden after refresh",
            )
        }

    // -------------------------------------------------------------------------
    // Navigation effects
    // -------------------------------------------------------------------------

    @Test
    fun `onAccountTapped emits NavigateToProfile`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = buildVm()
            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnAccountTapped("did:plc:alice", "alice.bsky.social"))
                val effect = awaitItem()
                assertTrue(effect is DiscoverEffect.NavigateToProfile)
                assertEquals("alice.bsky.social", (effect as DiscoverEffect.NavigateToProfile).handle)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `onFeedTapped emits NavigateToFeed`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = buildVm()
            vm.effects.test {
                vm.handleEvent(
                    DiscoverEvent.OnFeedTapped(
                        "at://did:plc:f1/app.bsky.feed.generator/test",
                        "Test Feed",
                    ),
                )
                val effect = awaitItem()
                assertTrue(effect is DiscoverEffect.NavigateToFeed)
                effect as DiscoverEffect.NavigateToFeed
                assertEquals("at://did:plc:f1/app.bsky.feed.generator/test", effect.uri)
                assertEquals("Test Feed", effect.displayName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    // -------------------------------------------------------------------------
    // Trends
    // -------------------------------------------------------------------------

    @Test
    fun `onAppear projects the trends section`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeExploreRepository(trendsResult = Result.success(listOf(trendFixture())))
            val vm = buildVm(suggestionsRepo = repo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.trendsStatus)
            assertEquals(
                listOf(trendFixture()),
                vm.uiState.value.trends
                    .toList(),
            )
        }

    @Test
    fun `show more expands and show less collapses`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = buildVm(suggestionsRepo = FakeExploreRepository(trendsResult = Result.success(listOf(trendFixture()))))
            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.trendsExpanded)

            vm.handleEvent(DiscoverEvent.OnTrendsExpansionToggled)
            assertTrue(vm.uiState.value.trendsExpanded)

            vm.handleEvent(DiscoverEvent.OnTrendsExpansionToggled)
            assertFalse(vm.uiState.value.trendsExpanded)
        }

    @Test
    fun `the expanded flag survives a content refresh`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = buildVm(suggestionsRepo = FakeExploreRepository(trendsResult = Result.success(listOf(trendFixture()))))
            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()
            vm.handleEvent(DiscoverEvent.OnTrendsExpansionToggled)

            vm.handleEvent(DiscoverEvent.OnRefresh)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.trendsExpanded)
            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.trendsStatus)
        }

    @Test
    fun `two instances over one repository show the same content`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeExploreRepository(
                    trendsResult = Result.success(listOf(trendFixture())),
                    accountsResult = Result.success(listOf(accountFixture(did = "did:plc:a"), accountFixture(did = "did:plc:b"))),
                    feedsResult = Result.success(listOf(feedFixture())),
                )
            val first = buildVm(suggestionsRepo = repo)
            val second = buildVm(suggestionsRepo = repo)

            first.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()
            first.handleEvent(DiscoverEvent.OnAccountDismissed("did:plc:a"))
            advanceUntilIdle()

            assertEquals(first.uiState.value, second.uiState.value)
            assertEquals(
                listOf("did:plc:b"),
                second.uiState.value.accounts
                    .map { it.did },
            )
        }

    @Test
    fun `content reset by an account change is reloaded without a new OnAppear`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeExploreRepository(
                    trendsResult = Result.success(listOf(trendFixture())),
                    accountsResult = Result.success(listOf(accountFixture())),
                    feedsResult = Result.success(listOf(feedFixture())),
                )
            val vm = buildVm(suggestionsRepo = repo)
            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()
            assertEquals(1, repo.accountCallCount)

            repo.resetForAccountChange()
            advanceUntilIdle()

            assertEquals(2, repo.accountCallCount, "reloaded once")
            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.accountsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.trendsStatus)
        }

    @Test
    fun `a reset before the first OnAppear does not load by itself`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeExploreRepository(accountsResult = Result.success(listOf(accountFixture())))
            val vm = buildVm(suggestionsRepo = repo)
            advanceUntilIdle()

            repo.resetForAccountChange()
            advanceUntilIdle()

            assertEquals(0, repo.accountCallCount)
            assertEquals(DiscoverSectionStatus.Idle, vm.uiState.value.accountsStatus)
        }

    @Test
    fun `a feed trend navigates to at did generator rkey`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeExploreRepository()
            val vm = buildVm(suggestionsRepo = repo)

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trendFixture(link = "/profile/did:plc:abc/feed/xyz", displayName = "Eclipse")))
                advanceUntilIdle()

                assertEquals(DiscoverEffect.NavigateToFeed("at://did:plc:abc/app.bsky.feed.generator/xyz", "Eclipse"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a second tap while a handle is resolving is ignored`() =
        runTest(mainDispatcher.dispatcher) {
            val gate = kotlinx.coroutines.CompletableDeferred<Unit>()
            val repo =
                FakeExploreRepository(
                    resolveResult = {
                        gate.await()
                        Result.success("did:plc:resolved")
                    },
                )
            val vm = buildVm(suggestionsRepo = repo)
            val trend = trendFixture(link = "/profile/bsky.app/feed/xyz", displayName = "Eclipse")

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trend))
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trend))
                gate.complete(Unit)
                advanceUntilIdle()

                assertEquals(DiscoverEffect.NavigateToFeed("at://did:plc:resolved/app.bsky.feed.generator/xyz", "Eclipse"), awaitItem())
                expectNoEvents()
                assertEquals(1, repo.resolveCalls.size)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a handle trend resolves first`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeExploreRepository(resolveResult = { Result.success("did:plc:resolved") })
            val vm = buildVm(suggestionsRepo = repo)

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trendFixture(link = "/profile/bsky.app/feed/xyz", displayName = "Eclipse")))
                advanceUntilIdle()

                assertEquals(
                    DiscoverEffect.NavigateToFeed("at://did:plc:resolved/app.bsky.feed.generator/xyz", "Eclipse"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("bsky.app"), repo.resolveCalls)
        }

    @Test
    fun `a failed resolve submits the display name`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeExploreRepository(resolveResult = { Result.failure(java.io.IOException("no")) })
            val vm = buildVm(suggestionsRepo = repo)

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trendFixture(link = "/profile/bsky.app/feed/xyz", displayName = "Eclipse")))
                advanceUntilIdle()

                assertEquals(DiscoverEffect.SubmitSearch("Eclipse"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `an unknown link submits the display name`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = buildVm()

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trendFixture(link = "/hashtag/x", displayName = "Eclipse")))
                advanceUntilIdle()

                assertEquals(DiscoverEffect.SubmitSearch("Eclipse"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a search link submits its query`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = buildVm()

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trendFixture(link = "/search?q=lunar%20eclipse", displayName = "Eclipse")))
                advanceUntilIdle()

                assertEquals(DiscoverEffect.SubmitSearch("lunar eclipse"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a starter-pack trend link navigates to the pack`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeExploreRepository(resolveResult = { Result.success("did:plc:resolved") })
            val vm = buildVm(suggestionsRepo = repo)

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trendFixture(link = "/starter-pack/alice.test/3k", displayName = "Eclipse")))
                advanceUntilIdle()

                assertEquals(
                    DiscoverEffect.NavigateToPack("at://did:plc:resolved/app.bsky.graph.starterpack/3k"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(listOf("alice.test"), repo.resolveCalls)
        }

    @Test
    fun `a starter-pack trend link with a DID navigates without resolving`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeExploreRepository()
            val vm = buildVm(suggestionsRepo = repo)

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trendFixture(link = "/starter-pack/did:plc:abc/3k", displayName = "Eclipse")))
                advanceUntilIdle()

                assertEquals(DiscoverEffect.NavigateToPack("at://did:plc:abc/app.bsky.graph.starterpack/3k"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `a starter-pack trend link whose handle does not resolve submits the display name`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeExploreRepository(resolveResult = { Result.failure(java.io.IOException("no")) })
            val vm = buildVm(suggestionsRepo = repo)

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnTrendTapped(trendFixture(link = "/starter-pack/alice.test/3k", displayName = "Eclipse")))
                advanceUntilIdle()

                assertEquals(DiscoverEffect.SubmitSearch("Eclipse"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `tapping a starter pack card navigates to it`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = buildVm()

            vm.effects.test {
                vm.handleEvent(DiscoverEvent.OnPackTapped("at://did:plc:c/app.bsky.graph.starterpack/p"))
                advanceUntilIdle()

                assertEquals(DiscoverEffect.NavigateToPack("at://did:plc:c/app.bsky.graph.starterpack/p"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `the starter pack section projects from the repository and loads with the rest`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeExploreRepository(packsResult = Result.success(listOf(StarterPackUiFixtures.pack(slug = "a"), StarterPackUiFixtures.pack(slug = "b"))))
            val vm = buildVm(suggestionsRepo = repo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertEquals(DiscoverSectionStatus.Loaded, vm.uiState.value.starterPacksStatus)
            assertEquals(2, vm.uiState.value.starterPacks.size)
            assertEquals(1, repo.packCallCount)

            // A loaded section is not fetched again on the next appearance.
            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()
            assertEquals(1, repo.packCallCount)
        }

    // -------------------------------------------------------------------------
    // PinnedFeeds observer seeds isPinned on load
    // -------------------------------------------------------------------------

    @Test
    fun `feeds load seeds isPinned from observePinnedFeeds`() =
        runTest(mainDispatcher.dispatcher) {
            val pinnedUri = "at://did:plc:f1/app.bsky.feed.generator/pinned"
            val unpinnedUri = "at://did:plc:f2/app.bsky.feed.generator/unpinned"
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult =
                        Result.success(
                            listOf(
                                feedFixture(uri = pinnedUri, isPinned = false), // API says false
                                feedFixture(uri = unpinnedUri, isPinned = false),
                            ),
                        ),
                )
            val pinnedRepo = FakePinnedFeedsRepository()
            pinnedRepo.setPinnedUris(setOf(pinnedUri)) // but it IS pinned in Room
            val vm = buildVm(suggestionsRepo = suggestionsRepo, pinnedRepo = pinnedRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            val pinnedFeed =
                vm.uiState.value.feeds
                    .find { it.feed.uri == pinnedUri }
            val unpinnedFeed =
                vm.uiState.value.feeds
                    .find { it.feed.uri == unpinnedUri }
            assertTrue(pinnedFeed?.feed?.isPinned ?: false, "pinned URI should have isPinned=true")
            assertFalse(unpinnedFeed?.feed?.isPinned ?: true, "unpinned URI should have isPinned=false")
        }

    @Test
    fun `observePinnedFeeds reactive update changes isPinned after initial load`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val suggestionsRepo =
                FakeExploreRepository(
                    accountsResult = Result.success(emptyList()),
                    feedsResult = Result.success(listOf(feedFixture(uri = uri, isPinned = false))),
                )
            val pinnedRepo = FakePinnedFeedsRepository()
            val vm = buildVm(suggestionsRepo = suggestionsRepo, pinnedRepo = pinnedRepo)

            vm.handleEvent(DiscoverEvent.OnAppear)
            advanceUntilIdle()

            assertFalse(
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
                    ?.feed
                    ?.isPinned ?: true,
                "should start unpinned",
            )

            // Observer emits a new pinned set
            pinnedRepo.setPinnedUris(setOf(uri))
            advanceUntilIdle()

            assertTrue(
                vm.uiState.value.feeds
                    .find { it.feed.uri == uri }
                    ?.feed
                    ?.isPinned ?: false,
                "observer update should flip isPinned to true",
            )
        }
}

// =============================================================================
// Fakes
// =============================================================================

private class FakeExploreRepository(
    var trendsResult: Result<List<TrendUi>> = Result.success(emptyList()),
    var accountsResult: Result<List<SuggestedAccountUi>> = Result.success(emptyList()),
    var feedsResult: Result<List<SuggestedFeedUi>> = Result.success(emptyList()),
    var packsResult: Result<List<StarterPackUi>> = Result.success(emptyList()),
    var previewResult: Result<List<FeedPreviewPostUi>> = Result.success(emptyList()),
    var resolveResult: suspend (String) -> Result<String> = { Result.success(it) },
) : ExploreRepository {
    private val state = MutableStateFlow(ExploreContent())
    private val dismissed = mutableSetOf<String>()

    override val content: StateFlow<ExploreContent> = state

    var trendCallCount = 0
    var accountCallCount = 0
    var feedCallCount = 0
    var packCallCount = 0
    val previewCallUris = mutableListOf<String>()
    val resolveCalls = mutableListOf<String>()

    override suspend fun load(force: Boolean) {
        if (force || state.value.trendsStatus != DiscoverSectionStatus.Loaded) {
            trendCallCount++
            trendsResult.fold(
                onSuccess = { list ->
                    state.update {
                        it.copy(
                            trends = list.toImmutableList(),
                            trendsStatus = if (list.isEmpty()) DiscoverSectionStatus.Empty else DiscoverSectionStatus.Loaded,
                        )
                    }
                },
                onFailure = { state.update { it.copy(trendsStatus = DiscoverSectionStatus.Error) } },
            )
        }
        if (force || state.value.accountsStatus != DiscoverSectionStatus.Loaded) {
            accountCallCount++
            accountsResult.fold(
                onSuccess = { list ->
                    val visible = list.filter { it.did !in dismissed }
                    state.update {
                        it.copy(
                            accounts = visible.toImmutableList(),
                            accountsStatus = if (visible.isEmpty()) DiscoverSectionStatus.Empty else DiscoverSectionStatus.Loaded,
                        )
                    }
                },
                onFailure = { state.update { it.copy(accountsStatus = DiscoverSectionStatus.Error) } },
            )
        }
        if (force || state.value.feedsStatus != DiscoverSectionStatus.Loaded) {
            feedCallCount++
            feedsResult.fold(
                onSuccess = { list ->
                    state.update { current ->
                        val priorByUri = current.feeds.associateBy { it.feed.uri }
                        current.copy(
                            feeds = list.map { priorByUri[it.uri] ?: DiscoverFeedUi(feed = it) }.toImmutableList(),
                            feedsStatus = if (list.isEmpty()) DiscoverSectionStatus.Empty else DiscoverSectionStatus.Loaded,
                        )
                    }
                },
                onFailure = { state.update { it.copy(feedsStatus = DiscoverSectionStatus.Error) } },
            )
        }
        if (force || state.value.starterPacksStatus != DiscoverSectionStatus.Loaded) {
            packCallCount++
            packsResult.fold(
                onSuccess = { list ->
                    state.update {
                        it.copy(
                            starterPacks = list.toImmutableList(),
                            starterPacksStatus = if (list.isEmpty()) DiscoverSectionStatus.Empty else DiscoverSectionStatus.Loaded,
                        )
                    }
                },
                onFailure = { state.update { it.copy(starterPacksStatus = DiscoverSectionStatus.Error) } },
            )
        }
    }

    override suspend fun loadFeedPreview(feedUri: String) {
        previewCallUris += feedUri

        fun update(transform: (DiscoverFeedUi) -> DiscoverFeedUi) =
            state.update { current ->
                current.copy(feeds = current.feeds.map { if (it.feed.uri == feedUri) transform(it) else it }.toImmutableList())
            }
        update { it.copy(previewStatus = FeedPreviewStatus.Loading) }
        previewResult.fold(
            onSuccess = { posts -> update { it.copy(preview = posts.toImmutableList(), previewStatus = FeedPreviewStatus.Loaded) } },
            onFailure = { update { it.copy(previewStatus = FeedPreviewStatus.Error) } },
        )
    }

    /** What the real repository does when the signed-in account changes. */
    fun resetForAccountChange() {
        dismissed.clear()
        state.value = ExploreContent()
    }

    override fun dismissAccount(did: String) {
        dismissed += did
        state.update { it.copy(accounts = it.accounts.filter { account -> account.did != did }.toImmutableList()) }
    }

    override suspend fun resolveDid(actor: String): Result<String> {
        resolveCalls += actor
        return resolveResult(actor)
    }
}

// =============================================================================
// Fixture helpers
// =============================================================================

private fun accountFixture(
    did: String = "did:plc:test",
    handle: String = "test.bsky.social",
    isFollowing: Boolean = false,
    followUri: String? = null,
): SuggestedAccountUi =
    SuggestedAccountUi(
        did = did,
        handle = handle,
        displayName = "Test",
        avatarUrl = null,
        isFollowing = isFollowing,
        followUri = followUri,
        mutualsCount = 0,
        mutualAvatarUrls = persistentListOf(),
    )

private fun trendFixture(
    link: String = "/profile/did:plc:abc/feed/xyz",
    displayName: String = "Trend",
): TrendUi =
    TrendUi(
        topic = "topic",
        displayName = displayName,
        link = link,
        category = null,
        postCount = 10L,
        isHot = false,
    )

private fun feedFixture(
    uri: String = "at://did:plc:f1/app.bsky.feed.generator/test",
    displayName: String = "Test Feed",
    isPinned: Boolean = false,
): SuggestedFeedUi =
    SuggestedFeedUi(
        uri = uri,
        displayName = displayName,
        creatorHandle = "creator.bsky.social",
        avatarUrl = null,
        description = null,
        isPinned = isPinned,
    )
