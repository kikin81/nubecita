package net.kikin.nubecita.feature.search.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.feature.search.impl.SearchPostsEvent
import net.kikin.nubecita.feature.search.impl.SearchPostsLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchPostsState
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

/**
 * Stateless body for the Posts tab. Branches on
 * [SearchPostsState.loadStatus]; renders the sort row above non-Idle
 * states; for Loaded uses a LazyColumn of PostCards with bodyMatch
 * highlighting; for Loaded + isAppending shows a bottom progress row.
 *
 * `onEvent` dispatches every user intent upward to the VM. Doesn't
 * hoist `hiltViewModel` itself — that's `SearchPostsScreen`'s job —
 * so previews + screenshot tests can drive this Composable without a
 * Hilt graph.
 *
 * Pagination trigger: a `snapshotFlow` over the threshold-boolean
 * (lastVisibleIndex > items.size - PAGINATION_PREFETCH_DISTANCE) fires
 * `LoadMore` exactly once per threshold crossing. Mirrors
 * `:feature:feed:impl/FeedScreen`'s shape.
 */
@Composable
internal fun PostsTabContent(
    state: SearchPostsState,
    onEvent: (SearchPostsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentOnEvent by rememberUpdatedState(onEvent)

    when (val status = state.loadStatus) {
        SearchPostsLoadStatus.Idle ->
            Box(modifier = modifier.fillMaxSize())
        SearchPostsLoadStatus.InitialLoading ->
            PostsLoadingBody(modifier = modifier)
        is SearchPostsLoadStatus.Loaded -> {
            LoadedBody(
                items = status.items,
                isAppending = status.isAppending,
                currentQuery = state.currentQuery,
                currentSort = state.sort,
                listState = listState,
                onEvent = onEvent,
                modifier = modifier,
            )
            // Pagination trigger — exactly once per threshold crossing.
            val currentItems by rememberUpdatedState(status.items)
            val endReached = status.endReached
            val isAppending = status.isAppending
            LaunchedEffect(listState, endReached, isAppending) {
                if (endReached || isAppending) return@LaunchedEffect
                snapshotFlow {
                    val lastVisible =
                        listState.layoutInfo.visibleItemsInfo
                            .lastOrNull()
                            ?.index ?: 0
                    lastVisible > currentItems.size - PAGINATION_PREFETCH_DISTANCE
                }.distinctUntilChanged()
                    .collect { pastThreshold ->
                        if (pastThreshold) currentOnEvent(SearchPostsEvent.LoadMore)
                    }
            }
        }
        SearchPostsLoadStatus.Empty ->
            Column(modifier = modifier.fillMaxSize()) {
                PostsSortRow(
                    activeSort = state.sort,
                    onSelectSort = { onEvent(SearchPostsEvent.SortClicked(it)) },
                )
                PostsEmptyBody(
                    currentQuery = state.currentQuery,
                    currentSort = state.sort,
                    onClearQuery = { onEvent(SearchPostsEvent.ClearQueryClicked) },
                    onToggleSort = { onEvent(SearchPostsEvent.SortClicked(it)) },
                )
            }
        is SearchPostsLoadStatus.InitialError ->
            PostsInitialErrorBody(
                error = status.error,
                onRetry = { onEvent(SearchPostsEvent.Retry) },
                modifier = modifier,
            )
    }
}

@Composable
private fun LoadedBody(
    items: ImmutableList<FeedItemUi.Single>,
    isAppending: Boolean,
    currentQuery: String,
    currentSort: SearchPostsSort,
    listState: LazyListState,
    onEvent: (SearchPostsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val callbacks =
        remember(onEvent) {
            PostCallbacks(onTap = { post -> onEvent(SearchPostsEvent.PostTapped(post.id)) })
        }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
    ) {
        item("sort-row") {
            PostsSortRow(
                activeSort = currentSort,
                onSelectSort = { onEvent(SearchPostsEvent.SortClicked(it)) },
            )
        }
        items(items = items, key = { it.post.id }) { item ->
            PostCard(
                post = item.post,
                bodyMatch = currentQuery.takeIf { it.isNotBlank() },
                callbacks = callbacks,
            )
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

private const val PAGINATION_PREFETCH_DISTANCE = 5

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@androidx.compose.ui.tooling.preview.Preview(name = "PostsTabContent — InitialLoading", showBackground = true)
@Composable
private fun PostsTabContentInitialLoadingPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PostsTabContent(
            state =
                SearchPostsState(
                    loadStatus = SearchPostsLoadStatus.InitialLoading,
                    currentQuery = "kotlin",
                ),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PostsTabContent — Empty", showBackground = true)
@Composable
private fun PostsTabContentEmptyPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PostsTabContent(
            state =
                SearchPostsState(
                    loadStatus = SearchPostsLoadStatus.Empty,
                    currentQuery = "xyzqq",
                ),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PostsTabContent — InitialError(Network)", showBackground = true)
@Composable
private fun PostsTabContentInitialErrorPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PostsTabContent(
            state =
                SearchPostsState(
                    loadStatus =
                        SearchPostsLoadStatus.InitialError(
                            error = net.kikin.nubecita.feature.search.impl.SearchPostsError.Network,
                        ),
                    currentQuery = "kotlin",
                ),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PostsTabContent — Loaded (with highlight)", showBackground = true)
@Composable
private fun PostsTabContentLoadedPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PostsTabContent(
            state =
                SearchPostsState(
                    currentQuery = "kotlin",
                    loadStatus =
                        SearchPostsLoadStatus.Loaded(
                            items =
                                kotlinx.collections.immutable.persistentListOf(
                                    previewSearchHit("at://p1", "Kotlin coroutines are great"),
                                    previewSearchHit("at://p2", "I love writing Kotlin"),
                                ),
                            nextCursor = "c2",
                            endReached = false,
                        ),
                ),
            onEvent = {},
        )
    }
}

@androidx.compose.ui.tooling.preview.Preview(name = "PostsTabContent — Loaded + Appending", showBackground = true)
@Composable
private fun PostsTabContentLoadedAppendingPreview() {
    net.kikin.nubecita.designsystem.NubecitaTheme {
        PostsTabContent(
            state =
                SearchPostsState(
                    currentQuery = "kotlin",
                    loadStatus =
                        SearchPostsLoadStatus.Loaded(
                            items =
                                kotlinx.collections.immutable.persistentListOf(
                                    previewSearchHit("at://p1", "Kotlin coroutines are great"),
                                ),
                            nextCursor = "c2",
                            endReached = false,
                            isAppending = true,
                        ),
                ),
            onEvent = {},
        )
    }
}

private fun previewSearchHit(
    uri: String,
    text: String,
): FeedItemUi.Single =
    FeedItemUi.Single(
        post =
            net.kikin.nubecita.data.models.PostUi(
                id = uri,
                cid = "bafyreifakecid000000000000000000000000000000000",
                author =
                    net.kikin.nubecita.data.models.AuthorUi(
                        did = "did:plc:fake",
                        handle = "fake.bsky.social",
                        displayName = "Fake User",
                        avatarUrl = null,
                    ),
                createdAt = kotlin.time.Instant.parse("2026-04-25T12:00:00Z"),
                text = text,
                facets = kotlinx.collections.immutable.persistentListOf(),
                embed = net.kikin.nubecita.data.models.EmbedUi.Empty,
                stats =
                    net.kikin.nubecita.data.models
                        .PostStatsUi(),
                viewer =
                    net.kikin.nubecita.data.models
                        .ViewerStateUi(),
                repostedBy = null,
            ),
    )
