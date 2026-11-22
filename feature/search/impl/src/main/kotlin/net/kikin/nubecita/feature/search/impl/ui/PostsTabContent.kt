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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.core.postinteractions.PostTapMarkers
import net.kikin.nubecita.core.posttranslation.ui.PostTranslationsHandle
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.feature.search.impl.SearchPostsEvent
import net.kikin.nubecita.feature.search.impl.SearchPostsLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchPostsState
import net.kikin.nubecita.feature.search.impl.SearchTestTags
import net.kikin.nubecita.feature.search.impl.data.PostSearchFilters

/**
 * Stateless body for the Posts tab. Branches on
 * [SearchPostsState.loadStatus]; pins the filter header (chips + count) above non-Idle
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
    callbacks: PostCallbacks = PostCallbacks.None,
    tapMarkers: PostTapMarkers = PostTapMarkers(),
    // Hoisted to the stateful screen, which drives translation from it.
    listState: LazyListState = rememberLazyListState(),
    translations: PostTranslationsHandle? = null,
    onOpenImage: (postUri: String, imageIndex: Int) -> Unit = { _, _ -> },
    fullScreenRangePicker: Boolean = false,
) {
    val currentOnEvent by rememberUpdatedState(onEvent)
    val status = state.loadStatus

    // One column for every state, with the filter header outside the `when`: the
    // header keeps its place in the composition across Loading -> Loaded -> Empty,
    // so the chip row's scroll position and open menus survive a filter change.
    // It stays pinned above the body; Idle shows nothing, header included.
    Column(modifier = modifier.fillMaxSize()) {
        if (status !is SearchPostsLoadStatus.Idle) {
            FilterHeader(
                filters = state.filters,
                hitsTotal = state.hitsTotal,
                onEvent = onEvent,
                fullScreenRangePicker = fullScreenRangePicker,
            )
        }
        when (status) {
            SearchPostsLoadStatus.Idle -> Box(modifier = Modifier.weight(1f).fillMaxWidth())
            SearchPostsLoadStatus.InitialLoading -> PostsLoadingBody(modifier = Modifier.weight(1f).fillMaxWidth())
            is SearchPostsLoadStatus.Loaded -> {
                LoadedBody(
                    items = status.items,
                    isAppending = status.isAppending,
                    currentQuery = state.currentQuery,
                    listState = listState,
                    callbacks = callbacks,
                    tapMarkers = tapMarkers,
                    translations = translations,
                    onOpenImage = onOpenImage,
                    modifier = Modifier.weight(1f),
                )
                // Pagination trigger — exactly once per threshold crossing.
                // Drop `isAppending` from the LaunchedEffect key: the inner guard
                // already enforces single-flight, and re-keying every time the
                // flag flips tears down + restarts the snapshotFlow needlessly.
                // `currentStatus` is the latest Loaded value at collect time so
                // the inner read of endReached / isAppending stays fresh.
                val currentStatus by rememberUpdatedState(status)
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
                            currentOnEvent(SearchPostsEvent.LoadMore)
                        }
                }
            }
            SearchPostsLoadStatus.Empty ->
                PostsEmptyBody(
                    currentQuery = state.currentQuery,
                    currentSort = state.sort,
                    onClearQuery = { onEvent(SearchPostsEvent.ClearQueryClicked) },
                    onToggleSort = { onEvent(SearchPostsEvent.SortClicked(it)) },
                    modifier = Modifier.weight(1f),
                )
            is SearchPostsLoadStatus.InitialError ->
                PostsInitialErrorBody(
                    error = status.error,
                    onRetry = { onEvent(SearchPostsEvent.Retry) },
                    modifier = Modifier.weight(1f),
                )
        }
    }
}

@Composable
private fun LoadedBody(
    items: ImmutableList<FeedItemUi.Single>,
    isAppending: Boolean,
    currentQuery: String,
    listState: LazyListState,
    callbacks: PostCallbacks,
    tapMarkers: PostTapMarkers,
    translations: PostTranslationsHandle?,
    onOpenImage: (postUri: String, imageIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Per-list reveal state for covered (NSFW-labelled) media — same shape as the
    // feed: a @Stable PersistentSet, rememberSaveable via an explicit listSaver.
    var revealedMedia by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toPersistentSet() }),
    ) { mutableStateOf(persistentSetOf<String>()) }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag(SearchTestTags.POSTS_LIST),
        state = listState,
    ) {
        items(items = items, key = { it.post.id }) { item ->
            PostCard(
                post = item.post,
                bodyMatch = currentQuery.takeIf { it.isNotBlank() },
                callbacks = callbacks,
                isMediaRevealed = item.post.id in revealedMedia,
                onRevealMedia = { revealedMedia = revealedMedia.adding(item.post.id) },
                animateLikeTap = item.post.id == tapMarkers.lastLikeTapPostUri,
                animateRepostTap = item.post.id == tapMarkers.lastRepostTapPostUri,
                translation = translations?.stateOf(item.post),
                onTranslate = { translations?.translate(item.post) },
                onToggleTranslation = { translations?.toggleOriginal(item.post.id) },
                onImageClick = { index -> onOpenImage(item.post.id, index) },
                onQuotedImageClick = onOpenImage,
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
                    NubecitaWavyProgressIndicator()
                }
            }
        }
    }
}

/** The filter row and count, wired to the VM; pinned above every non-idle body. */
@Composable
private fun FilterHeader(
    filters: PostSearchFilters,
    hitsTotal: Long?,
    onEvent: (SearchPostsEvent) -> Unit,
    fullScreenRangePicker: Boolean,
) {
    PostsFilterHeader(
        filters = filters,
        hitsTotal = hitsTotal,
        onFiltersChange = { onEvent(SearchPostsEvent.FiltersChanged(it)) },
        onOpenSheet = { onEvent(SearchPostsEvent.OpenFilterSheetClicked) },
        fullScreenRangePicker = fullScreenRangePicker,
    )
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
