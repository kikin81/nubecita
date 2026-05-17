package net.kikin.nubecita.feature.profile.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
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
        private val postInteractionsCache: PostInteractionsCache,
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

        /**
         * Per-tab initial-load guard. Tracks the in-flight initial
         * `fetchTab` (from `init`, `RetryTab`, or a refresh routed
         * to the initial-load path). Rapid Retry taps would
         * otherwise fan out into concurrent fetches and the
         * later-completed-but-stale request could overwrite a
         * successful one — flipping the tab back into
         * `InitialError`. Cancelling the prior job on a new
         * initial-load call enforces single-flight semantics.
         */
        private val activeInitialLoadJobs = mutableMapOf<ProfileTab, Job>()

        init {
            val actor = resolveActor()
            if (actor == null) {
                sendEffect(ProfileEffect.ShowError(ProfileError.Unknown(NOT_SIGNED_IN_REASON)))
            } else {
                launchInitialLoads(actor)
            }
            viewModelScope.launch {
                postInteractionsCache.state
                    .map { interactionMap -> uiState.value.applyInteractions(interactionMap) }
                    .distinctUntilChanged()
                    .collect { merged -> setState { merged } }
            }
        }

        override fun handleEvent(event: ProfileEvent) {
            when (event) {
                is ProfileEvent.TabSelected -> onTabSelected(event.tab)
                is ProfileEvent.PostTapped -> sendEffect(ProfileEffect.NavigateToPost(event.postUri))
                is ProfileEvent.OnImageTapped ->
                    sendEffect(ProfileEffect.NavigateToMediaViewer(event.post.id, event.imageIndex))
                is ProfileEvent.OnQuotedPostTapped ->
                    sendEffect(ProfileEffect.NavigateToPost(event.quotedPostUri))
                is ProfileEvent.OnMediaCellTapped ->
                    if (event.isVideo) {
                        sendEffect(ProfileEffect.NavigateToVideoPlayer(event.postUri))
                    } else {
                        sendEffect(ProfileEffect.NavigateToMediaViewer(event.postUri, imageIndex = 0))
                    }
                is ProfileEvent.HandleTapped -> onHandleTapped(event.handle)
                ProfileEvent.Refresh -> onRefresh()
                is ProfileEvent.LoadMore -> onLoadMore(event.tab)
                is ProfileEvent.RetryTab -> onRetryTab(event.tab)
                ProfileEvent.FollowTapped -> onFollowTapped()
                ProfileEvent.EditTapped ->
                    sendEffect(ProfileEffect.ShowComingSoon(StubbedAction.Edit))
                ProfileEvent.MessageTapped -> onMessageTapped()
                is ProfileEvent.StubActionTapped ->
                    sendEffect(ProfileEffect.ShowComingSoon(event.action))
                ProfileEvent.SettingsTapped -> sendEffect(ProfileEffect.NavigateToSettings)
                is ProfileEvent.OnLikeClicked -> {
                    setState { copy(lastLikeTapPostUri = event.post.id) }
                    viewModelScope.launch {
                        postInteractionsCache
                            .toggleLike(event.post.id, event.post.cid)
                            .onFailure { sendEffect(ProfileEffect.ShowError(it.toProfileError())) }
                    }
                }
                is ProfileEvent.OnRepostClicked -> {
                    setState { copy(lastRepostTapPostUri = event.post.id) }
                    viewModelScope.launch {
                        postInteractionsCache
                            .toggleRepost(event.post.id, event.post.cid)
                            .onFailure { sendEffect(ProfileEffect.ShowError(it.toProfileError())) }
                    }
                }
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
                    .onSuccess { result ->
                        setState {
                            copy(
                                header = result.header,
                                viewerRelationship =
                                    if (ownProfile) ViewerRelationship.Self else result.viewerRelationship,
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
            // Single-flight per tab: cancel any prior initial-load
            // job before starting a new one. Without this, rapid
            // Retry taps fan out into concurrent fetches and the
            // last-completed-but-stale request can flip the tab
            // back into InitialError after a more recent success.
            activeInitialLoadJobs.remove(tab)?.cancel()
            setTabStatus(tab) { TabLoadStatus.InitialLoading }
            val job =
                viewModelScope.launch {
                    repository
                        .fetchTab(actor, tab)
                        .onSuccess { page ->
                            postInteractionsCache.seed(page.items.filterIsInstance<TabItemUi.Post>().map { it.post })
                            val merged = page.items.map { it.applyInteraction(postInteractionsCache.state.value) }.toImmutableList()
                            setTabStatus(tab) { page.toLoaded(items = merged) }
                        }.onFailure { throwable ->
                            setTabStatus(tab) { TabLoadStatus.InitialError(throwable.toProfileError()) }
                        }
                    activeInitialLoadJobs.remove(tab)
                }
            activeInitialLoadJobs[tab] = job
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

        private fun onMessageTapped() {
            // The Message button is rendered only on a loaded
            // other-user header (see `ProfileHero` / `OtherUserActionsRow`),
            // so under normal UI flow `state.header` is non-null and
            // `ownProfile` is false. Guard defensively against
            // synthetic events (tests, SavedStateHandle replays):
            // a missing header or own-profile tap is a silent no-op.
            val state = uiState.value
            if (state.ownProfile) return
            val did = state.header?.did ?: return
            sendEffect(ProfileEffect.NavigateToMessage(otherUserDid = did))
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
                            postInteractionsCache.seed(page.items.filterIsInstance<TabItemUi.Post>().map { it.post })
                            val merged = page.items.map { it.applyInteraction(postInteractionsCache.state.value) }.toImmutableList()
                            setTabStatus(tab) { page.toLoaded(items = merged) }
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
                            postInteractionsCache.seed(page.items.filterIsInstance<TabItemUi.Post>().map { it.post })
                            val mergedNewItems = page.items.map { it.applyInteraction(postInteractionsCache.state.value) }.toImmutableList()
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
                                        items = (latest.items + mergedNewItems).toImmutableList(),
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

        /**
         * Tab-level retry from the inline error state. Re-launches the
         * initial-load path for the named tab via the same code path
         * `launchInitialLoads` uses on first composition. No header
         * refresh — header has its own retry affordance.
         */
        private fun onRetryTab(tab: ProfileTab) {
            val actor = resolveActor() ?: return
            launchInitialTabLoad(actor, tab)
        }

        /**
         * Handle a Follow / Unfollow tap. Branches on the current
         * [ViewerRelationship]:
         *
         * - [ViewerRelationship.NotFollowing] → optimistically flip to
         *   `Following(followUri = null, isPending = true)`, then issue
         *   `app.bsky.graph.follow` create. Success commits the wire
         *   AT-URI; failure rolls back to the prior state and surfaces
         *   a [ProfileEffect.ShowError] snackbar.
         * - [ViewerRelationship.Following] → optimistically flip to
         *   `NotFollowing(isPending = true)`, then issue
         *   `app.bsky.graph.follow` delete against the URI carried on
         *   the prior `Following` (the [Following.followUri] invariant
         *   guarantees it is non-null when `isPending == false`).
         * - [ViewerRelationship.Self] / [ViewerRelationship.None] /
         *   any `isPending` variant → silent no-op. `isPending` is
         *   the single-flight guard; the disabled-while-pending button
         *   is its visible counterpart, and the early-return here
         *   covers the wing-of-recomposition gap before the disable
         *   propagates to the next frame.
         */
        private fun onFollowTapped() {
            val current = uiState.value.viewerRelationship
            if (current.isPending) return
            when (current) {
                is ViewerRelationship.NotFollowing -> launchFollow(previous = current)
                is ViewerRelationship.Following -> {
                    // Following with isPending=false implies followUri != null,
                    // enforced by Following.init { require(...) }. The require here
                    // surfaces an invariant violation rather than silently no-op'ing.
                    val followUri =
                        requireNotNull(current.followUri) {
                            "committed Following MUST have a non-null followUri"
                        }
                    launchUnfollow(previous = current, followUri = followUri)
                }
                ViewerRelationship.Self, ViewerRelationship.None -> Unit
            }
        }

        private fun launchFollow(previous: ViewerRelationship.NotFollowing) {
            val targetDid = uiState.value.header?.did ?: return
            setState {
                copy(viewerRelationship = ViewerRelationship.Following(followUri = null, isPending = true))
            }
            viewModelScope.launch {
                repository
                    .follow(targetDid)
                    .onSuccess { uri ->
                        setState {
                            copy(viewerRelationship = ViewerRelationship.Following(followUri = uri, isPending = false))
                        }
                    }.onFailure { throwable ->
                        setState { copy(viewerRelationship = previous) }
                        sendEffect(ProfileEffect.ShowError(throwable.toProfileError()))
                    }
            }
        }

        private fun launchUnfollow(
            previous: ViewerRelationship.Following,
            followUri: String,
        ) {
            setState { copy(viewerRelationship = ViewerRelationship.NotFollowing(isPending = true)) }
            viewModelScope.launch {
                repository
                    .unfollow(followUri)
                    .onSuccess {
                        setState { copy(viewerRelationship = ViewerRelationship.NotFollowing(isPending = false)) }
                    }.onFailure { throwable ->
                        setState { copy(viewerRelationship = previous) }
                        sendEffect(ProfileEffect.ShowError(throwable.toProfileError()))
                    }
            }
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

        private fun ProfileTabPage.toLoaded(
            items: ImmutableList<TabItemUi> = this.items,
        ): TabLoadStatus.Loaded =
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

private fun ProfileScreenViewState.applyInteractions(
    map: PersistentMap<String, PostInteractionState>,
): ProfileScreenViewState =
    copy(
        postsStatus = postsStatus.applyInteractions(map),
        repliesStatus = repliesStatus.applyInteractions(map),
        mediaStatus = mediaStatus.applyInteractions(map),
    )

private fun TabLoadStatus.applyInteractions(
    map: PersistentMap<String, PostInteractionState>,
): TabLoadStatus =
    if (this is TabLoadStatus.Loaded) {
        copy(items = items.map { it.applyInteraction(map) }.toImmutableList())
    } else {
        this
    }

private fun TabItemUi.applyInteraction(
    map: PersistentMap<String, PostInteractionState>,
): TabItemUi =
    when (this) {
        is TabItemUi.Post -> {
            val state = map[post.id] ?: return this
            copy(post = post.mergeInteractionState(state))
        }
        is TabItemUi.MediaCell -> this // media cells don't show interaction UI
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
