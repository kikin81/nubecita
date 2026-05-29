package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.api.Chat
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toChatError
import net.kikin.nubecita.feature.chats.impl.data.toThreadItems
import kotlin.time.Clock

/**
 * MVI presenter for the chat thread screen.
 *
 * Receives its peer DID via assisted injection of the [Chat] NavKey at the
 * navigation entry point — matches the project pattern used by
 * `ComposerViewModel` / `MediaViewerViewModel`. Auto-loads on construction:
 * resolves the peer DID to a convoId via `chat.bsky.convo.getConvoForMembers`,
 * then loads the first page of messages via `chat.bsky.convo.getMessages`.
 * Refresh / retry events re-run the chain. Single-flight via [inFlightLoad].
 */
@HiltViewModel(assistedFactory = ChatViewModel.Factory::class)
internal class ChatViewModel
    @AssistedInject
    constructor(
        @Assisted private val chat: Chat,
        private val repository: ChatRepository,
    ) : MviViewModel<ChatScreenViewState, ChatEvent, ChatEffect>(ChatScreenViewState()) {
        @AssistedFactory
        interface Factory {
            fun create(chat: Chat): ChatViewModel
        }

        private val otherUserDid: String = chat.otherUserDid
        private var inFlightLoad: Job? = null

        /**
         * Resolved convo id (set on the first successful `resolveConvo`). Sends
         * are gated on this — a send before the load chain resolves is a no-op.
         */
        private var convoId: String? = null

        /**
         * Canonical newest-first message list (the source the thread `LazyColumn`
         * derives from via [toThreadItems]). Held outside `UiState` so optimistic
         * appends and the success/failure reconcile can recompute grouping; the
         * projected `ThreadItem` stream is what lands on [ChatScreenViewState].
         */
        private var messages: List<MessageUi> = emptyList()
        private var sendCounter = 0

        /**
         * Composer text/selection, owned here per the sanctioned MVI editor
         * exception (CLAUDE.md). The screen wires the `state =` text-field
         * overload; a `snapshotFlow` collector projects non-blank-ness into
         * [ChatScreenViewState.isSendEnabled].
         */
        val textFieldState: TextFieldState = TextFieldState()

        init {
            // Project the boolean gate (not the raw text) so the collector only
            // touches state when blank/non-blank actually flips — snapshotFlow
            // dedupes on the computed value, so per-keystroke setState churn is
            // avoided.
            snapshotFlow { textFieldState.text.toString().isNotBlank() }
                .onEach { enabled -> setState { copy(isSendEnabled = enabled) } }
                .launchIn(viewModelScope)
            launchLoad()
        }

        override fun handleEvent(event: ChatEvent) {
            when (event) {
                ChatEvent.Refresh -> launchLoad()
                ChatEvent.RetryClicked -> launchLoad()
                ChatEvent.BackPressed -> Unit // screen handles back via nav state
                ChatEvent.Send -> onSend()
                is ChatEvent.QuotedPostTapped ->
                    sendEffect(ChatEffect.NavigateToPost(event.quotedPostUri))
            }
        }

        /**
         * Optimistic send: clear the field, append a `Sending` row with a client
         * temp id, then call the repository. On success the temp row is replaced
         * by the server message (`Sent`); on failure it flips to `Failed`. The
         * server id reconcile means the next `getMessages` refresh (which replaces
         * [messages] wholesale) won't double the row. The transient error effect +
         * inline retry are a follow-up (child D).
         *
         * If a refresh completes mid-send and wipes the temp row, the reconcile
         * re-surfaces the result instead of dropping it: a confirmed message is
         * re-inserted (de-duped on server id in case the refresh already pulled it
         * in), and a failed row is re-inserted so the send isn't silently lost.
         */
        private fun onSend() {
            val convo = convoId ?: return
            val text = textFieldState.text.toString()
            if (text.isBlank()) return
            textFieldState.clearText()
            val tempId = "local:${sendCounter++}"
            // Group the optimistic row with the viewer's prior outgoing run; the
            // empty fallback (first send in a fresh convo) is a lone run, so the
            // missing did doesn't mis-group. Reconcile swaps in the server did.
            val viewerDid = messages.firstOrNull { it.isOutgoing }?.senderDid.orEmpty()
            val optimistic =
                MessageUi(
                    id = tempId,
                    senderDid = viewerDid,
                    isOutgoing = true,
                    text = text,
                    isDeleted = false,
                    sentAt = Clock.System.now(),
                    sendStatus = MessageSendStatus.Sending,
                )
            messages = listOf(optimistic) + messages
            commitMessages()
            viewModelScope.launch {
                repository
                    .sendMessage(convo, text)
                    .onSuccess { server ->
                        messages =
                            when {
                                messages.any { it.id == tempId } -> messages.map { if (it.id == tempId) server else it }
                                messages.any { it.id == server.id } -> messages // refresh already pulled it in
                                else -> listOf(server) + messages // temp row wiped mid-send; don't lose the sent message
                            }
                        commitMessages()
                    }.onFailure {
                        val failed = optimistic.copy(sendStatus = MessageSendStatus.Failed)
                        messages =
                            if (messages.any { it.id == tempId }) {
                                messages.map { if (it.id == tempId) failed else it }
                            } else {
                                listOf(failed) + messages // temp row wiped mid-send; re-surface the failure
                            }
                        commitMessages()
                    }
            }
        }

        /**
         * Recompute the projected `ThreadItem` stream from [messages]. Defaults to
         * preserving the current refresh flag (send-path commits must not disturb a
         * concurrent refresh); the load-success path passes `isRefreshing = false`
         * explicitly to clear it.
         */
        private fun commitMessages(
            isRefreshing: Boolean = (uiState.value.status as? ChatLoadStatus.Loaded)?.isRefreshing ?: false,
        ) {
            setState {
                copy(
                    status =
                        ChatLoadStatus.Loaded(
                            items = messages.toThreadItems(now = Clock.System.now()),
                            isRefreshing = isRefreshing,
                        ),
                )
            }
        }

        private fun launchLoad() {
            if (inFlightLoad?.isActive == true) return
            val priorStatus = uiState.value.status
            if (priorStatus is ChatLoadStatus.Loaded) {
                setState { copy(status = priorStatus.copy(isRefreshing = true)) }
            }
            inFlightLoad =
                viewModelScope.launch {
                    repository
                        .resolveConvo(otherUserDid)
                        .onSuccess { resolution ->
                            convoId = resolution.convoId
                            setState {
                                copy(
                                    otherUserHandle = resolution.otherUserHandle,
                                    otherUserDisplayName = resolution.otherUserDisplayName,
                                    otherUserAvatarUrl = resolution.otherUserAvatarUrl,
                                    otherUserAvatarHue = resolution.otherUserAvatarHue,
                                )
                            }
                            repository
                                .getMessages(resolution.convoId)
                                .onSuccess { page ->
                                    messages = page.messages
                                    commitMessages(isRefreshing = false)
                                }.onFailure { throwable ->
                                    handleFailure(throwable)
                                }
                        }.onFailure { throwable ->
                            handleFailure(throwable)
                        }
                    inFlightLoad = null
                }
        }

        private fun handleFailure(throwable: Throwable) {
            val error = throwable.toChatError()
            val prior = uiState.value.status
            if (prior is ChatLoadStatus.Loaded) {
                setState { copy(status = prior.copy(isRefreshing = false)) }
            } else {
                setState { copy(status = ChatLoadStatus.InitialError(error)) }
            }
        }
    }
