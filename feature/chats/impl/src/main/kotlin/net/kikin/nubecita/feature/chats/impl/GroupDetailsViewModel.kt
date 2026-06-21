package net.kikin.nubecita.feature.chats.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.FollowRepository
import net.kikin.nubecita.feature.chats.api.GroupDetails
import net.kikin.nubecita.feature.chats.impl.data.ChatRepository
import net.kikin.nubecita.feature.chats.impl.data.toChatError
import net.kikin.nubecita.feature.profile.api.Profile

/**
 * MVI presenter for the group-details screen.
 *
 * Receives the [GroupDetails] NavKey via assisted injection (matching
 * [ChatViewModel]). Auto-loads on construction: `getConvo` resolves the group's
 * name (a non-group header is a misuse → [ChatError.ConvoNotFound]); then
 * `getConvoMembers` paginates the roster (one page in practice; the loop is
 * defensive should the 50-member cap ever rise). Supports per-member optimistic
 * Follow toggle (mirroring [ChatViewModel.onToggleReaction]'s in-flight guard +
 * reconcile/rollback shape), Leave, Mute, and member→Profile navigation.
 *
 * The muted flag is read from the convo-list cache entry for this convoId
 * ([loadMutedFromCache]) — `getConvo` doesn't surface muted on the header, and
 * the cache (`observeConvos`) already carries `ConvoRowUi.muted`. It defaults to
 * `false` when the inbox was never opened; [GroupDetailsEvent.ToggleMute] flips
 * it optimistically and persists via `setMuted`.
 */
