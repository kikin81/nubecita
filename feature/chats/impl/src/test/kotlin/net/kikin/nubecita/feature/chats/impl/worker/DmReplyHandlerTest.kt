package net.kikin.nubecita.feature.chats.impl.worker

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DmReplyHandlerTest {
    private val repository = mockk<ChatRepository>()
    private val notifier = mockk<MessagingStyleDmNotifier>(relaxed = true)

    // A real Unconfined scope so handle()'s launch executes inline (testable).
    private val handler =
        DmReplyHandler(
            chatRepository = repository,
            notifier = notifier,
            scope = CoroutineScope(Dispatchers.Unconfined),
        )

    // --- trySend (pure) ---

    @Test
    fun `trySend trims, sends, and returns true on success`() =
        runTest {
            coEvery { repository.sendMessage("c1", "hello") } returns Result.success(mockk<MessageUi>())
            assertTrue(handler.trySend("c1", "  hello  "))
            coVerify(exactly = 1) { repository.sendMessage("c1", "hello") }
        }

    @Test
    fun `trySend no-ops and returns false on blank text`() =
        runTest {
            assertFalse(handler.trySend("c1", "   "))
            coVerify(exactly = 0) { repository.sendMessage(any(), any()) }
        }

    @Test
    fun `trySend no-ops and returns false on blank convo`() =
        runTest {
            assertFalse(handler.trySend("  ", "hello"))
            coVerify(exactly = 0) { repository.sendMessage(any(), any()) }
        }

    @Test
    fun `trySend returns false when the send fails`() =
        runTest {
            coEvery { repository.sendMessage("c1", "hi") } returns Result.failure(RuntimeException("boom"))
            assertFalse(handler.trySend("c1", "hi"))
        }

    // --- handle (orchestration; runs inline on the Unconfined scope) ---

    @Test
    fun `handle appends the reply to the notification on success`() {
        coEvery { repository.sendMessage("c1", "hi") } returns Result.success(mockk<MessageUi>())
        handler.handle("c1", "  hi  ", null)
        coVerify(exactly = 1) { repository.sendMessage("c1", "hi") }
        verify(exactly = 1) { notifier.appendSentReply("c1", "hi") }
        verify(exactly = 0) { notifier.clearReplySpinner(any()) }
    }

    @Test
    fun `handle clears the spinner on blank text without sending`() {
        handler.handle("c1", "   ", null)
        coVerify(exactly = 0) { repository.sendMessage(any(), any()) }
        verify(exactly = 1) { notifier.clearReplySpinner("c1") }
        verify(exactly = 0) { notifier.appendSentReply(any(), any()) }
    }

    @Test
    fun `handle clears the spinner when the send fails`() {
        coEvery { repository.sendMessage("c1", "hi") } returns Result.failure(RuntimeException("boom"))
        handler.handle("c1", "hi", null)
        verify(exactly = 1) { notifier.clearReplySpinner("c1") }
        verify(exactly = 0) { notifier.appendSentReply(any(), any()) }
    }
}
