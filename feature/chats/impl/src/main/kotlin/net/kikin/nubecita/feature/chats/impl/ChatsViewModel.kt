package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
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
 * The inbox has two segments — accepted Chats ([ChatRepository.observeConvos]) and
 * pending Requests ([ChatRepository.observeRequestConvos]) — each a reactive cache
 * with its own refresh lifecycle. The screen's [ChatsLoadStatus] is `combine`d from
 * both caches, both per-segment phases, and the active segment: the ACTIVE segment's
 * (items × phase) drives `status`, while the request list's size feeds the Requests
 * pill badge. The accepted cache is shared with the thread screen, so sending a
 * message from a thread (which patches it) updates the Chats list live without a
 * refetch.
 *
 * [Refresh] / [RetryClicked] refresh both segments concurrently (each in its own child
 * coroutine so a slow segment can't stall the other), single-flighted via the parent
 * job so rapid pull-to-refresh can't fan out into concurrent requests. An accepted
 * failure surfaces a transient snackbar; a requests-only failure stays inline in the
 * Requests segment.
 */
@HiltViewModel
class ChatsViewModel
    @Inject
    constructor(
        private val repository: ChatRepository,
    ) : MviViewModel<ChatsScreenViewState, ChatsEvent, ChatsEffect>(ChatsScreenViewState()) {
        // Accepted convos + pending requests are separate caches/lifecycles. The
        // ACTIVE segment decides which (items × phase) drives [status]; the request
        // count always feeds the Requests pill badge. Captured once so the VM never
        // depends on observe*() returning the same instance per call.
        private val acceptedConvos = repository.observeConvos()
        private val requestConvos = repository.observeRequestConvos()
        private val acceptedPhase = MutableStateFlow<RefreshPhase>(RefreshPhase.InitialLoading)
        private val requestPhase = MutableStateFlow<RefreshPhase>(RefreshPhase.InitialLoading)
        private val activeSegment = MutableStateFlow(ChatsSegment.Chats)
        private var refreshJob: Job? = null

        init {
            // Project (active segment's items × phase) into status, and the request
            // count into the badge. Both caches are the source of truth for items;
            // the per-segment phase drives loading / refreshing / error.
            combine(
                acceptedConvos,
                requestConvos,
                acceptedPhase,
                requestPhase,
                activeSegment,
            ) { accepted, requests, aPhase, rPhase, segment ->
                val items = if (segment == ChatsSegment.Chats) accepted else requests
                val phase = if (segment == ChatsSegment.Chats) aPhase else rPhase
                ChatsScreenViewState(
                    status = statusFor(items, phase),
                    activeSegment = segment,
                    requestCount = requests?.size ?: 0,
                )
            }.onEach { next -> setState { next } }
                .launchIn(viewModelScope)

            refresh()
        }

        override fun handleEvent(event: ChatsEvent) {
            when (event) {
                ChatsEvent.Refresh -> refresh()
                ChatsEvent.RetryClicked -> refresh()
                is ChatsEvent.ConvoTapped -> sendEffect(ChatsEffect.NavigateToChat(event.otherUserDid))
                ChatsEvent.SettingsTapped -> sendEffect(ChatsEffect.NavigateToChatSettings)
                is ChatsEvent.SegmentSelected -> activeSegment.value = event.segment
            }
        }

        private fun statusFor(
            items: ImmutableList<ConvoListItemUi>?,
            phase: RefreshPhase,
        ): ChatsLoadStatus =
            when {
                items != null ->
                    ChatsLoadStatus.Loaded(items = items, isRefreshing = phase == RefreshPhase.Refreshing)
                phase is RefreshPhase.InitialError -> ChatsLoadStatus.InitialError(phase.error)
                else -> ChatsLoadStatus.Loading
            }

        private fun refresh() {
            if (refreshJob?.isActive == true) return // single-flight on rapid Refresh.
            // Refreshing over an existing list shows the spinner atop it; a refresh
            // with no items yet is the initial load. Tracked per segment.
            acceptedPhase.value = phaseForRefreshStart(acceptedConvos.value != null)
            requestPhase.value = phaseForRefreshStart(requestConvos.value != null)
            refreshJob =
                viewModelScope.launch {
                    // Two independent children so each segment's phase updates the moment
                    // ITS fetch returns — a slow accepted refresh must not hold the
                    // requests segment in Loading (and vice-versa). The parent job stays
                    // active until both finish, preserving single-flight.
                    // Accepted failure surfaces a transient snackbar (the primary list);
                    // a request failure stays inline in the Requests segment (no snackbar).
                    launch {
                        applyRefresh(repository.refreshConvos(), acceptedPhase, acceptedConvos.value != null, surfaceSnackbar = true)
                    }
                    launch {
                        applyRefresh(repository.refreshRequestConvos(), requestPhase, requestConvos.value != null, surfaceSnackbar = false)
                    }
                }
        }

        private fun phaseForRefreshStart(hasItems: Boolean): RefreshPhase = if (hasItems) RefreshPhase.Refreshing else RefreshPhase.InitialLoading

        private fun applyRefresh(
            result: Result<Unit>,
            phase: MutableStateFlow<RefreshPhase>,
            hadItems: Boolean,
            surfaceSnackbar: Boolean,
        ) {
            result
                .onSuccess { phase.value = RefreshPhase.Idle }
                .onFailure { throwable ->
                    val error = throwable.toChatsError()
                    if (hadItems) {
                        // Failure with items present: keep them, drop the indicator.
                        phase.value = RefreshPhase.Idle
                        if (surfaceSnackbar) sendEffect(ChatsEffect.ShowRefreshError(error))
                    } else {
                        phase.value = RefreshPhase.InitialError(error)
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
