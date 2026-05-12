package net.kikin.nubecita.feature.profile.impl

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileTabPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

/**
 * Behavior tests for [ProfileViewModel]. Mirrors the structure of
 * `PostDetailViewModelTest` — fake repository, `MainDispatcherExtension`
 * for `runTest`, Turbine for effect collection.
 *
 * Each test asserts one ViewModel invariant from the
 * `feature-profile/spec.md` capability spec.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    @Test
    fun `init kicks off four concurrent loads (header + three tabs)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeProfileRepository()
            newVm(repo = repo)
            advanceUntilIdle()

            assertEquals(1, repo.headerCalls.get(), "header MUST be loaded exactly once on init")
            assertEquals(1, repo.tabCalls[ProfileTab.Posts]?.get())
            assertEquals(1, repo.tabCalls[ProfileTab.Replies]?.get())
            assertEquals(1, repo.tabCalls[ProfileTab.Media]?.get())
        }

    @Test
    fun `per-tab independent failure leaves siblings intact`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerResult = Result.success(SAMPLE_HEADER),
                    tabResults =
                        mapOf(
                            ProfileTab.Posts to Result.success(EMPTY_PAGE),
                            ProfileTab.Replies to Result.failure(IOException("net down")),
                            ProfileTab.Media to Result.success(EMPTY_PAGE),
                        ),
                )
            val vm = newVm(repo = repo)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertNotNull(state.header, "header MUST load when its own call succeeds")
            assertTrue(state.postsStatus is TabLoadStatus.Loaded, "posts MUST be Loaded")
            assertTrue(state.repliesStatus is TabLoadStatus.InitialError, "replies MUST be InitialError")
            assertEquals(
                ProfileError.Network,
                (state.repliesStatus as TabLoadStatus.InitialError).error,
                "IOException MUST map to ProfileError.Network",
            )
            assertTrue(state.mediaStatus is TabLoadStatus.Loaded, "media MUST be Loaded")
        }

    @Test
    fun `self-handle tap is a silent no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerResult = Result.success(SAMPLE_HEADER.copy(handle = "alice.bsky.social")),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, route = Profile(handle = "alice.bsky.social"))
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.HandleTapped("alice.bsky.social"))
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cross-handle tap emits NavigateToProfile`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerResult = Result.success(SAMPLE_HEADER.copy(handle = "alice.bsky.social")),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, route = Profile(handle = "alice.bsky.social"))
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.HandleTapped("bob.bsky.social"))
                val effect = awaitItem()
                assertEquals(ProfileEffect.NavigateToProfile("bob.bsky.social"), effect)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `FollowTapped emits ShowComingSoon and never touches the repository`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerResult = Result.success(SAMPLE_HEADER),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            // Snapshot call counts after init; FollowTapped MUST not move them.
            val priorHeaderCalls = repo.headerCalls.get()
            val priorPostsCalls = repo.tabCalls[ProfileTab.Posts]!!.get()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.FollowTapped)
                val effect = awaitItem()
                assertEquals(ProfileEffect.ShowComingSoon(StubbedAction.Follow), effect)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(priorHeaderCalls, repo.headerCalls.get(), "FollowTapped MUST NOT issue a repository call")
            assertEquals(priorPostsCalls, repo.tabCalls[ProfileTab.Posts]!!.get(), "FollowTapped MUST NOT issue a tab fetch")
        }

    @Test
    fun `TabSelected does not re-fetch when target tab is already Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerResult = Result.success(SAMPLE_HEADER),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo)
            advanceUntilIdle()

            val priorRepliesCalls = repo.tabCalls[ProfileTab.Replies]!!.get()

            vm.handleEvent(ProfileEvent.TabSelected(ProfileTab.Replies))
            advanceUntilIdle()

            assertEquals(ProfileTab.Replies, vm.uiState.value.selectedTab)
            assertEquals(
                priorRepliesCalls,
                repo.tabCalls[ProfileTab.Replies]!!.get(),
                "Tab switch to an already-Loaded tab MUST NOT re-fetch",
            )
        }

    @Test
    fun `LoadMore issues a getAuthorFeed call with the correct per-tab cursor`() =
        runTest(mainDispatcher.dispatcher) {
            // First page returns a cursor; LoadMore re-issues with that cursor.
            val repo =
                FakeProfileRepository(
                    headerResult = Result.success(SAMPLE_HEADER),
                    tabResults =
                        mapOf(
                            ProfileTab.Posts to Result.success(ProfileTabPage(items = persistentListOf(), nextCursor = "cursor-page-2")),
                            ProfileTab.Replies to Result.success(EMPTY_PAGE),
                            ProfileTab.Media to Result.success(EMPTY_PAGE),
                        ),
                )
            val vm = newVm(repo = repo)
            advanceUntilIdle()
            // After init, Posts cursor should be the one from page-1's response.
            val loaded = vm.uiState.value.postsStatus as TabLoadStatus.Loaded
            assertEquals("cursor-page-2", loaded.cursor)

            vm.handleEvent(ProfileEvent.LoadMore(ProfileTab.Posts))
            advanceUntilIdle()

            // Repository MUST have been called for Posts with cursor=cursor-page-2.
            assertEquals(
                "cursor-page-2",
                repo.lastTabCursor[ProfileTab.Posts],
                "LoadMore MUST pass the tab's current cursor to the repository",
            )
            // Other tab cursors MUST be untouched (per the spec scenario).
            assertNull(repo.lastTabCursor[ProfileTab.Replies], "Replies cursor MUST be unchanged by Posts LoadMore")
            assertNull(repo.lastTabCursor[ProfileTab.Media], "Media cursor MUST be unchanged by Posts LoadMore")
        }

    @Test
    fun `RetryTab re-launches initial tab load for the named tab`() =
        runTest(mainDispatcher.dispatcher) {
            // First call fails (initial load), second call succeeds (the retry).
            val firstResult = Result.failure<ProfileTabPage>(IOException("net down"))
            val secondResult = Result.success(EMPTY_PAGE)
            val repo =
                FakeProfileRepository(
                    headerResult = Result.success(SAMPLE_HEADER),
                    tabResults =
                        mapOf(
                            ProfileTab.Posts to firstResult,
                            ProfileTab.Replies to Result.success(EMPTY_PAGE),
                            ProfileTab.Media to Result.success(EMPTY_PAGE),
                        ),
                )
            val vm = newVm(repo = repo)
            advanceUntilIdle()
            assertTrue(
                vm.uiState.value.postsStatus is TabLoadStatus.InitialError,
                "after init the Posts tab MUST be in InitialError",
            )
            // Flip the fake's result for the next call.
            repo.tabResults =
                repo.tabResults.toMutableMap().apply {
                    put(ProfileTab.Posts, secondResult)
                }
            val priorPostsCalls = repo.tabCalls[ProfileTab.Posts]!!.get()

            vm.handleEvent(ProfileEvent.RetryTab(ProfileTab.Posts))
            advanceUntilIdle()

            assertEquals(
                priorPostsCalls + 1,
                repo.tabCalls[ProfileTab.Posts]!!.get(),
                "RetryTab MUST issue exactly one additional fetchTab call",
            )
            assertTrue(
                vm.uiState.value.postsStatus is TabLoadStatus.Loaded,
                "after RetryTab succeeds the Posts tab MUST be in Loaded",
            )
        }

    // -- Test helpers ----------------------------------------------------------

    private fun newVm(
        repo: ProfileRepository,
        route: Profile = Profile(handle = null),
        sessionState: SessionState =
            SessionState.SignedIn(handle = "viewer.bsky.social", did = "did:plc:viewer123"),
    ): ProfileViewModel {
        val sessionProvider =
            mockk<SessionStateProvider>(relaxed = true).also {
                every { it.state } returns MutableStateFlow(sessionState)
            }
        return ProfileViewModel(
            route = route,
            repository = repo,
            sessionStateProvider = sessionProvider,
        )
    }

    private companion object {
        val SAMPLE_HEADER =
            ProfileHeaderUi(
                did = "did:plc:alice",
                handle = "alice.bsky.social",
                displayName = "Alice",
                avatarUrl = null,
                bannerUrl = null,
                avatarHue = 217,
                bio = null,
                location = null,
                website = null,
                joinedDisplay = null,
                postsCount = 0L,
                followersCount = 0L,
                followsCount = 0L,
            )
        val EMPTY_PAGE = ProfileTabPage(items = persistentListOf(), nextCursor = null)
    }

    /**
     * In-memory [ProfileRepository] for VM tests. Tracks call counts +
     * cursor history per tab so the test assertions can introspect
     * what the VM actually requested.
     */
    private class FakeProfileRepository(
        private val headerResult: Result<ProfileHeaderUi> = Result.success(SAMPLE_HEADER),
        var tabResults: Map<ProfileTab, Result<ProfileTabPage>> =
            ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
    ) : ProfileRepository {
        val headerCalls = AtomicInteger(0)
        val tabCalls: Map<ProfileTab, AtomicInteger> =
            ProfileTab.entries.associateWith { AtomicInteger(0) }
        val lastTabCursor: MutableMap<ProfileTab, String?> = mutableMapOf()

        override suspend fun fetchHeader(actor: String): Result<ProfileHeaderUi> {
            headerCalls.incrementAndGet()
            return headerResult
        }

        override suspend fun fetchTab(
            actor: String,
            tab: ProfileTab,
            cursor: String?,
            limit: Int,
        ): Result<ProfileTabPage> {
            tabCalls.getValue(tab).incrementAndGet()
            // Only record cursors for non-initial calls. Initial loads
            // pass cursor=null (the default); LoadMore passes the
            // current cursor — that's what the spec scenario asserts.
            if (cursor != null) lastTabCursor[tab] = cursor
            return tabResults.getValue(tab)
        }
    }
}
