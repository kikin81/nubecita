package net.kikin.nubecita.feature.chats.impl.worker

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.preferences.DmPollCursorStore
import net.kikin.nubecita.core.preferences.MessageCheckingPreference
import net.kikin.nubecita.feature.chats.impl.ConvoRowUi
import net.kikin.nubecita.feature.chats.impl.FakeChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ChatLogEvent
import net.kikin.nubecita.feature.chats.impl.data.ChatLogPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
internal class DmPollRunnerTest {
    @Test
    fun `signed out is a no-op success without polling`() =
        runTest {
            val repo = FakeChatRepository()
            val f = fixture(repo = repo, session = SessionState.SignedOut)

            assertEquals(DmPollRunner.Outcome.SUCCESS, f.runner.run())
            assertEquals(0, repo.getLogCalls.get())
        }

    @Test
    fun `message-checking off is a no-op success without polling`() =
        runTest {
            val repo = FakeChatRepository()
            val f = fixture(repo = repo, enabled = false)

            assertEquals(DmPollRunner.Outcome.SUCCESS, f.runner.run())
            assertEquals(0, repo.getLogCalls.get())
        }

    @Test
    fun `foregrounded suppresses without polling or advancing the cursor`() =
        runTest {
            val repo = FakeChatRepository()
            val cursor = FakeCursorStore(initial = "c0")
            val f = fixture(repo = repo, foregrounded = true, cursorStore = cursor)

            assertEquals(DmPollRunner.Outcome.SUCCESS, f.runner.run())
            assertEquals(0, repo.getLogCalls.get(), "no network while foregrounded")
            assertEquals("c0", cursor.value, "cursor must not advance (D5)")
            assertTrue(f.notifier.posted.isEmpty())
        }

    @Test
    fun `first poll with null cursor sets the baseline and notifies nothing`() =
        runTest {
            val repo =
                FakeChatRepository().apply {
                    nextRefreshResult = Result.success(persistentListOf(convo("c1", unread = 3)))
                    nextGetLogResult = Result.success(ChatLogPage(events = persistentListOf(event("c1", ALICE)), nextCursor = "head"))
                }
            val cursor = FakeCursorStore(initial = null)
            val f = fixture(repo = repo, cursorStore = cursor)

            assertEquals(DmPollRunner.Outcome.SUCCESS, f.runner.run())
            assertEquals("head", cursor.value, "baseline cursor established")
            assertTrue(f.notifier.posted.isEmpty(), "no notifications for the pre-existing backlog")
        }

    @Test
    fun `notifies an inbound message in an unread convo and advances the cursor`() =
        runTest {
            val repo =
                FakeChatRepository().apply {
                    nextRefreshResult =
                        Result.success(persistentListOf(convo("c1", unread = 1, displayName = "Alice", handle = "alice.bsky.social")))
                    nextGetLogResult =
                        Result.success(ChatLogPage(events = persistentListOf(event("c1", ALICE, text = "hey")), nextCursor = "next"))
                }
            val cursor = FakeCursorStore(initial = "old")
            val f = fixture(repo = repo, cursorStore = cursor)

            assertEquals(DmPollRunner.Outcome.SUCCESS, f.runner.run())
            assertEquals(1, f.notifier.posted.size)
            val n = f.notifier.posted.single()
            assertEquals("c1", n.convoId)
            assertEquals("Alice", n.title)
            assertEquals("hey", n.body)
            // The tap deep-link addresses the CONVERSATION, not the other user —
            // so groups and 1:1s open the same way (regression: nubecita-g1ph).
            assertEquals("nubecita://chat/convo/c1", n.deepLinkUri)
            assertEquals("next", cursor.value)
        }

    @Test
    fun `group notification deep-links to the convo, not the message sender`() =
        runTest {
            // A group message from ALICE. The pre-fix bug set the tap target to the
            // sender's DID, so tapping opened the 1:1 DM with ALICE instead of the
            // group. The deep-link must address the group convo id.
            val repo =
                FakeChatRepository().apply {
                    nextRefreshResult = Result.success(persistentListOf(groupConvo("g1", unread = 1, name = "Weekend Trip")))
                    nextGetLogResult =
                        Result.success(ChatLogPage(events = persistentListOf(event("g1", senderDid = ALICE, text = "who's driving?")), nextCursor = "next"))
                }
            val f = fixture(repo = repo)

            assertEquals(DmPollRunner.Outcome.SUCCESS, f.runner.run())
            val n = f.notifier.posted.single()
            assertEquals("g1", n.convoId)
            assertEquals("Weekend Trip", n.title, "group notifications are titled by the group name")
            assertEquals("who's driving?", n.body)
            assertEquals("nubecita://chat/convo/g1", n.deepLinkUri)
        }

