package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.api.ManageJoinLink
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toMemberMgmtError

/**
 * MVI presenter for the owner's manage-join-link screen.
 *
 * Loads the group's single link via [ChatRepository.getJoinLink] on init. Create / enable / disable /
 * edit are optimistic: state updates immediately, the call runs, and a failure rolls back + emits
 * [ManageJoinLinkEffect.ShowError]. A single [ManageJoinLinkViewState.mutationInFlight] flag guards
 * against concurrent mutations (the link is one object). A [JoinRule.Unsupported] link is read-only:
 * every setting event is dropped, the backstop for the screen's disabled controls.
 */
@HiltViewModel(assistedFactory = ManageJoinLinkViewModel.Factory::class)
class ManageJoinLinkViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: ManageJoinLink,
        private val repository: ChatRepository,
    ) : MviViewModel<ManageJoinLinkViewState, ManageJoinLinkEvent, ManageJoinLinkEffect>(
            ManageJoinLinkViewState(),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: ManageJoinLink): ManageJoinLinkViewModel
        }

        private val convoId: String = route.convoId

        init {
            load()
        }

        override fun handleEvent(event: ManageJoinLinkEvent) {
            when (event) {
                ManageJoinLinkEvent.Retry -> load()
                is ManageJoinLinkEvent.CreateTapped -> create(event.joinRule, event.requireApproval)
                is ManageJoinLinkEvent.JoinRuleChanged -> editRule(event.joinRule)
                is ManageJoinLinkEvent.RequireApprovalChanged -> editApproval(event.requireApproval)
                ManageJoinLinkEvent.EnableTapped -> setEnabled(enable = true)
                ManageJoinLinkEvent.DisableTapped -> setEnabled(enable = false)
            }
        }

        private fun load() {
            setState { copy(status = ManageJoinLinkStatus.Loading) }
            viewModelScope.launch {
                repository
                    .getJoinLink(convoId)
                    .onSuccess { setState { copy(status = ManageJoinLinkStatus.Loaded(it)) } }
                    .onFailure { setState { copy(status = ManageJoinLinkStatus.Error(it.toMemberMgmtError())) } }
            }
        }

        private fun create(
            joinRule: JoinRule,
            requireApproval: Boolean,
        ) {
            if (uiState.value.mutationInFlight) return
            setState { copy(mutationInFlight = true) }
            viewModelScope.launch {
                try {
                    repository
                        .createJoinLink(convoId, joinRule, requireApproval)
                        .onSuccess { setState { copy(status = ManageJoinLinkStatus.Loaded(it)) } }
                        .onFailure { sendEffect(ManageJoinLinkEffect.ShowError(it.toMemberMgmtError())) }
                } finally {
                    setState { copy(mutationInFlight = false) }
                }
            }
        }

        private fun editRule(joinRule: JoinRule) {
            val current = editableLink() ?: return
            applyOptimistic(current, current.copy(joinRule = joinRule)) {
                repository.editJoinLink(convoId, joinRule = joinRule)
            }
        }

        private fun editApproval(requireApproval: Boolean) {
            val current = editableLink() ?: return
            applyOptimistic(current, current.copy(requireApproval = requireApproval)) {
                repository.editJoinLink(convoId, requireApproval = requireApproval)
            }
        }

        private fun setEnabled(enable: Boolean) {
            val current = editableLink() ?: return
            applyOptimistic(current, current.copy(enabled = enable)) {
                if (enable) repository.enableJoinLink(convoId) else repository.disableJoinLink(convoId)
            }
        }

        /**
         * The current link if it exists AND is editable. Returns `null` (no-op) when there is no
         * link, the mutation is already in flight, or its rule is [JoinRule.Unsupported] — the
         * read-only backstop.
         */
        private fun editableLink(): JoinLinkUi? {
            if (uiState.value.mutationInFlight) return null
            val link = (uiState.value.status as? ManageJoinLinkStatus.Loaded)?.link ?: return null
            return link.takeIf { it.joinRule != JoinRule.Unsupported }
        }

        private fun applyOptimistic(
            prior: JoinLinkUi,
            optimistic: JoinLinkUi,
            call: suspend () -> Result<JoinLinkUi>,
        ) {
            setState { copy(status = ManageJoinLinkStatus.Loaded(optimistic), mutationInFlight = true) }
            viewModelScope.launch {
                try {
                    call()
                        .onSuccess { setState { copy(status = ManageJoinLinkStatus.Loaded(it)) } }
                        .onFailure {
                            setState { copy(status = ManageJoinLinkStatus.Loaded(prior)) }
                            sendEffect(ManageJoinLinkEffect.ShowError(it.toMemberMgmtError()))
                        }
                } finally {
                    setState { copy(mutationInFlight = false) }
                }
            }
        }
    }
