package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.ActorRepository
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.chats.api.AddGroupMembers
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toMemberMgmtError
import kotlin.time.Duration.Companion.milliseconds

/**
 * Presenter for the add-group-members recipient picker.
 *
 * Forks [NewChatViewModel]'s merge/debounce/flatMapLatest search pipeline and
 * adds a multi-select model: a [AddGroupMembersViewState.selected] chip set,
 * capacity enforcement against [GROUP_MAX_MEMBERS] (existing roster + picked),
 * exclusion of existing members from the candidate lists, and an Add action that
 * calls [ChatRepository.addMembers].
 *
 * On construction it best-effort loads the current roster ([ChatRepository.getConvoMembers])
 * to seed [existingDids] / [memberCount]; a load failure is ignored — the picker
 * still works, it just can't pre-filter or pre-fill the capacity counter.
 *
 * # Text-field ownership
 *
 * [queryFieldState] is exposed as a public val — the search field is an editor
 * surface and follows the sanctioned MVI editor exception (CLAUDE.md). The VM
 * observes it via `snapshotFlow` and does NOT route keystrokes through
 * [handleEvent] / [setState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel(assistedFactory = AddGroupMembersViewModel.Factory::class)
class AddGroupMembersViewModel
    @AssistedInject
    constructor(
        @Assisted route: AddGroupMembers,
        private val actorRepository: ActorRepository,
        private val chatRepository: ChatRepository,
        sessionStateProvider: SessionStateProvider,
    ) : MviViewModel<AddGroupMembersViewState, AddMembersEvent, AddMembersEffect>(AddGroupMembersViewState()) {
        @AssistedFactory
        interface Factory {
            fun create(route: AddGroupMembers): AddGroupMembersViewModel
        }

        /** Editor-exception: the VM owns the search field's text + selection. */
        val queryFieldState: TextFieldState = TextFieldState()

        private val convoId: String = route.convoId

        // Stable for the session — AddGroupMembers is only reachable inside MainShell (SignedIn).
        private val selfDid: String? = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        // Seeded best-effort from the roster load; until it resolves both stay empty/0.
        private var existingDids: Set<String> = emptySet()
        private var memberCount: Int = 0

        // extraBufferCapacity = 1 + tryEmit: at most one retry is buffered (see NewChatViewModel).
        private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        init {
            // Best-effort roster load to seed exclusion set + capacity counter. A failure is
            // ignored: the picker still works, it just can't pre-filter or pre-count.
            viewModelScope.launch {
                chatRepository.getConvoMembers(convoId).onSuccess { page ->
                    existingDids = page.members.map { it.did }.toSet()
                    memberCount = page.members.size
                    recomputeCapacity()
                }
            }

            val rawStatusFlow =
                merge(
                    snapshotFlow { queryFieldState.text.toString() },
                    retryTrigger.map { queryFieldState.text.toString() },
                ).flatMapLatest { raw ->
                    val q = raw.trim()
                    if (q.isEmpty()) {
                        actorRepository
                            .recentActors(selfDid)
                            .map<List<ActorUi>, AddMembersStatus> { actors ->
                                AddMembersStatus.Recent(actors.toImmutableList())
                            }.catch { emit(AddMembersStatus.Error) } // recent cache read failed; pipeline survives
                    } else {
                        flow {
                            emit(AddMembersStatus.Searching)
                            delay(DEBOUNCE)
                            emit(
                                actorRepository.searchTypeahead(q).fold(
                                    onSuccess = { actors ->
                                        if (actors.isEmpty()) {
                                            AddMembersStatus.NoResults
                                        } else {
                                            AddMembersStatus.Results(actors.toImmutableList())
                                        }
                                    },
                                    onFailure = { AddMembersStatus.Error },
                                ),
                            )
                        }
                    }
                }

            combine(
                rawStatusFlow,
                // uiState is StateFlow-backed (not Compose snapshot state), so observe the
                // selection via map/distinctUntilChanged — a snapshotFlow wouldn't re-emit.
                uiState.map { it.selected }.distinctUntilChanged(),
            ) { rawStatus, selected ->
                val selectedDids = selected.mapTo(mutableSetOf()) { it.did }
                when (rawStatus) {
                    is AddMembersStatus.Recent -> AddMembersStatus.Recent(rawStatus.items.pickable(selectedDids).toImmutableList())
                    is AddMembersStatus.Results -> AddMembersStatus.Results(rawStatus.items.pickable(selectedDids).toImmutableList())
                    else -> rawStatus
                }
            }.onEach { status -> setState { copy(status = status) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: AddMembersEvent) {
            when (event) {
                is AddMembersEvent.RecipientToggled -> onRecipientToggled(event.did)
                is AddMembersEvent.RecipientRemoved -> onRecipientRemoved(event.did)
                AddMembersEvent.AddTapped -> submit()
                AddMembersEvent.RetryClicked -> retryTrigger.tryEmit(Unit)
            }
        }

        private fun onRecipientToggled(did: String) {
            val current = uiState.value.selected
            if (current.any { it.did == did }) {
                onRecipientRemoved(did)
                return
            }
            // Capacity gate: existing roster + already-picked must leave room for one more.
            if (memberCount + current.size >= GROUP_MAX_MEMBERS) return
            val actor = currentItems().firstOrNull { it.did == did } ?: return
            setState {
                copy(
                    selected =
                        (selected + RecipientUi(actor.did, actor.handle, actor.displayName, actor.avatarUrl))
                            .toImmutableList(),
                )
            }
            recomputeCapacity()
        }

        private fun onRecipientRemoved(did: String) {
            setState { copy(selected = selected.filterNot { it.did == did }.toImmutableList()) }
            recomputeCapacity()
        }

        private fun recomputeCapacity() {
            setState { copy(atCapacity = memberCount + selected.size >= GROUP_MAX_MEMBERS) }
        }

        private fun submit() {
            val dids = uiState.value.selected.map { it.did }
            if (dids.isEmpty() || uiState.value.isSubmitting) return
            setState { copy(isSubmitting = true) }
            viewModelScope.launch {
                chatRepository
                    .addMembers(convoId, dids)
                    .onSuccess {
                        // Defensive: MembersAdded pops the screen, which normally tears the VM
                        // down before this matters — but reset isSubmitting anyway so a stuck
                        // spinner can't happen if the pop ever becomes async.
                        setState { copy(isSubmitting = false) }
                        sendEffect(AddMembersEffect.MembersAdded)
                    }.onFailure {
                        setState { copy(isSubmitting = false) }
                        sendEffect(AddMembersEffect.ShowError(it.toMemberMgmtError()))
                    }
            }
        }

        /** The candidate actors in the current status (Recent / Results), or empty. */
        private fun currentItems(): List<ActorUi> =
            when (val status = uiState.value.status) {
                is AddMembersStatus.Recent -> status.items
                is AddMembersStatus.Results -> status.items
                else -> emptyList()
            }

        /**
         * Drop actors that can't be picked: self, current roster members, and
         * already-selected recipients (they appear as chips, not in the list).
         */
        private fun List<ActorUi>.pickable(selectedDids: Set<String>): List<ActorUi> = filter { it.did != selfDid && it.did !in existingDids && it.did !in selectedDids }

        private companion object {
            val DEBOUNCE = 250.milliseconds
        }
    }
