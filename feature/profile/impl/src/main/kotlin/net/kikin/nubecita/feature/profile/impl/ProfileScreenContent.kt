package net.kikin.nubecita.feature.profile.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.component.NubecitaPullToRefreshBox
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.tabs.ProfilePillTabs
import net.kikin.nubecita.feature.profile.impl.ui.ProfileHero
import net.kikin.nubecita.feature.profile.impl.ui.ProfileTopBar
import net.kikin.nubecita.feature.profile.impl.ui.ProfileVerbsRow
import net.kikin.nubecita.feature.profile.impl.ui.profileFeedTabBody
import net.kikin.nubecita.feature.profile.impl.ui.profileMediaTabBody

private const val PREFETCH_DISTANCE = 5

/**
 * Stateless screen body. Takes the canonical
 * [ProfileScreenViewState] plus the small set of
 * callbacks the host wires to VM events.
 *
 * Material 3 Expressive layout:
 * - TopAppBar in Scaffold's topBar slot, transparent over the hero
 *   banner and fading to opaque as the hero scrolls off.
 * - LazyColumn extends edge-to-edge behind the topBar so the hero
 *   banner reaches the device's top corners. The hero applies a
 *   `topInset` internally to extend its banner up by the topBar's
 *   reserved height, and rounds the banner's top corners to echo
 *   the device's screen curvature.
 * - The verbs row and pill tabs are a normal LazyColumn item — they
 *   scroll away with the hero. Sticky behavior was considered, but
 *   it consumes a meaningful slice of the scrollable viewport once
 *   pinned and the design intent is to maximize feed real estate.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun ProfileScreenContent(
    state: ProfileScreenViewState,
    listState: LazyListState,
    snackbarHostState: SnackbarHostState,
    postCallbacks: PostCallbacks,
    onEvent: (ProfileEvent) -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val pillTabs = rememberProfilePillTabs()
    val activeTabIsRefreshing = state.activeTabIsRefreshing()
    val onVideoTap =
        remember(onEvent) {
            { uri: String -> onEvent(ProfileEvent.OnVideoTapped(uri)) }
        }
    // Per-screen reveal state for covered (NSFW-labelled) media — shared across
    // the Posts and Replies tabs (same @Stable PersistentSet, rememberSaveable
    // via an explicit listSaver). The Media grid drops covered media outright,
    // so it needs no reveal state.
    var revealedMedia by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toPersistentSet() }),
    ) { mutableStateOf(persistentSetOf<String>()) }
    val onRevealMedia = remember { { id: String -> revealedMedia = revealedMedia.adding(id) } }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            ProfileTopBar(
                header = state.header,
                listState = listState,
                ownProfile = state.ownProfile,
                onBack = onBack,
                onSettings = { onEvent(ProfileEvent.SettingsTapped) },
            )
        },
    ) { padding ->
        val topBarPadding = padding.calculateTopPadding()

        NubecitaPullToRefreshBox(
            isRefreshing = activeTabIsRefreshing,
            onRefresh = { onEvent(ProfileEvent.Refresh) },
            modifier = Modifier.fillMaxSize(),
            // Offset the indicator below the profile top bar. (nubecita-tfbc)
            indicatorPadding = PaddingValues(top = topBarPadding),
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().testTag("profile_list"),
                contentPadding = PaddingValues(bottom = padding.calculateBottomPadding()),
            ) {
                item(key = "hero", contentType = "hero") {
                    ProfileHero(
                        header = state.header,
                        headerError = state.headerError,
                        showSupporterBadge = state.showSupporterBadge,
                        onRetryHeader = { onEvent(ProfileEvent.Refresh) },
                        topInset = topBarPadding,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                item(key = "verbs_and_tabs", contentType = "verbs_and_tabs") {
                    Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                        ProfileVerbsRow(
                            ownProfile = state.ownProfile,
                            viewerRelationship = state.viewerRelationship,
                            canMessage = state.header?.canMessage == true,
                            onEdit = { onEvent(ProfileEvent.EditTapped) },
                            onFollow = { onEvent(ProfileEvent.FollowTapped) },
                            onMessage = { onEvent(ProfileEvent.MessageTapped) },
                            onReport = { onEvent(ProfileEvent.OnReportAccountRequested) },
                            onMute = { onEvent(ProfileEvent.HeroMuteTapped) },
                            onOverflowAction = { action -> onEvent(ProfileEvent.StubActionTapped(action)) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        ProfilePillTabs(
                            tabs = pillTabs,
                            selectedValue = state.selectedTab,
                            onSelect = { tab -> onEvent(ProfileEvent.TabSelected(tab)) },
                        )
                    }
                }
                when (state.selectedTab) {
                    ProfileTab.Posts ->
                        profileFeedTabBody(
                            tab = ProfileTab.Posts,
                            status = state.postsStatus,
                            callbacks = postCallbacks,
                            onImageTap = { post, idx -> onEvent(ProfileEvent.OnImageTapped(post, idx)) },
                            onVideoTap = onVideoTap,
                            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Posts)) },
                            lastLikeTapPostUri = state.lastLikeTapPostUri,
                            lastRepostTapPostUri = state.lastRepostTapPostUri,
                            revealedMedia = revealedMedia,
                            onRevealMedia = onRevealMedia,
                        )
                    ProfileTab.Replies ->
                        profileFeedTabBody(
                            tab = ProfileTab.Replies,
                            status = state.repliesStatus,
                            callbacks = postCallbacks,
                            onImageTap = { post, idx -> onEvent(ProfileEvent.OnImageTapped(post, idx)) },
                            onVideoTap = onVideoTap,
                            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Replies)) },
                            lastLikeTapPostUri = state.lastLikeTapPostUri,
                            lastRepostTapPostUri = state.lastRepostTapPostUri,
                            revealedMedia = revealedMedia,
                            onRevealMedia = onRevealMedia,
                        )
                    ProfileTab.Media ->
                        profileMediaTabBody(
                            status = state.mediaStatus,
                            onMediaTap = { cell ->
                                onEvent(
                                    ProfileEvent.OnMediaCellTapped(
                                        postUri = cell.postUri,
                                        isVideo = cell.isVideo,
                                    ),
                                )
                            },
                            onRetry = { onEvent(ProfileEvent.RetryTab(ProfileTab.Media)) },
                        )
                }
            }
        }
    }

    val currentSelectedTab by rememberUpdatedState(state.selectedTab)
    val currentActiveTabStatus by rememberUpdatedState(state.activeTabStatus())
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            lastVisible > total - PREFETCH_DISTANCE
        }.distinctUntilChanged()
            .collect { pastThreshold ->
                val status = currentActiveTabStatus
                if (
                    pastThreshold &&
                    status is TabLoadStatus.Loaded &&
                    status.hasMore &&
                    !status.isAppending
                ) {
                    currentOnEvent(ProfileEvent.LoadMore(currentSelectedTab))
                }
            }
    }
}

private fun ProfileScreenViewState.activeTabStatus(): TabLoadStatus =
    when (selectedTab) {
        ProfileTab.Posts -> postsStatus
        ProfileTab.Replies -> repliesStatus
        ProfileTab.Media -> mediaStatus
    }

private fun ProfileScreenViewState.activeTabIsRefreshing(): Boolean {
    val status = activeTabStatus()
    return status is TabLoadStatus.Loaded && status.isRefreshing
}
