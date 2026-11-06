package net.kikin.nubecita.feature.profile.impl

import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.toPersistentMap
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.analytics.ActorAction
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.InteractActor
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.feature.profile.api.ProfileConnections
import net.kikin.nubecita.feature.profile.impl.data.ConnectionFollowStore
import net.kikin.nubecita.feature.profile.impl.data.ConnectionPage
import net.kikin.nubecita.feature.profile.impl.data.ConnectionRowUi
import net.kikin.nubecita.feature.profile.impl.data.DELETED_ACCOUNT_HANDLE
import net.kikin.nubecita.feature.profile.impl.data.ProfileConnectionsRepository

/** Presenter for the followers and following screen. */
@HiltViewModel(assistedFactory = ProfileConnectionsViewModel.Factory::class)
internal class ProfileConnectionsViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: ProfileConnections,
        private val repository: ProfileConnectionsRepository,
        private val followRepository: FollowRepository,
        private val analytics: AnalyticsClient,
        private val followStore: ConnectionFollowStore,
    ) : MviViewModel<ProfileConnectionsState, ProfileConnectionsEvent, ProfileConnectionsEffect>(
            ProfileConnectionsState(
                selectedTab = route.initialTab,
                title = route.title,
                followersCount = route.followersCount,
                followsCount = route.followsCount,
            ),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: ProfileConnections): ProfileConnectionsViewModel
        }

        val followers: Flow<PagingData<ConnectionRowUi>> = pagedList { repository.getFollowers(route.actor, it) }

        val following: Flow<PagingData<ConnectionRowUi>> = pagedList { repository.getFollows(route.actor, it) }

        private fun pagedList(load: suspend (String?) -> Result<ConnectionPage>): Flow<PagingData<ConnectionRowUi>> =
            Pager(PagingConfig(pageSize = PAGE_SIZE)) { ProfileConnectionsPagingSource(load) }
                .flow
                .cachedIn(viewModelScope)

        init {
            followStore.committed
                .onEach(::applyCommitted)
                .launchIn(viewModelScope)
        }

        /** A write in flight here wins: it records its own result when it lands. */
        private fun applyCommitted(committed: Map<String, String?>) {
            setState {
                val updated = overlay.toPersistentMap().builder()
                committed.forEach { (did, followUri) ->
                    if (updated[did] !is ConnectionFollowState.Pending) {
                        updated[did] = ConnectionFollowState.Committed(followUri)
                    }
                }
                copy(overlay = updated.build())
            }
        }

        override fun handleEvent(event: ProfileConnectionsEvent) {
            when (event) {
                is ProfileConnectionsEvent.TabSelected -> setState { copy(selectedTab = event.tab) }
                is ProfileConnectionsEvent.FollowTapped -> onFollowTapped(event.row)
                is ProfileConnectionsEvent.RowTapped -> {
                    // A deleted account has no profile to open.
                    if (event.row.actor.handle != DELETED_ACCOUNT_HANDLE) {
                        sendEffect(ProfileConnectionsEffect.NavigateToProfile(event.row.actor.handle))
                    }
                }
            }
        }

        private fun onFollowTapped(row: ConnectionRowUi) {
            val did = row.actor.did
            val previous = uiState.value.overlay[did]
            // Also what keeps an unfollow from firing before the follow it
            // would undo has returned a URI.
            if (previous is ConnectionFollowState.Pending) return
            val followUri = if (previous is ConnectionFollowState.Committed) previous.followUri else row.followUri
            if (followUri == null) {
                analytics.log(InteractActor(ActorAction.Follow, PostSurface.ProfileConnections))
                write(did, previous, ConnectionFollowState.Pending(following = true)) {
                    followRepository.follow(did).map { ConnectionFollowState.Committed(it) }
                }
            } else {
                analytics.log(InteractActor(ActorAction.Unfollow, PostSurface.ProfileConnections))
                write(did, previous, ConnectionFollowState.Pending(following = false)) {
                    followRepository.unfollow(followUri).map { ConnectionFollowState.Committed(null) }
                }
            }
        }

        private fun write(
            did: String,
            previous: ConnectionFollowState?,
            pending: ConnectionFollowState.Pending,
            request: suspend () -> Result<ConnectionFollowState.Committed>,
        ) {
            setOverlay(did, pending)
            viewModelScope.launch {
                request()
                    .onSuccess {
                        setOverlay(did, it)
                        followStore.record(did, it.followUri)
                    }.onFailure {
                        setOverlay(did, previous)
                        sendEffect(ProfileConnectionsEffect.ShowError)
                    }
            }
        }

        private fun setOverlay(
            did: String,
            value: ConnectionFollowState?,
        ) {
            setState {
                val updated = overlay.toPersistentMap()
                copy(overlay = if (value == null) updated.remove(did) else updated.put(did, value))
            }
        }

        private companion object {
            const val PAGE_SIZE = 50
        }
    }
