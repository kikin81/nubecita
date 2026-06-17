package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toChatsError
import net.kikin.nubecita.feature.moderation.api.Block
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.profile.api.Profile
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

        // Selection mode: set of selected convoIds, or null when not selecting.
        private val selection = MutableStateFlow<PersistentSet<String>?>(null)
        private var refreshJob: Job? = null

        init {
            // Project (active segment's items × phase) into status, and the request
            // count into the badge. Both caches are the source of truth for items;
            // the per-segment phase drives loading / refreshing / error.
            val projected =
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
                }
            // Fold the selection in as a 6th input (combine tops out at 5 typed flows).
            projected
                .combine(selection) { state, sel -> state.copy(selection = sel) }
                .onEach { next -> setState { next } }
                .launchIn(viewModelScope)

            refresh()
        }

        override fun handleEvent(event: ChatsEvent) {
            when (event) {
                ChatsEvent.Refresh -> refresh()
                ChatsEvent.RetryClicked -> refresh()
                is ChatsEvent.ConvoTapped -> sendEffect(ChatsEffect.NavigateToChat(event.otherUserDid))
                ChatsEvent.SettingsTapped -> sendEffect(ChatsEffect.NavigateToChatSettings)
                is ChatsEvent.SegmentSelected -> {
                    // Switching segments exits any in-progress selection (the action
                    // set + the underlying list both change).
                    selection.value = null
                    activeSegment.value = event.segment
                }
                is ChatsEvent.ConvoLongPressed -> selection.value = persistentSetOf(event.convoId)
                is ChatsEvent.SelectionToggled -> toggleSelection(event.convoId)
                ChatsEvent.ClearSelection -> selection.value = null
                ChatsEvent.LeaveSelected -> runBulkAction { repository.leaveConvo(it) }
                ChatsEvent.AcceptSelected -> runBulkAction { repository.acceptConvo(it) }
                ChatsEvent.ToggleMuteSelected -> toggleMuteSelected()
                ChatsEvent.ProfileSelected -> navigateSingle { Profile(handle = it.otherUserHandle) }
                ChatsEvent.ReportSelected -> navigateSingle { Report.forAccount(it.otherUserDid) }
                ChatsEvent.BlockSelected -> navigateSingle { Block.forAccount(did = it.otherUserDid, handle = it.otherUserHandle) }
            }
        }

        private fun toggleSelection(convoId: String) {
            val current = selection.value ?: persistentSetOf()
            val next = if (convoId in current) current.remove(convoId) else current.add(convoId)
            // Toggling the last selected convo off exits selection mode.
            selection.value = if (next.isEmpty()) null else next
        }

        /** The currently-displayed list for the active segment. */
        private fun activeList(): List<ConvoListItemUi> = (if (activeSegment.value == ChatsSegment.Chats) acceptedConvos.value else requestConvos.value).orEmpty()

        private fun selectedConvos(): List<ConvoListItemUi> {
            val ids = selection.value ?: return emptyList()
            return activeList().filter { it.convoId in ids }
        }

        /**
         * Run [action] over each selected convo, then exit selection mode. The repo
         * patches its cache on each success; the first failure surfaces a transient
         * error (the un-actioned convo stays in the list). G2 leaves immediately;
         * the deferred leave-with-undo window lands in nubecita-kc17.4.
         */
        private fun runBulkAction(action: suspend (convoId: String) -> Result<Unit>) {
            val ids = selection.value?.toList() ?: return
            selection.value = null
            viewModelScope.launch {
                var error: ChatsError? = null
                ids.forEach { id -> action(id).onFailure { error = error ?: it.toChatsError() } }
                error?.let { sendEffect(ChatsEffect.ShowActionError(it)) }
            }
        }

        private fun toggleMuteSelected() {
            val selected = selectedConvos()
            if (selected.isEmpty()) return
            // Mute if ANY selected convo is currently unmuted; otherwise unmute all.
            val targetMuted = selected.any { !it.muted }
            runBulkAction { repository.setMuted(it, targetMuted) }
        }

        private fun navigateSingle(toKey: (ConvoListItemUi) -> NavKey) {
            // Single-target actions are only offered at exactly one selection.
            val convo = selectedConvos().singleOrNull() ?: return
            selection.value = null
            sendEffect(ChatsEffect.NavigateTo(toKey(convo)))
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
