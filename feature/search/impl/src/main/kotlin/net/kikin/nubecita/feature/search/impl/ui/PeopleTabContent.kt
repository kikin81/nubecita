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
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.feature.search.impl.SearchActorsError
import net.kikin.nubecita.feature.search.impl.SearchActorsEvent
import net.kikin.nubecita.feature.search.impl.SearchActorsLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchActorsState

/**
 * Stateless body for the People tab. Branches on
 * [SearchActorsState.loadStatus]. For Loaded, renders a LazyColumn of
 * [ActorRow]s with HorizontalDivider between rows. Pagination trigger
 * keyed on `(listState, items.size, endReached)` — NOT isAppending —
 * per vrba.6's code-quality lesson. The inner `.collect` reads the
 * live status via [rememberUpdatedState] so the guard sees current
 * `isAppending`.
 *
 * `onEvent` dispatches every user intent upward. Doesn't hoist
 * `hiltViewModel` itself — that's `SearchActorsScreen`'s job — so
 * previews + screenshot tests can drive this Composable without a
 * Hilt graph.
 */
@Composable
internal fun PeopleTabContent(
    state: SearchActorsState,
    onEvent: (SearchActorsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentOnEvent by rememberUpdatedState(onEvent)

    when (val status = state.loadStatus) {
        SearchActorsLoadStatus.Idle ->
            Box(modifier = modifier.fillMaxSize())
        SearchActorsLoadStatus.InitialLoading ->
            PeopleLoadingBody(modifier = modifier)
        is SearchActorsLoadStatus.Loaded -> {
            val currentStatus by rememberUpdatedState(status)
            LoadedBody(
                items = status.items,
                isAppending = status.isAppending,
                currentQuery = state.currentQuery,
                listState = listState,
                onEvent = onEvent,
                modifier = modifier,
            )
            // Pagination trigger — keyed on (listState, items.size, endReached),
            // NOT isAppending (vrba.6 code-quality lesson). Live status is read
            // inside the collect lambda via rememberUpdatedState so the guard
            // sees current isAppending without retearing the snapshotFlow.
            LaunchedEffect(listState, status.items.size, status.endReached) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    val last = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                    last to info.totalItemsCount
                }.distinctUntilChanged()
                    .collect { (last, total) ->
                        if (total <= 0 || last < total - PAGINATION_LOAD_AHEAD) return@collect
                        if (currentStatus.endReached || currentStatus.isAppending) return@collect
                        currentOnEvent(SearchActorsEvent.LoadMore)
                    }
            }
        }
        SearchActorsLoadStatus.Empty ->
            PeopleEmptyBody(
                currentQuery = state.currentQuery,
                onClearQuery = { onEvent(SearchActorsEvent.ClearQueryClicked) },
                modifier = modifier,
            )
        is SearchActorsLoadStatus.InitialError ->
            PeopleInitialErrorBody(
                error = status.error,
                onRetry = { onEvent(SearchActorsEvent.Retry) },
                modifier = modifier,
            )
    }
}

@Composable
private fun LoadedBody(
    items: ImmutableList<ActorUi>,
    isAppending: Boolean,
    currentQuery: String,
    listState: LazyListState,
    onEvent: (SearchActorsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize(), state = listState) {
        items(items = items, key = { it.did }) { actor ->
            ActorRow(
                actor = actor,
                query = currentQuery,
                onClick = { onEvent(SearchActorsEvent.ActorTapped(actor.handle)) },
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

// ---------------------------------------------------------------------------
// Previews
// ---------------------------------------------------------------------------

@Preview(name = "PeopleTabContent — InitialLoading", showBackground = true)
@Composable
private fun PeopleTabContentInitialLoadingPreview() {
    NubecitaTheme {
        PeopleTabContent(
            state = SearchActorsState(loadStatus = SearchActorsLoadStatus.InitialLoading, currentQuery = "alice"),
            onEvent = {},
        )
    }
}

@Preview(name = "PeopleTabContent — Empty", showBackground = true)
@Composable
private fun PeopleTabContentEmptyPreview() {
    NubecitaTheme {
        PeopleTabContent(
            state = SearchActorsState(loadStatus = SearchActorsLoadStatus.Empty, currentQuery = "xyzqq"),
            onEvent = {},
        )
    }
}

@Preview(name = "PeopleTabContent — Loaded with match", showBackground = true)
@Composable
private fun PeopleTabContentLoadedPreview() {
    val actors =
        persistentListOf(
            ActorUi(
                did = "did:plc:alice",
                handle = "alice.bsky.social",
                displayName = "Alice Chen",
                avatarUrl = null,
            ),
            ActorUi(
                did = "did:plc:alex",
                handle = "alex.bsky.social",
                displayName = "Alex Park",
                avatarUrl = null,
            ),
        )
    NubecitaTheme {
        PeopleTabContent(
            state =
                SearchActorsState(
                    currentQuery = "al",
                    loadStatus =
                        SearchActorsLoadStatus.Loaded(
                            items = actors,
                            nextCursor = "c2",
                            endReached = false,
                            isAppending = false,
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "PeopleTabContent — Loaded + Appending", showBackground = true)
@Composable
private fun PeopleTabContentLoadedAppendingPreview() {
    val actors =
        persistentListOf(
            ActorUi(
                did = "did:plc:alice",
                handle = "alice.bsky.social",
                displayName = "Alice Chen",
                avatarUrl = null,
            ),
        )
    NubecitaTheme {
        PeopleTabContent(
            state =
                SearchActorsState(
                    currentQuery = "al",
                    loadStatus =
                        SearchActorsLoadStatus.Loaded(
                            items = actors,
                            nextCursor = "c2",
                            endReached = false,
                            isAppending = true,
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "PeopleTabContent — InitialError(Network)", showBackground = true)
@Composable
private fun PeopleTabContentInitialErrorPreview() {
    NubecitaTheme {
        PeopleTabContent(
            state =
                SearchActorsState(
                    loadStatus =
                        SearchActorsLoadStatus.InitialError(
                            error = SearchActorsError.Network,
                        ),
                    currentQuery = "alice",
                ),
            onEvent = {},
        )
    }
}
