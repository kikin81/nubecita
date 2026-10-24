package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.core.postinteractions.PostTapMarkers
import net.kikin.nubecita.designsystem.component.NubecitaPullToRefreshBox
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.HashtagEvent
import net.kikin.nubecita.feature.search.impl.HashtagState
import net.kikin.nubecita.feature.search.impl.HashtagTabState
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.SearchPostsLoadStatus
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

/**
 * Stateless Hashtag screen: top bar (title, back, share), Top / Latest tabs,
 * the optional "From @author" filter chip, and the selected tab's body.
 * Hoists nothing from Hilt so previews and screenshot tests can drive it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HashtagContent(
    state: HashtagState,
    onEvent: (HashtagEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    callbacks: PostCallbacks = PostCallbacks.None,
    tapMarkers: PostTapMarkers = PostTapMarkers(),
) {
    // One list state per tab so switching tabs keeps each tab's scroll position.
    val topListState = rememberLazyListState()
    val latestListState = rememberLazyListState()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = hashtagTitle(state.tag),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.hashtag_back_content_description),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { onEvent(HashtagEvent.ShareClicked) }) {
                        NubecitaIcon(
                            name = NubecitaIconName.IosShare,
                            contentDescription = stringResource(R.string.hashtag_share_content_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            HashtagTabBar(
                selectedSort = state.selectedSort,
                onSelect = { onEvent(HashtagEvent.TabSelected(it)) },
            )
            state.author?.let { author ->
                FilterChip(
                    selected = state.authorFilterOn,
                    onClick = { onEvent(HashtagEvent.AuthorFilterToggled) },
                    label = { Text(stringResource(R.string.hashtag_filter_from_author, author)) },
                    leadingIcon =
                        if (state.authorFilterOn) {
                            { NubecitaIcon(name = NubecitaIconName.Check, contentDescription = null) }
                        } else {
                            null
                        },
                    modifier = Modifier.padding(horizontal = MaterialTheme.spacing.s4),
                )
            }
            HashtagTabBody(
                tag = state.tag,
                tab = state.tab(state.selectedSort),
                listState =
                    when (state.selectedSort) {
                        SearchPostsSort.TOP -> topListState
                        SearchPostsSort.LATEST -> latestListState
                    },
                callbacks = callbacks,
                tapMarkers = tapMarkers,
                onEvent = onEvent,
            )
        }
    }
}

/** `#tag` lowercased like the official client; a cashtag (`$BTC`) stays as-is, uppercased. */
internal fun hashtagTitle(tag: String): String = if (tag.startsWith("$")) tag.uppercase() else "#" + tag.lowercase()

@Composable
private fun HashtagTabBar(
    selectedSort: SearchPostsSort,
    onSelect: (SearchPostsSort) -> Unit,
) {
    SecondaryTabRow(selectedTabIndex = selectedSort.ordinal) {
        SearchPostsSort.entries.forEach { sort ->
            Tab(
                selected = sort == selectedSort,
                onClick = { onSelect(sort) },
                text = {
                    Text(
                        stringResource(
                            when (sort) {
                                SearchPostsSort.TOP -> R.string.search_posts_sort_top
                                SearchPostsSort.LATEST -> R.string.search_posts_sort_latest
                            },
                        ),
                    )
                },
            )
        }
    }
}

@Composable
private fun HashtagTabBody(
    tag: String,
    tab: HashtagTabState,
    listState: LazyListState,
    callbacks: PostCallbacks,
    tapMarkers: PostTapMarkers,
    onEvent: (HashtagEvent) -> Unit,
) {
    when (val status = tab.status) {
        SearchPostsLoadStatus.Idle,
        SearchPostsLoadStatus.InitialLoading,
        -> PostsLoadingBody()
        is SearchPostsLoadStatus.InitialError ->
            PostsInitialErrorBody(error = status.error, onRetry = { onEvent(HashtagEvent.Retry) })
        SearchPostsLoadStatus.Empty ->
            NubecitaPullToRefreshBox(
                isRefreshing = tab.isRefreshing,
                onRefresh = { onEvent(HashtagEvent.Refresh) },
            ) {
                HashtagEmptyBody(tag = tag)
            }
        is SearchPostsLoadStatus.Loaded ->
            NubecitaPullToRefreshBox(
                isRefreshing = tab.isRefreshing,
                onRefresh = { onEvent(HashtagEvent.Refresh) },
            ) {
                HashtagResultsList(
                    status = status,
                    listState = listState,
                    callbacks = callbacks,
                    tapMarkers = tapMarkers,
                    onEvent = onEvent,
                )
            }
    }
}

@Composable
private fun HashtagResultsList(
    status: SearchPostsLoadStatus.Loaded,
    listState: LazyListState,
    callbacks: PostCallbacks,
    tapMarkers: PostTapMarkers,
    onEvent: (HashtagEvent) -> Unit,
) {
    // Per-list reveal state for covered (NSFW-labelled) media, as in the Posts tab.
    var revealedMedia by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toPersistentSet() }),
    ) { mutableStateOf(persistentSetOf<String>()) }
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("hashtag_posts_list"),
        state = listState,
    ) {
        items(items = status.items, key = { it.post.id }, contentType = { "post" }) { item ->
            PostCard(
                post = item.post,
                callbacks = callbacks,
                isMediaRevealed = item.post.id in revealedMedia,
                onRevealMedia = { revealedMedia = revealedMedia.adding(item.post.id) },
                animateLikeTap = item.post.id == tapMarkers.lastLikeTapPostUri,
                animateRepostTap = item.post.id == tapMarkers.lastRepostTapPostUri,
            )
        }
        if (status.isAppending) {
            item(key = "appending-indicator", contentType = "appending-indicator") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = MaterialTheme.spacing.s4),
                    contentAlignment = Alignment.Center,
                ) {
                    NubecitaWavyProgressIndicator()
                }
            }
        }
    }

    // Pagination: fire LoadMore once per crossing of the prefetch threshold.
    val currentStatus by rememberUpdatedState(status)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LaunchedEffect(listState) {
        snapshotFlow {
            val lastVisible =
                listState.layoutInfo.visibleItemsInfo
                    .lastOrNull()
                    ?.index ?: 0
            lastVisible > currentStatus.items.size - PAGINATION_PREFETCH_DISTANCE
        }.distinctUntilChanged()
            .collect { pastThreshold ->
                if (!pastThreshold) return@collect
                if (currentStatus.endReached || currentStatus.isAppending) return@collect
                currentOnEvent(HashtagEvent.LoadMore)
            }
    }
}

@Composable
private fun HashtagEmptyBody(tag: String) {
    // Scrollable so pull-to-refresh can be dragged on an empty tab.
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = MaterialTheme.spacing.s6, vertical = MaterialTheme.spacing.s8),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Inbox,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = EMPTY_ICON_SIZE,
        )
        Text(
            text = stringResource(R.string.hashtag_empty_title, hashtagTitle(tag)),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.hashtag_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private const val PAGINATION_PREFETCH_DISTANCE = 5
private val EMPTY_ICON_SIZE = 96.dp
