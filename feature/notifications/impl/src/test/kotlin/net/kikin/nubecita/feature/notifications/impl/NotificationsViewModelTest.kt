package net.kikin.nubecita.feature.notifications.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.NotificationFilter
import net.kikin.nubecita.data.models.NotificationItemUi
import net.kikin.nubecita.data.models.NotificationItemUiFixtures
import net.kikin.nubecita.data.models.NotificationReason
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsPage
import net.kikin.nubecita.feature.notifications.impl.data.NotificationsRepository
import net.kikin.nubecita.feature.postdetail.api.PostDetailRoute
import net.kikin.nubecita.feature.profile.api.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class NotificationsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    // ---------- init / initial fetch ----------

    @Test
    fun `init fires first fetch with filter All and cursor null`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages = listOf(Result.success(page(items = items("a1"), nextCursor = "c1"))),
                )
            // Construction kicks off the initial fetch via `init`.
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            assertEquals(1, repo.fetchPageCalls.size, "init MUST issue exactly one listNotifications call")
            val call = repo.fetchPageCalls.single()
            assertEquals(NotificationFilter.All, call.filter)
            assertNull(call.cursor)

            val state = vm.uiState.value
            assertEquals(1, state.items.size)
            assertEquals("c1", state.cursor)
            assertTrue(state.hasMore)
            assertEquals(NotificationsLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `initial load failure with empty items transitions to InitialError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeNotificationsRepository(pages = listOf(Result.failure(IOException("network down"))))

            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is NotificationsLoadStatus.InitialError, "expected InitialError, got $status")
            assertEquals(
                NotificationsError.Network,
                (status as NotificationsLoadStatus.InitialError).error,
            )
            assertTrue(
                vm.uiState.value.items
                    .isEmpty(),
            )
        }

    @Test
    fun `NoSessionException maps to InitialError(Unauthenticated)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeNotificationsRepository(pages = listOf(Result.failure(NoSessionException())))

            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is NotificationsLoadStatus.InitialError)
            assertEquals(
                NotificationsError.Unauthenticated,
                (status as NotificationsLoadStatus.InitialError).error,
            )
        }

    // ---------- Refresh ----------

    @Test
    fun `Refresh success replaces items and advances cursor`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages =
                        listOf(
                            Result.success(page(items = items("a1", "a2"), nextCursor = "c1")),
                            Result.success(page(items = items("b1"), nextCursor = "c2")),
                        ),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            vm.handleEvent(NotificationsEvent.Refresh)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf("b1"), state.items.map { it.itemKey })
            assertEquals("c2", state.cursor)
            assertEquals(NotificationsLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `Refresh failure with non-empty items preserves items and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages =
                        listOf(
                            Result.success(page(items = items("a1"), nextCursor = "c1")),
                            Result.failure(IOException("refresh failed")),
                        ),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.Refresh)
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is NotificationsEffect.ShowError)
            }

            val state = vm.uiState.value
            assertEquals(listOf("a1"), state.items.map { it.itemKey })
            assertEquals("c1", state.cursor)
            assertEquals(NotificationsLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `Refresh while InitialLoading is dropped (no second fetch)`() =
        runTest(mainDispatcher.dispatcher) {
            // Park the init fetch on a deferred so the VM sits in
            // `InitialLoading`. A Refresh dispatched at that moment used to
            // launch a second head request; the single-flight invariant
            // (mirrored from FeedViewModel) now drops it.
            val initialDeferred = CompletableDeferred<Result<NotificationsPage>>()
            val callIndex = AtomicInteger(0)
            val repo =
                FakeNotificationsRepository(
                    pageProducer = { _, _ ->
                        when (callIndex.getAndIncrement()) {
                            0 -> initialDeferred.await()
                            else -> error("Refresh during InitialLoading should NOT issue a second fetch")
                        }
                    },
                )
            val vm = NotificationsViewModel(repo)
            // Don't advanceUntilIdle — init fetch is parked.
            assertEquals(NotificationsLoadStatus.InitialLoading, vm.uiState.value.loadStatus)

            vm.handleEvent(NotificationsEvent.Refresh) // must be dropped

            initialDeferred.complete(Result.success(page(items = items("a1"), nextCursor = null)))
            advanceUntilIdle()

            // Exactly one call: the init. The Refresh would have produced a second.
            assertEquals(1, repo.fetchPageCalls.size)
        }

    @Test
    fun `Refresh while Appending is dropped (no second fetch)`() =
        runTest(mainDispatcher.dispatcher) {
            val initial = page(items = items("a1"), nextCursor = "c1")
            val appendDeferred = CompletableDeferred<Result<NotificationsPage>>()
            val callIndex = AtomicInteger(0)
            val repo =
                FakeNotificationsRepository(
                    pageProducer = { _, _ ->
                        when (callIndex.getAndIncrement()) {
                            0 -> Result.success(initial)
                            else -> appendDeferred.await()
                        }
                    },
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            vm.handleEvent(NotificationsEvent.LoadMore) // enters Appending
            vm.handleEvent(NotificationsEvent.Refresh) // must be dropped

            appendDeferred.complete(Result.success(page(items = items("a2"), nextCursor = "c2")))
            advanceUntilIdle()

            // Two repo calls total: the initial fetch and the LoadMore.
            // The dropped Refresh would have produced a third invocation.
            assertEquals(2, repo.fetchPageCalls.size)
        }

    // ---------- LoadMore ----------

    @Test
    fun `LoadMore appends items, updates cursor, and reflects hasMore`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages =
                        listOf(
                            Result.success(page(items = items("a1", "a2"), nextCursor = "c1")),
                            Result.success(page(items = items("a3"), nextCursor = null)),
                        ),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            vm.handleEvent(NotificationsEvent.LoadMore)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(listOf("a1", "a2", "a3"), state.items.map { it.itemKey })
            assertNull(state.cursor, "null nextCursor exposed verbatim on state")
            assertEquals(false, state.hasMore, "hasMore flips to false when nextCursor is null")
            assertEquals(NotificationsLoadStatus.Idle, state.loadStatus)
        }

    @Test
    fun `LoadMore is gated when hasMore is false (no fetch fires)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages = listOf(Result.success(page(items = items("a1"), nextCursor = null))),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()
            assertEquals(false, vm.uiState.value.hasMore)
            assertEquals(1, repo.fetchPageCalls.size)

            vm.handleEvent(NotificationsEvent.LoadMore)
            advanceUntilIdle()

            // No additional call: the LoadMore reducer's `if (!hasMore) return`
            // short-circuits before viewModelScope.launch.
            assertEquals(1, repo.fetchPageCalls.size)
        }

    @Test
    fun `LoadMore failure preserves cursor and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages =
                        listOf(
                            Result.success(page(items = items("a1"), nextCursor = "c1")),
                            Result.failure(IOException("page failed")),
                        ),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.LoadMore)
                advanceUntilIdle()

                val effect = awaitItem()
                assertTrue(effect is NotificationsEffect.ShowError)
            }

            val state = vm.uiState.value
            assertEquals(listOf("a1"), state.items.map { it.itemKey })
            // Cursor preserved so retry can replay against the same page boundary.
            assertEquals("c1", state.cursor)
            assertEquals(NotificationsLoadStatus.Idle, state.loadStatus)
        }

    // ---------- FilterSelected ----------

    @Test
    fun `FilterSelected with same filter is a no-op (no fetch fires)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages = listOf(Result.success(page(items = items("a1"), nextCursor = null))),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()
            val before = vm.uiState.value
            assertEquals(1, repo.fetchPageCalls.size)

            vm.handleEvent(NotificationsEvent.FilterSelected(NotificationFilter.All))
            advanceUntilIdle()

            // Identity-of-filter short-circuits the reducer; no state change,
            // no second repo call.
            assertSame(before, vm.uiState.value)
            assertEquals(1, repo.fetchPageCalls.size)
        }

    @Test
    fun `FilterSelected with a different filter resets state and refetches`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages =
                        listOf(
                            Result.success(page(items = items("a1", "a2"), nextCursor = "c1")),
                            Result.success(page(items = items("m1"), nextCursor = "c2")),
                        ),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            vm.handleEvent(NotificationsEvent.FilterSelected(NotificationFilter.Mentions))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(NotificationFilter.Mentions, state.activeFilter)
            assertEquals(listOf("m1"), state.items.map { it.itemKey })
            assertEquals("c2", state.cursor)
            assertEquals(NotificationsLoadStatus.Idle, state.loadStatus)

            // The second repo call MUST go out with the new filter and a
            // null cursor (filter switch resets pagination).
            val secondCall = repo.fetchPageCalls[1]
            assertEquals(NotificationFilter.Mentions, secondCall.filter)
            assertNull(secondCall.cursor)
        }

    @Test
    fun `FilterSelected drops the stale completion from an in-flight initial load`() =
        runTest(mainDispatcher.dispatcher) {
            // Park the All-filter init fetch; user switches to Mentions before
            // it completes; Mentions completes first, then the stale All
            // completion lands and MUST be rejected by the requestFilter check.
            val allDeferred = CompletableDeferred<Result<NotificationsPage>>()
            val mentionsDeferred = CompletableDeferred<Result<NotificationsPage>>()
            val repo =
                FakeNotificationsRepository(
                    pageProducer = { filter, _ ->
                        when (filter) {
                            NotificationFilter.All -> allDeferred.await()
                            NotificationFilter.Mentions -> mentionsDeferred.await()
                            else -> error("unexpected filter $filter")
                        }
                    },
                )
            val vm = NotificationsViewModel(repo)
            // init fetch parked on allDeferred; activeFilter = All.

            vm.handleEvent(NotificationsEvent.FilterSelected(NotificationFilter.Mentions))
            // Mentions fetch parked on mentionsDeferred; activeFilter = Mentions.

            // Resolve Mentions first.
            mentionsDeferred.complete(Result.success(page(items = items("m1"), nextCursor = "mc")))
            advanceUntilIdle()
            assertEquals(NotificationFilter.Mentions, vm.uiState.value.activeFilter)
            assertEquals(
                listOf("m1"),
                vm.uiState.value.items
                    .map { it.itemKey },
            )
            assertEquals("mc", vm.uiState.value.cursor)

            // Now resolve the stale All fetch with different items — its
            // completion must be dropped (state unchanged).
            allDeferred.complete(Result.success(page(items = items("stale-a1", "stale-a2"), nextCursor = "stale-cursor")))
            advanceUntilIdle()

            assertEquals(NotificationFilter.Mentions, vm.uiState.value.activeFilter)
            assertEquals(
                listOf("m1"),
                vm.uiState.value.items
                    .map { it.itemKey },
            )
            assertEquals("mc", vm.uiState.value.cursor)
        }

    @Test
    fun `FilterSelected back-toggle drops the original stale completion under the same filter`() =
        runTest(mainDispatcher.dispatcher) {
            // The user's filter switches: All (init) → Mentions → All. All
            // three fetches share `requestFilter = All` for two of the three
            // requests, so a filter-equality guard wouldn't catch the race —
            // the slow first All can overwrite the newer third All. The
            // monotonic generation tag distinguishes them.
            val firstAllDeferred = CompletableDeferred<Result<NotificationsPage>>()
            val mentionsDeferred = CompletableDeferred<Result<NotificationsPage>>()
            val secondAllDeferred = CompletableDeferred<Result<NotificationsPage>>()
            val allCallIndex = AtomicInteger(0)
            val repo =
                FakeNotificationsRepository(
                    pageProducer = { filter, _ ->
                        when (filter) {
                            NotificationFilter.All ->
                                when (allCallIndex.getAndIncrement()) {
                                    0 -> firstAllDeferred.await()
                                    else -> secondAllDeferred.await()
                                }
                            NotificationFilter.Mentions -> mentionsDeferred.await()
                            else -> error("unexpected filter $filter")
                        }
                    },
                )
            val vm = NotificationsViewModel(repo)
            // gen 1 = first All (parked)

            vm.handleEvent(NotificationsEvent.FilterSelected(NotificationFilter.Mentions))
            // gen 2 = Mentions (parked)

            vm.handleEvent(NotificationsEvent.FilterSelected(NotificationFilter.All))
            // gen 3 = second All (parked). state.activeFilter = All again.

            // Resolve the second (latest) All first — its completion lands.
            secondAllDeferred.complete(Result.success(page(items = items("fresh-a1"), nextCursor = "fresh-cursor")))
            advanceUntilIdle()
            assertEquals(
                listOf("fresh-a1"),
                vm.uiState.value.items
                    .map { it.itemKey },
            )
            assertEquals("fresh-cursor", vm.uiState.value.cursor)

            // Resolve the original (stale) All — its requestFilter (All)
            // matches state.activeFilter (All), but its generation (1) does
            // NOT match the current generation (3). The completion MUST be
            // dropped — without the generation tag, the stale items would
            // overwrite the fresh state.
            firstAllDeferred.complete(Result.success(page(items = items("stale-a1", "stale-a2"), nextCursor = "stale-cursor")))
            advanceUntilIdle()
            assertEquals(
                listOf("fresh-a1"),
                vm.uiState.value.items
                    .map { it.itemKey },
                "stale same-filter completion must NOT overwrite fresh state",
            )
            assertEquals("fresh-cursor", vm.uiState.value.cursor)

            // Resolve the orphaned Mentions completion too — it must also be dropped
            // (different filter from current All, generation 2 != 3).
            mentionsDeferred.complete(Result.success(page(items = items("mentions-orphan"), nextCursor = "mentions-cursor")))
            advanceUntilIdle()
            assertEquals(
                listOf("fresh-a1"),
                vm.uiState.value.items
                    .map { it.itemKey },
            )
        }

    @Test
    fun `Refresh failure ShowError effect carries the typed NotificationsError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages =
                        listOf(
                            Result.success(page(items = items("a1"), nextCursor = "c1")),
                            Result.failure(IOException("network down")),
                        ),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.Refresh)
                advanceUntilIdle()

                val effect = awaitItem() as NotificationsEffect.ShowError
                // Effect carries a typed `NotificationsError`, not a pre-rendered string.
                // The screen maps each variant to its stringResource at render time —
                // same shape as FeedEffect.ShowError(error: FeedError).
                assertEquals(NotificationsError.Network, effect.error)
            }
        }

    @Test
    fun `Refresh failure under NoSessionException emits ShowError with Unauthenticated`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages =
                        listOf(
                            Result.success(page(items = items("a1"), nextCursor = "c1")),
                            Result.failure(NoSessionException()),
                        ),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.Refresh)
                advanceUntilIdle()

                val effect = awaitItem() as NotificationsEffect.ShowError
                assertEquals(NotificationsError.Unauthenticated, effect.error)
            }
        }

    // ---------- toViewState projection ----------

    @Test
    fun `empty items + Refreshing projects to InitialLoading view state`() {
        // Reachable when the user retries from `InitialError` (the VM
        // transitions to Refreshing while items are still empty). Was
        // mapping to `Empty` — flashed the all-caught-up affordance for
        // a brief instant before items arrived. Now maps to shimmer.
        val state =
            NotificationsState(
                items = persistentListOf(),
                loadStatus = NotificationsLoadStatus.Refreshing,
            )
        assertEquals(NotificationsScreenViewState.InitialLoading, state.toViewState())
    }

    @Test
    fun `empty items + Idle projects to Empty view state`() {
        // Sanity check: the Empty case is still preserved for a settled
        // empty result.
        val state =
            NotificationsState(
                items = persistentListOf(),
                loadStatus = NotificationsLoadStatus.Idle,
            )
        assertEquals(NotificationsScreenViewState.Empty, state.toViewState())
    }

    // ---------- RowTapped: deep-link routing per reason ----------

    @Test
    fun `RowTapped on a Like row emits NavigateTo with PostDetailRoute`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val subject = NotificationItemUiFixtures.singleLike()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(subject))
                val effect = awaitItem()
                assertTrue(effect is NotificationsEffect.NavigateTo)
                val target = (effect as NotificationsEffect.NavigateTo).target
                assertTrue(target is PostDetailRoute)
                assertEquals(subject.subjectPost?.id, (target as PostDetailRoute).postUri)
            }
        }

    @Test
    fun `RowTapped on a Repost row emits NavigateTo with PostDetailRoute`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleRepost()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertTrue(effect.target is PostDetailRoute)
                assertEquals(item.subjectPost?.id, (effect.target as PostDetailRoute).postUri)
            }
        }

    @Test
    fun `RowTapped on a LikeViaRepost row emits NavigateTo with PostDetailRoute`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleLikeViaRepost()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertEquals(item.subjectPost?.id, (effect.target as PostDetailRoute).postUri)
            }
        }

    @Test
    fun `RowTapped on a RepostViaRepost row emits NavigateTo with PostDetailRoute`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleRepostViaRepost()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertEquals(item.subjectPost?.id, (effect.target as PostDetailRoute).postUri)
            }
        }

    @Test
    fun `RowTapped on a Reply row emits NavigateTo with PostDetailRoute`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleReply()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertEquals(item.subjectPost?.id, (effect.target as PostDetailRoute).postUri)
            }
        }

    @Test
    fun `RowTapped on a Quote row emits NavigateTo with PostDetailRoute`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleQuote()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertEquals(item.subjectPost?.id, (effect.target as PostDetailRoute).postUri)
            }
        }

    @Test
    fun `RowTapped on a Mention row emits NavigateTo with PostDetailRoute`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleMention()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertEquals(item.subjectPost?.id, (effect.target as PostDetailRoute).postUri)
            }
        }

    @Test
    fun `RowTapped on a SubscribedPost row emits NavigateTo with PostDetailRoute`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleSubscribedPost()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertEquals(item.subjectPost?.id, (effect.target as PostDetailRoute).postUri)
            }
        }

    @Test
    fun `RowTapped on a Follow row emits NavigateTo with the actor's Profile`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleFollow()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertTrue(effect.target is Profile)
                assertEquals(item.actors.first().did, (effect.target as Profile).handle)
            }
        }

    @Test
    fun `RowTapped on a ContactMatch row emits NavigateTo with the actor's Profile`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleContactMatch()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertEquals(item.actors.first().did, (effect.target as Profile).handle)
            }
        }

    @Test
    fun `RowTapped on a StarterpackJoined row emits NavigateTo with the actor's Profile`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleStarterpackJoined()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertEquals(item.actors.first().did, (effect.target as Profile).handle)
            }
        }

    @Test
    fun `RowTapped on a Verified row emits NavigateTo with self Profile`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleVerified()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertTrue(effect.target is Profile)
                // Self-shaped reasons route to Profile(handle = null) — the
                // canonical "current authenticated user" key per the
                // navigation contract.
                assertNull((effect.target as Profile).handle)
            }
        }

    @Test
    fun `RowTapped on an Unverified row emits NavigateTo with self Profile`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item = NotificationItemUiFixtures.singleUnverified()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                val effect = awaitItem() as NotificationsEffect.NavigateTo
                assertNull((effect.target as Profile).handle)
            }
        }

    @Test
    fun `RowTapped on a row with unknown reason emits no effect`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val item =
                NotificationItemUi.Single(
                    itemKey = "unknown-fixture-1",
                    reason = NotificationReason.Unknown,
                    indexedAt = Instant.parse("2026-05-26T12:00:00Z"),
                    isRead = false,
                    actors =
                        persistentListOf(
                            AuthorUi(
                                did = "did:plc:unknown00000000000000000",
                                handle = "unknown.bsky.social",
                                displayName = "Unknown",
                                avatarUrl = null,
                            ),
                        ),
                    subjectPost = null,
                )

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                advanceUntilIdle()
                expectNoEvents()
            }
        }

    @Test
    fun `RowTapped on engagement row with null subjectPost emits no effect`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            // Engagement reason with a hydration miss — `subjectPost` is
            // null. The row still renders but the deep-link is undefined,
            // so the VM swallows the tap rather than emitting a NavigateTo
            // with a meaningless URI.
            val item = NotificationItemUiFixtures.singleLike(subjectPost = null)

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.RowTapped(item))
                advanceUntilIdle()
                expectNoEvents()
            }
        }

    // ---------- AvatarStackTapped + SheetDismissed ----------

    @Test
    fun `AvatarStackTapped writes the actors to state actorListSheet`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val aggregated = NotificationItemUiFixtures.aggregatedLikes(actorCount = 4)

            assertNull(vm.uiState.value.actorListSheet, "sheet starts closed")
            vm.handleEvent(NotificationsEvent.AvatarStackTapped(aggregated))
            advanceUntilIdle()

            assertEquals(aggregated.actors, vm.uiState.value.actorListSheet)
        }

    @Test
    fun `SheetDismissed clears state actorListSheet`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vmWithInitialPage()
            val aggregated = NotificationItemUiFixtures.aggregatedLikes(actorCount = 3)

            vm.handleEvent(NotificationsEvent.AvatarStackTapped(aggregated))
            advanceUntilIdle()
            assertNotNull(vm.uiState.value.actorListSheet)

            vm.handleEvent(NotificationsEvent.SheetDismissed)
            advanceUntilIdle()
            assertNull(vm.uiState.value.actorListSheet)
        }

    // ---------- TabExited ----------

    @Test
    fun `TabExited calls repository markSeen`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages = listOf(Result.success(page(items = items("a1"), nextCursor = null))),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()
            assertEquals(0, repo.markSeenCalls.size)

            vm.handleEvent(NotificationsEvent.TabExited)
            advanceUntilIdle()

            assertEquals(1, repo.markSeenCalls.size, "TabExited MUST issue exactly one markSeen call")
            // The instant arg is `now` — non-null, monotonic. Specific
            // value isn't load-bearing; what matters is that the repo got
            // a real Instant (not a sentinel).
            assertNotNull(repo.markSeenCalls.single())
        }

    @Test
    fun `TabExited swallows markSeen failures without emitting ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeNotificationsRepository(
                    pages = listOf(Result.success(page(items = items("a1"), nextCursor = null))),
                    markSeenResult = Result.failure(IOException("seen failed")),
                )
            val vm = NotificationsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(NotificationsEvent.TabExited)
                advanceUntilIdle()
                // Per design D6: failures are intentionally not surfaced.
                // Next 60s poll corrects the badge.
                expectNoEvents()
            }
        }
}

// ---------- helpers ----------

/**
 * Build a VM whose initial fetch succeeds and drains the loading state to
 * Idle. Used by every RowTapped / AvatarStackTapped test so the unrelated
 * initial-load setState pulse doesn't share a frame with the effect under
 * assertion. Each caller does `advanceUntilIdle()` already via the
 * enclosing `runTest`; this helper just bootstraps the fake.
 */
private fun kotlinx.coroutines.test.TestScope.vmWithInitialPage(): NotificationsViewModel {
    val repo =
        FakeNotificationsRepository(
            pages = listOf(Result.success(page(items = persistentListOf(), nextCursor = null))),
        )
    val vm = NotificationsViewModel(repo)
    testScheduler.advanceUntilIdle()
    return vm
}

private fun page(
    items: ImmutableList<NotificationItemUi>,
    nextCursor: String?,
): NotificationsPage = NotificationsPage(items = items, nextCursor = nextCursor)

private fun items(vararg keys: String): ImmutableList<NotificationItemUi> =
    keys
        .map { key ->
            NotificationItemUiFixtures.singleLike(itemKey = key)
        }.toImmutableList()

/**
 * Hand-rolled fake repository — clearer for VM tests than mocking via
 * MockK because every test asserts on call records (filter + cursor of
 * each fetchPage invocation; presence of markSeen).
 *
 * Two construction modes:
 *  - `pages = listOf(...)`: a queue of pre-built Results, consumed in
 *    FIFO order. Index out-of-bounds means the test forgot to enqueue a
 *    page for the call it issued — fail loudly instead of returning a
 *    stale value.
 *  - `pageProducer = { filter, cursor -> ... }`: dynamic — for tests
 *    that need to suspend the result (e.g., dropping events while a
 *    fetch is in flight).
 */
private class FakeNotificationsRepository(
    private val pages: List<Result<NotificationsPage>> = emptyList(),
    private val pageProducer: (suspend (NotificationFilter, String?) -> Result<NotificationsPage>)? = null,
    private val markSeenResult: Result<Unit> = Result.success(Unit),
    private val unreadCountResult: Result<Int> = Result.success(0),
) : NotificationsRepository {
    data class FetchCall(
        val filter: NotificationFilter,
        val cursor: String?,
    )

    val fetchPageCalls: MutableList<FetchCall> = mutableListOf()
    val markSeenCalls: MutableList<Instant> = mutableListOf()

    override suspend fun fetchPage(
        filter: NotificationFilter,
        cursor: String?,
    ): Result<NotificationsPage> {
        fetchPageCalls += FetchCall(filter, cursor)
        return pageProducer?.invoke(filter, cursor)
            ?: pages.getOrElse(fetchPageCalls.size - 1) {
                error("FakeNotificationsRepository: no result queued for call #${fetchPageCalls.size}")
            }
    }

    override suspend fun markSeen(seenAt: Instant): Result<Unit> {
        markSeenCalls += seenAt
        return markSeenResult
    }

    override suspend fun unreadCount(): Result<Int> = unreadCountResult
}
