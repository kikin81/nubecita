package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.filter
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.api.GroupJoinRequests
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.JOIN_REQUESTS_PAGE_LIMIT
import net.kikin.nubecita.feature.chats.impl.data.toMemberMgmtError

/**
 * MVI presenter for the group join-requests screen.
 *
 * Receives the [GroupJoinRequests] NavKey via assisted injection. Streams pending requests as
 * [PagingData] via [JoinRequestPagingSource]. Approve/Reject are optimistic: the tapped row is
 * filtered out of the cached paging stream immediately (via [removedDids]) and re-appears if the
 * call fails (rollback + [GroupJoinRequestsEffect.ShowError]). An approve success emits
 * [GroupJoinRequestsEffect.RosterChanged] so the group-details roster can refresh. A per-did
 * in-flight guard ([GroupJoinRequestsViewState.inFlightDids]) drops a rapid second tap so the two
 * non-idempotent calls can't race.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = GroupJoinRequestsViewModel.Factory::class)
class GroupJoinRequestsViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: GroupJoinRequests,
        private val repository: ChatRepository,
    ) : MviViewModel<GroupJoinRequestsViewState, GroupJoinRequestsEvent, GroupJoinRequestsEffect>(
            GroupJoinRequestsViewState(),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: GroupJoinRequests): GroupJoinRequestsViewModel
        }

        private val convoId: String = route.convoId

        /**
         * Optimistic-removal overlay: a removed did is filtered out of the cached PagingData,
         * and re-appears if its approve/reject call fails (rollback).
         */
        private val removedDids = MutableStateFlow<Set<String>>(emptySet())

        val joinRequests: Flow<PagingData<JoinRequestUi>> =
            Pager(PagingConfig(pageSize = JOIN_REQUESTS_PAGE_LIMIT)) {
                JoinRequestPagingSource(convoId, repository)
            }.flow
                .cachedIn(viewModelScope)
                .combine(removedDids) { data, removed -> data.filter { it.did !in removed } }

        override fun handleEvent(event: GroupJoinRequestsEvent) {
            when (event) {
                is GroupJoinRequestsEvent.ApproveTapped -> act(event.did, approve = true)
                is GroupJoinRequestsEvent.RejectTapped -> act(event.did, approve = false)
            }
        }

        private fun act(
            did: String,
            approve: Boolean,
        ) {
            if (did in uiState.value.inFlightDids) return
            setState { copy(inFlightDids = (inFlightDids + did).toPersistentSet()) }
            removedDids.update { it + did } // optimistic remove
            viewModelScope.launch {
                try {
                    val result =
                        if (approve) {
                            repository.approveJoinRequest(convoId, did)
                        } else {
                            repository.rejectJoinRequest(convoId, did)
                        }
                    result
                        .onSuccess { if (approve) sendEffect(GroupJoinRequestsEffect.RosterChanged) }
                        .onFailure {
                            removedDids.update { it - did } // rollback — row reappears
                            sendEffect(GroupJoinRequestsEffect.ShowError(it.toMemberMgmtError()))
                        }
                } finally {
                    setState { copy(inFlightDids = (inFlightDids - did).toPersistentSet()) }
                }
            }
        }
    }
