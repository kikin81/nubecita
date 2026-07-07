package net.kikin.nubecita.feature.search.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.ActorAction
import net.kikin.nubecita.core.analytics.FeedAction
import net.kikin.nubecita.core.analytics.InteractActor
import net.kikin.nubecita.core.analytics.InteractFeed
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.feeds.PinnedFeedsRepository
import net.kikin.nubecita.core.feeds.PinnedFeedsResult
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.data.models.FeedKind
import net.kikin.nubecita.data.models.PinnedFeedUi
import net.kikin.nubecita.feature.search.impl.data.FeedPreviewPostUi
import net.kikin.nubecita.feature.search.impl.data.SuggestedAccountUi
import net.kikin.nubecita.feature.search.impl.data.SuggestedFeedUi
import net.kikin.nubecita.feature.search.impl.data.SuggestionsRepository
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
 * - [FakeSuggestionsRepository] — configurable success/failure results for
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
        suggestionsRepo: FakeSuggestionsRepository = FakeSuggestionsRepository(),
        followRepo: FakeFollowRepository = FakeFollowRepository(),
        pinnedRepo: FakePinnedFeedsRepository = FakePinnedFeedsRepository(),
    ): DiscoverViewModel = DiscoverViewModel(suggestionsRepo, followRepo, pinnedRepo, analytics)

    // -------------------------------------------------------------------------
    // Section loading — OnAppear
    // -------------------------------------------------------------------------

    @Test
    fun `onAppear loads both sections concurrently and both are Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val suggestionsRepo =
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
    fun `follow failure rolls back isFollowing and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val did = "did:plc:alice"
            val account = accountFixture(did = did, isFollowing = false)
            val suggestionsRepo =
                FakeSuggestionsRepository(
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
                assertTrue(effect is DiscoverEffect.ShowError, "expected ShowError, was $effect")
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
                FakeSuggestionsRepository(
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
    fun `unfollow failure rolls back isFollowing and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val did = "did:plc:alice"
            val followUri = "at://did:plc:me/app.bsky.graph.follow/existing"
            val account = accountFixture(did = did, isFollowing = true, followUri = followUri)
            val suggestionsRepo =
                FakeSuggestionsRepository(
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
                assertTrue(effect is DiscoverEffect.ShowError, "expected ShowError, was $effect")
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
    fun `pin failure rolls back isPinned on current state and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val feed = feedFixture(uri = uri, isPinned = false)
            val suggestionsRepo =
                FakeSuggestionsRepository(
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
                assertTrue(effect is DiscoverEffect.ShowError, "expected ShowError, was $effect")
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
                FakeSuggestionsRepository(
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
    fun `unpin failure rolls back isPinned and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val uri = "at://did:plc:f1/app.bsky.feed.generator/test"
            val feed = feedFixture(uri = uri, isPinned = false) // API value; repo is source of truth
            val suggestionsRepo =
                FakeSuggestionsRepository(
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
                assertTrue(effect is DiscoverEffect.ShowError, "expected ShowError, was $effect")
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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
    // PinnedFeeds observer seeds isPinned on load
    // -------------------------------------------------------------------------

    @Test
    fun `feeds load seeds isPinned from observePinnedFeeds`() =
        runTest(mainDispatcher.dispatcher) {
            val pinnedUri = "at://did:plc:f1/app.bsky.feed.generator/pinned"
            val unpinnedUri = "at://did:plc:f2/app.bsky.feed.generator/unpinned"
            val suggestionsRepo =
                FakeSuggestionsRepository(
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
                FakeSuggestionsRepository(
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

private class FakeSuggestionsRepository(
    var accountsResult: Result<List<SuggestedAccountUi>> = Result.success(emptyList()),
    var feedsResult: Result<List<SuggestedFeedUi>> = Result.success(emptyList()),
    var previewResult: Result<List<FeedPreviewPostUi>> = Result.success(emptyList()),
) : SuggestionsRepository {
    var accountCallCount = 0
    var feedCallCount = 0
    val previewCallUris = mutableListOf<String>()

    override suspend fun getSuggestedAccounts(limit: Int): Result<List<SuggestedAccountUi>> {
        accountCallCount++
        return accountsResult
    }

    override suspend fun getSuggestedFeeds(limit: Int): Result<List<SuggestedFeedUi>> {
        feedCallCount++
        return feedsResult
    }

    override suspend fun getFeedPreview(
        feedUri: String,
        limit: Int,
    ): Result<List<FeedPreviewPostUi>> {
        previewCallUris += feedUri
        return previewResult
    }
}

private class FakeFollowRepository(
    var followResult: Result<String> = Result.success("at://did:plc:me/app.bsky.graph.follow/new"),
    var unfollowResult: Result<Unit> = Result.success(Unit),
) : FollowRepository {
    val followCallDids = mutableListOf<String>()
    val unfollowCallUris = mutableListOf<String>()

    override suspend fun follow(subjectDid: String): Result<String> {
        followCallDids += subjectDid
        return followResult
    }

    override suspend fun unfollow(followUri: String): Result<Unit> {
        unfollowCallUris += followUri
        return unfollowResult
    }
}

private class FakePinnedFeedsRepository(
    var pinResult: Result<Unit> = Result.success(Unit),
    var unpinResult: Result<Unit> = Result.success(Unit),
) : PinnedFeedsRepository {
    val pinCallUris = mutableListOf<String>()
    val unpinCallUris = mutableListOf<String>()

    private val pinnedFlow = MutableStateFlow(PinnedFeedsResult(persistentListOf(), usedFallback = false))

    fun setPinnedUris(uris: Set<String>) {
        val feeds =
            uris
                .map { uri ->
                    PinnedFeedUi(
                        id = uri,
                        uri = uri,
                        kind = FeedKind.Generator,
                        displayName = "Feed",
                        avatarUrl = null,
                    )
                }.toImmutableList()
        pinnedFlow.value = PinnedFeedsResult(feeds = feeds, usedFallback = false)
    }

    override fun observePinnedFeeds(): Flow<PinnedFeedsResult> = pinnedFlow

    override suspend fun refresh(): Result<Unit> = Result.success(Unit)

    override fun validateSelectedFeedUri(
        uri: String?,
        pinned: List<PinnedFeedUi>,
    ): String = uri ?: PinnedFeedsRepository.FOLLOWING_FEED_URI

    override suspend fun pinFeed(uri: String): Result<Unit> {
        pinCallUris += uri
        return pinResult
    }

    override suspend fun unpinFeed(uri: String): Result<Unit> {
        unpinCallUris += uri
        return unpinResult
    }

    override suspend fun reorderPinnedFeeds(orderedPinnedUris: List<String>): Result<Unit> = Result.success(Unit)
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
