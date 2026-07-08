package net.kikin.nubecita.feature.profile.impl

import androidx.lifecycle.viewModelScope
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import dagger.assisted.AssistedInject
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.kikin81.atproto.runtime.XrpcError
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.PersistentMap
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import net.kikin.nubecita.core.actors.MuteRepository
import net.kikin.nubecita.core.analytics.ActorAction
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.InteractActor
import net.kikin.nubecita.core.analytics.PostSurface
import net.kikin.nubecita.core.auth.NoSessionException
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.billing.EntitlementRepository
import net.kikin.nubecita.core.common.mvi.MviViewModel
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.mergeInteractionState
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.PostOverflowAction
import net.kikin.nubecita.feature.moderation.api.Report
import net.kikin.nubecita.feature.profile.api.EditProfile
import net.kikin.nubecita.feature.profile.api.Profile
import net.kikin.nubecita.feature.profile.impl.data.ProfileRepository
import net.kikin.nubecita.feature.profile.impl.data.ProfileTabPage
import net.kikin.nubecita.feature.profile.impl.data.dedupeByKey
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
        private val entitlementRepository: EntitlementRepository,
        private val analytics: AnalyticsClient,
        private val muteRepository: MuteRepository,
        private val handler: PostInteractionHandler,
    ) : MviViewModel<ProfileScreenViewState, ProfileEvent, ProfileEffect>(
            ProfileScreenViewState(
                handle = route.handle,
                ownProfile = route.handle == null,
            ),
        ),
        PostInteractionHandler by handler {
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
            // Bind the handler to Profile surface and this VM's scope first.
            handler.bind(PostSurface.Profile, viewModelScope)

            // Mirror handler tap-markers into ProfileScreenViewState so
            // ProfileScreenContent can animate the ±1 count transition before the
            // network resolves. Keeps lastLikeTapPostUri / lastRepostTapPostUri
            // on ProfileScreenViewState byte-identical for screenshot baseline parity.
            viewModelScope.launch {
                handler.tapMarkers.collect { markers ->
                    setState {
                        copy(
                            lastLikeTapPostUri = markers.lastLikeTapPostUri,
                            lastRepostTapPostUri = markers.lastRepostTapPostUri,
                        )
                    }
                }
            }

            val actor = resolveActor()
            if (actor == null) {
                sendEffect(ProfileEffect.ShowError(ProfileError.Unknown(NOT_SIGNED_IN_REASON)))
            } else {
                launchInitialLoads(actor)
            }
            // Atomic merge: reads + applies INSIDE setState so a concurrent
            // tab-append or refresh cannot clobber the cache snapshot between
            // the read and the write. The feed currently reads
            // postInteractionsCache.state.value OUTSIDE setState; nubecita-w4o9
            // will bring it up to this same atomic-reducer pattern.
            viewModelScope.launch {
                postInteractionsCache.state.collect { snapshot ->
                    setState { applyInteractions(snapshot) }
                }
            }
            // Mirror the viewer's Pro entitlement into flat state. isPro is a
            // hot StateFlow (starts false, never throws), so no .catch — we
            // just project each emission. The hero gates the badge on this
            // flag AND ownProfile (see ProfileScreenViewState.showSupporterBadge).
            entitlementRepository.isPro
                .onEach { isPro -> setState { copy(isProSupporter = isPro) } }
                .launchIn(viewModelScope)
            // When the user saves an edit to their OWN profile, refetch the
            // header so the new name/bio/avatar/banner replace the stale ones
            // under the (now-popped) editor. Only the own-profile screen can
            // receive a write, so other-actor screens skip this collector.
            if (uiState.value.ownProfile) {
                repository.ownProfileUpdates
                    .onEach { resolveActor()?.let(::launchHeaderLoad) }
                    .launchIn(viewModelScope)
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
                is ProfileEvent.OnVideoTapped ->
                    sendEffect(ProfileEffect.NavigateToVideoPlayer(event.postUri))
                is ProfileEvent.HandleTapped -> onHandleTapped(event.handle)
                ProfileEvent.Refresh -> onRefresh()
                is ProfileEvent.LoadMore -> onLoadMore(event.tab)
                is ProfileEvent.RetryTab -> onRetryTab(event.tab)
                ProfileEvent.FollowTapped -> onFollowTapped()
                ProfileEvent.HeroMuteTapped -> onHeroMuteTapped()
                ProfileEvent.EditTapped -> {
                    // Only open the editor once the header has loaded. Navigating
                    // with a null header would open an EMPTY form, and a Save
                    // would write those blanks back, clearing the real name/bio.
                    // A brand-new account has a non-null header with null fields,
                    // so it still opens (correctly empty).
                    uiState.value.header?.let { header ->
                        sendEffect(
                            ProfileEffect.NavigateTo(
                                EditProfile(
                                    displayName = header.displayName,
                                    description = header.bio,
                                    avatarUrl = header.avatarUrl,
                                    bannerUrl = header.bannerUrl,
                                ),
                            ),
                        )
                    }
                }
                ProfileEvent.MessageTapped -> onMessageTapped()
                is ProfileEvent.StubActionTapped ->
                    sendEffect(ProfileEffect.ShowComingSoon(event.action))
                ProfileEvent.OnReportAccountRequested -> onReportAccountRequested()
                ProfileEvent.SettingsTapped -> sendEffect(ProfileEffect.NavigateToSettings)
                is ProfileEvent.OnPostOverflowAction -> onOverflowAction(event.post, event.action)
                ProfileEvent.VerificationBadgeTapped -> onVerificationBadgeTapped()
                ProfileEvent.VerificationSheetDismissed -> setState { copy(verificationSheetVisible = false) }
            }
        }

        /**
         * Open the verification explanation sheet and lazily resolve the verifier
         * DIDs → names on FIRST open only. Re-tapping after a successful load just
         * re-opens the sheet (the resolved list is cached in state). A load in
         * flight, or no verifiers to resolve, also skips the fetch — so the
         * common case (viewing a profile, never opening the sheet) issues zero
         * extra `getProfiles` calls.
         */
        private fun onVerificationBadgeTapped() {
            val refs = uiState.value.header?.verifierRefs ?: persistentListOf()
            val alreadyLoaded = uiState.value.verifiers.isNotEmpty()
            if (refs.isEmpty() || alreadyLoaded || uiState.value.verifiersLoading) {
                // Nothing to resolve, already resolved, or in flight — just show the sheet.
                setState { copy(verificationSheetVisible = true) }
                return
            }
            setState { copy(verificationSheetVisible = true, verifiersLoading = true, verifiersError = false) }
            viewModelScope.launch {
                repository
                    .resolveVerifiers(refs)
                    .onSuccess { resolved ->
                        setState { copy(verifiers = resolved, verifiersLoading = false) }
                    }.onFailure {
                        setState { copy(verifiersLoading = false, verifiersError = true) }
                    }
            }
        }

        /**
         * Profile-specific override: [PostOverflowAction.MuteAuthor] /
         * [PostOverflowAction.UnmuteAuthor] carry optimistic flag-flip mutations
         * across all three tab statuses and rollback on failure — identical to the
         * hero-mute path in [onHeroMuteTapped] but operating on per-post author DIDs.
         * All other overflow actions — including [PostOverflowAction.BlockAuthor]
         * (block→real in PR4 `nubecita-tgqv`) — are forwarded to the injected [handler]
         * which emits the appropriate [net.kikin.nubecita.core.postinteractions.InteractionEffect]
         * onto the channel consumed by [net.kikin.nubecita.core.postinteractions.ui.rememberPostInteractions].
         */
        override fun onOverflowAction(
            post: PostUi,
            action: PostOverflowAction,
        ) {
            when (action) {
                PostOverflowAction.MuteAuthor -> {
                    val authorDid = post.author.did
                    setState { updateMutedByAuthor(authorDid, muted = true) }
                    viewModelScope.launch {
                        muteRepository
                            .muteActor(authorDid)
                            .onFailure {
                                setState { updateMutedByAuthor(authorDid, muted = false) }
                                sendEffect(ProfileEffect.ShowError(it.toProfileError()))
                            }
                    }
                }
                PostOverflowAction.UnmuteAuthor -> {
                    val authorDid = post.author.did
                    setState { updateMutedByAuthor(authorDid, muted = false) }
                    viewModelScope.launch {
                        muteRepository
                            .unmuteActor(authorDid)
                            .onFailure {
                                setState { updateMutedByAuthor(authorDid, muted = true) }
                                sendEffect(ProfileEffect.ShowError(it.toProfileError()))
                            }
                    }
                }
                else -> handler.onOverflowAction(post, action)
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
                            val merged =
                                page.items
                                    .dedupeByKey()
                                    .map { it.applyInteraction(postInteractionsCache.state.value) }
                                    .toImmutableList()
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

        /**
         * Resolve the loaded profile's DID and emit a navigation effect
         * targeting the report-account sub-route. Silent no-op when the
         * header hasn't loaded yet — the overflow menu is only visible
         * on a loaded other-user header (see `ProfileHero` /
         * `OtherUserActionsRow`), so under normal UI flow `header` is
         * non-null. Guards defensively against synthetic events (tests,
         * SavedStateHandle replays) — same shape as [onMessageTapped].
         */
        private fun onReportAccountRequested() {
            val did = uiState.value.header?.did ?: return
            sendEffect(ProfileEffect.NavigateTo(Report.forAccount(did)))
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

        /**
         * Handle a Mute / Unmute tap on the other-user hero overflow menu.
         *
         * Reads [ProfileHeaderUi.viewerModeration.isMutedByViewer] to
         * determine the current mute state. Optimistically flips the flag
         * in [ProfileHeaderUi.viewerModeration] before issuing the network
         * call so the UI reflects the action immediately. On failure, rolls
         * back the header to its pre-tap state and surfaces a
         * [ProfileEffect.ShowError] snackbar.
         *
         * Silent no-op when the header hasn't loaded yet — the overflow
         * menu is only visible on a loaded other-user header, so under
         * normal UI flow this guard is defensive.
         */
        private fun onHeroMuteTapped() {
            val header = uiState.value.header ?: return
            val isMuted = header.viewerModeration.isMutedByViewer
            setState {
                copy(
                    header =
                        header.copy(
                            viewerModeration = header.viewerModeration.copy(isMutedByViewer = !isMuted),
                        ),
                )
            }
            viewModelScope.launch {
                if (isMuted) {
                    muteRepository
                        .unmuteActor(header.did)
                        .onFailure {
                            setState {
                                copy(
                                    header =
                                        this.header?.copy(
                                            viewerModeration = this.header.viewerModeration.copy(isMutedByViewer = isMuted),
                                        ),
                                )
                            }
                            sendEffect(ProfileEffect.ShowError(it.toProfileError()))
                        }
                } else {
                    muteRepository
                        .muteActor(header.did)
                        .onFailure {
                            setState {
                                copy(
                                    header =
                                        this.header?.copy(
                                            viewerModeration = this.header.viewerModeration.copy(isMutedByViewer = isMuted),
                                        ),
                                )
                            }
                            sendEffect(ProfileEffect.ShowError(it.toProfileError()))
                        }
                }
            }
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
                            val merged =
                                page.items
                                    .dedupeByKey()
                                    .map { it.applyInteraction(postInteractionsCache.state.value) }
                                    .toImmutableList()
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
                            val mergedNewItems =
                                page.items
                                    .map { it.applyInteraction(postInteractionsCache.state.value) }
                                    .toImmutableList()
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
                                    val deduped = (latest.items + mergedNewItems).dedupeByKey().toImmutableList()
                                    latest.copy(
                                        items = deduped,
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
                is ViewerRelationship.NotFollowing -> {
                    analytics.log(InteractActor(ActorAction.Follow, PostSurface.Profile))
                    launchFollow(previous = current)
                }
                is ViewerRelationship.Following -> {
                    // Following with isPending=false implies followUri != null,
                    // enforced by Following.init { require(...) }. The require here
                    // surfaces an invariant violation rather than silently no-op'ing.
                    val followUri =
                        requireNotNull(current.followUri) {
                            "committed Following MUST have a non-null followUri"
                        }
                    analytics.log(InteractActor(ActorAction.Unfollow, PostSurface.Profile))
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
 * Flips [net.kikin.nubecita.data.models.ViewerStateUi.isAuthorMutedByViewer]
 * on every [TabItemUi.Post] whose `post.author.did` matches [did] across all
 * three tab statuses. [TabItemUi.MediaCell] items are left unchanged because
 * they carry no viewer state. Non-Loaded tab statuses are also left unchanged.
 *
 * Mirrors the `updateMutedByAuthor` helper from
 * `:feature:postdetail:impl/PostDetailViewModel` — same pessimistic-copy
 * pattern, different data shape.
 */
private fun ProfileScreenViewState.updateMutedByAuthor(
    did: String,
    muted: Boolean,
): ProfileScreenViewState =
    copy(
        postsStatus = postsStatus.updateMutedByAuthor(did, muted),
        repliesStatus = repliesStatus.updateMutedByAuthor(did, muted),
        mediaStatus = mediaStatus.updateMutedByAuthor(did, muted),
    )

private fun TabLoadStatus.updateMutedByAuthor(
    did: String,
    muted: Boolean,
): TabLoadStatus =
    if (this is TabLoadStatus.Loaded) {
        copy(
            items =
                items
                    .map { item ->
                        if (item is TabItemUi.Post && item.post.author.did == did) {
                            item.copy(post = item.post.copy(viewer = item.post.viewer.copy(isAuthorMutedByViewer = muted)))
                        } else {
                            item
                        }
                    }.toImmutableList(),
        )
    } else {
        this
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
