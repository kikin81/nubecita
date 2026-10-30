package net.kikin.nubecita.feature.chats.impl

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.core.testing.RecordingAnalyticsClient
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.impl.data.ChatConvo
import net.kikin.nubecita.feature.chats.impl.data.ChatLogEvent
import net.kikin.nubecita.feature.chats.impl.data.ChatLogPage
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import kotlin.time.Instant

/**
 * The open thread polls `getLog` while the screen is started and refetches its messages when
 * the log carries a new message for this convo. The poll loop never goes idle, so these tests
 * drive virtual time with [advanceTimeBy] and stop the screen before finishing.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class ChatLivePollTest {
    @JvmField
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val convoId = "c1"

    @Test
    fun `a log event for this convo refetches and shows the new message`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = loadedRepo()
            val vm = openThread(repo)
            repo.nextGetLogResult = Result.success(ChatLogPage(nextCursor = "cursor-0"))
            vm.handleEvent(ChatEvent.ScreenStarted)
            runCurrent()

            repo.nextMessagesResult = Result.success(MessagePage(messages = persistentListOf(message("m2"), message("m1"))))
            repo.nextGetLogResult = Result.success(ChatLogPage(events = persistentListOf(logEvent(convoId, "m2")), nextCursor = "cursor-1"))
            val messagesBefore = repo.messagesCalls.get()
            advanceTimeBy(ChatViewModel.ACTIVE_POLL_INTERVAL_MS + 1)

            assertEquals("cursor-0", repo.lastGetLogCursor)
            assertEquals(messagesBefore + 1, repo.messagesCalls.get())
            assertTrue(vm.messageIds().contains("m2"))
            vm.handleEvent(ChatEvent.ScreenStopped)
        }

    @Test
    fun `log events for other convos don't refetch`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = loadedRepo()
            val vm = openThread(repo)
            repo.nextGetLogResult = Result.success(ChatLogPage(nextCursor = "cursor-0"))
            vm.handleEvent(ChatEvent.ScreenStarted)
            runCurrent()

            repo.nextGetLogResult = Result.success(ChatLogPage(events = persistentListOf(logEvent("other", "x1")), nextCursor = "cursor-1"))
            val messagesBefore = repo.messagesCalls.get()
            advanceTimeBy(ChatViewModel.ACTIVE_POLL_INTERVAL_MS + 1)

            assertEquals(messagesBefore, repo.messagesCalls.get())
            vm.handleEvent(ChatEvent.ScreenStopped)
        }

    @Test
    fun `polling stops when the screen stops`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = loadedRepo()
            val vm = openThread(repo)
            vm.handleEvent(ChatEvent.ScreenStarted)
            advanceTimeBy(ChatViewModel.ACTIVE_POLL_INTERVAL_MS + 1)
            vm.handleEvent(ChatEvent.ScreenStopped)
            val callsAtStop = repo.getLogCalls.get()

            advanceTimeBy(ChatViewModel.IDLE_POLL_INTERVAL_MS * 3)

            assertEquals(callsAtStop, repo.getLogCalls.get())
        }

    @Test
    fun `after the inactivity timeout it slows to the idle interval`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = loadedRepo()
            val vm = openThread(repo)
            vm.handleEvent(ChatEvent.ScreenStarted)
            advanceTimeBy(ChatViewModel.INACTIVE_TIMEOUT_MS + 1)
            val callsWhenIdle = repo.getLogCalls.get()

            // Several active intervals pass without a poll once idle...
            advanceTimeBy(ChatViewModel.ACTIVE_POLL_INTERVAL_MS * 3)
            assertEquals(callsWhenIdle, repo.getLogCalls.get())
            // ...and one lands within the idle interval.
            advanceTimeBy(ChatViewModel.IDLE_POLL_INTERVAL_MS)
            assertTrue(repo.getLogCalls.get() > callsWhenIdle)
            vm.handleEvent(ChatEvent.ScreenStopped)
        }

    private fun loadedRepo(): FakeChatRepository =
        FakeChatRepository(
            nextMessagesResult = Result.success(MessagePage(messages = persistentListOf(message("m1")))),
        ).apply {
            getConvoResult =
                Result.success(
                    ChatConvo(
                        convoId = convoId,
                        header = ChatHeader.Group(name = "Devs", members = persistentListOf()),
                        canPost = true,
                    ),
                )
        }

    private fun TestScope.openThread(repo: FakeChatRepository): ChatViewModel {
        val vm = ChatViewModel(chat = Chat(convoId = convoId), repository = repo, analytics = RecordingAnalyticsClient())
        runCurrent()
        return vm
    }

    private fun ChatViewModel.messageIds(): List<String> =
        (uiState.value.status as ChatLoadStatus.Loaded)
            .items
            .filterIsInstance<ThreadItem.Message>()
            .map { it.message.id }

    private fun message(id: String): MessageUi =
        MessageUi(
            id = id,
            senderDid = "did:plc:bob",
            isOutgoing = false,
            text = "hi",
            isDeleted = false,
            sentAt = Instant.parse("2026-10-29T12:00:00Z"),
        )

    private fun logEvent(
        convoId: String,
        messageId: String,
    ): ChatLogEvent =
        ChatLogEvent(
            convoId = convoId,
            messageId = messageId,
            senderDid = "did:plc:bob",
            text = "hi",
            isDeleted = false,
            sentAt = Instant.parse("2026-10-29T12:00:00Z"),
        )
}
