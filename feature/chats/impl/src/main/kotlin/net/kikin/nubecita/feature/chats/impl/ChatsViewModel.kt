package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toChatsError
import javax.inject.Inject

/**
 * Presenter for the Chats tab home.
 *
 * On construction, kicks off a `listConvos` call. Subsequent Refresh
 * and RetryClicked events re-issue the call. Refresh is single-flighted
 * (a second Refresh while the first is in flight is dropped, not
 * queued) so rapid pull-to-refresh gestures can't fan out into
 * multiple concurrent requests.
 *
 * `ConvoTapped` emits a `NavigateToChat(otherUserDid)` effect that the
 * screen collector translates into a `MainShellNavState.add(Chat(did))`
 * call. The Chat NavKey itself lands in nn3.2 — this VM doesn't depend
 * on it.
 */
@HiltViewModel
internal class ChatsViewModel
    @Inject
    constructor(
        private val repository: ChatRepository,
    ) : MviViewModel<ChatsScreenViewState, ChatsEvent, ChatsEffect>(ChatsScreenViewState()) {
        private var inFlightLoad: Job? = null

        init {
            launchLoad()
        }

        override fun handleEvent(event: ChatsEvent) {
            when (event) {
                ChatsEvent.Refresh -> launchLoad()
                ChatsEvent.RetryClicked -> launchLoad()
                is ChatsEvent.ConvoTapped -> sendEffect(ChatsEffect.NavigateToChat(event.otherUserDid))
            }
        }

        private fun launchLoad() {
            if (inFlightLoad?.isActive == true) return // single-flight on rapid Refresh.
            val current = uiState.value.status
            // Preserve the Loaded items + flip isRefreshing so the screen can render the spinner over the
            // existing list rather than blanking. The initial Loading state stays unchanged otherwise.
            if (current is ChatsLoadStatus.Loaded) {
                setState { copy(status = current.copy(isRefreshing = true)) }
            }
            inFlightLoad =
                viewModelScope.launch {
                    repository
                        .listConvos()
                        .onSuccess { page ->
                            setState { copy(status = ChatsLoadStatus.Loaded(items = page.items)) }
                        }.onFailure { throwable ->
                            val error = throwable.toChatsError()
                            val prior = uiState.value.status
                            if (prior is ChatsLoadStatus.Loaded) {
                                // Refresh-time failure: keep existing items visible, drop the
                                // refresh indicator, surface a transient snackbar.
                                setState { copy(status = prior.copy(isRefreshing = false)) }
                                sendEffect(ChatsEffect.ShowRefreshError(error))
                            } else {
                                setState { copy(status = ChatsLoadStatus.InitialError(error)) }
                            }
                        }
                    inFlightLoad = null
                }
        }
    }
