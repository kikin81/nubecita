package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
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

        init {
            launchLoad()
        }

        override fun handleEvent(event: ChatEvent) {
            when (event) {
                ChatEvent.Refresh -> launchLoad()
                ChatEvent.RetryClicked -> launchLoad()
                ChatEvent.BackPressed -> Unit // screen handles back via nav state
                is ChatEvent.QuotedPostTapped ->
                    sendEffect(ChatEffect.NavigateToPost(event.quotedPostUri))
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
                                    val items = page.messages.toThreadItems(now = Clock.System.now())
                                    setState { copy(status = ChatLoadStatus.Loaded(items = items)) }
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
