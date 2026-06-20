package net.kikin.nubecita.feature.chats.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.feature.chats.api.GroupDetails
import net.kikin.nubecita.feature.chats.impl.data.ChatConvo
import net.kikin.nubecita.feature.chats.impl.data.MemberPage
import net.kikin.nubecita.feature.profile.api.Profile
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
internal class GroupDetailsViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val convoId = "g1"

    @Test
    fun `load populates name, members and count from a group convo`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("Trip planning")
            repo.getConvoMembersResult =
                Result.success(
                    MemberPage(
                        members =
                            persistentListOf(
                                member("did:plc:a", "alice", FollowState.Following, "at://f/a"),
                                member("did:plc:b", "bob", FollowState.NotFollowing),
                            ),
                    ),
                )
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals("Trip planning", state.name)
            val loaded = state.status as GroupDetailsLoadStatus.Loaded
            assertEquals(2, loaded.members.size)
            assertEquals(2, loaded.memberCount)
            assertEquals(convoId, repo.lastGetConvoMembersConvoId)
        }

    @Test
    fun `load on a non-group convo ends in InitialError ConvoNotFound`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult =
                Result.success(
                    ChatConvo(
                        convoId = convoId,
                        header =
                            ChatHeader.Direct(
                                did = "did:plc:alice",
                                handle = "alice.bsky.social",
                                displayName = "Alice",
                                avatarUrl = null,
                            ),
                        canPost = true,
                    ),
                )
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()

            val status = vm.uiState.value.status
            assertEquals(GroupDetailsLoadStatus.InitialError(ChatError.ConvoNotFound), status)
            assertEquals(0, repo.getConvoMembersCalls.get(), "members not fetched for a direct convo")
        }

    @Test
    fun `getConvo failure ends in InitialError mapped via toChatError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = Result.failure(IOException("offline"))
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()

            assertEquals(
                GroupDetailsLoadStatus.InitialError(ChatError.Network),
                vm.uiState.value.status,
            )
        }

    @Test
    fun `ToggleFollow on a NotFollowing member calls follow and reconciles to Following`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.getConvoMembersResult =
                Result.success(
                    MemberPage(members = persistentListOf(member("did:plc:b", "bob", FollowState.NotFollowing))),
                )
            val follow = FakeFollowRepository(followResult = Result.success("at://follow/b"))
            val vm = groupDetailsViewModel(repo, follow)
            advanceUntilIdle()

            vm.handleEvent(GroupDetailsEvent.ToggleFollow("did:plc:b"))
            advanceUntilIdle()

            assertEquals(listOf("did:plc:b"), follow.followCalls)
            val m = vm.memberFor("did:plc:b")
            assertEquals(FollowState.Following, m.followState)
            assertEquals("at://follow/b", m.followUri)
        }

    @Test
    fun `ToggleFollow shows InFlight optimistically while the call is gated`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.getConvoMembersResult =
                Result.success(
                    MemberPage(members = persistentListOf(member("did:plc:b", "bob", FollowState.NotFollowing))),
                )
            val follow = FakeFollowRepository()
            follow.gate = CompletableDeferred()
            val vm = groupDetailsViewModel(repo, follow)
            advanceUntilIdle()

            vm.handleEvent(GroupDetailsEvent.ToggleFollow("did:plc:b"))
            advanceUntilIdle()
            assertEquals(FollowState.InFlight, vm.memberFor("did:plc:b").followState)

            follow.gate!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(FollowState.Following, vm.memberFor("did:plc:b").followState)
        }

    @Test
    fun `ToggleFollow on a Following member calls unfollow and reconciles to NotFollowing`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.getConvoMembersResult =
                Result.success(
                    MemberPage(members = persistentListOf(member("did:plc:a", "alice", FollowState.Following, "at://f/a"))),
                )
            val follow = FakeFollowRepository()
            val vm = groupDetailsViewModel(repo, follow)
            advanceUntilIdle()

            vm.handleEvent(GroupDetailsEvent.ToggleFollow("did:plc:a"))
            advanceUntilIdle()

            assertEquals(listOf("at://f/a"), follow.unfollowCalls)
            val m = vm.memberFor("did:plc:a")
            assertEquals(FollowState.NotFollowing, m.followState)
            assertEquals(null, m.followUri)
        }

    @Test
    fun `ToggleFollow failure rolls back and emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.getConvoMembersResult =
                Result.success(
                    MemberPage(members = persistentListOf(member("did:plc:b", "bob", FollowState.NotFollowing))),
                )
            val follow = FakeFollowRepository(followResult = Result.failure(IOException("down")))
            val vm = groupDetailsViewModel(repo, follow)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(GroupDetailsEvent.ToggleFollow("did:plc:b"))
                advanceUntilIdle()
                assertEquals(GroupDetailsEffect.ShowError(ChatError.Network), awaitItem())
            }
            assertEquals(FollowState.NotFollowing, vm.memberFor("did:plc:b").followState)
        }

    @Test
    fun `a second ToggleFollow for the same did is ignored while one is in flight`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.getConvoMembersResult =
                Result.success(
                    MemberPage(members = persistentListOf(member("did:plc:b", "bob", FollowState.NotFollowing))),
                )
            val follow = FakeFollowRepository()
            follow.gate = CompletableDeferred()
            val vm = groupDetailsViewModel(repo, follow)
            advanceUntilIdle()

            vm.handleEvent(GroupDetailsEvent.ToggleFollow("did:plc:b"))
            advanceUntilIdle()
            vm.handleEvent(GroupDetailsEvent.ToggleFollow("did:plc:b"))
            advanceUntilIdle()
            follow.gate!!.complete(Unit)
            advanceUntilIdle()

            assertEquals(1, follow.followCalls.size, "second toggle dropped while the first is in-flight")
        }

    @Test
    fun `ToggleFollow on the viewer is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.getConvoMembersResult =
                Result.success(
                    MemberPage(
                        members =
                            persistentListOf(
                                member("did:plc:me", "me", FollowState.NotFollowing, isViewer = true),
                            ),
                    ),
                )
            val follow = FakeFollowRepository()
            val vm = groupDetailsViewModel(repo, follow)
            advanceUntilIdle()

            vm.handleEvent(GroupDetailsEvent.ToggleFollow("did:plc:me"))
            advanceUntilIdle()

            assertTrue(follow.followCalls.isEmpty())
        }

    @Test
    fun `LeaveTapped success emits NavigateBack`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(GroupDetailsEvent.LeaveTapped)
                advanceUntilIdle()
                assertEquals(GroupDetailsEffect.NavigateBack, awaitItem())
            }
            assertEquals(listOf(convoId), repo.leaveCalls)
        }

    @Test
    fun `LeaveTapped failure emits ShowError`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.nextLeaveResult = Result.failure(IOException("offline"))
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(GroupDetailsEvent.LeaveTapped)
                advanceUntilIdle()
                assertEquals(GroupDetailsEffect.ShowError(ChatError.Network), awaitItem())
            }
        }

    @Test
    fun `ToggleMute calls setMuted and flips muted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()
            assertFalse(vm.uiState.value.muted)

            vm.handleEvent(GroupDetailsEvent.ToggleMute)
            advanceUntilIdle()

            assertEquals(listOf(convoId to true), repo.setMutedCalls)
            assertTrue(vm.uiState.value.muted)
        }

    @Test
    fun `muted is seeded from the convo-list cache`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.emitConvos(
                persistentListOf(
                    ConvoRowUi.Group(
                        convoId = convoId,
                        name = "G",
                        members = persistentListOf(),
                        lastMessageSnippet = null,
                        lastMessageFromViewer = false,
                        lastMessageIsAttachment = false,
                        sentAt = null,
                        muted = true,
                    ),
                ),
            )
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()

            assertTrue(vm.uiState.value.muted)
        }

    @Test
    fun `ToggleMute failure emits ShowError and does not flip muted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.nextSetMutedResult = Result.failure(IOException("offline"))
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(GroupDetailsEvent.ToggleMute)
                advanceUntilIdle()
                assertEquals(GroupDetailsEffect.ShowError(ChatError.Network), awaitItem())
            }
            assertFalse(vm.uiState.value.muted)
        }

    @Test
    fun `MemberTapped emits NavigateTo Profile with the member handle`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            repo.getConvoMembersResult =
                Result.success(
                    MemberPage(members = persistentListOf(member("did:plc:a", "alice.bsky.social", FollowState.NotFollowing))),
                )
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(GroupDetailsEvent.MemberTapped("did:plc:a"))
                advanceUntilIdle()
                assertEquals(GroupDetailsEffect.NavigateTo(Profile(handle = "alice.bsky.social")), awaitItem())
            }
        }

    @Test
    fun `BackPressed emits NavigateBack`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.getConvoResult = groupConvo("G")
            val vm = groupDetailsViewModel(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(GroupDetailsEvent.BackPressed)
                advanceUntilIdle()
                assertEquals(GroupDetailsEffect.NavigateBack, awaitItem())
            }
        }

    private fun groupDetailsViewModel(
        repo: FakeChatRepository,
        follow: FakeFollowRepository = FakeFollowRepository(),
    ): GroupDetailsViewModel =
        GroupDetailsViewModel(
            route = GroupDetails(convoId = convoId),
            repository = repo,
            followRepository = follow,
        )

    private fun groupConvo(name: String): Result<ChatConvo> =
        Result.success(
            ChatConvo(
                convoId = convoId,
                header = ChatHeader.Group(name = name, members = persistentListOf<AuthorUi>()),
                canPost = true,
            ),
        )

    private fun member(
        did: String,
        handle: String,
        followState: FollowState,
        followUri: String? = null,
        isViewer: Boolean = false,
    ): GroupMemberUi =
        GroupMemberUi(
            did = did,
            handle = handle,
            displayName = null,
            avatarUrl = null,
            role = GroupRole.Member,
            addedByName = null,
            isViewer = isViewer,
            followState = followState,
            followUri = followUri,
        )

    private fun GroupDetailsViewModel.memberFor(did: String): GroupMemberUi = (uiState.value.status as GroupDetailsLoadStatus.Loaded).members.first { it.did == did }
}
