package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toChatsError
import javax.inject.Inject

/**
 * Presenter for the Chats tab home.
 *
 * Items come from the repository's reactive convo cache ([ChatRepository.observeConvos]) —
 * the single source of truth shared with the thread screen. The screen's
 * [ChatsLoadStatus] is `combine`d from that cache and a local [refreshPhase]
 * lifecycle: the cache supplies the list, the phase supplies loading /
 * refreshing / initial-error. Because the cache is shared and hot, sending a
 * message from a thread (which patches the cache) updates this list live without
 * a refetch.
 *
 * [Refresh] / [RetryClicked] trigger [ChatRepository.refreshConvos], single-flighted so
 * rapid pull-to-refresh can't fan out into concurrent requests.
 */
@HiltViewModel
class ChatsViewModel
    @Inject
    constructor(
        private val repository: ChatRepository,
    ) : MviViewModel<ChatsScreenViewState, ChatsEvent, ChatsEffect>(ChatsScreenViewState()) {
        private val refreshPhase = MutableStateFlow<RefreshPhase>(RefreshPhase.InitialLoading)
        private var refreshJob: Job? = null

        init {
            // Derive the screen status from (cached items × refresh phase). The
            // cache is the source of truth for items; the phase drives the
            // loading / refreshing / error lifecycle around them.
            combine(repository.observeConvos(), refreshPhase) { items, phase ->
                when {
                    items != null ->
                        ChatsLoadStatus.Loaded(
                            items = items,
                            isRefreshing = phase == RefreshPhase.Refreshing,
                        )
                    phase is RefreshPhase.InitialError -> ChatsLoadStatus.InitialError(phase.error)
                    else -> ChatsLoadStatus.Loading
                }
            }.onEach { status -> setState { copy(status = status) } }
                .launchIn(viewModelScope)

            refresh()
        }

        override fun handleEvent(event: ChatsEvent) {
            when (event) {
                ChatsEvent.Refresh -> refresh()
                ChatsEvent.RetryClicked -> refresh()
                is ChatsEvent.ConvoTapped -> sendEffect(ChatsEffect.NavigateToChat(event.otherUserDid))
                ChatsEvent.SettingsTapped -> sendEffect(ChatsEffect.NavigateToChatSettings)
            }
        }

        private fun refresh() {
            if (refreshJob?.isActive == true) return // single-flight on rapid Refresh.
            // Refreshing over an existing list shows the spinner atop it; a refresh
            // with no items yet is the initial load.
            refreshPhase.value =
                if (repository.observeConvos().value != null) {
                    RefreshPhase.Refreshing
                } else {
                    RefreshPhase.InitialLoading
                }
            refreshJob =
                viewModelScope.launch {
                    repository
                        .refreshConvos()
                        .onSuccess { refreshPhase.value = RefreshPhase.Idle }
                        .onFailure { throwable ->
                            val error = throwable.toChatsError()
                            if (repository.observeConvos().value != null) {
                                // Refresh-time failure with items present: keep them,
                                // drop the indicator, surface a transient snackbar.
                                refreshPhase.value = RefreshPhase.Idle
                                sendEffect(ChatsEffect.ShowRefreshError(error))
                            } else {
                                refreshPhase.value = RefreshPhase.InitialError(error)
                            }
                        }
                }
        }

        /** Lifecycle of the refresh request, combined with the cache to form the screen status. */
        private sealed interface RefreshPhase {
            data object InitialLoading : RefreshPhase

            data object Refreshing : RefreshPhase

            data object Idle : RefreshPhase

            data class InitialError(
                val error: ChatsError,
            ) : RefreshPhase
        }
    }
