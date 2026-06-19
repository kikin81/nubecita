package net.kikin.nubecita.feature.chats.impl.store

import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatsUnreadCountStoreTest {
    @Test
    fun `sums unread across non-muted convos and excludes muted`() =
        runTest {
            val store =
                storeWith(
                    convo(unreadCount = 3, muted = false),
                    convo(unreadCount = 5, muted = true), // excluded
                    convo(unreadCount = 2, muted = false),
                )

            val result = store.refresh()

            assertEquals(5, result.getOrNull(), "3 + 2 (muted 5 excluded)")
            assertEquals(5, store.unreadCount.value)
        }

    @Test
    fun `null cache yields zero`() =
        runTest {
            val store = storeWith(convos = null)
            assertEquals(0, store.refresh().getOrNull())
            assertEquals(0, store.unreadCount.value)
        }

    @Test
    fun `empty cache yields zero`() =
        runTest {
            val store = storeWith() // no convos
            assertEquals(0, store.refresh().getOrNull())
            assertEquals(0, store.unreadCount.value)
        }

    @Test
    fun `refreshConvos failure passes through and leaves count unchanged`() =
        runTest {
            val repo = mockk<ChatRepository>()
            every { repo.observeConvos() } returns MutableStateFlow(persistentListOf())
            coEvery { repo.refreshConvos() } returns Result.failure(RuntimeException("network"))
            val store = ChatsUnreadCountStore(repo)

            val result = store.refresh()

            assertTrue(result.isFailure)
            assertEquals(0, store.unreadCount.value, "count untouched on failure")
        }

    @Test
    fun `clear resets the count to zero`() =
        runTest {
            val store = storeWith(convo(unreadCount = 9, muted = false))
            store.refresh()
            assertEquals(9, store.unreadCount.value)

            store.clear()

            assertEquals(0, store.unreadCount.value)
        }

    // ---- helpers ----

    private fun storeWith(vararg convos: ConvoListItemUi): ChatsUnreadCountStore = storeWith(convos.toList().toImmutableList())

    private fun storeWith(convos: ImmutableList<ConvoListItemUi>?): ChatsUnreadCountStore {
        val repo = mockk<ChatRepository>()
        every { repo.observeConvos() } returns MutableStateFlow(convos)
        coEvery { repo.refreshConvos() } returns Result.success(Unit)
        return ChatsUnreadCountStore(repo)
    }

    private fun convo(
        unreadCount: Int,
        muted: Boolean,
        convoId: String = "c$unreadCount${if (muted) "m" else ""}",
    ): ConvoListItemUi =
        ConvoListItemUi(
            convoId = convoId,
            otherUserDid = "did:plc:other",
            otherUserHandle = "other.bsky.social",
            displayName = "Other",
            avatarUrl = null,
            lastMessageSnippet = "hi",
            lastMessageFromViewer = false,
            lastMessageIsAttachment = false,
            sentAt = null,
            unreadCount = unreadCount,
            muted = muted,
        )
}
