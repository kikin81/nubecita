package net.kikin.nubecita.wear.dms

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.wear.data.PhoneActionSupport
import net.kikin.nubecita.wear.data.WatchDmActionRepository
import net.kikin.nubecita.wear.data.WatchDmRepository
import net.kikin.nubecita.wear.data.db.WatchConvoEntity
import net.kikin.nubecita.wear.data.db.WatchDmStateEntity
import net.kikin.nubecita.wear.data.db.WatchMessageEntity
import net.kikin.nubecita.wear.data.db.WatchPendingDmEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith

@OptIn(ExperimentalCoroutinesApi::class)
@ExtendWith(MainDispatcherExtension::class)
class WatchDmViewModelTest {
    private val did = "did:plc:alice"
    private val repo = FakeDmRepository()
    private val actions = FakeDmActions()
    private val support = FakeSupport()

    private fun vm() = WatchDmViewModel(repo, actions, support)

    private fun convo(
        id: String,
        position: Int,
        unread: Int = 0,
    ) = WatchConvoEntity(did, id, position, "Title $id", isGroup = false, unreadCount = unread, muted = false, lastMessageAtMs = 1)

    private fun message(
        convoId: String,
        position: Int,
        text: String,
        mine: Boolean = false,
    ) = WatchMessageEntity(did, convoId, position, "$convoId-$position", mine, null, text, sentAtMs = position.toLong(), isDeleted = false, hasAttachment = false)

    @Test
    fun without_an_account_it_is_loading() =
        runTest {
            val vm = vm()
            advanceUntilIdle()

            assertEquals(WatchDmStatus.Loading, vm.uiState.value.status)
        }

    @Test
    fun before_the_phone_syncs_dms_it_says_not_synced() =
        runTest {
            val vm = vm()
            vm.handleEvent(WatchDmEvent.AccountChanged(did))
            advanceUntilIdle()

            assertEquals(WatchDmStatus.NotSynced, vm.uiState.value.status)
        }

    @Test
    fun synced_convos_show_in_order_with_their_last_message_and_the_unread_total() =
        runTest {
            repo.header.value = WatchDmStateEntity(did, publishedAtMs = 1, unreadCount = 3)
            repo.convos.value = listOf(convo("c1", 0, unread = 3), convo("c2", 1))
            repo.messages.value = listOf(message("c1", 0, "hi"), message("c1", 1, "latest", mine = true), message("c2", 0, "yo"))
            val vm = vm()
            vm.handleEvent(WatchDmEvent.AccountChanged(did))
            advanceUntilIdle()

            val state = vm.uiState.value
            assertEquals(WatchDmStatus.Ready, state.status)
            assertEquals(3, state.unreadCount)
            assertEquals(listOf("c1", "c2"), state.convos.map { it.convoId })
            assertEquals("latest", state.convos[0].lastMessage?.text)
            assertNull(state.thread)
        }

    @Test
    fun opening_a_thread_shows_its_messages_oldest_first() =
        runTest {
            repo.header.value = WatchDmStateEntity(did, publishedAtMs = 1, unreadCount = 0)
            repo.convos.value = listOf(convo("c1", 0))
            repo.messages.value = listOf(message("c1", 0, "first"), message("c1", 1, "second"))
            val vm = vm()
            vm.handleEvent(WatchDmEvent.AccountChanged(did))
            vm.handleEvent(WatchDmEvent.ThreadOpened("c1"))
            advanceUntilIdle()

            val thread = vm.uiState.value.thread
            assertEquals("c1", thread?.convoId)
            assertEquals(listOf("first", "second"), thread?.messages?.map { it.text })
        }

    @Test
    fun switching_accounts_closes_the_open_thread() =
        runTest {
            repo.header.value = WatchDmStateEntity(did, publishedAtMs = 1, unreadCount = 0)
            repo.convos.value = listOf(convo("c1", 0))
            val vm = vm()
            vm.handleEvent(WatchDmEvent.AccountChanged(did))
            vm.handleEvent(WatchDmEvent.ThreadOpened("c1"))
            advanceUntilIdle()

            vm.handleEvent(WatchDmEvent.AccountChanged("did:plc:bob"))
            advanceUntilIdle()

            assertNull(vm.uiState.value.thread)
            assertFalse(repo.messageQueries.contains("did:plc:bob" to "c1"))
        }

