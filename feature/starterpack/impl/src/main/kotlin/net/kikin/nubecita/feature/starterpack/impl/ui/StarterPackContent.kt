package net.kikin.nubecita.feature.starterpack.impl.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.flow.distinctUntilChanged
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.starterpack.impl.R
import net.kikin.nubecita.feature.starterpack.impl.StarterPackEvent
import net.kikin.nubecita.feature.starterpack.impl.StarterPackLoadStatus
import net.kikin.nubecita.feature.starterpack.impl.StarterPackState
import net.kikin.nubecita.feature.starterpack.impl.data.StarterPackDetail

/**
 * Stateless starter-pack screen: top bar (back, the pack's name), then the body for the load
 * status. Hoists nothing from Hilt, so previews and screenshot tests can drive it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StarterPackContent(
    state: StarterPackState,
    onEvent: (StarterPackEvent) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
    listState: LazyListState = rememberLazyListState(),
) {
    val status = state.status
    val title =
        when (status) {
            is StarterPackLoadStatus.Loaded -> status.pack.name
            is StarterPackLoadStatus.Empty -> status.pack.name
            else -> stringResource(R.string.starterpack_title)
        }
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.starterpack_back_content_description),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val bodyModifier = Modifier.fillMaxSize().padding(innerPadding)
        when (status) {
            StarterPackLoadStatus.InitialLoading -> PackLoadingBody(modifier = bodyModifier)
            is StarterPackLoadStatus.InitialError ->
                PackErrorBody(
                    error = status.error,
                    onRetry = { onEvent(StarterPackEvent.Retry) },
                    modifier = bodyModifier,
                )
            is StarterPackLoadStatus.Empty ->
                LazyColumn(modifier = bodyModifier.testTag("starterpack_list")) {
                    item(key = "header", contentType = "header") { PackHeader(pack = status.pack) }
                    feedsSection(status.pack, onEvent)
                    item(key = "empty", contentType = "empty") { PackEmptyBody() }
                }
            is StarterPackLoadStatus.Loaded ->
                LoadedBody(
                    status = status,
                    viewerDid = state.viewerDid,
                    onEvent = onEvent,
                    listState = listState,
                    modifier = bodyModifier,
                )
        }
    }
}

@Composable
private fun LoadedBody(
    status: StarterPackLoadStatus.Loaded,
    viewerDid: String?,
    onEvent: (StarterPackEvent) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val currentStatus by rememberUpdatedState(status)
    val currentOnEvent by rememberUpdatedState(onEvent)
    LazyColumn(modifier = modifier.testTag("starterpack_list"), state = listState) {
        item(key = "header", contentType = "header") { PackHeader(pack = status.pack) }
        feedsSection(status.pack, onEvent)
        item(key = "people-title", contentType = "section-title") {
            PackSectionTitle(
                title = stringResource(R.string.starterpack_section_people),
                count = status.pack.memberCount,
            )
        }
        items(items = status.members, key = { it.did }, contentType = { "member" }) { member ->
            PackMemberRow(
                actor = member,
                onClick = { onEvent(StarterPackEvent.MemberTapped(member.handle)) },
                onFollowClick = if (member.did == viewerDid) null else ({ onEvent(StarterPackEvent.FollowTapped(member.did)) }),
            )
        }
        if (status.isAppending) {
            item(key = "appending", contentType = "appending") {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = MaterialTheme.spacing.s4),
                    contentAlignment = Alignment.Center,
                ) {
                    NubecitaWavyProgressIndicator()
                }
            }
        }
    }
    LaunchedEffect(listState, status.members.size, status.endReached) {
        snapshotFlow {
            val info = listState.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }.distinctUntilChanged()
            .collect { (last, total) ->
                if (total <= 0 || last < total - PAGINATION_LOAD_AHEAD) return@collect
                if (currentStatus.endReached || currentStatus.isAppending) return@collect
                currentOnEvent(StarterPackEvent.LoadMore)
            }
    }
}

/** The pack's feeds, when it has any: a section title and one row per feed. */
private fun androidx.compose.foundation.lazy.LazyListScope.feedsSection(
    pack: StarterPackDetail,
    onEvent: (StarterPackEvent) -> Unit,
) {
    if (pack.feeds.isEmpty()) return
    item(key = "feeds-title", contentType = "section-title") {
        PackSectionTitle(title = stringResource(R.string.starterpack_section_feeds))
    }
    items(items = pack.feeds, key = { "feed-${it.uri}" }, contentType = { "feed" }) { feed ->
        PackFeedRow(
            feed = feed,
            onClick = { onEvent(StarterPackEvent.FeedTapped(feed.uri, feed.displayName)) },
        )
    }
}

private const val PAGINATION_LOAD_AHEAD = 5
