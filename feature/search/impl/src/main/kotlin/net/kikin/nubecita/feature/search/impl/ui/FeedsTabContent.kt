package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.SearchFeedsError
import net.kikin.nubecita.feature.search.impl.SearchFeedsEvent
import net.kikin.nubecita.feature.search.impl.SearchFeedsLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchFeedsState
import net.kikin.nubecita.feature.search.impl.data.FeedGeneratorUi

/**
 * Stateless body for the Feeds tab. Mirrors [PeopleTabContent] —
 * branches on [SearchFeedsState.loadStatus], renders a LazyColumn of
 * [FeedRow]s with HorizontalDivider between rows for the Loaded
 * variant.
 */
@Composable
internal fun FeedsTabContent(
    state: SearchFeedsState,
    onEvent: (SearchFeedsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentOnEvent by rememberUpdatedState(onEvent)

    when (val status = state.loadStatus) {
        SearchFeedsLoadStatus.Idle ->
            Box(modifier = modifier.fillMaxSize())
        SearchFeedsLoadStatus.InitialLoading ->
            FeedsLoadingBody(modifier = modifier)
        is SearchFeedsLoadStatus.Loaded -> {
            val currentStatus by rememberUpdatedState(status)
            LoadedBody(
                items = status.items,
                isAppending = status.isAppending,
                listState = listState,
                onEvent = onEvent,
                modifier = modifier,
            )
            LaunchedEffect(listState, status.items.size, status.endReached) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    last to info.totalItemsCount
                }.distinctUntilChanged()
                    .collect { (last, total) ->
                        if (total <= 0 || last < total - PAGINATION_LOAD_AHEAD) return@collect
                        if (currentStatus.endReached || currentStatus.isAppending) return@collect
                        currentOnEvent(SearchFeedsEvent.LoadMore)
                    }
            }
        }
        SearchFeedsLoadStatus.Empty ->
            FeedsEmptyBody(
                currentQuery = state.currentQuery,
                onClearQuery = { onEvent(SearchFeedsEvent.ClearQueryClicked) },
                modifier = modifier,
            )
        is SearchFeedsLoadStatus.InitialError ->
            FeedsInitialErrorBody(
                error = status.error,
                onRetry = { onEvent(SearchFeedsEvent.Retry) },
                modifier = modifier,
            )
    }
}

@Composable
private fun LoadedBody(
    items: ImmutableList<FeedGeneratorUi>,
    isAppending: Boolean,
    listState: LazyListState,
    onEvent: (SearchFeedsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        items(items = items, key = { it.uri }) { feed ->
            FeedRow(
                feed = feed,
                onClick = { onEvent(SearchFeedsEvent.FeedTapped(feed.uri)) },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (isAppending) {
            item("appending-indicator") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp)
                }
            }
        }
    }
}

private const val PAGINATION_LOAD_AHEAD = 5

@Preview(name = "FeedsTabContent — InitialLoading", showBackground = true)
@Composable
private fun FeedsTabContentInitialLoadingPreview() {
    NubecitaTheme {
        FeedsTabContent(
            state = SearchFeedsState(loadStatus = SearchFeedsLoadStatus.InitialLoading, currentQuery = "art"),
            onEvent = {},
        )
    }
}

@Preview(name = "FeedsTabContent — Empty", showBackground = true)
@Composable
private fun FeedsTabContentEmptyPreview() {
    NubecitaTheme {
        FeedsTabContent(
            state = SearchFeedsState(loadStatus = SearchFeedsLoadStatus.Empty, currentQuery = "xyzqq"),
            onEvent = {},
        )
    }
}

@Preview(name = "FeedsTabContent — Loaded", showBackground = true)
@Composable
private fun FeedsTabContentLoadedPreview() {
    val feeds =
        persistentListOf(
            FeedGeneratorUi(
                uri = "at://did:plc:f1/app.bsky.feed.generator/discover",
                displayName = "Discover",
                creatorHandle = "skyfeed.bsky.social",
                creatorDisplayName = "skyfeed",
                description = "A curated feed of trending posts on Bluesky.",
                avatarUrl = null,
                likeCount = 14_237L,
            ),
            FeedGeneratorUi(
                uri = "at://did:plc:f2/app.bsky.feed.generator/art",
                displayName = "Art",
                creatorHandle = "art-feed.bsky.social",
                creatorDisplayName = null,
                description = null,
                avatarUrl = null,
                likeCount = 1_234L,
            ),
        )
    NubecitaTheme {
        FeedsTabContent(
            state =
                SearchFeedsState(
                    currentQuery = "art",
                    loadStatus =
                        SearchFeedsLoadStatus.Loaded(
                            items = feeds,
                            nextCursor = "c2",
                            endReached = false,
                            isAppending = false,
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "FeedsTabContent — Loaded + Appending", showBackground = true)
@Composable
private fun FeedsTabContentLoadedAppendingPreview() {
    val feeds =
        persistentListOf(
            FeedGeneratorUi(
                uri = "at://did:plc:f1/app.bsky.feed.generator/discover",
                displayName = "Discover",
                creatorHandle = "skyfeed.bsky.social",
                creatorDisplayName = "skyfeed",
                description = "A curated feed of trending posts on Bluesky.",
                avatarUrl = null,
                likeCount = 14_237L,
            ),
        )
    NubecitaTheme {
        FeedsTabContent(
            state =
                SearchFeedsState(
                    currentQuery = "art",
                    loadStatus =
                        SearchFeedsLoadStatus.Loaded(
                            items = feeds,
                            nextCursor = "c2",
                            endReached = false,
                            isAppending = true,
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "FeedsTabContent — InitialError(Network)", showBackground = true)
@Composable
private fun FeedsTabContentInitialErrorPreview() {
    NubecitaTheme {
        FeedsTabContent(
            state =
                SearchFeedsState(
                    loadStatus =
                        SearchFeedsLoadStatus.InitialError(error = SearchFeedsError.Network),
                    currentQuery = "art",
                ),
            onEvent = {},
        )
    }
}
