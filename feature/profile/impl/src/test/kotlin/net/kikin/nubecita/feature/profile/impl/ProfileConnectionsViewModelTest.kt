package net.kikin.nubecita.feature.profile.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.ActorAction
import net.kikin.nubecita.core.analytics.InteractActor
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.profile.api.ProfileConnections
import net.kikin.nubecita.feature.profile.api.ProfileConnectionsTab
import net.kikin.nubecita.feature.profile.impl.data.ConnectionFollowStore
import net.kikin.nubecita.feature.profile.impl.data.ConnectionPage
import net.kikin.nubecita.feature.profile.impl.data.ConnectionRowUi
import net.kikin.nubecita.feature.profile.impl.data.ProfileConnectionsRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@OptIn(ExperimentalCoroutinesApi::class)
internal class ProfileConnectionsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val follows = FakeFollowRepository()
    private val analytics = RecordingAnalyticsClient()
    private val followStore = ConnectionFollowStore()

    private fun vm(initialTab: ProfileConnectionsTab = ProfileConnectionsTab.Followers) =
        ProfileConnectionsViewModel(
            route =
                ProfileConnections(
                    actor = "did:plc:subject",
                    initialTab = initialTab,
                    title = "Subject",
                    followersCount = 5,
                    followsCount = 3,
                ),
            repository = EmptyRepository,
            followRepository = follows,
            analytics = analytics,
            followStore = followStore,
        )

    private fun row(
        did: String = "did:plc:alice",
        followUri: String? = null,
        handle: String = "alice.test",
    ) = ConnectionRowUi(
        actor = ActorUi(did = did, handle = handle, displayName = null, avatarUrl = null),
        followUri = followUri,
        canFollow = true,
    )

    private fun ProfileConnectionsViewModel.display(row: ConnectionRowUi) = row.followDisplay(uiState.value.overlay[row.actor.did])

    @Test
    fun `opens on the route's tab`() {
        assertEquals(ProfileConnectionsTab.Following, vm(ProfileConnectionsTab.Following).uiState.value.selectedTab)
    }

    @Test
    fun `selecting a tab switches to it`() {
        val vm = vm()

        vm.handleEvent(ProfileConnectionsEvent.TabSelected(ProfileConnectionsTab.Following))

        assertEquals(ProfileConnectionsTab.Following, vm.uiState.value.selectedTab)
    }

    @Test
    fun `following a row updates it immediately and commits the uri`() =
        runTest(mainDispatcher.dispatcher) {
            follows.gate = CompletableDeferred()
            val vm = vm()
            val row = row()

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            runCurrent()

            assertEquals(ConnectionFollowDisplay(following = true, pending = true), vm.display(row))

            follows.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(ConnectionFollowDisplay(following = true, pending = false), vm.display(row))
            assertEquals(listOf("did:plc:alice"), follows.followed)
            assertEquals(listOf(InteractActor(ActorAction.Follow, PostSurface.ProfileConnections)), analytics.events)
        }

    @Test
    fun `a failed follow restores the row and shows one error`() =
        runTest(mainDispatcher.dispatcher) {
            follows.followResult = Result.failure(RuntimeException("boom"))
            val vm = vm()
            val row = row()

            vm.effects.test {
                vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
                advanceUntilIdle()

                assertEquals(ProfileConnectionsEffect.ShowError, awaitItem())
                expectNoEvents()
            }
            assertEquals(ConnectionFollowDisplay(following = false, pending = false), vm.display(row))
        }

    @Test
    fun `a failed unfollow restores the row and shows one error`() =
        runTest(mainDispatcher.dispatcher) {
            follows.unfollowResult = Result.failure(RuntimeException("boom"))
            val vm = vm()
            val row = row(followUri = URI)

            vm.effects.test {
                vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
                advanceUntilIdle()

                assertEquals(ProfileConnectionsEffect.ShowError, awaitItem())
                expectNoEvents()
            }
            assertEquals(ConnectionFollowDisplay(following = true, pending = false), vm.display(row))
        }

    @Test
    fun `unfollow uses the row's uri`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            val row = row(followUri = URI)

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            advanceUntilIdle()

            assertEquals(listOf(URI), follows.unfollowed)
            assertEquals(ConnectionFollowDisplay(following = false, pending = false), vm.display(row))
            assertEquals(listOf(InteractActor(ActorAction.Unfollow, PostSurface.ProfileConnections)), analytics.events)
        }

    @Test
    fun `a follow committed here can be unfollowed`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            val row = row()

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            advanceUntilIdle()
            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            advanceUntilIdle()

            assertEquals(listOf(FakeFollowRepository.CREATED_URI), follows.unfollowed)
            assertFalse(vm.display(row).following)
        }

    @Test
    fun `an unfollow committed here can be followed again`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            val row = row(followUri = URI)

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            advanceUntilIdle()
            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            advanceUntilIdle()

            assertEquals(listOf("did:plc:alice"), follows.followed)
            assertTrue(vm.display(row).following)
        }

    @Test
    fun `a second tap while pending sends nothing`() =
        runTest(mainDispatcher.dispatcher) {
            follows.gate = CompletableDeferred()
            val vm = vm()
            val row = row()

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            runCurrent()
            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            follows.gate?.complete(Unit)
            advanceUntilIdle()

            assertEquals(listOf("did:plc:alice"), follows.followed)
            assertTrue(follows.unfollowed.isEmpty(), "must not unfollow while the follow has no uri")
        }

    @Test
    fun `the overlay outranks a page reporting old state`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            val row = row()

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            advanceUntilIdle()

            // A reload during AppView lag: the same account, still reported as not followed.
            assertTrue(vm.display(row(followUri = null)).following)
        }

    @Test
    fun `one overlay entry covers both tabs`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row()))
            advanceUntilIdle()
            vm.handleEvent(ProfileConnectionsEvent.TabSelected(ProfileConnectionsTab.Following))

            assertTrue(vm.display(row()).following)
        }

    /** A cached page says not followed, so a tap must unfollow, not follow again. */
    @Test
    fun `a follow made on the profile shows on the row and can be unfollowed`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            runCurrent()
            val row = row()

            followStore.record("did:plc:alice", URI)
            runCurrent()

            assertTrue(vm.display(row).following)

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            advanceUntilIdle()

            assertTrue(follows.followed.isEmpty(), "a second follow record must not be created")
            assertEquals(listOf(URI), follows.unfollowed)
        }

    @Test
    fun `an unfollow made on the profile shows on the row`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()
            runCurrent()
            val row = row(followUri = URI)

            followStore.record("did:plc:alice", null)
            runCurrent()

            assertFalse(vm.display(row).following)
        }

    @Test
    fun `changes from before the screen opened still apply`() =
        runTest(mainDispatcher.dispatcher) {
            followStore.record("did:plc:alice", URI)

            val vm = vm()
            runCurrent()

            assertTrue(vm.display(row()).following)
        }

    @Test
    fun `a committed follow here is a positive moment`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()

            vm.effects.test {
                vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row()))
                advanceUntilIdle()
                assertEquals(ProfileConnectionsEffect.FollowCommitted, awaitItem())
            }
        }

    @Test
    fun `an unfollow here is not a positive moment`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()

            vm.effects.test {
                vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row(followUri = URI)))
                advanceUntilIdle()
                expectNoEvents()
            }
        }

    @Test
    fun `a follow made here reaches the shared store`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row()))
            advanceUntilIdle()

            assertEquals(FakeFollowRepository.CREATED_URI, followStore.committed.value["did:plc:alice"])
        }

    @Test
    fun `an unfollow made here reaches the shared store`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row(followUri = URI)))
            advanceUntilIdle()

            assertTrue(followStore.committed.value.containsKey("did:plc:alice"))
            assertEquals(null, followStore.committed.value["did:plc:alice"])
        }

    @Test
    fun `a pending write isn't overwritten by the shared store`() =
        runTest(mainDispatcher.dispatcher) {
            follows.gate = CompletableDeferred()
            val vm = vm()
            runCurrent()
            val row = row()

            vm.handleEvent(ProfileConnectionsEvent.FollowTapped(row))
            runCurrent()
            followStore.record("did:plc:other", URI)
            followStore.record("did:plc:alice", null)
            runCurrent()

            assertEquals(ConnectionFollowDisplay(following = true, pending = true), vm.display(row))
            follows.gate?.complete(Unit)
            advanceUntilIdle()
        }

    @Test
    fun `a row tap emits NavigateToProfile`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()

            vm.effects.test {
                vm.handleEvent(ProfileConnectionsEvent.RowTapped(row()))

                assertEquals(ProfileConnectionsEffect.NavigateToProfile("alice.test"), awaitItem())
            }
        }

    @Test
    fun `a deleted account's row goes nowhere`() =
        runTest(mainDispatcher.dispatcher) {
            val vm = vm()

            vm.effects.test {
                vm.handleEvent(ProfileConnectionsEvent.RowTapped(row(handle = "missing.invalid")))

                expectNoEvents()
            }
        }

    private object EmptyRepository : ProfileConnectionsRepository {
        override suspend fun getFollowers(
            actor: String,
            cursor: String?,
        ) = Result.success(ConnectionPage(persistentListOf(), null))

        override suspend fun getFollows(
            actor: String,
            cursor: String?,
        ) = Result.success(ConnectionPage(persistentListOf(), null))
    }

    private class FakeFollowRepository : FollowRepository {
        val followed = mutableListOf<String>()
        val unfollowed = mutableListOf<String>()
        var followResult: Result<String> = Result.success(CREATED_URI)
        var unfollowResult: Result<Unit> = Result.success(Unit)
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun follow(subjectDid: String): Result<String> {
            followed += subjectDid
            gate?.await()
            return followResult
        }

        override suspend fun unfollow(followUri: String): Result<Unit> {
            unfollowed += followUri
            gate?.await()
            return unfollowResult
        }

        companion object {
            const val CREATED_URI = "at://did:plc:me/app.bsky.graph.follow/new"
        }
    }

    private companion object {
        const val URI = "at://did:plc:me/app.bsky.graph.follow/old"
    }
}
