package net.kikin.nubecita.feature.bookmarks.impl.ui

import android.content.res.Configuration
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toPersistentSet
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.core.postinteractions.PostTapMarkers
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.designsystem.component.PostCard
import net.kikin.nubecita.designsystem.component.PostCardShimmer
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.bookmarks.impl.BookmarksError
import net.kikin.nubecita.feature.bookmarks.impl.BookmarksEvent
import net.kikin.nubecita.feature.bookmarks.impl.BookmarksLoadStatus
import net.kikin.nubecita.feature.bookmarks.impl.BookmarksState
import net.kikin.nubecita.feature.bookmarks.impl.R

/**
 * Stateless body for the Bookmarks list. Branches on
 * [BookmarksState.loadStatus]: shimmer cards while loading, a LazyColumn
 * of [PostCard]s when loaded, an empty state, and a full-screen retry
 * layout on the initial error.
 *
 * `onEvent` dispatches every user intent upward to the ViewModel; the
 * Composable doesn't hoist `hiltViewModel` itself (that's
 * `BookmarksScreen`'s job) so previews + screenshot tests can drive it
 * without a Hilt graph.
 *
 * Pagination trigger: a `snapshotFlow` over the threshold-boolean
 * (lastVisibleIndex > items.size - PAGINATION_PREFETCH_DISTANCE) fires
 * `LoadMore` exactly once per threshold crossing. Mirrors
 * `:feature:search:impl/ui/PostsTabContent`.
 */
@Composable
internal fun BookmarksContent(
    state: BookmarksState,
    onEvent: (BookmarksEvent) -> Unit,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks.None,
    tapMarkers: PostTapMarkers = PostTapMarkers(),
) {
    val listState = rememberLazyListState()
    val currentOnEvent by rememberUpdatedState(onEvent)

    when (val status = state.loadStatus) {
        BookmarksLoadStatus.InitialLoading ->
            BookmarksLoadingBody(modifier = modifier)
        is BookmarksLoadStatus.Loaded -> {
            BookmarksLoadedBody(
                items = status.items,
                isAppending = status.isAppending,
                listState = listState,
                callbacks = callbacks,
                tapMarkers = tapMarkers,
                modifier = modifier,
            )
            // Pagination trigger — exactly once per threshold crossing. Drop
            // `isAppending` from the LaunchedEffect key: the inner guard already
            // enforces single-flight, and re-keying on every flag flip tears down
            // + restarts the snapshotFlow needlessly. `currentStatus` stays fresh.
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
                        currentOnEvent(BookmarksEvent.LoadMore)
                    }
            }
        }
        BookmarksLoadStatus.Empty ->
            BookmarksEmptyBody(modifier = modifier)
        is BookmarksLoadStatus.InitialError ->
            BookmarksInitialErrorBody(
                error = status.error,
                onRetry = { onEvent(BookmarksEvent.Retry) },
                modifier = modifier,
            )
    }
}

@Composable
private fun BookmarksLoadedBody(
    items: ImmutableList<PostUi>,
    isAppending: Boolean,
    listState: LazyListState,
    callbacks: PostCallbacks,
    tapMarkers: PostTapMarkers,
    modifier: Modifier = Modifier,
) {
    // Per-list reveal state for covered (NSFW-labelled) media — same shape as the
    // feed / search: a @Stable PersistentSet, rememberSaveable via listSaver.
    var revealedMedia by rememberSaveable(
        stateSaver = listSaver(save = { it.toList() }, restore = { it.toPersistentSet() }),
    ) { mutableStateOf(persistentSetOf<String>()) }
    LazyColumn(
        modifier = modifier.fillMaxSize().testTag("bookmarks_list"),
        state = listState,
    ) {
        items(items = items, key = { it.id }) { post ->
            PostCard(
                post = post,
                callbacks = callbacks,
                isMediaRevealed = post.id in revealedMedia,
                onRevealMedia = { revealedMedia = revealedMedia.adding(post.id) },
                animateLikeTap = post.id == tapMarkers.lastLikeTapPostUri,
                animateRepostTap = post.id == tapMarkers.lastRepostTapPostUri,
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

@Composable
private fun BookmarksLoadingBody(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.bookmarks_loading_content_description)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
    ) {
        repeat(3) { PostCardShimmer() }
    }
}

@Composable
private fun BookmarksEmptyBody(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Bookmark,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(R.string.bookmarks_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.bookmarks_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun BookmarksInitialErrorBody(
    error: BookmarksError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (titleRes, bodyRes) =
        when (error) {
            BookmarksError.Network ->
                R.string.bookmarks_error_network_title to R.string.bookmarks_error_network_body
            is BookmarksError.Unknown ->
                R.string.bookmarks_error_unknown_title to R.string.bookmarks_error_unknown_body
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRetry) {
            Text(stringResource(R.string.bookmarks_error_retry))
        }
    }
}

private val ICON_SIZE = 64.dp
private const val PAGINATION_PREFETCH_DISTANCE = 5

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "BookmarksContent — Loading", showBackground = true)
@Preview(name = "BookmarksContent — Loading (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookmarksContentLoadingPreview() {
    NubecitaTheme {
        BookmarksContent(state = BookmarksState(BookmarksLoadStatus.InitialLoading), onEvent = {})
    }
}

@Preview(name = "BookmarksContent — Empty", showBackground = true)
@Composable
private fun BookmarksContentEmptyPreview() {
    NubecitaTheme {
        BookmarksContent(state = BookmarksState(BookmarksLoadStatus.Empty), onEvent = {})
    }
}

@Preview(name = "BookmarksContent — InitialError(Network)", showBackground = true)
@Composable
private fun BookmarksContentErrorPreview() {
    NubecitaTheme {
        BookmarksContent(
            state = BookmarksState(BookmarksLoadStatus.InitialError(BookmarksError.Network)),
            onEvent = {},
        )
    }
}

@Preview(name = "BookmarksContent — Loaded", showBackground = true)
@Preview(name = "BookmarksContent — Loaded (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BookmarksContentLoadedPreview() {
    NubecitaTheme {
        BookmarksContent(
            state =
                BookmarksState(
                    BookmarksLoadStatus.Loaded(
                        items =
                            persistentListOf(
                                previewBookmarkPost("at://p1", "A post I bookmarked about Kotlin coroutines."),
                                previewBookmarkPost("at://p2", "Another bookmarked post about Compose."),
                            ),
                        nextCursor = null,
                        endReached = true,
                    ),
                ),
            onEvent = {},
        )
    }
}

private fun previewBookmarkPost(
    uri: String,
    text: String,
): PostUi =
    PostUi(
        id = uri,
        cid = "bafyreifakecid000000000000000000000000000000000",
        author =
            AuthorUi(
                did = "did:plc:fake",
                handle = "fake.bsky.social",
                displayName = "Fake User",
                avatarUrl = null,
            ),
        createdAt = kotlin.time.Instant.parse("2026-04-25T12:00:00Z"),
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )
