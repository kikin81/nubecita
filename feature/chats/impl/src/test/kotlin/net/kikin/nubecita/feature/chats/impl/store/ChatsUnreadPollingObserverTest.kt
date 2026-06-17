package net.kikin.nubecita.feature.chats.impl.store

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.feature.chats.impl.ConvoListItemUi
import net.kikin.nubecita.feature.chats.impl.MessageUi
import net.kikin.nubecita.feature.chats.impl.data.ChatLogPage
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.ConvoResolution
import net.kikin.nubecita.feature.chats.impl.data.MessagePage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

/**
 * Mirrors `NotificationsPollingObserverTest` — the observer logic is a
 * verbatim copy, so this covers the chat-specific wiring: foreground gating,
 * 60s cadence, backoff, store update, and sign-out clear.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatsUnreadPollingObserverTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension(UnconfinedTestDispatcher())

    @Test
    fun `start is idempotent`() =
        runPollingTest { f ->
            f.observer.start()
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(1, f.repository.refreshCalls, "second start() must short-circuit")
        }

    @Test
    fun `immediate refresh on STARTED then 60s cadence`() =
        runPollingTest { f ->
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(1, f.repository.refreshCalls)

            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(2, f.repository.refreshCalls)
        }

    @Test
    fun `no polls fire while backgrounded`() =
        runPollingTest { f ->
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(1, f.repository.refreshCalls)

            f.lifecycleRegistry.currentState = Lifecycle.State.CREATED
            advanceTimeBy(120_000L)
            runCurrent()
            assertEquals(1, f.repository.refreshCalls, "backgrounded: no polls")
        }

    @Test
    fun `backoff after failure then reset on success`() =
        runPollingTest { f ->
            f.repository.failures = 1
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(1, f.repository.refreshCalls) // fails → backoff 120s

            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(1, f.repository.refreshCalls, "still backed off at 60s")

            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(2, f.repository.refreshCalls, "second poll at t=120s succeeds → reset to 60s")

            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(3, f.repository.refreshCalls, "cadence back to 60s after success")
        }

    @Test
    fun `successful poll updates the store`() =
        runPollingTest(unread = 13) { f ->
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(13, f.store.unreadCount.value)
        }

    @Test
    fun `SignedOut clears the store`() =
        runPollingTest(
            unread = 8,
            sessionFlow = MutableStateFlow(SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")),
        ) { f ->
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(8, f.store.unreadCount.value)

            (f.sessionStateProvider.state as MutableStateFlow).value = SessionState.SignedOut
            runCurrent()
            assertEquals(0, f.store.unreadCount.value)
        }

    @Test
    fun `does not poll while message checking is disabled`() =
        runPollingTest(unread = 5, messageCheckingFlow = MutableStateFlow(false)) { f ->
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            advanceTimeBy(180_000L)
            runCurrent()
            assertEquals(0, f.repository.refreshCalls, "message checking off: no refresh attempts")
        }

    @Test
    fun `turning message checking off clears the badge`() =
        runPollingTest(unread = 9) { f ->
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(9, f.store.unreadCount.value)

            f.messageCheckingFlow.value = false
            runCurrent()
            assertEquals(0, f.store.unreadCount.value, "disabling clears the badge")
        }

    @Test
    fun `does not poll while signed out`() =
        runPollingTest(sessionFlow = MutableStateFlow(SessionState.SignedOut)) { f ->
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            advanceTimeBy(180_000L)
            runCurrent()
            assertEquals(0, f.repository.refreshCalls, "signed out: no refresh attempts")
        }

    @Test
    fun `starts polling once the session becomes SignedIn`() =
        runPollingTest(sessionFlow = MutableStateFlow(SessionState.Loading)) { f ->
            f.observer.start()
            runCurrent()
            f.lifecycleRegistry.currentState = Lifecycle.State.STARTED
            runCurrent()
            assertEquals(0, f.repository.refreshCalls, "Loading: no poll yet")

            (f.sessionStateProvider.state as MutableStateFlow).value =
                SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")
            // The loop re-checks the session value on its next tick.
            advanceTimeBy(60_000L)
            runCurrent()
            assertEquals(1, f.repository.refreshCalls, "polls after sign-in")
        }

    // ---- harness ----

    private fun runPollingTest(
        unread: Int = 0,
        sessionFlow: MutableStateFlow<SessionState> =
            MutableStateFlow(SessionState.SignedIn(handle = "alice.bsky.social", did = "did:plc:alice")),
        messageCheckingFlow: MutableStateFlow<Boolean> = MutableStateFlow(true),
        block: suspend TestScope.(Fixture) -> Unit,
    ) = runTest(mainDispatcherExtension.dispatcher) {
        val f = newFixture(unread, sessionFlow, messageCheckingFlow)
        try {
            block(f)
        } finally {
            f.scope.cancel()
        }
    }

    private data class Fixture(
        val observer: ChatsUnreadPollingObserver,
        val store: ChatsUnreadCountStore,
        val repository: FakeChatRepository,
        val sessionStateProvider: SessionStateProvider,
        val lifecycleRegistry: LifecycleRegistry,
        val owner: LifecycleOwner,
        val scope: CoroutineScope,
        val messageCheckingFlow: MutableStateFlow<Boolean>,
    )

    private fun newFixture(
        unread: Int,
        sessionFlow: MutableStateFlow<SessionState>,
        messageCheckingFlow: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): Fixture {
        val dispatcher = mainDispatcherExtension.dispatcher
        val repository = FakeChatRepository(unread = unread)
        val store = ChatsUnreadCountStore(repository)
        val sessionStateProvider = FakeSessionStateProvider(sessionFlow)
        val owner = FakeLifecycleOwner()
        val registry = LifecycleRegistry.createUnsafe(owner).also { owner.registry = it }
        val scope = CoroutineScope(dispatcher + Job())
        val observer =
            ChatsUnreadPollingObserver(
                store = store,
                sessionStateProvider = sessionStateProvider,
                messageChecking = FakeMessageChecking(messageCheckingFlow),
                scope = scope,
                lifecycle = registry,
            )
        return Fixture(observer, store, repository, sessionStateProvider, registry, owner, scope, messageCheckingFlow)
    }

    private class FakeMessageChecking(
        override val enabled: MutableStateFlow<Boolean>,
    ) : net.kikin.nubecita.core.preferences.MessageCheckingPreference {
        override suspend fun setEnabled(enabled: Boolean) {
            this.enabled.value = enabled
        }
    }

    private class FakeLifecycleOwner : LifecycleOwner {
        lateinit var registry: LifecycleRegistry

        override val lifecycle: Lifecycle
            get() = registry
    }

    private class FakeSessionStateProvider(
        override val state: MutableStateFlow<SessionState>,
    ) : SessionStateProvider {
        override suspend fun refresh() = Unit
    }

    /** Fake whose [refreshConvos] success publishes one convo carrying [unread]. */
    private class FakeChatRepository(
        private val unread: Int,
    ) : ChatRepository {
        var refreshCalls: Int = 0
        var failures: Int = 0
        private val convos = MutableStateFlow<ImmutableList<ConvoListItemUi>?>(null)
        private val requestConvos = MutableStateFlow<ImmutableList<ConvoListItemUi>?>(null)

        override fun observeConvos(): StateFlow<ImmutableList<ConvoListItemUi>?> = convos

        override fun observeRequestConvos(): StateFlow<ImmutableList<ConvoListItemUi>?> = requestConvos

        override suspend fun refreshRequestConvos(): Result<Unit> {
            // Honor the contract: refresh publishes into the observed cache.
            requestConvos.value = persistentListOf()
            return Result.success(Unit)
        }

        override suspend fun refreshConvos(): Result<Unit> {
            refreshCalls++
            return if (failures > 0) {
                failures--
                Result.failure(RuntimeException("simulated refreshConvos failure"))
            } else {
                convos.value =
                    if (unread == 0) {
                        persistentListOf()
                    } else {
                        persistentListOf(sampleConvo(unread))
                    }
                Result.success(Unit)
            }
        }

        override suspend fun leaveConvo(convoId: String): Result<Unit> = error("not used")

        override suspend fun acceptConvo(convoId: String): Result<Unit> = error("not used")

        override suspend fun setMuted(
            convoId: String,
            muted: Boolean,
        ): Result<Unit> = error("not used")

        override suspend fun resolveConvo(otherUserDid: String): Result<ConvoResolution> = error("not used")

        override suspend fun getMessages(
            convoId: String,
            cursor: String?,
            limit: Int,
        ): Result<MessagePage> = error("not used")

        override suspend fun sendMessage(
            convoId: String,
            text: String,
        ): Result<MessageUi> = error("not used")

        override suspend fun markConvoRead(convoId: String): Result<Unit> = error("not used")

        override suspend fun getLog(cursor: String?): Result<ChatLogPage> = error("not used")
    }

    private companion object {
        fun sampleConvo(unread: Int): ConvoListItemUi =
            ConvoListItemUi(
                convoId = "c1",
                otherUserDid = "did:plc:other",
                otherUserHandle = "other.bsky.social",
                displayName = "Other",
                avatarUrl = null,
                avatarHue = 0,
                lastMessageSnippet = "hi",
                lastMessageFromViewer = false,
                lastMessageIsAttachment = false,
                sentAt = null,
                unreadCount = unread,
                muted = false,
            )
    }
}
