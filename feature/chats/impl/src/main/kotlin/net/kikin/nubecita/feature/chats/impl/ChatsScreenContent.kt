package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.chats.impl.ui.ConvoListItem

/**
 * Stateless content for the Chats tab home. The stateful entry
 * [ChatsScreen] hosts the ViewModel; previews + screenshot tests
 * render this composable directly with fixture inputs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatsScreenContent(
    state: ChatsScreenViewState,
    snackbarHostState: SnackbarHostState,
    onEvent: (ChatsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.chats_title)) })
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val status = state.status) {
                ChatsLoadStatus.Loading -> LoadingBody()
                is ChatsLoadStatus.Loaded ->
                    // PullToRefreshBox wraps both empty and non-empty Loaded states so a user
                    // sitting on the empty state can pull to refresh and discover newly-started
                    // conversations without leaving the screen. Loading and InitialError have
                    // their own affordances (spinner / Retry button), so they stay outside.
                    PullToRefreshBox(
                        isRefreshing = status.isRefreshing,
                        onRefresh = { onEvent(ChatsEvent.Refresh) },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        if (status.items.isEmpty()) {
                            EmptyBody()
                        } else {
                            LoadedBody(
                                items = status.items,
                                onTap = { did -> onEvent(ChatsEvent.ConvoTapped(did)) },
                            )
                        }
                    }
                is ChatsLoadStatus.InitialError -> ErrorBody(error = status.error, onRetry = { onEvent(ChatsEvent.RetryClicked) })
            }
        }
    }
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBody() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(R.string.chats_empty_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.chats_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LoadedBody(
    items: kotlinx.collections.immutable.ImmutableList<ConvoListItemUi>,
    onTap: (otherUserDid: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
    ) {
        items(items = items, key = { it.convoId }, contentType = { "convo-row" }) { item ->
            ConvoListItem(item = item, onTap = onTap)
        }
    }
}

@Composable
private fun ErrorBody(
    error: ChatsError,
    onRetry: () -> Unit,
) {
    val (titleRes, bodyRes, showRetry) =
        when (error) {
            ChatsError.Network -> Triple(R.string.chats_error_network_title, R.string.chats_error_network_body, true)
            ChatsError.NotEnrolled -> Triple(R.string.chats_error_not_enrolled_title, R.string.chats_error_not_enrolled_body, false)
            is ChatsError.Unknown -> Triple(R.string.chats_error_unknown_title, R.string.chats_error_unknown_body, true)
        }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (showRetry) {
            OutlinedButton(onClick = onRetry) {
                Text(stringResource(R.string.chats_error_retry))
            }
        }
    }
}