    @Test
    fun `does not notify when the convo is already read (read-state filter)`() =
        runTest {
            val repo =
                FakeChatRepository().apply {
                    nextRefreshResult = Result.success(persistentListOf(convo("c1", unread = 0)))
                    nextGetLogResult =
                        Result.success(ChatLogPage(events = persistentListOf(event("c1", ALICE)), nextCursor = "next"))
                }
            val cursor = FakeCursorStore(initial = "old")
            val f = fixture(repo = repo, cursorStore = cursor)

            f.runner.run()
            assertTrue(f.notifier.posted.isEmpty())
            assertEquals("next", cursor.value, "cursor still advances past handled events")
        }

    @Test
    fun `refresh failure retries`() =
        runTest {
            val repo = FakeChatRepository().apply { nextRefreshResult = Result.failure(java.io.IOException("down")) }
            val f = fixture(repo = repo)
            assertEquals(DmPollRunner.Outcome.RETRY, f.runner.run())
            assertEquals(0, repo.getLogCalls.get())
        }

    @Test
    fun `getLog failure retries`() =
        runTest {
            val repo =
                FakeChatRepository().apply {
                    nextRefreshResult = Result.success(persistentListOf(convo("c1", unread = 1)))
                    nextGetLogResult = Result.failure(java.io.IOException("down"))
                }
            val cursor = FakeCursorStore(initial = "old")
            val f = fixture(repo = repo, cursorStore = cursor)
            assertEquals(DmPollRunner.Outcome.RETRY, f.runner.run())
            assertEquals("old", cursor.value, "cursor untouched on failure")
        }

    // ---- fixtures / fakes ----

    private companion object {
        const val VIEWER = "did:plc:viewer"
        const val ALICE = "did:plc:alice"
    }

    private class Fixture(
        val runner: DmPollRunner,
        val notifier: RecordingNotifier,
    )

    private fun fixture(
        repo: FakeChatRepository,
        session: SessionState = SessionState.SignedIn(handle = "me.bsky.social", did = VIEWER),
        enabled: Boolean = true,
        foregrounded: Boolean = false,
        cursorStore: FakeCursorStore = FakeCursorStore(initial = "old"),
    ): Fixture {
        val notifier = RecordingNotifier()
        val runner =
            DmPollRunner(
                repository = repo,
                cursorStore = cursorStore,
                messageChecking = FakeMessageChecking(enabled),
                sessionStateProvider = FakeSessionStateProvider(session),
                foreground = FakeForeground(foregrounded),
                notifier = notifier,
            )
        return Fixture(runner, notifier)
    }

    private fun convo(
        id: String,
        unread: Int,
        muted: Boolean = false,
        displayName: String? = "Alice",
        handle: String = "alice.bsky.social",
    ): ConvoRowUi =
        ConvoRowUi.Direct(
            convoId = id,
            otherUserDid = ALICE,
            otherUserHandle = handle,
            displayName = displayName,
            avatarUrl = null,
            lastMessageSnippet = null,
            lastMessageFromViewer = false,
            lastMessageIsAttachment = false,
            sentAt = null,
            unreadCount = unread,
            muted = muted,
        )

    private fun groupConvo(
        id: String,
        unread: Int,
        name: String,
        muted: Boolean = false,
    ): ConvoRowUi =
        ConvoRowUi.Group(
            convoId = id,
            name = name,
            members = persistentListOf(),
            lastMessageSnippet = null,
            lastMessageFromViewer = false,
            lastMessageIsAttachment = false,
            sentAt = null,
            unreadCount = unread,
            muted = muted,
        )

    private fun event(
        convoId: String,
        senderDid: String,
        text: String = "hi",
    ): ChatLogEvent =
        ChatLogEvent(
            convoId = convoId,
            messageId = "m-$convoId",
            senderDid = senderDid,
            text = text,
            isDeleted = false,
            sentAt = Instant.parse("2026-05-14T12:00:00Z"),
        )

    private class RecordingNotifier : DmNotifier {
        val posted = mutableListOf<DmNotification>()

        override fun notify(notifications: List<DmNotification>) {
            posted += notifications
        }
    }

    private class FakeCursorStore(
        initial: String?,
    ) : DmPollCursorStore {
        var value: String? = initial

        override fun cursor(did: String): Flow<String?> = flowOf(value)

        override suspend fun setCursor(
            did: String,
            cursor: String,
        ) {
            value = cursor
        }
    }

    private class FakeMessageChecking(
        enabled: Boolean,
    ) : MessageCheckingPreference {
        override val enabled: Flow<Boolean> = flowOf(enabled)

        override suspend fun setEnabled(enabled: Boolean) = Unit
    }

    private class FakeForeground(
        private val foregrounded: Boolean,
    ) : AppForegroundSignal {
        override fun isForegrounded(): Boolean = foregrounded
    }

    private class FakeSessionStateProvider(
        state: SessionState,
    ) : SessionStateProvider {
        override val state: StateFlow<SessionState> = MutableStateFlow(state)

        override suspend fun refresh() = Unit
    }
}
