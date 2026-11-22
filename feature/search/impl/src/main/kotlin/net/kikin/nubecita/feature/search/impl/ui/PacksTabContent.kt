package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.data.models.StarterPackUi
import net.kikin.nubecita.data.models.StarterPackUiFixtures
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.SearchPacksError
import net.kikin.nubecita.feature.search.impl.SearchPacksEvent
import net.kikin.nubecita.feature.search.impl.SearchPacksLoadStatus
import net.kikin.nubecita.feature.search.impl.SearchPacksState

/**
 * Stateless body for the Packs tab. Branches on [SearchPacksState.loadStatus] and renders a
 * LazyColumn of [StarterPackRow]s for the loaded variant, paginating near the end. Mirrors
 * [FeedsTabContent].
 */
@Composable
internal fun PacksTabContent(
    state: SearchPacksState,
    onEvent: (SearchPacksEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val currentOnEvent by rememberUpdatedState(onEvent)

    when (val status = state.loadStatus) {
        SearchPacksLoadStatus.Idle -> Box(modifier = modifier.fillMaxSize())
        SearchPacksLoadStatus.InitialLoading -> PacksLoadingBody(modifier = modifier)
        is SearchPacksLoadStatus.Loaded -> {
            val currentStatus by rememberUpdatedState(status)
            LoadedBody(
                items = status.items,
                isAppending = status.isAppending,
                listState = listState,
                onPackClick = { pack -> onEvent(SearchPacksEvent.PackTapped(pack.uri)) },
                modifier = modifier,
            )
            LaunchedEffect(listState, status.items.size, status.endReached) {
                snapshotFlow {
                    val info = listState.layoutInfo
                    (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
                }.distinctUntilChanged()
                    .collect { (last, total) ->
                        if (total <= 0 || last < total - PAGINATION_LOAD_AHEAD) return@collect
                        if (currentStatus.endReached || currentStatus.isAppending) return@collect
                        currentOnEvent(SearchPacksEvent.LoadMore)
                    }
            }
        }
        SearchPacksLoadStatus.Empty ->
            PacksEmptyBody(
                currentQuery = state.currentQuery,
                onClearQuery = { onEvent(SearchPacksEvent.ClearQueryClicked) },
                modifier = modifier,
            )
        is SearchPacksLoadStatus.InitialError ->
            PacksInitialErrorBody(
                error = status.error,
                onRetry = { onEvent(SearchPacksEvent.Retry) },
                modifier = modifier,
            )
    }
}

@Composable
private fun LoadedBody(
    items: ImmutableList<StarterPackUi>,
    isAppending: Boolean,
    listState: LazyListState,
    onPackClick: (StarterPackUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(modifier = modifier.fillMaxSize().testTag("search_packs_list"), state = listState) {
        items(items = items, key = { it.uri }, contentType = { "pack" }) { pack ->
            StarterPackRow(pack = pack, onClick = { onPackClick(pack) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        if (isAppending) {
            item(key = "appending-indicator", contentType = "appending") {
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

/** Three skeleton pack rows while the first page loads. */
@Composable
internal fun PacksLoadingBody(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.search_packs_loading_content_description)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
    ) {
        repeat(3) { PackRowSkeleton() }
    }
}

@Composable
private fun PackRowSkeleton() {
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Box(
                Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholder),
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth(0.4f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(placeholder),
            )
        }
        Box(Modifier.size(32.dp).clip(CircleShape).background(placeholder))
    }
}

@Composable
private fun PacksEmptyBody(
    currentQuery: String,
    onClearQuery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.s6, vertical = MaterialTheme.spacing.s8),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.Inbox,
            contentDescription = stringResource(R.string.search_packs_empty_icon_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = 96.dp,
        )
        Text(
            text = stringResource(R.string.search_packs_empty_title, currentQuery),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.search_packs_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        FilledTonalButton(onClick = onClearQuery) {
            Text(stringResource(R.string.search_packs_empty_clear))
        }
    }
}

@Composable
private fun PacksInitialErrorBody(
    error: SearchPacksError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val (titleRes, bodyRes) =
        when (error) {
            SearchPacksError.Network -> R.string.search_packs_error_network_title to R.string.search_packs_error_network_body
            SearchPacksError.RateLimited -> R.string.search_packs_error_rate_limited_title to R.string.search_packs_error_rate_limited_body
            is SearchPacksError.Unknown -> R.string.search_packs_error_unknown_title to R.string.search_packs_error_unknown_body
        }
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(horizontal = MaterialTheme.spacing.s6, vertical = MaterialTheme.spacing.s8),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = NubecitaIconName.WifiOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = 64.dp,
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
            Text(stringResource(R.string.search_packs_error_retry))
        }
    }
}

private const val PAGINATION_LOAD_AHEAD = 5

@Preview(name = "PacksTabContent — Loaded", showBackground = true)
@Composable
private fun PacksTabContentLoadedPreview() {
    NubecitaTheme {
        PacksTabContent(
            state =
                SearchPacksState(
                    currentQuery = "android",
                    loadStatus =
                        SearchPacksLoadStatus.Loaded(
                            items = StarterPackUiFixtures.packs(3),
                            nextCursor = "c2",
                            endReached = false,
                        ),
                ),
            onEvent = {},
        )
    }
}

@Preview(name = "PacksTabContent — Error dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PacksTabContentErrorPreview() {
    NubecitaTheme {
        PacksTabContent(
            state = SearchPacksState(currentQuery = "android", loadStatus = SearchPacksLoadStatus.InitialError(SearchPacksError.Network)),
            onEvent = {},
        )
    }
}
