package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.snapshots.Snapshot
import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
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

    @Test
    fun `IOException on resolveConvo maps to InitialError Network`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResolveResult = Result.failure(java.io.IOException("net down")))
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatLoadStatus.InitialError)
            assertEquals(ChatError.Network, (status as ChatLoadStatus.InitialError).error)
        }

    @Test
    fun `IOException on getMessages after successful resolve maps to InitialError Network`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeChatRepository(
                    nextMessagesResult = Result.failure(java.io.IOException("net down")),
                )
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatLoadStatus.InitialError)
            assertEquals(ChatError.Network, (status as ChatLoadStatus.InitialError).error)
            assertEquals("alice.bsky.social", vm.uiState.value.otherUserHandle)
        }

    @Test
    fun `RetryClicked re-issues both resolve and getMessages`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository(nextResolveResult = Result.failure(java.io.IOException("net down")))
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val priorResolve = repo.resolveCalls.get()
            val priorMessages = repo.messagesCalls.get()
            repo.nextResolveResult =
                Result.success(
                    ConvoResolution(
                        convoId = "c1",
                        otherUserHandle = "alice.bsky.social",
                        otherUserDisplayName = "Alice",
                        otherUserAvatarUrl = null,
                        otherUserAvatarHue = 0,
                    ),
                )
            vm.handleEvent(ChatEvent.RetryClicked)
            advanceUntilIdle()
            assertEquals(priorResolve + 1, repo.resolveCalls.get())
            assertEquals(priorMessages + 1, repo.messagesCalls.get())
            assertTrue(vm.uiState.value.status is ChatLoadStatus.Loaded)
        }

    @Test
    fun `Refresh on Loaded flips isRefreshing then commits new items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.status is ChatLoadStatus.Loaded)
            repo.nextMessagesResult =
                Result.success(
                    MessagePage(
                        messages =
                            persistentListOf(
                                MessageUi(
                                    id = "m-new",
                                    senderDid = otherUserDid,
                                    isOutgoing = false,
                                    text = "fresh",
                                    isDeleted = false,
                                    sentAt = Instant.parse("2026-05-14T13:00:00Z"),
                                ),
                            ),
                    ),
                )
            vm.handleEvent(ChatEvent.Refresh)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatLoadStatus.Loaded)
            val loaded = status as ChatLoadStatus.Loaded
            assertEquals(false, loaded.isRefreshing)
            assertTrue(loaded.items.isNotEmpty())
        }

    @Test
    fun `double-Refresh is single-flighted`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val priorResolve = repo.resolveCalls.get()
            vm.handleEvent(ChatEvent.Refresh)
            vm.handleEvent(ChatEvent.Refresh)
            advanceUntilIdle()
            assertEquals(priorResolve + 1, repo.resolveCalls.get())
        }

    @Test
    fun `Refresh failure on Loaded keeps existing items, drops isRefreshing`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val priorLoaded = vm.uiState.value.status as ChatLoadStatus.Loaded
            val priorItems = priorLoaded.items
            repo.nextMessagesResult = Result.failure(java.io.IOException("net down"))
            vm.handleEvent(ChatEvent.Refresh)
            advanceUntilIdle()
            val status = vm.uiState.value.status
            assertTrue(status is ChatLoadStatus.Loaded, "status should remain Loaded after refresh failure")
            val loaded = status as ChatLoadStatus.Loaded
            assertEquals(priorItems, loaded.items)
            assertEquals(false, loaded.isRefreshing)
        }

    @Test
    fun `QuotedPostTapped emits NavigateToPost with the quoted post's URI`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            val quotedUri = "at://did:plc:other/app.bsky.feed.post/q1"

            vm.effects.test {
                vm.handleEvent(ChatEvent.QuotedPostTapped(quotedUri))

                val effect = awaitItem()
                assertEquals(ChatEffect.NavigateToPost(quotedUri), effect)
            }
        }

    // --- Composer: enable gate + optimistic send (child B) ---

    @Test
    fun `isSendEnabled tracks whether the composer text is non-blank`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = chatViewModel(repo)
            advanceUntilIdle()
            assertEquals(false, vm.uiState.value.isSendEnabled)

            setComposerText(vm, "hello")
            assertEquals(true, vm.uiState.value.isSendEnabled)

            setComposerText(vm, "   ")
            assertEquals(false, vm.uiState.value.isSendEnabled, "whitespace-only is not sendable")
        }

    @Test
    fun `Send appends an optimistic Sending row and clears the composer`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            // Gate the send so the optimistic row is observable before reconcile.
            repo.sendGate = CompletableDeferred()
            val vm = chatViewModel(repo)
            advanceUntilIdle()

            setComposerText(vm, "hello")
            vm.handleEvent(ChatEvent.Send)
            runCurrent()

            assertEquals("", vm.textFieldState.text.toString(), "composer clears immediately on send")
            val outgoing = vm.uiState.value.outgoingMessages()
            assertEquals(1, outgoing.size)
            assertEquals("hello", outgoing.single().text)
            assertEquals(MessageSendStatus.Sending, outgoing.single().sendStatus)
            assertTrue(outgoing.single().id.startsWith("local:"), "optimistic row uses a client temp id")
            assertEquals("convo-1", repo.lastSendConvoId)
            assertEquals("hello", repo.lastSendText)
        }

    @Test
    fun `Send success replaces the optimistic row with the server message`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.nextSendResult =
                Result.success(
                    MessageUi(
                        id = "server-99",
                        senderDid = "did:plc:viewer",
                        isOutgoing = true,
                        text = "hello",
                        isDeleted = false,
                        sentAt = Instant.parse("2026-05-20T10:00:00Z"),
                        sendStatus = MessageSendStatus.Sent,
                    ),
                )
            val vm = chatViewModel(repo)
            advanceUntilIdle()

            setComposerText(vm, "hello")
            vm.handleEvent(ChatEvent.Send)
            advanceUntilIdle()

            val outgoing = vm.uiState.value.outgoingMessages()
            assertEquals(1, outgoing.size, "exactly one outgoing row after reconcile — no duplicate")
            assertEquals("server-99", outgoing.single().id)
            assertEquals(MessageSendStatus.Sent, outgoing.single().sendStatus)
            assertEquals(1, repo.sendCalls.get())
        }

    @Test
    fun `Send failure flips the optimistic row to Failed`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            repo.nextSendResult = Result.failure(IOException("net down"))
            val vm = chatViewModel(repo)
            advanceUntilIdle()

            setComposerText(vm, "oops")
            vm.handleEvent(ChatEvent.Send)
            advanceUntilIdle()

            val outgoing = vm.uiState.value.outgoingMessages()
            assertEquals(1, outgoing.size)
            assertEquals("oops", outgoing.single().text)
            assertEquals(MessageSendStatus.Failed, outgoing.single().sendStatus)
        }

    @Test
    fun `Send with a blank composer is a no-op`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeChatRepository()
            val vm = chatViewModel(repo)
            advanceUntilIdle()

            setComposerText(vm, "   ")
            vm.handleEvent(ChatEvent.Send)
            advanceUntilIdle()

            assertEquals(0, repo.sendCalls.get())
            assertTrue(
                vm.uiState.value
                    .outgoingMessages()
                    .isEmpty(),
            )
        }

    /**
     * Mutates the VM's [ChatViewModel.textFieldState] and drives the Compose
     * snapshot system so the change reaches the VM's `snapshotFlow` collector.
     * No frame loop exists in unit tests, so [Snapshot.sendApplyNotifications]
     * is triggered manually; [runCurrent] then advances past the collector's
     * suspending boundary. Mirrors `ComposerViewModelTest.setComposerText`.
     */
    private fun TestScope.setComposerText(
        vm: ChatViewModel,
        text: String,
    ) {
        vm.textFieldState.setTextAndPlaceCursorAtEnd(text)
        Snapshot.sendApplyNotifications()
        testScheduler.runCurrent()
    }

    private fun ChatScreenViewState.outgoingMessages(): List<MessageUi> =
        (status as ChatLoadStatus.Loaded)
            .items
            .filterIsInstance<ThreadItem.Message>()
            .map { it.message }
            .filter { it.isOutgoing }

    private fun chatViewModel(repo: FakeChatRepository): ChatViewModel =
        ChatViewModel(
            chat = Chat(otherUserDid = otherUserDid),
            repository = repo,
        )
}
