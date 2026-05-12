package net.kikin.nubecita.feature.profile.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileTabPage
import java.io.IOException

/**
 * Presenter for the Profile screen — both own (`handle = null`) and
 * other-user (`handle = "..."`) variants share this one class.
 *
 * On construction, kicks off **four concurrent loads** (header +
 * Posts + Replies + Media) — per `openspec/.../design.md` Decision 3
 * (eager fetch beats lazy-on-tab-select for the wireframes' visual
 * model and for tab-switch latency). Each load updates its own state
 * field independently; one tab failure does NOT mask sibling
 * successes (per the spec's `Per-tab independent failure` scenario).
 *
 * `Profile(handle = null)` resolves to the authenticated user's DID
 * via `:core:auth`'s [SessionStateProvider]. The repository takes a
 * non-null `actor: String` — the auth-aware resolution stays in the
 * VM so the repository remains pure (sets us up for future
 * multi-account support, see Bead C's brainstorming note).
 */
@HiltViewModel(assistedFactory = ProfileViewModel.Factory::class)
internal class ProfileViewModel
    @AssistedInject
    constructor(
        @Assisted private val route: Profile,
        private val repository: ProfileRepository,
        private val sessionStateProvider: SessionStateProvider,
    ) : MviViewModel<ProfileScreenViewState, ProfileEvent, ProfileEffect>(
            ProfileScreenViewState(
                handle = route.handle,
                ownProfile = route.handle == null,
            ),
        ) {
        @AssistedFactory
        interface Factory {
            fun create(route: Profile): ProfileViewModel
        }

        /**
         * Per-tab pagination guard. Tracks the in-flight `LoadMore`
         * coroutine for each tab so repeated `LoadMore` events
         * (e.g. fast-scrolling near the end of a list) don't fan
         * out into multiple concurrent `fetchTab` calls with the
         * same cursor. Cleared in the success / failure branches.
         */
        private val activeLoadMoreJobs = mutableMapOf<ProfileTab, Job>()

        init {
            val actor = resolveActor()
            if (actor == null) {
                sendEffect(ProfileEffect.ShowError(ProfileError.Unknown(NOT_SIGNED_IN_REASON)))
            } else {
                launchInitialLoads(actor)
            }
        }

        override fun handleEvent(event: ProfileEvent) {
            when (event) {
                is ProfileEvent.TabSelected -> onTabSelected(event.tab)
                is ProfileEvent.PostTapped -> sendEffect(ProfileEffect.NavigateToPost(event.postUri))
                is ProfileEvent.HandleTapped -> onHandleTapped(event.handle)
                ProfileEvent.Refresh -> onRefresh()
                is ProfileEvent.LoadMore -> onLoadMore(event.tab)
                ProfileEvent.FollowTapped ->
                    sendEffect(ProfileEffect.ShowComingSoon(StubbedAction.Follow))
                ProfileEvent.EditTapped ->
                    sendEffect(ProfileEffect.ShowComingSoon(StubbedAction.Edit))
                ProfileEvent.MessageTapped ->
                    sendEffect(ProfileEffect.ShowComingSoon(StubbedAction.Message))
                ProfileEvent.SettingsTapped -> sendEffect(ProfileEffect.NavigateToSettings)
            }
        }

        // -- Internals ---------------------------------------------------------

        /**
         * Returns the actor (DID or handle) to pass to repository
         * calls. `route.handle` wins when non-null; otherwise reads
         * the current [SessionStateProvider.state] (synchronous —
         * profile screens only mount post-login per the outer
         * Navigator's contract). Returns null only in the edge
         * case where the session vanished between MainShell mount
         * and ProfileViewModel construction.
         */
        private fun resolveActor(): String? = route.handle ?: (sessionStateProvider.state.value as? SessionState.SignedIn)?.did

        private fun launchInitialLoads(actor: String) {
            launchHeaderLoad(actor)
            ProfileTab.entries.forEach { tab -> launchInitialTabLoad(actor, tab) }
        }

        private fun launchHeaderLoad(actor: String) {
            viewModelScope.launch {
                setState { copy(headerError = null) }
                repository
                    .fetchHeader(actor)
                    .onSuccess { header ->
                        setState {
                            copy(
                                header = header,
                                viewerRelationship = if (ownProfile) ViewerRelationship.Self else viewerRelationship,
                            )
                        }
                    }.onFailure { throwable ->
                        val error = throwable.toProfileError()
                        setState { copy(headerError = error) }
                        sendEffect(ProfileEffect.ShowError(error))
                    }
            }
        }

        private fun launchInitialTabLoad(
            actor: String,
            tab: ProfileTab,
        ) {
            setTabStatus(tab) { TabLoadStatus.InitialLoading }
            viewModelScope.launch {
                repository
                    .fetchTab(actor, tab)
                    .onSuccess { page -> setTabStatus(tab) { page.toLoaded() } }
                    .onFailure { throwable ->
                        setTabStatus(tab) { TabLoadStatus.InitialError(throwable.toProfileError()) }
                    }
            }
        }

        private fun onTabSelected(tab: ProfileTab) {
            // Switch the active tab. We do NOT re-fetch — if the
            // target tab is Idle / InitialLoading / InitialError,
            // the screen renders the corresponding state inline.
            // Initial loads already kicked off in `init`; if one
            // ended in `InitialError`, the user's Retry affordance
            // re-issues the load via the existing `LoadMore`-style
            // path on demand.
            setState { copy(selectedTab = tab) }
        }

        private fun onHandleTapped(handle: String) {
            // Self-tap guard: tapping the @handle on a post within
            // the current profile's own posts MUST NOT push another
            // copy of the same profile onto the back stack. Silent
            // no-op (per the `Self-handle tap is a no-op` scenario).
            val currentHandle = uiState.value.header?.handle ?: route.handle
            if (handle == currentHandle) return
            sendEffect(ProfileEffect.NavigateToProfile(handle))
        }

        private fun onRefresh() {
            val actor = resolveActor() ?: return
            val activeTab = uiState.value.selectedTab
            launchHeaderLoad(actor)
            launchTabRefresh(actor, activeTab)
        }

        private fun launchTabRefresh(
            actor: String,
            tab: ProfileTab,
        ) {
            // Refresh wins over any in-flight append: cancelling here
            // prevents a stale append result from clobbering the
            // refreshed page. The cursor-identity guard in `onLoadMore`
            // is the belt-and-suspenders backup for the case where the
            // append already finished its repository call before the
            // cancel arrives.
            activeLoadMoreJobs.remove(tab)?.cancel()

            val currentStatus = tabStatus(tab)
            // Mark the existing Loaded as refreshing; non-Loaded
            // statuses (Idle / InitialLoading / InitialError) just
            // re-route into the initial-load path so the user
            // never sees a "refresh" indicator over a never-loaded
            // tab.
            if (currentStatus is TabLoadStatus.Loaded) {
                setTabStatus(tab) { currentStatus.copy(isRefreshing = true) }
                viewModelScope.launch {
                    repository
                        .fetchTab(actor, tab)
                        .onSuccess { page ->
                            setTabStatus(tab) { page.toLoaded() }
                        }.onFailure {
                            // On refresh failure, restore the prior Loaded items
                            // and drop the refresh flag — the existing items
                            // stay visible. No effect emitted; partial-tab
                            // refresh failures are silent.
                            setTabStatus(tab) { currentStatus.copy(isRefreshing = false) }
                        }
                }
            } else {
                launchInitialTabLoad(actor, tab)
            }
        }

        private fun onLoadMore(tab: ProfileTab) {
            val actor = resolveActor() ?: return
            val current = tabStatus(tab) as? TabLoadStatus.Loaded ?: return
            if (!current.hasMore) return
            if (current.isAppending) return
            // Single-flight guard: don't double-launch even if the
            // screen fires two LoadMore events from adjacent scroll
            // frames before our setState happens.
            if (activeLoadMoreJobs[tab]?.isActive == true) return

            setTabStatus(tab) { current.copy(isAppending = true) }
            val job =
                viewModelScope.launch {
                    repository
                        .fetchTab(actor, tab, cursor = current.cursor)
                        .onSuccess { page ->
                            setTabStatus(tab) { latest ->
                                // Refresh-vs-append race guard: only apply
                                // the append if the snapshot we captured at
                                // append-start is still the current state
                                // (same cursor). If a refresh ran while we
                                // were in-flight, the refresh's items win and
                                // this append is dropped.
                                if (latest is TabLoadStatus.Loaded &&
                                    latest.cursor == current.cursor
                                ) {
                                    latest.copy(
                                        items = (latest.items + page.items).toImmutableList(),
                                        isAppending = false,
                                        hasMore = page.nextCursor != null,
                                        cursor = page.nextCursor,
                                    )
                                } else {
                                    latest
                                }
                            }
                        }.onFailure {
                            // Append failure: drop the spinner but
                            // preserve the cursor so a later scroll
                            // retries with the same cursor (no
                            // surfaced effect; per-tab partial
                            // failures are silent). Apply against the
                            // latest state so a concurrent refresh
                            // success isn't overwritten.
                            setTabStatus(tab) { latest ->
                                if (latest is TabLoadStatus.Loaded &&
                                    latest.cursor == current.cursor
                                ) {
                                    latest.copy(isAppending = false)
                                } else {
                                    latest
                                }
                            }
                        }
                    activeLoadMoreJobs.remove(tab)
                }
            activeLoadMoreJobs[tab] = job
        }

        // -- State / status helpers --------------------------------------------

        private fun tabStatus(tab: ProfileTab): TabLoadStatus =
            when (tab) {
                ProfileTab.Posts -> uiState.value.postsStatus
                ProfileTab.Replies -> uiState.value.repliesStatus
                ProfileTab.Media -> uiState.value.mediaStatus
            }

        private fun setTabStatus(
            tab: ProfileTab,
            transform: (TabLoadStatus) -> TabLoadStatus,
        ) {
            setState {
                when (tab) {
                    ProfileTab.Posts -> copy(postsStatus = transform(postsStatus))
                    ProfileTab.Replies -> copy(repliesStatus = transform(repliesStatus))
                    ProfileTab.Media -> copy(mediaStatus = transform(mediaStatus))
                }
            }
        }

        private fun ProfileTabPage.toLoaded(): TabLoadStatus.Loaded =
            TabLoadStatus.Loaded(
                items = items,
                isAppending = false,
                isRefreshing = false,
                hasMore = nextCursor != null,
                cursor = nextCursor,
            )

        private companion object {
            const val NOT_SIGNED_IN_REASON = "session-vanished-mid-mount"
        }
    }

/**
 * Closes throwables produced by the atproto pipeline into the
 * UI's [ProfileError] sealed sum. Mirrors
 * `:feature:postdetail:impl/PostDetailViewModel#toPostDetailError`.
 */
internal fun Throwable.toProfileError(): ProfileError =
    when (this) {
        is IOException -> ProfileError.Network
        is NoSessionException -> ProfileError.Unknown("not-signed-in")
        is XrpcError -> ProfileError.Unknown(javaClass.simpleName)
        else -> ProfileError.Unknown(javaClass.simpleName)
    }
