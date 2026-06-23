package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toJoinError

/**
 * MVI presenter for the invite-link group preview. Loads the public group info on init; `JoinTapped`
 * runs `requestJoin` (guarded by [GroupJoinPreviewViewState.joinInFlight]) — a direct join emits
 * [GroupJoinPreviewEffect.NavigateToConvo], a pending request transitions to
 * [GroupJoinPreviewStatus.RequestSent], and a failure emits [GroupJoinPreviewEffect.ShowError]. The
 * link `code` is never logged (it's a capability token).
 */
@HiltViewModel(assistedFactory = GroupJoinPreviewViewModel.Factory::class)
class GroupJoinPreviewViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: GroupJoinPreview,
        private val repository: ChatRepository,
    ) : MviViewModel<GroupJoinPreviewViewState, GroupJoinPreviewEvent, GroupJoinPreviewEffect>(
            GroupJoinPreviewViewState(),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: GroupJoinPreview): GroupJoinPreviewViewModel
        }

        private val code: String = route.code

        init {
            load()
        }

        override fun handleEvent(event: GroupJoinPreviewEvent) {
            when (event) {
                GroupJoinPreviewEvent.Retry -> load()
                GroupJoinPreviewEvent.JoinTapped -> join()
            }
        }

        private fun load() {
            setState { copy(status = GroupJoinPreviewStatus.Loading) }
            viewModelScope.launch {
                repository
                    .getGroupPublicInfo(code)
                    .onSuccess { setState { copy(status = GroupJoinPreviewStatus.Loaded(it)) } }
                    .onFailure { setState { copy(status = GroupJoinPreviewStatus.Error(it.toJoinError())) } }
            }
        }

        private fun join() {
            if (uiState.value.joinInFlight) return
            if (uiState.value.status !is GroupJoinPreviewStatus.Loaded) return
            setState { copy(joinInFlight = true) }
            viewModelScope.launch {
                try {
                    repository
                        .requestJoin(code)
                        .onSuccess { result ->
                            when (result) {
                                is JoinResult.Joined -> sendEffect(GroupJoinPreviewEffect.NavigateToConvo(result.convoId))
                                JoinResult.Pending -> setState { copy(status = GroupJoinPreviewStatus.RequestSent) }
                            }
                        }.onFailure { sendEffect(GroupJoinPreviewEffect.ShowError(it.toJoinError())) }
                } finally {
                    setState { copy(joinInFlight = false) }
                }
            }
        }
    }
