package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.SavedStateHandle
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Clock
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val otherUserDid = "did:plc:alice"

    @Test
    fun `init resolves then loads messages, ending in Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextResolveResult =
                        Result.success(
                            ConvoResolution(
                                convoId = "c1",
                                otherUserHandle = "alice.bsky.social",
                                otherUserDisplayName = "Alice",
                                otherUserAvatarUrl = null,
                                otherUserAvatarHue = 217,
                            ),
                        ),
                    nextMessagesResult =
                        Result.success(
                            MessagePage(
                                messages =
                                    persistentListOf(
                                        MessageUi(
                                            id = "m1",
                                            senderDid = otherUserDid,
                                            isOutgoing = false,
                                            text = "hi",
                                            isDeleted = false,
                                            sentAt = Instant.parse("2026-05-14T12:00:00Z"),
                                        ),
                                    ),
                            ),
                        ),
                )
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val state = vm.uiState.value
            assertEquals(1, repo.resolveCalls.get())
            assertEquals(1, repo.messagesCalls.get())
            assertEquals("c1", repo.lastMessagesConvoId)
            assertEquals("alice.bsky.social", state.otherUserHandle)
            assertEquals("Alice", state.otherUserDisplayName)
            assertTrue(state.status is ChatLoadStatus.Loaded)
            val loaded = state.status as ChatLoadStatus.Loaded
            assertEquals(false, loaded.isRefreshing)
            assertTrue(loaded.items.isNotEmpty())
        }

    private fun chatViewModel(repo: FakeChatRepository): ChatViewModel =
        ChatViewModel(
            savedStateHandle = SavedStateHandle(mapOf("otherUserDid" to otherUserDid)),
            repository = repo,
            clock = Clock.System,
        )
}
