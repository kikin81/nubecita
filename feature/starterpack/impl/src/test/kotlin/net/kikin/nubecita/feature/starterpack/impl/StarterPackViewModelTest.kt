package net.kikin.nubecita.feature.starterpack.impl

import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.postinteractions.FollowOverlay
import net.kikin.nubecita.core.postinteractions.FollowOverride
import net.kikin.nubecita.core.postinteractions.FollowToggle
import net.kikin.nubecita.feature.starterpack.api.StarterPack
import net.kikin.nubecita.feature.starterpack.impl.data.FakeStarterPackRepository
import net.kikin.nubecita.feature.starterpack.impl.data.LIST_URI
import net.kikin.nubecita.feature.starterpack.impl.data.PACK_URI
import net.kikin.nubecita.feature.starterpack.impl.data.PackFeedUi
import net.kikin.nubecita.feature.starterpack.impl.data.detailFixture
import net.kikin.nubecita.feature.starterpack.impl.data.memberFixture
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class StarterPackViewModelTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private val repo = FakeStarterPackRepository()
    private val followRepo = FakeFollowRepository()
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "me.bsky.social", did = "did:plc:me"))
    private val sessionProvider =
        object : SessionStateProvider {
            override val state = session

            override suspend fun refresh() = Unit
        }
    private val overlay = FollowOverlay(CoroutineScope(SupervisorJob() + testDispatcher), sessionProvider)
    private val toggle = FollowToggle(followRepo, overlay, sessionProvider, CoroutineScope(SupervisorJob() + testDispatcher))

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newVm(uri: String = PACK_URI) = StarterPackViewModel(StarterPack(uri), repo, toggle, overlay, sessionProvider)

    private fun StarterPackViewModel.loaded() = uiState.value.status as StarterPackLoadStatus.Loaded

    @Test
    fun `loads header and first member page`() =
        runTest {
            val feed = PackFeedUi("at://did:plc:f/app.bsky.feed.generator/f", "Android feed", "feeder.bsky.social", null)
            repo.respondPack(PACK_URI, detailFixture(feeds = listOf(feed)))
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a"), memberFixture("did:plc:b")), nextCursor = "c2")

            val vm = newVm()
            runCurrent()

            val status = vm.loaded()
            assertEquals("Android developers", status.pack.name)
            assertEquals("creator.bsky.social", status.pack.creatorHandle)
            assertEquals("People building Android.", status.pack.description)
            assertEquals(listOf(feed), status.pack.feeds.toList())
            assertEquals(listOf("did:plc:a", "did:plc:b"), status.members.map { it.did })
            assertEquals("c2", status.nextCursor)
            assertFalse(status.endReached)
            assertFalse(status.isAppending)
            assertEquals(listOf(PACK_URI), repo.packCalls)
            assertEquals(listOf(LIST_URI to null), repo.memberCalls)
        }

    @Test
    fun `appends the next page`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a")), nextCursor = "c2")
            repo.respondMembers(LIST_URI, "c2", listOf(memberFixture("did:plc:b")), nextCursor = null)
            val vm = newVm()
            runCurrent()

            vm.handleEvent(StarterPackEvent.LoadMore)
            runCurrent()

            val status = vm.loaded()
            assertEquals(listOf("did:plc:a", "did:plc:b"), status.members.map { it.did })
            assertTrue(status.endReached)
            assertNull(status.nextCursor)
            assertFalse(status.isAppending)
        }

    @Test
    fun `load more is a no-op while a page is loading and once the end is reached`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a")), nextCursor = "c2")
            val gate = repo.gateMembers(LIST_URI, "c2")
            val vm = newVm()
            runCurrent()

            vm.handleEvent(StarterPackEvent.LoadMore)
            vm.handleEvent(StarterPackEvent.LoadMore)
            runCurrent()

            assertTrue(vm.loaded().isAppending)
            assertEquals(1, repo.memberCalls.count { it.second == "c2" }, "a second call must not start while one is in flight")

            gate.complete(
                Result.success(
                    net.kikin.nubecita.feature.starterpack.impl.data
                        .PackMembersPage(kotlinx.collections.immutable.persistentListOf(), null),
                ),
            )
            runCurrent()
            vm.handleEvent(StarterPackEvent.LoadMore)
            runCurrent()

            assertEquals(1, repo.memberCalls.count { it.second == "c2" }, "end reached: no further call")
        }

    @Test
    fun `an append failure keeps the members and emits a snackbar effect`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a")), nextCursor = "c2")
            repo.failMembers(LIST_URI, "c2", IOException("down"))
            val vm = newVm()
            runCurrent()

            vm.effects.test {
                vm.handleEvent(StarterPackEvent.LoadMore)
                runCurrent()
                assertEquals(StarterPackEffect.ShowAppendError(StarterPackError.Network), awaitItem())
            }

            val status = vm.loaded()
            assertEquals(listOf("did:plc:a"), status.members.map { it.did })
            assertFalse(status.isAppending)
            assertEquals("c2", status.nextCursor)
        }

    @Test
    fun `an invalid uri shows the error state and retry loads again`() =
        runTest {
            val bad = "not-an-at-uri"
            repo.failPack(bad, IllegalArgumentException("invalid uri"))
            val vm = newVm(bad)
            runCurrent()

            val status = vm.uiState.value.status
            assertTrue(status is StarterPackLoadStatus.InitialError, "expected InitialError, was $status")
            assertEquals(StarterPackError.Unknown, (status as StarterPackLoadStatus.InitialError).error)

            repo.clearPack(bad)
            repo.respondPack(bad, detailFixture(uri = bad))
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a")))
            vm.handleEvent(StarterPackEvent.Retry)
            runCurrent()

            assertEquals(listOf("did:plc:a"), vm.loaded().members.map { it.did })
            assertEquals(2, repo.packCalls.size)
        }

    @Test
    fun `a network failure on the pack is a network error`() =
        runTest {
            repo.failPack(PACK_URI, IOException("down"))
            val vm = newVm()
            runCurrent()

            assertEquals(StarterPackLoadStatus.InitialError(StarterPackError.Network), vm.uiState.value.status)
        }

    @Test
    fun `a failure on the first member page is an initial error`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.failMembers(LIST_URI, null, IOException("down"))
            val vm = newVm()
            runCurrent()

            assertEquals(StarterPackLoadStatus.InitialError(StarterPackError.Network), vm.uiState.value.status)
        }

    @Test
    fun `a pack with no members is empty and keeps its header`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture(memberCount = 0))
            repo.respondMembers(LIST_URI, null, emptyList())
            val vm = newVm()
            runCurrent()

            val status = vm.uiState.value.status
            assertTrue(status is StarterPackLoadStatus.Empty, "expected Empty, was $status")
            assertEquals("Android developers", (status as StarterPackLoadStatus.Empty).pack.name)
        }

    @Test
    fun `a pack without a member list is empty without asking for members`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture(listUri = null))
            val vm = newVm()
            runCurrent()

            assertTrue(vm.uiState.value.status is StarterPackLoadStatus.Empty)
            assertTrue(repo.memberCalls.isEmpty())
        }

    @Test
    fun `follow is optimistic and rolls back`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a")))
            val gate = kotlinx.coroutines.CompletableDeferred<Result<String>>()
            val pending = SuspendingFollowRepository(gate)
            val vm = StarterPackViewModel(StarterPack(PACK_URI), repo, FollowToggle(pending, overlay, sessionProvider, CoroutineScope(SupervisorJob() + testDispatcher)), overlay, sessionProvider)
            runCurrent()

            vm.effects.test {
                vm.handleEvent(StarterPackEvent.FollowTapped("did:plc:a"))
                runCurrent()
                // Optimistic: shown as followed before the call returns.
                assertTrue(
                    vm
                        .loaded()
                        .members
                        .single()
                        .isFollowing,
                )

                gate.complete(Result.failure(IOException("down")))
                runCurrent()

                assertEquals(StarterPackEffect.FollowFailed(wasUnfollow = false), awaitItem())
            }
            assertFalse(
                vm
                    .loaded()
                    .members
                    .single()
                    .isFollowing,
                "rolled back",
            )
        }

    @Test
    fun `a successful follow keeps the new follow record`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a")))
            val vm = newVm()
            runCurrent()

            vm.handleEvent(StarterPackEvent.FollowTapped("did:plc:a"))
            runCurrent()

            val member = vm.loaded().members.single()
            assertTrue(member.isFollowing)
            assertEquals("at://did:plc:me/app.bsky.graph.follow/new", member.followUri)
            assertEquals(listOf("did:plc:a"), followRepo.followCallDids)
        }

    @Test
    fun `the signed-in account's member row has no follow action`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:me"), memberFixture("did:plc:a")))
            val vm = newVm()
            runCurrent()

            assertEquals("did:plc:me", vm.uiState.value.viewerDid)

            vm.handleEvent(StarterPackEvent.FollowTapped("did:plc:me"))
            runCurrent()

            assertTrue(followRepo.followCallDids.isEmpty(), "following yourself must not reach the repository")
            assertFalse(
                vm
                    .loaded()
                    .members
                    .first { it.did == "did:plc:me" }
                    .isFollowing,
            )
        }

    @Test
    fun `a follow made here is visible through the shared overlay`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a")))
            val vm = newVm()
            runCurrent()

            vm.handleEvent(StarterPackEvent.FollowTapped("did:plc:a"))
            runCurrent()

            assertEquals(FollowOverride(isFollowing = true, followUri = "at://did:plc:me/app.bsky.graph.follow/new"), overlay["did:plc:a"])
        }

    @Test
    fun `a follow made elsewhere shows on the rows, including pages that arrive later`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a")), nextCursor = "c2")
            repo.respondMembers(LIST_URI, "c2", listOf(memberFixture("did:plc:b")))
            val vm = newVm()
            runCurrent()

            overlay.write("did:plc:b", FollowOverride(true, "at://follow/b"), account = "did:plc:me")
            overlay.write("did:plc:a", FollowOverride(true, "at://follow/a"), account = "did:plc:me")
            runCurrent()
            assertTrue(
                vm
                    .loaded()
                    .members
                    .single()
                    .isFollowing,
            )

            vm.handleEvent(StarterPackEvent.LoadMore)
            runCurrent()

            assertTrue(vm.loaded().members.all { it.isFollowing })
        }

    @Test
    fun `tapping a member opens their profile and tapping a feed opens it`() =
        runTest {
            repo.respondPack(PACK_URI, detailFixture())
            repo.respondMembers(LIST_URI, null, listOf(memberFixture("did:plc:a")))
            val vm = newVm()
            runCurrent()

            vm.effects.test {
                vm.handleEvent(StarterPackEvent.MemberTapped("a.bsky.social"))
                assertEquals(StarterPackEffect.NavigateToProfile("a.bsky.social"), awaitItem())
                vm.handleEvent(StarterPackEvent.FeedTapped("at://f", "Feed"))
                assertEquals(StarterPackEffect.NavigateToFeed("at://f", "Feed"), awaitItem())
            }
        }
}
