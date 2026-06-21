package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.viewModelScope
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
import net.kikin.nubecita.core.common.text.GraphemeCounter
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toMemberMgmtError
import javax.inject.Inject
import kotlin.time.Duration.Companion.milliseconds

/**
 * Presenter for the new-group creation picker.
 *
 * Forks [AddGroupMembersViewModel]'s merge/debounce/flatMapLatest search
 * pipeline and multi-select model (a [NewGroupViewState.selected] chip set,
 * capacity enforcement against [GROUP_MAX_MEMBERS], self-exclusion). It differs
 * in three ways: it is a plain `@Inject` VM (no route — a brand-new group has no
 * convoId and no existing roster to load); it owns a second editor field
 * ([nameFieldState]) with grapheme validation; and its submit action calls
 * [ChatRepository.createGroup] under an [NewGroupViewState.isSubmitting]
 * input-lock that drops further picker edits while the create is in flight.
 *
 * # Text-field ownership
 *
 * [nameFieldState] and [queryFieldState] are exposed as public vals — both are
 * editor surfaces and follow the sanctioned MVI editor exception (CLAUDE.md).
 * The VM observes them via `snapshotFlow` and does NOT route keystrokes through
 * [handleEvent] / [setState].
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class NewGroupViewModel
    @Inject
    constructor(
        private val actorRepository: ActorRepository,
        private val chatRepository: ChatRepository,
        sessionStateProvider: SessionStateProvider,
    ) : MviViewModel<NewGroupViewState, NewGroupEvent, NewGroupEffect>(NewGroupViewState()) {
        /** Editor-exception: the VM owns the group-name field's text + selection. */
        val nameFieldState: TextFieldState = TextFieldState()

        /** Editor-exception: the VM owns the search field's text + selection. */
        val queryFieldState: TextFieldState = TextFieldState()

        // Stable for the session — NewGroup is only reachable inside MainShell (SignedIn).
        private val selfDid: String? = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        // extraBufferCapacity = 1 + tryEmit: at most one retry is buffered (see AddGroupMembersViewModel).
        private val retryTrigger = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

        init {
            // Name validation: a trimmed grapheme count, valid in 1..max.
            snapshotFlow { nameFieldState.text.toString() }
                .onEach { text ->
                    val count = GraphemeCounter.count(text.trim())
                    setState { copy(nameGraphemeCount = count, isNameValid = count in 1..GROUP_NAME_MAX_GRAPHEMES) }
                }.launchIn(viewModelScope)

            val rawStatusFlow =
                merge(
                    snapshotFlow { queryFieldState.text.toString() },
                    retryTrigger.map { queryFieldState.text.toString() },
                ).flatMapLatest { raw ->
                    val q = raw.trim()
                    if (q.isEmpty()) {
                        actorRepository
                            .recentActors(selfDid)
                            .map<List<ActorUi>, NewGroupStatus> { actors ->
                                NewGroupStatus.Recent(actors.toImmutableList())
                            }.catch { emit(NewGroupStatus.Error) } // recent cache read failed; pipeline survives
                    } else {
                        flow {
                            emit(NewGroupStatus.Searching)
                            delay(DEBOUNCE)
                            emit(
                                actorRepository.searchTypeahead(q).fold(
                                    onSuccess = { actors ->
                                        if (actors.isEmpty()) {
                                            NewGroupStatus.NoResults
                                        } else {
                                            NewGroupStatus.Results(actors.toImmutableList())
                                        }
                                    },
                                    onFailure = { NewGroupStatus.Error },
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
                    is NewGroupStatus.Recent -> NewGroupStatus.Recent(rawStatus.items.pickable(selectedDids).toImmutableList())
                    is NewGroupStatus.Results -> NewGroupStatus.Results(rawStatus.items.pickable(selectedDids).toImmutableList())
                    else -> rawStatus
                }
            }.onEach { status -> setState { copy(status = status) } }
                .launchIn(viewModelScope)
        }

        override fun handleEvent(event: NewGroupEvent) {
            when (event) {
                is NewGroupEvent.RecipientToggled -> {
                    if (uiState.value.isSubmitting) return
                    onRecipientToggled(event.did)
                }
                is NewGroupEvent.RecipientRemoved -> {
                    if (uiState.value.isSubmitting) return
                    onRecipientRemoved(event.did)
                }
                NewGroupEvent.CreateTapped -> submit()
                NewGroupEvent.RetryClicked -> retryTrigger.tryEmit(Unit)
            }
        }

        private fun onRecipientToggled(did: String) {
            val current = uiState.value.selected
            if (current.any { it.did == did }) {
                onRecipientRemoved(did)
                return
            }
            // Capacity gate: a brand-new group has no existing roster, so room is just
            // GROUP_MAX_MEMBERS - 1 (the creator implicitly occupies one slot).
            if (current.size >= GROUP_MAX_MEMBERS - 1) return
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
            setState { copy(atCapacity = selected.size >= GROUP_MAX_MEMBERS - 1) }
        }

        private fun submit() {
            if (!uiState.value.canCreate) return
            val name = nameFieldState.text.toString().trim()
            val dids = uiState.value.selected.map { it.did }
            setState { copy(isSubmitting = true) }
            viewModelScope.launch {
                chatRepository
                    .createGroup(name, dids)
                    .onSuccess { convoId -> sendEffect(NewGroupEffect.GroupCreated(convoId)) }
                    .onFailure {
                        setState { copy(isSubmitting = false) }
                        sendEffect(NewGroupEffect.ShowError(it.toMemberMgmtError()))
                    }
            }
        }

        /** The candidate actors in the current status (Recent / Results), or empty. */
        private fun currentItems(): List<ActorUi> =
            when (val status = uiState.value.status) {
                is NewGroupStatus.Recent -> status.items
                is NewGroupStatus.Results -> status.items
                else -> emptyList()
            }

        /**
         * Drop actors that can't be picked: self and already-selected recipients
         * (they appear as chips, not in the list). A brand-new group has no existing
         * members, so there is no roster to exclude.
         */
        private fun List<ActorUi>.pickable(selectedDids: Set<String>): List<ActorUi> = filter { it.did != selfDid && it.did !in selectedDids }

        private companion object {
            val DEBOUNCE = 250.milliseconds
        }
    }