    @Test
    fun replies_reactions_and_retries_go_to_the_action_repository_for_the_account() =
        runTest {
            val vm = vm()
            vm.handleEvent(WatchDmEvent.AccountChanged(did))
            advanceUntilIdle()

            vm.handleEvent(WatchDmEvent.ReplySent("c1", "On my way"))
            vm.handleEvent(WatchDmEvent.ReactionSent("c1", "m9", "👍"))
            vm.handleEvent(WatchDmEvent.RetryTapped("a1"))
            advanceUntilIdle()

            assertEquals(listOf("reply:$did:c1:On my way", "react:$did:c1:m9:👍", "retry:a1"), actions.calls)
        }

    @Test
    fun the_open_thread_shows_pending_replies_and_reactions_with_their_status() =
        runTest {
            repo.header.value = WatchDmStateEntity(did, publishedAtMs = 1, unreadCount = 0)
            repo.convos.value = listOf(convo("c1", 0))
            repo.messages.value = listOf(message("c1", 0, "hi"))
            actions.pending.value =
                listOf(
                    WatchPendingDmEntity("r1", did, "c1", WatchPendingDmEntity.KIND_REPLY, "On my way", createdAtMs = 1),
                    WatchPendingDmEntity("x1", did, "c1", WatchPendingDmEntity.KIND_REACT, "❤️", targetMessageId = "c1-0", createdAtMs = 2, status = WatchPendingDmEntity.STATUS_FAILED),
                    WatchPendingDmEntity("other", did, "c2", WatchPendingDmEntity.KIND_REPLY, "elsewhere", createdAtMs = 3),
                )
            val vm = vm()
            vm.handleEvent(WatchDmEvent.AccountChanged(did))
            vm.handleEvent(WatchDmEvent.ThreadOpened("c1"))
            advanceUntilIdle()

            val thread = vm.uiState.value.thread!!
            assertEquals(listOf("On my way" to WatchSendStatus.SENDING), thread.pendingReplies.map { it.text to it.status })
            assertEquals(
                listOf("❤️" to WatchSendStatus.FAILED),
                thread.messages
                    .single()
                    .pendingReactions
                    .map { it.text to it.status },
            )
        }

    @Test
    fun replies_and_reactions_are_offered_only_when_the_phone_supports_them() =
        runTest {
            val vm = vm()
            vm.handleEvent(WatchDmEvent.AccountChanged(did))
            advanceUntilIdle()
            assertEquals(true, vm.uiState.value.actionsAvailable)

            support.dm = false
            val older = vm()
            older.handleEvent(WatchDmEvent.AccountChanged(did))
            advanceUntilIdle()
            assertFalse(older.uiState.value.actionsAvailable)
        }

    private class FakeDmActions : WatchDmActionRepository {
        val pending = MutableStateFlow(emptyList<WatchPendingDmEntity>())
        val calls = mutableListOf<String>()

        override fun pending(accountDid: String): Flow<List<WatchPendingDmEntity>> = pending

        override suspend fun sendReply(
            accountDid: String,
            convoId: String,
            text: String,
        ) {
            calls += "reply:$accountDid:$convoId:$text"
        }

        override suspend fun sendReaction(
            accountDid: String,
            convoId: String,
            messageId: String,
            emoji: String,
        ) {
            calls += "react:$accountDid:$convoId:$messageId:$emoji"
        }

        override suspend fun retry(actionId: String) {
            calls += "retry:$actionId"
        }
    }

    private class FakeSupport : PhoneActionSupport {
        var dm = true

        override suspend fun available() = true

        override suspend fun dmAvailable() = dm
    }

    private class FakeDmRepository : WatchDmRepository {
        val header = MutableStateFlow<WatchDmStateEntity?>(null)
        val convos = MutableStateFlow(emptyList<WatchConvoEntity>())
        val messages = MutableStateFlow(emptyList<WatchMessageEntity>())
        val messageQueries = mutableListOf<Pair<String, String>>()

        override fun state(accountDid: String): Flow<WatchDmStateEntity?> = header.map { it?.takeIf { h -> h.accountDid == accountDid } }

        override fun convos(accountDid: String): Flow<List<WatchConvoEntity>> = convos.map { all -> all.filter { it.accountDid == accountDid } }

        override fun lastMessages(accountDid: String): Flow<List<WatchMessageEntity>> = messages.map { all -> all.filter { it.accountDid == accountDid }.groupBy { it.convoId }.map { (_, m) -> m.maxBy { it.position } } }

        override fun messages(
            accountDid: String,
            convoId: String,
        ): Flow<List<WatchMessageEntity>> {
            messageQueries += accountDid to convoId
            return messages.map { all -> all.filter { it.accountDid == accountDid && it.convoId == convoId }.sortedBy { it.position } }
        }

        override suspend fun latestUnreadCount() = header.value?.unreadCount
    }
}