@HiltViewModel(assistedFactory = GroupDetailsViewModel.Factory::class)
class GroupDetailsViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: GroupDetails,
        private val repository: ChatRepository,
        private val followRepository: FollowRepository,
    ) : MviViewModel<GroupDetailsViewState, GroupDetailsEvent, GroupDetailsEffect>(GroupDetailsViewState()) {
        @AssistedFactory
        interface Factory {
            fun create(route: GroupDetails): GroupDetailsViewModel
        }

        private val convoId: String = route.convoId
        private var inFlightLoad: Job? = null

        /**
         * Member DIDs with a follow/unfollow call in flight. Guards a rapid
         * double-tap of the same row from issuing two racing (non-idempotent)
         * calls; a second toggle of the same DID is dropped until the first
         * resolves. Mirrors [ChatViewModel.inFlightReactions].
         */
        private val inFlightFollows = mutableSetOf<String>()

        init {
            launchLoad()
        }

        override fun handleEvent(event: GroupDetailsEvent) {
            when (event) {
                GroupDetailsEvent.Refresh -> launchLoad()
                GroupDetailsEvent.RetryClicked -> launchLoad()
                GroupDetailsEvent.BackPressed -> sendEffect(GroupDetailsEffect.NavigateBack)
                is GroupDetailsEvent.ToggleFollow -> onToggleFollow(event.did)
                GroupDetailsEvent.LeaveTapped -> onLeave()
                GroupDetailsEvent.ToggleMute -> onToggleMute()
                is GroupDetailsEvent.MemberTapped -> onMemberTapped(event.did)
            }
        }

        private fun launchLoad() {
            if (inFlightLoad?.isActive == true) return
            inFlightLoad =
                viewModelScope.launch {
                    repository
                        .getConvo(convoId)
                        .onSuccess { convo ->
                            val header = convo.header
                            if (header !is ChatHeader.Group) {
                                // A group-details route on a direct convo is a misuse.
                                setState { copy(status = GroupDetailsLoadStatus.InitialError(ChatError.ConvoNotFound)) }
                                return@onSuccess
                            }
                            setState { copy(name = header.name, muted = loadMutedFromCache()) }
                            loadMembers()
                        }.onFailure { throwable ->
                            setState { copy(status = GroupDetailsLoadStatus.InitialError(throwable.toChatError())) }
                        }
                    inFlightLoad = null
                }
        }

        /**
         * Accumulate every member page. In practice a single `limit=100` call
         * returns the full roster (groups cap at 50), but the cursor loop is
         * defensive should the cap ever rise. Any page failure surfaces as an
         * [GroupDetailsLoadStatus.InitialError].
         */
        private suspend fun loadMembers() {
            val accumulated = mutableListOf<GroupMemberUi>()
            var cursor: String? = null
            do {
                val result = repository.getConvoMembers(convoId, cursor)
                val page = result.getOrNull()
                if (page == null) {
                    val error = result.exceptionOrNull()?.toChatError() ?: ChatError.Unknown(null)
                    setState { copy(status = GroupDetailsLoadStatus.InitialError(error)) }
                    return
                }
                accumulated += page.members
                cursor = page.cursor
            } while (cursor != null)
            val members = accumulated.toImmutableList()
            setState {
                copy(status = GroupDetailsLoadStatus.Loaded(members = members, memberCount = members.size))
            }
        }

        /**
         * Read this convo's muted flag from the in-memory convo-list cache. `getConvo`
         * doesn't surface muted on the header, so the cache (`observeConvos`, carrying
         * `ConvoRowUi.muted`) is the source. Defaults `false` when the inbox was never
         * opened (cache is `null` / lacks this convo); the toggle still persists
         * server-side regardless.
         */
        private fun loadMutedFromCache(): Boolean =
            repository
                .observeConvos()
                .value
                ?.firstOrNull { it.convoId == convoId }
                ?.muted ?: false

        /**
         * Optimistic per-member follow toggle: flip the member's [GroupMemberUi.followState]
         * to [FollowState.InFlight], then call follow/unfollow. On success reconcile to
         * `Following(uri)` / `NotFollowing(null)`; on failure restore the exact prior follow
         * state and emit [GroupDetailsEffect.ShowError]. An in-flight DID guard drops a rapid
         * second tap so the two (non-idempotent) calls can't race. No-ops when not loaded, the
         * member is missing, or the member is the viewer.
         */
        private fun onToggleFollow(did: String) {
            val loaded = uiState.value.status as? GroupDetailsLoadStatus.Loaded ?: return
            if (did in inFlightFollows) return
            val target = loaded.members.firstOrNull { it.did == did } ?: return
            if (target.isViewer) return
            val priorState = target.followState
            val priorUri = target.followUri
            val wasFollowing = priorState == FollowState.Following
            inFlightFollows += did
            updateMember(did) { it.copy(followState = FollowState.InFlight) }
            viewModelScope.launch {
                // finally (not a trailing statement) so a cancellation between the
                // suspending call and cleanup can't leave the DID permanently in the
                // in-flight set — which would wedge the row's Follow button at InFlight.
                try {
                    if (wasFollowing) {
                        val uri = priorUri
                        val result =
                            if (uri != null) {
                                followRepository.unfollow(uri)
                            } else {
                                // Defensive: Following with no uri can't be undone; treat as already not-following.
                                Result.success(Unit)
                            }
                        result
                            .onSuccess { updateMember(did) { it.copy(followState = FollowState.NotFollowing, followUri = null) } }
                            .onFailure {
                                updateMember(did) { it.copy(followState = priorState, followUri = priorUri) }
                                sendEffect(GroupDetailsEffect.ShowError(it.toChatError()))
                            }
                    } else {
                        followRepository
                            .follow(did)
                            .onSuccess { uri -> updateMember(did) { it.copy(followState = FollowState.Following, followUri = uri) } }
                            .onFailure {
                                updateMember(did) { it.copy(followState = priorState, followUri = priorUri) }
                                sendEffect(GroupDetailsEffect.ShowError(it.toChatError()))
                            }
                    }
                } finally {
                    inFlightFollows -= did
                }
            }
        }

        /** Apply [transform] to the member with [did] inside the [GroupDetailsLoadStatus.Loaded] roster. */
        private fun updateMember(
            did: String,
            transform: (GroupMemberUi) -> GroupMemberUi,
        ) {
            setState {
                val loaded = status as? GroupDetailsLoadStatus.Loaded ?: return@setState this
                val members: ImmutableList<GroupMemberUi> =
                    loaded.members.map { if (it.did == did) transform(it) else it }.toImmutableList()
                copy(status = loaded.copy(members = members))
            }
        }

        private fun onLeave() {
            viewModelScope.launch {
                repository
                    .leaveConvo(convoId)
                    .onSuccess { sendEffect(GroupDetailsEffect.NavigateBack) }
                    .onFailure { sendEffect(GroupDetailsEffect.ShowError(it.toChatError())) }
            }
        }

        private fun onToggleMute() {
            val target = !uiState.value.muted
            viewModelScope.launch {
                repository
                    .setMuted(convoId, target)
                    .onSuccess { setState { copy(muted = target) } }
                    .onFailure { sendEffect(GroupDetailsEffect.ShowError(it.toChatError())) }
            }
        }

        private fun onMemberTapped(did: String) {
            val loaded = uiState.value.status as? GroupDetailsLoadStatus.Loaded ?: return
            val member = loaded.members.firstOrNull { it.did == did } ?: return
            sendEffect(GroupDetailsEffect.NavigateTo(Profile(handle = member.handle)))
        }
    }
