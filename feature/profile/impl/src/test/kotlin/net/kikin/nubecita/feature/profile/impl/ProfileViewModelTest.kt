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
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.data.ProfileHeaderWithViewer
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
import kotlin.time.Instant

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
                    headerWithViewerResult =
                        Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
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
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                SAMPLE_HEADER.copy(handle = "alice.bsky.social"),
                                ViewerRelationship.None,
                            ),
                        ),
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
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                SAMPLE_HEADER.copy(handle = "alice.bsky.social"),
                                ViewerRelationship.None,
                            ),
                        ),
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
    fun `FollowTapped on NotFollowing optimistically flips to Following pending then commits the wire AT-URI`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                SAMPLE_HEADER.copy(handle = "bob.bsky.social"),
                                ViewerRelationship.NotFollowing(),
                            ),
                        ),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                    followResult = Result.success(SAMPLE_FOLLOW_URI),
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            // Tap; gate the repo so the test can observe the pending state in
            // between the optimistic flip and the wire commit.
            repo.gateFollow = true
            vm.handleEvent(ProfileEvent.FollowTapped)
            // The optimistic flip is synchronous on the dispatcher.
            mainDispatcher.dispatcher.scheduler.runCurrent()
            assertEquals(
                ViewerRelationship.Following(followUri = null, isPending = true),
                vm.uiState.value.viewerRelationship,
                "FollowTapped MUST optimistically flip to Following(pending=true) before the wire call returns",
            )

            // Release the gate and let the success commit.
            repo.releaseFollow()
            advanceUntilIdle()
            assertEquals(
                ViewerRelationship.Following(followUri = SAMPLE_FOLLOW_URI, isPending = false),
                vm.uiState.value.viewerRelationship,
                "follow success MUST commit the wire AT-URI and clear pending",
            )
            assertEquals(1, repo.followCalls.get(), "exactly one follow call MUST be issued")
            assertEquals("did:plc:alice", repo.lastFollowSubject, "follow MUST target the rendered profile's DID")
        }

    @Test
    fun `FollowTapped on Following optimistically flips to NotFollowing pending then commits unfollow`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                SAMPLE_HEADER.copy(handle = "bob.bsky.social"),
                                SAMPLE_FOLLOWING,
                            ),
                        ),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                    unfollowResult = Result.success(Unit),
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            repo.gateUnfollow = true
            vm.handleEvent(ProfileEvent.FollowTapped)
            mainDispatcher.dispatcher.scheduler.runCurrent()
            assertEquals(
                ViewerRelationship.NotFollowing(isPending = true),
                vm.uiState.value.viewerRelationship,
                "FollowTapped on Following MUST optimistically flip to NotFollowing(pending=true)",
            )

            repo.releaseUnfollow()
            advanceUntilIdle()
            assertEquals(
                ViewerRelationship.NotFollowing(isPending = false),
                vm.uiState.value.viewerRelationship,
                "unfollow success MUST clear pending on NotFollowing",
            )
            assertEquals(1, repo.unfollowCalls.get(), "exactly one unfollow call MUST be issued")
            assertEquals(
                SAMPLE_FOLLOW_URI,
                repo.lastUnfollowUri,
                "unfollow MUST target the AT-URI carried on the prior Following state",
            )
        }

    @Test
    fun `FollowTapped failure rolls back to the prior relationship and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                SAMPLE_HEADER.copy(handle = "bob.bsky.social"),
                                ViewerRelationship.NotFollowing(),
                            ),
                        ),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                    followResult = Result.failure(IOException("net down")),
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.FollowTapped)
                advanceUntilIdle()
                val effect = awaitItem()
                assertTrue(effect is ProfileEffect.ShowError, "follow failure MUST surface ShowError")
                assertEquals(
                    ProfileError.Network,
                    (effect as ProfileEffect.ShowError).error,
                    "IOException MUST map to ProfileError.Network",
                )
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                ViewerRelationship.NotFollowing(),
                vm.uiState.value.viewerRelationship,
                "follow failure MUST roll back to the prior relationship",
            )
        }

    @Test
    fun `unfollow failure rolls back to Following with the original AT-URI`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                SAMPLE_HEADER.copy(handle = "bob.bsky.social"),
                                SAMPLE_FOLLOWING,
                            ),
                        ),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                    unfollowResult = Result.failure(IOException("net down")),
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.FollowTapped)
                advanceUntilIdle()
                val effect = awaitItem()
                assertTrue(effect is ProfileEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                SAMPLE_FOLLOWING,
                vm.uiState.value.viewerRelationship,
                "unfollow failure MUST restore Following with the original AT-URI so a retry can target it",
            )
        }

    @Test
    fun `double-tap during pending is single-flighted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                SAMPLE_HEADER.copy(handle = "bob.bsky.social"),
                                ViewerRelationship.NotFollowing(),
                            ),
                        ),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                    followResult = Result.success(SAMPLE_FOLLOW_URI),
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            repo.gateFollow = true
            vm.handleEvent(ProfileEvent.FollowTapped)
            mainDispatcher.dispatcher.scheduler.runCurrent()
            // Second tap while pending — must NOT issue a second follow call.
            vm.handleEvent(ProfileEvent.FollowTapped)
            mainDispatcher.dispatcher.scheduler.runCurrent()

            repo.releaseFollow()
            advanceUntilIdle()

            assertEquals(
                1,
                repo.followCalls.get(),
                "double-tap during pending MUST NOT fan out into a second follow call",
            )
        }

    @Test
    fun `FollowTapped on Self is a silent no-op (own profile)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, route = Profile(handle = null))
            advanceUntilIdle()
            assertEquals(ViewerRelationship.Self, vm.uiState.value.viewerRelationship)

            vm.effects.test {
                vm.handleEvent(ProfileEvent.FollowTapped)
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
            assertEquals(0, repo.followCalls.get(), "FollowTapped on Self MUST NOT call follow")
            assertEquals(0, repo.unfollowCalls.get(), "FollowTapped on Self MUST NOT call unfollow")
            assertEquals(ViewerRelationship.Self, vm.uiState.value.viewerRelationship)
        }

    @Test
    fun `TabSelected does not re-fetch when target tab is already Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
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
                    headerWithViewerResult =
                        Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
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
                    headerWithViewerResult =
                        Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
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

    @Test
    fun `LoadMore on Replies does not touch Posts or Media cursors`() =
        runTest(mainDispatcher.dispatcher) {
            // All three tabs Loaded with a non-null cursor so LoadMore is legal on each.
            val pagedPage = ProfileTabPage(items = persistentListOf(), nextCursor = "next-cursor")
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
                    tabResults = ProfileTab.entries.associateWith { Result.success(pagedPage) },
                )
            val vm = newVm(repo = repo)
            advanceUntilIdle()
            val priorPostsCalls = repo.tabCalls[ProfileTab.Posts]!!.get()
            val priorMediaCalls = repo.tabCalls[ProfileTab.Media]!!.get()

            vm.handleEvent(ProfileEvent.LoadMore(ProfileTab.Replies))
            advanceUntilIdle()

            assertEquals(
                priorPostsCalls,
                repo.tabCalls[ProfileTab.Posts]!!.get(),
                "Replies LoadMore MUST NOT issue a Posts fetch",
            )
            assertEquals(
                priorMediaCalls,
                repo.tabCalls[ProfileTab.Media]!!.get(),
                "Replies LoadMore MUST NOT issue a Media fetch",
            )
            assertEquals(
                "next-cursor",
                repo.lastTabCursor[ProfileTab.Replies],
                "Replies LoadMore MUST pass the Replies cursor",
            )
            assertNull(
                repo.lastTabCursor[ProfileTab.Posts],
                "Replies LoadMore MUST NOT touch Posts cursor",
            )
            assertNull(
                repo.lastTabCursor[ProfileTab.Media],
                "Replies LoadMore MUST NOT touch Media cursor",
            )
        }

    @Test
    fun `Media tab PostTapped emits NavigateToPost effect with the tapped postUri`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo)
            advanceUntilIdle()

            vm.handleEvent(ProfileEvent.TabSelected(ProfileTab.Media))
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.PostTapped("at://did:plc:alice/post/abc"))
                val effect = awaitItem()
                assertEquals(
                    ProfileEffect.NavigateToPost("at://did:plc:alice/post/abc"),
                    effect,
                    "Media-tab PostTapped MUST emit the same NavigateToPost effect shape as Posts-tab PostTapped",
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `OnImageTapped emits NavigateToMediaViewer with the tapped post's URI and image index`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(repo = FakeProfileRepository())
            advanceUntilIdle()
            val post = samplePostUi(id = "at://did:plc:alice/post/imgs", cid = "bafyI")

            vm.effects.test {
                vm.handleEvent(ProfileEvent.OnImageTapped(post = post, imageIndex = 2))
                val effect = awaitItem()
                assertEquals(
                    ProfileEffect.NavigateToMediaViewer(postUri = post.id, imageIndex = 2),
                    effect,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `OnQuotedPostTapped emits NavigateToPost with the quoted post's URI`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(repo = FakeProfileRepository())
            advanceUntilIdle()
            val quotedUri = "at://did:plc:other/app.bsky.feed.post/q1"

            vm.effects.test {
                vm.handleEvent(ProfileEvent.OnQuotedPostTapped(quotedUri))
                val effect = awaitItem()
                assertEquals(ProfileEffect.NavigateToPost(quotedUri), effect)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `OnMediaCellTapped emits NavigateToMediaViewer at imageIndex 0`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(repo = FakeProfileRepository())
            advanceUntilIdle()

            vm.effects.test {
                // Media cells in the grid render one thumb per post (the
                // post's first image). Tapping the cell opens the carousel
                // at index 0 — the same image the thumb is showing.
                vm.handleEvent(ProfileEvent.OnMediaCellTapped(postUri = "at://did:plc:alice/post/m1"))
                val effect = awaitItem()
                assertEquals(
                    ProfileEffect.NavigateToMediaViewer(postUri = "at://did:plc:alice/post/m1", imageIndex = 0),
                    effect,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `OnMediaCellTapped with isVideo emits NavigateToVideoPlayer instead of MediaViewer`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(repo = FakeProfileRepository())
            advanceUntilIdle()

            vm.effects.test {
                // Video cells in the grid route around MediaViewer (which
                // would dead-end on "post has no images") and into the
                // fullscreen video player route.
                vm.handleEvent(
                    ProfileEvent.OnMediaCellTapped(
                        postUri = "at://did:plc:alice/post/v1",
                        isVideo = true,
                    ),
                )
                val effect = awaitItem()
                assertEquals(
                    ProfileEffect.NavigateToVideoPlayer(postUri = "at://did:plc:alice/post/v1"),
                    effect,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `OnVideoTapped emits NavigateToVideoPlayer with the same URI`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = newVm(repo = FakeProfileRepository())
            advanceUntilIdle()

            vm.effects.test {
                // Posts/Replies tab in-card video taps route to the
                // fullscreen player on the outer NavDisplay, bypassing
                // the PostDetail detour an outer-card tap takes.
                vm.handleEvent(ProfileEvent.OnVideoTapped(postUri = "at://did:plc:alice/post/v1"))
                val effect = awaitItem()
                assertEquals(
                    ProfileEffect.NavigateToVideoPlayer(postUri = "at://did:plc:alice/post/v1"),
                    effect,
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `StubActionTapped emits ShowComingSoon with the same action value, never touches the repo`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()
            val priorHeaderCalls = repo.headerCalls.get()
            val priorPostsCalls = repo.tabCalls[ProfileTab.Posts]!!.get()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.StubActionTapped(StubbedAction.Block))
                assertEquals(ProfileEffect.ShowComingSoon(StubbedAction.Block), awaitItem())
                vm.handleEvent(ProfileEvent.StubActionTapped(StubbedAction.Mute))
                assertEquals(ProfileEffect.ShowComingSoon(StubbedAction.Mute), awaitItem())
                vm.handleEvent(ProfileEvent.StubActionTapped(StubbedAction.Report))
                assertEquals(ProfileEffect.ShowComingSoon(StubbedAction.Report), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }

            assertEquals(
                priorHeaderCalls,
                repo.headerCalls.get(),
                "StubActionTapped MUST NOT issue a repository call",
            )
            assertEquals(
                priorPostsCalls,
                repo.tabCalls[ProfileTab.Posts]!!.get(),
                "StubActionTapped MUST NOT issue a tab fetch",
            )
        }

    @Test
    fun `MessageTapped on other-user profile emits NavigateToMessage with the header DID`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                SAMPLE_HEADER.copy(
                                    did = "did:plc:bob",
                                    handle = "bob.bsky.social",
                                ),
                                ViewerRelationship.None,
                            ),
                        ),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.MessageTapped)
                assertEquals(
                    ProfileEffect.NavigateToMessage(otherUserDid = "did:plc:bob"),
                    awaitItem(),
                )
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `MessageTapped on own profile is a silent no-op (defensive — UI gates the button)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            // route.handle = null → ownProfile = true
            val vm = newVm(repo = repo, route = Profile(handle = null))
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.MessageTapped)
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `MessageTapped before header loads is a silent no-op`() =
        runTest(mainDispatcher.dispatcher) {
            // Header failure → state.header stays null, but ownProfile is false
            // (other-user route). MessageTapped must still no-op because the
            // VM has no DID to route on.
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult = Result.failure(IOException("boom")),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            vm.effects.test {
                // Drain the header-failure ShowError that init's launchHeaderLoad emits.
                awaitItem()
                vm.handleEvent(ProfileEvent.MessageTapped)
                advanceUntilIdle()
                expectNoEvents()
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `own-profile header load overrides mapper-reported viewerRelationship to Self`() =
        runTest(mainDispatcher.dispatcher) {
            // Mapper reports NotFollowing (own user has no follow record pointing
            // at themselves). The VM MUST override this to Self for the own-profile
            // route.
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                header = SAMPLE_HEADER,
                                viewerRelationship = ViewerRelationship.NotFollowing(),
                            ),
                        ),
                )
            val vm = newVm(repo = repo, route = Profile(handle = null))
            advanceUntilIdle()

            assertEquals(
                ViewerRelationship.Self,
                vm.uiState.value.viewerRelationship,
                "Own-profile MUST override mapper-reported relationship to Self",
            )
        }

    @Test
    fun `other-user header load preserves mapper-reported viewerRelationship`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(
                                header = SAMPLE_HEADER.copy(handle = "bob.bsky.social"),
                                viewerRelationship = SAMPLE_FOLLOWING,
                            ),
                        ),
                )
            val vm = newVm(repo = repo, route = Profile(handle = "bob.bsky.social"))
            advanceUntilIdle()

            assertEquals(
                SAMPLE_FOLLOWING,
                vm.uiState.value.viewerRelationship,
                "Other-user route MUST preserve mapper-reported relationship",
            )
        }

    @Test
    fun `OnLikeClicked dispatches cache toggleLike with post id and cid`() =
        runTest(mainDispatcher.dispatcher) {
            val cache = FakePostInteractionsCache()
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None),
                        ),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, postInteractionsCache = cache)
            advanceUntilIdle()
            val post = samplePostUi(id = "at://post-p", cid = "bafyP")

            vm.handleEvent(ProfileEvent.OnLikeClicked(post))
            advanceUntilIdle()

            assertEquals(1, cache.toggleLikeCalls.get())
            assertEquals("at://post-p" to "bafyP", cache.lastToggleLikeArgs.last())
        }

    @Test
    fun `OnLikeClicked failure surfaces ProfileEffect_ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val cache =
                FakePostInteractionsCache().apply {
                    nextToggleLikeResult = Result.failure(java.io.IOException("net down"))
                }
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None),
                        ),
                    tabResults = ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
                )
            val vm = newVm(repo = repo, postInteractionsCache = cache)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(ProfileEvent.OnLikeClicked(samplePostUi(id = "at://x", cid = "bafyX")))
                advanceUntilIdle()
                assertTrue(awaitItem() is ProfileEffect.ShowError)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun `cache emission projects onto the active tab's items`() =
        runTest(mainDispatcher.dispatcher) {
            val postsPage =
                ProfileTabPage(
                    items =
                        persistentListOf(
                            TabItemUi.Post(samplePostUi(id = "at://post-A", cid = "bafyA")),
                        ),
                    nextCursor = null,
                )
            val cache = FakePostInteractionsCache()
            val repo =
                FakeProfileRepository(
                    headerWithViewerResult =
                        Result.success(
                            ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None),
                        ),
                    tabResults =
                        mapOf(
                            ProfileTab.Posts to Result.success(postsPage),
                            ProfileTab.Replies to Result.success(EMPTY_PAGE),
                            ProfileTab.Media to Result.success(EMPTY_PAGE),
                        ),
                )
            val vm = newVm(repo = repo, postInteractionsCache = cache)
            advanceUntilIdle()

            cache.emit(
                kotlinx.collections.immutable.persistentMapOf(
                    "at://post-A" to
                        PostInteractionState(
                            viewerLikeUri = "at://did:plc:viewer/app.bsky.feed.like/test",
                            likeCount = 42,
                        ),
                ),
            )
            advanceUntilIdle()

            val merged =
                (vm.uiState.value.postsStatus as TabLoadStatus.Loaded)
                    .items
                    .filterIsInstance<TabItemUi.Post>()
                    .first { it.post.id == "at://post-A" }
            assertTrue(merged.post.viewer.isLikedByViewer)
            assertEquals(42, merged.post.stats.likeCount)
        }

    // -- Test helpers ----------------------------------------------------------

    private fun samplePostUi(
        id: String,
        cid: String = "bafyreifakefakefakefakefakefakefakefakefakefake",
    ): PostUi =
        PostUi(
            id = id,
            cid = cid,
            author =
                AuthorUi(
                    did = "did:plc:fake",
                    handle = "fake.bsky.social",
                    displayName = "Fake",
                    avatarUrl = null,
                ),
            createdAt = Instant.parse("2026-04-25T12:00:00Z"),
            text = "fake text $id",
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(),
            viewer = ViewerStateUi(),
            repostedBy = null,
        )

    private fun newVm(
        repo: ProfileRepository,
        route: Profile = Profile(handle = null),
        sessionState: SessionState =
            SessionState.SignedIn(handle = "viewer.bsky.social", did = "did:plc:viewer123"),
        postInteractionsCache: PostInteractionsCache = FakePostInteractionsCache(),
    ): ProfileViewModel {
        val sessionProvider =
            mockk<SessionStateProvider>(relaxed = true).also {
                every { it.state } returns MutableStateFlow(sessionState)
            }
        return ProfileViewModel(
            route = route,
            repository = repo,
            sessionStateProvider = sessionProvider,
            postInteractionsCache = postInteractionsCache,
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
        const val SAMPLE_FOLLOW_URI = "at://did:plc:viewer123/app.bsky.graph.follow/sample-rkey"
        val SAMPLE_FOLLOWING = ViewerRelationship.Following(followUri = SAMPLE_FOLLOW_URI)
    }

    /**
     * In-memory [ProfileRepository] for VM tests. Tracks call counts +
     * cursor history per tab so the test assertions can introspect
     * what the VM actually requested.
     */
    private class FakeProfileRepository(
        private val headerWithViewerResult: Result<ProfileHeaderWithViewer> =
            Result.success(ProfileHeaderWithViewer(SAMPLE_HEADER, ViewerRelationship.None)),
        var tabResults: Map<ProfileTab, Result<ProfileTabPage>> =
            ProfileTab.entries.associateWith { Result.success(EMPTY_PAGE) },
        private val followResult: Result<String> = Result.success(SAMPLE_FOLLOW_URI),
        private val unfollowResult: Result<Unit> = Result.success(Unit),
    ) : ProfileRepository {
        val headerCalls = AtomicInteger(0)
        val tabCalls: Map<ProfileTab, AtomicInteger> =
            ProfileTab.entries.associateWith { AtomicInteger(0) }
        val lastTabCursor: MutableMap<ProfileTab, String?> = mutableMapOf()

        val followCalls = AtomicInteger(0)
        val unfollowCalls = AtomicInteger(0)
        var lastFollowSubject: String? = null
        var lastUnfollowUri: String? = null

        // Gates let pending-state tests synchronize the optimistic-flip
        // observation with the wire commit. Set the gate before issuing
        // FollowTapped, observe the pending state, then `release*` to
        // let the suspended call complete.
        var gateFollow: Boolean = false
        var gateUnfollow: Boolean = false
        private var followGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
        private var unfollowGate: kotlinx.coroutines.CompletableDeferred<Unit>? = null

        fun releaseFollow() {
            followGate?.complete(Unit)
            followGate = null
            gateFollow = false
        }

        fun releaseUnfollow() {
            unfollowGate?.complete(Unit)
            unfollowGate = null
            gateUnfollow = false
        }

        override suspend fun fetchHeader(actor: String): Result<ProfileHeaderWithViewer> {
            headerCalls.incrementAndGet()
            return headerWithViewerResult
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

        override suspend fun follow(subjectDid: String): Result<String> {
            followCalls.incrementAndGet()
            lastFollowSubject = subjectDid
            if (gateFollow) {
                val gate = kotlinx.coroutines.CompletableDeferred<Unit>().also { followGate = it }
                gate.await()
            }
            return followResult
        }

        override suspend fun unfollow(followUri: String): Result<Unit> {
            unfollowCalls.incrementAndGet()
            lastUnfollowUri = followUri
            if (gateUnfollow) {
                val gate = kotlinx.coroutines.CompletableDeferred<Unit>().also { unfollowGate = it }
                gate.await()
            }
            return unfollowResult
        }
    }
}
