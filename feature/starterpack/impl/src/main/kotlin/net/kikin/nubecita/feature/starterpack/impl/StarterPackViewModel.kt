package net.kikin.nubecita.feature.starterpack.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.FollowOutcome
import net.kikin.nubecita.core.postinteractions.FollowOverlay
import net.kikin.nubecita.core.postinteractions.FollowToggle
import net.kikin.nubecita.core.postinteractions.withFollowOverride
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.starterpack.api.StarterPack
import net.kikin.nubecita.feature.starterpack.impl.data.StarterPackRepository

/**
 * Presenter for the starter-pack screen: the pack's header, then its members paged from the
 * pack's list, 50 at a time.
 *
 * Follow state has one source, [FollowOverlay]: a follow made here shows on the People tab and
 * the Explore suggestions, and one made there shows on these rows, including pages that arrive
 * later. Taps go through the shared [FollowToggle], which owns the optimistic flip, the confirm
 * and the rollback; this keeps only the error message for the screen. There is no "Follow all".
 *
 * A load started by [StarterPackEvent.Retry] supersedes the one before it: a page that arrives
 * for a superseded load is dropped.
 */
@HiltViewModel(assistedFactory = StarterPackViewModel.Factory::class)
internal class StarterPackViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: StarterPack,
        private val repository: StarterPackRepository,
        private val followToggle: FollowToggle,
        private val followOverlay: FollowOverlay,
        sessionStateProvider: SessionStateProvider,
    ) : MviViewModel<StarterPackState, StarterPackEvent, StarterPackEffect>(StarterPackState()) {
        @AssistedFactory
        interface Factory {
            fun create(route: StarterPack): StarterPackViewModel
        }

        /** Bumps on every (re)load; a result carrying an older value is dropped. */
        private var loadEpoch = 0
        private var loadJob: Job? = null

        init {
            followOverlay.overrides
                .onEach { overrides -> mapMembers { it.withFollowOverride(overrides) } }
                .launchIn(viewModelScope)
            sessionStateProvider.state
                .map { (it as? SessionState.SignedIn)?.did }
                .distinctUntilChanged()
                .onEach { did -> setState { copy(viewerDid = did) } }
                .launchIn(viewModelScope)
            load()
        }

        override fun handleEvent(event: StarterPackEvent) {
            when (event) {
                StarterPackEvent.LoadMore -> loadMore()
                StarterPackEvent.Retry -> load()
                is StarterPackEvent.MemberTapped -> sendEffect(StarterPackEffect.NavigateToProfile(event.actor))
                is StarterPackEvent.FeedTapped -> sendEffect(StarterPackEffect.NavigateToFeed(event.uri, event.displayName))
                is StarterPackEvent.FollowTapped -> onFollowTapped(event.did)
            }
        }

        private fun load() {
            val epoch = ++loadEpoch
            loadJob?.cancel()
            setState { copy(status = StarterPackLoadStatus.InitialLoading) }
            loadJob =
                viewModelScope.launch {
                    val pack =
                        repository.getStarterPack(route.uri).getOrElse { throwable ->
                            if (epoch == loadEpoch) setState { copy(status = StarterPackLoadStatus.InitialError(throwable.toStarterPackError())) }
                            return@launch
                        }
                    val listUri = pack.listUri
                    if (listUri == null) {
                        if (epoch == loadEpoch) setState { copy(status = StarterPackLoadStatus.Empty(pack)) }
                        return@launch
                    }
                    repository
                        .getMembers(listUri = listUri, cursor = null)
                        .onSuccess { page ->
                            if (epoch != loadEpoch) return@onSuccess
                            val status =
                                if (page.members.isEmpty()) {
                                    StarterPackLoadStatus.Empty(pack)
                                } else {
                                    StarterPackLoadStatus.Loaded(
                                        pack = pack,
                                        members = page.members.distinctBy { it.did }.withOverlay(),
                                        nextCursor = page.nextCursor,
                                        endReached = page.nextCursor == null,
                                    )
                                }
                            setState { copy(status = status) }
                        }.onFailure { throwable ->
                            if (epoch == loadEpoch) setState { copy(status = StarterPackLoadStatus.InitialError(throwable.toStarterPackError())) }
                        }
                }
        }

        private fun loadMore() {
            val status = uiState.value.status as? StarterPackLoadStatus.Loaded ?: return
            if (status.endReached || status.isAppending) return
            val listUri = status.pack.listUri ?: return
            val epoch = loadEpoch
            setState { copy(status = status.copy(isAppending = true)) }
            viewModelScope.launch {
                repository
                    .getMembers(listUri = listUri, cursor = status.nextCursor)
                    .onSuccess { page ->
                        if (epoch != loadEpoch) return@onSuccess
                        val current = uiState.value.status as? StarterPackLoadStatus.Loaded ?: return@onSuccess
                        val appended = (current.members + page.members.withOverlay()).distinctBy { it.did }.toImmutableList()
                        setState {
                            copy(
                                status =
                                    current.copy(
                                        members = appended,
                                        nextCursor = page.nextCursor,
                                        endReached = page.nextCursor == null,
                                        isAppending = false,
                                    ),
                            )
                        }
                    }.onFailure { throwable ->
                        if (epoch != loadEpoch) return@onFailure
                        val current = uiState.value.status as? StarterPackLoadStatus.Loaded ?: return@onFailure
                        setState { copy(status = current.copy(isAppending = false)) }
                        sendEffect(StarterPackEffect.ShowAppendError(throwable.toStarterPackError()))
                    }
            }
        }

        private fun onFollowTapped(did: String) {
            // The account's own row has no Follow action.
            if (did == uiState.value.viewerDid) return
            val actor = loadedMembers().find { it.did == did } ?: return
            followToggle.toggleActor(
                scope = viewModelScope,
                actor = actor,
                updateActor = { transform -> mapMembers { if (it.did == did) transform(it) else it } },
            ) { outcome ->
                when (outcome) {
                    FollowOutcome.Followed, FollowOutcome.Unfollowed -> Unit
                    FollowOutcome.FollowFailed -> sendEffect(StarterPackEffect.FollowFailed(wasUnfollow = false))
                    FollowOutcome.UnfollowFailed -> sendEffect(StarterPackEffect.FollowFailed(wasUnfollow = true))
                }
            }
        }

        private fun List<ActorUi>.withOverlay() = followOverlay.current().let { overrides -> map { it.withFollowOverride(overrides) }.toImmutableList() }

        private fun loadedMembers(): List<ActorUi> = (uiState.value.status as? StarterPackLoadStatus.Loaded)?.members.orEmpty()

        /** Rewrites the loaded members on the CURRENT state; a no-op when nothing is loaded. */
        private fun mapMembers(transform: (ActorUi) -> ActorUi) {
            setState {
                val status = status as? StarterPackLoadStatus.Loaded ?: return@setState this
                copy(status = status.copy(members = status.members.map(transform).toImmutableList()))
            }
        }
    }
