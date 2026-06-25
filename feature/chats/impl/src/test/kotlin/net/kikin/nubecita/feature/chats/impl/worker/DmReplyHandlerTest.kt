package net.kikin.nubecita.feature.chats.impl.worker

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class DmReplyHandlerTest {
    private val repository = mockk<ChatRepository>()
    private val handler =
        DmReplyHandler(
            chatRepository = repository,
            notifier = mockk(relaxed = true),
            scope = mockk(relaxed = true),
        )

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
}
