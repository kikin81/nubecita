package net.kikin.nubecita.feature.moderation.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.data.models.BlockedAccount
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Blocked-accounts management screen (`nubecita-oftc.17`). Lists the viewer's
 * blocked accounts with a per-row Unblock action. Reached from Settings →
 * Moderation. Stateful entry owns the VM + effect collector + snackbar;
 * [BlockedAccountsContent] is the pure projection for previews/screenshots.
 */
@Composable
internal fun BlockedAccountsScreen(
    viewModel: BlockedAccountsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val unblockErrorMsg = stringResource(R.string.blocked_accounts_unblock_error)
    val currentOnBack by rememberUpdatedState(onBack)

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BlockedAccountsEffect.ShowUnblockError -> {
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(unblockErrorMsg)
                }
            }
        }
    }

    BlockedAccountsContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = currentOnBack,
        onEvent = viewModel::handleEvent,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BlockedAccountsContent(
    state: BlockedAccountsState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onEvent: (BlockedAccountsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.blocked_accounts_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.blocked_accounts_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val status = state.status) {
                BlockedAccountsStatus.Loading ->
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                BlockedAccountsStatus.Error ->
                    CenteredMessage(
                        title = stringResource(R.string.blocked_accounts_error_title),
                        body = stringResource(R.string.blocked_accounts_error_body),
                        action = {
                            TextButton(onClick = { onEvent(BlockedAccountsEvent.Retry) }) {
                                Text(stringResource(R.string.blocked_accounts_retry))
                            }
                        },
                    )
                is BlockedAccountsStatus.Loaded ->
                    if (status.accounts.isEmpty()) {
                        CenteredMessage(
                            title = stringResource(R.string.blocked_accounts_empty_title),
                            body = stringResource(R.string.blocked_accounts_empty_body),
                        )
                    } else {
                        BlockedList(accounts = status.accounts, onEvent = onEvent)
                    }
            }
        }
    }
}

@Composable
private fun BlockedList(
    accounts: ImmutableList<BlockedAccount>,
    onEvent: (BlockedAccountsEvent) -> Unit,
) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(items = accounts, key = { it.did }) { account ->
            BlockedRow(account = account, onUnblock = { onEvent(BlockedAccountsEvent.UnblockClicked(account)) })
        }
    }
}

@Composable
private fun BlockedRow(
    account: BlockedAccount,
    onUnblock: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NubecitaAvatar(
            model = account.avatarUrl,
            contentDescription = null,
            fallback =
                avatarFallbackFor(
                    did = account.did,
                    handle = account.handle,
                    displayName = account.displayName,
                ),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = account.displayName ?: "@${account.handle}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (account.displayName != null) {
                Text(
                    text = "@${account.handle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        OutlinedButton(onClick = onUnblock) {
            Text(stringResource(R.string.blocked_accounts_unblock))
        }
    }
}

@Composable
private fun CenteredMessage(
    title: String,
    body: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
        action?.invoke()
    }
}
