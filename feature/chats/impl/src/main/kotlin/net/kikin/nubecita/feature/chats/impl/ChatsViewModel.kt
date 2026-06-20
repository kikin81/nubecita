package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
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
        // Commits a deferred leave (timeout / supersede / screen-leave flush) must
        // outlive viewModelScope — fired here, not on viewModelScope, so navigating
        // away mid-window still leaves the convo. nubecita-kc17.4 (design D-7).
        @param:ApplicationScope private val applicationScope: CoroutineScope,
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

        // Leave-with-undo (nubecita-kc17.4). Convos pending an optimistic leave are
        // HIDDEN from the projected list but still live in the cache, so Undo is a
        // pure client-side un-hide (zero network). `leaveConvo` fires only when the
        // batch commits (undo window elapsed / superseded / screen-leave flush).
        private val pendingLeave = MutableStateFlow<PersistentSet<String>>(persistentSetOf())

        // The single currently-undoable batch + its token. Only one batch is undoable
        // at a time; starting another commits this one first (Gmail-style supersede).
        // The token guards a stale Undo tap from a superseded/committed batch.
        private var undoableLeave: PersistentSet<String> = persistentSetOf()
        private var leaveToken = 0L
        private var leaveTimerJob: Job? = null

        init {
            // Optimistic-leave projection: hide convos pending a deferred leave from
            // both segment lists before anything else reads them. Hidden rows stay in
            // the cache (Undo un-hides without a refetch); the badge count drops too
            // when a hidden row is a request.
            val visibleAccepted = acceptedConvos.combine(pendingLeave) { list, hidden -> list.hide(hidden) }
            val visibleRequests = requestConvos.combine(pendingLeave) { list, hidden -> list.hide(hidden) }

            // Project (active segment's items × phase) into status, and the request
            // count into the badge. Both caches are the source of truth for items;
            // the per-segment phase drives loading / refreshing / error.
            val projected =
                combine(
                    visibleAccepted,
                    visibleRequests,
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
                // Enter selection mode, or extend an existing selection — long-
                // pressing another row while already selecting adds it rather
                // than resetting to just that row.
                is ChatsEvent.ConvoLongPressed ->
                    selection.value = (selection.value ?: persistentSetOf()).add(event.convoId)
                is ChatsEvent.SelectionToggled -> toggleSelection(event.convoId)
                ChatsEvent.ClearSelection -> selection.value = null
                ChatsEvent.LeaveSelected -> startLeaveWithUndo()
                ChatsEvent.AcceptSelected -> runBulkAction { repository.acceptConvo(it) }
                ChatsEvent.ToggleMuteSelected -> toggleMuteSelected()
                ChatsEvent.ProfileSelected -> navigateSingle { Profile(handle = it.otherUserHandle) }
                ChatsEvent.ReportSelected -> navigateSingle { Report.forAccount(it.otherUserDid) }
                ChatsEvent.BlockSelected -> navigateSingle { Block.forAccount(did = it.otherUserDid, handle = it.otherUserHandle) }
                is ChatsEvent.UndoLeaveTapped -> undoLeave(event.token)
            }
        }

        private fun toggleSelection(convoId: String) {
            val current = selection.value ?: persistentSetOf()
            val next = if (convoId in current) current.remove(convoId) else current.add(convoId)
            // Toggling the last selected convo off exits selection mode.
            selection.value = if (next.isEmpty()) null else next
        }

        /** The currently-displayed list for the active segment. */
        private fun activeList(): List<ConvoRowUi> = (if (activeSegment.value == ChatsSegment.Chats) acceptedConvos.value else requestConvos.value).orEmpty()

        private fun selectedConvos(): List<ConvoRowUi> {
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
            // Order by the on-screen list (selectedConvos preserves activeList
            // order), not the selection set's iteration order, so the action
            // sequence — and which failure becomes the surfaced "first" — is
            // deterministic and matches what the user sees.
            val ids = selectedConvos().map { it.convoId }
            if (ids.isEmpty()) return
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

        private fun navigateSingle(toKey: (ConvoRowUi.Direct) -> NavKey) {
            // Profile / Report / Block are single-user actions; they don't apply to a
            // group selection in Phase 1, so no-op when the selected row isn't Direct
            // (and, as before, only fire at exactly one selection).
            val convo = selectedConvos().singleOrNull() as? ConvoRowUi.Direct ?: return
            selection.value = null
            sendEffect(ChatsEffect.NavigateTo(toKey(convo)))
        }

        /**
         * Optimistically leave the selected convos: hide them, start the undo
         * window, and exit selection. `leaveConvo` is NOT called yet — Undo within
         * the window is a pure un-hide. Any prior pending batch commits immediately
         * (single-batch / Gmail supersede).
         */
        private fun startLeaveWithUndo() {
            val ids = selectedConvos().map { it.convoId }.toPersistentSet()
            if (ids.isEmpty()) return
            selection.value = null
            // Cancel the prior batch's timer FIRST, then commit it (supersede). Doing
            // this before bumping the token means the old timer can never observe the
            // new batch — belt-and-suspenders over the token guard, which already makes
            // a stale timer inert (it captures its own token and compares to leaveToken).
            leaveTimerJob?.cancel()
            commitPendingLeave()
            undoableLeave = ids
            val token = ++leaveToken
            pendingLeave.update { it.addAll(ids) }
            sendEffect(ChatsEffect.ShowLeaveUndo(token = token, count = ids.size))
            // VM-owned dismiss timer (not the snackbar's): commit when the window
            // elapses, guarded by the token so a superseded batch's timer is inert.
            leaveTimerJob =
                viewModelScope.launch {
                    delay(LEAVE_UNDO_WINDOW_MS)
                    if (token == leaveToken) {
                        commitPendingLeave()
                        sendEffect(ChatsEffect.HideLeaveUndo)
                    }
                }
        }

        /** Undo the current pending leave: un-hide the rows (zero network). */
        private fun undoLeave(token: Long) {
            if (token != leaveToken || undoableLeave.isEmpty()) return
            leaveTimerJob?.cancel()
            val restored = undoableLeave
            undoableLeave = persistentSetOf()
            pendingLeave.update { it.removeAll(restored) }
        }

        /**
         * Commit the undoable batch: fire `leaveConvo` per convo on the application
         * scope (so it outlives this VM), un-hiding each row as its call settles.
         * A no-op when nothing is pending. A row stays hidden until its `leaveConvo`
         * settles, so there's no reappear-then-vanish flicker; the per-id `finally`
         * un-hides incrementally and guarantees cleanup even if a call throws (a
         * failed leave leaves the cache untouched, so the un-hide restores the row —
         * the next refresh reconciles).
         */
        private fun commitPendingLeave() {
            val ids = undoableLeave
            if (ids.isEmpty()) return
            undoableLeave = persistentSetOf()
            val pending = pendingLeave
            applicationScope.launch {
                ids.forEach { id ->
                    try {
                        repository.leaveConvo(id)
                    } finally {
                        pending.update { it.remove(id) }
                    }
                }
            }
        }

        /** Filter convos pending an optimistic leave out of a cache snapshot. */
        private fun ImmutableList<ConvoRowUi>?.hide(hidden: PersistentSet<String>): ImmutableList<ConvoRowUi>? =
            when {
                this == null -> null
                hidden.isEmpty() -> this
                else -> filterNot { it.convoId in hidden }.toImmutableList()
            }

        override fun onCleared() {
            // Normal screen clearance (navigate away) FLUSHES a pending leave — leaving
            // a chat then navigating away must leave it, not silently restore it. The
            // commit runs on applicationScope, so it survives this teardown. Process
            // death skips onCleared, so an un-acknowledged leave is dropped (we never
            // persist pending leaves to fire post-death). Design D-8.
            commitPendingLeave()
            super.onCleared()
        }

        private fun statusFor(
            items: ImmutableList<ConvoRowUi>?,
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

        private companion object {
            // Undo window for a deferred leave. Matches Material's "Long" snackbar
            // feel; the snackbar itself is shown Indefinite and dismissed by the VM
            // (HideLeaveUndo) when this elapses, so the affordance and the commit
            // stay in lock-step regardless of the snackbar's own duration.
            const val LEAVE_UNDO_WINDOW_MS = 5_000L
        }
    }
