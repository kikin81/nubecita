package net.kikin.nubecita.feature.chats.impl

import android.content.ClipData
import android.content.Intent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.ui.CreateJoinLinkContent
import net.kikin.nubecita.feature.chats.impl.ui.JoinLinkCard

@Composable
internal fun ManageJoinLinkScreen(
    viewModel: ManageJoinLinkViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()

    val fullMsg = stringResource(R.string.add_members_error_full)
    val followMsg = stringResource(R.string.add_members_error_follow_required)
    val permMsg = stringResource(R.string.add_members_error_permission)
    val genericMsg = stringResource(R.string.add_members_error_generic)
    val copiedMsg = stringResource(R.string.join_link_copied)

    LaunchedEffect(Unit) {
        // showSnackbar suspends until the snackbar is dismissed (~4s); launch it in a
        // child coroutine so a queued effect isn't head-of-line blocked.
        // Mirrors GroupJoinRequestsScreen's effect collector.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is ManageJoinLinkEffect.ShowError -> {
                    val msg =
                        when (effect.error) {
                            ChatError.GroupFull -> fullMsg
                            ChatError.FollowRequiredToAdd -> followMsg
                            ChatError.InsufficientPermission -> permMsg
                            else -> genericMsg
                        }
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(msg)
                    }
                }
            }
        }
    }

    ManageJoinLinkScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onClose = onBack,
        onCopy = { url ->
            scope.launch {
                clipboard.setClipEntry(ClipData.newPlainText("invite", url).toClipEntry())
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(copiedMsg)
            }
        },
        onShare = { url ->
            val send =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, url)
                }
            context.startActivity(Intent.createChooser(send, null))
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ManageJoinLinkScreenContent(
    state: ManageJoinLinkViewState,
    onEvent: (ManageJoinLinkEvent) -> Unit,
    onClose: () -> Unit,
    onCopy: (String) -> Unit,
    onShare: (String) -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.join_link_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription = stringResource(R.string.join_link_close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Box(
                modifier =
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter),
            ) {
                when (val status = state.status) {
                    ManageJoinLinkStatus.Loading ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            NubecitaWavyProgressIndicator()
                        }

                    is ManageJoinLinkStatus.Error ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { onEvent(ManageJoinLinkEvent.Retry) }) {
                                Text(stringResource(R.string.new_chat_retry))
                            }
                        }

                    is ManageJoinLinkStatus.Loaded ->
                        when (val link = status.link) {
                            null ->
                                CreateJoinLinkContent(
                                    creating = state.mutationInFlight,
                                    onCreate = { rule, approval ->
                                        onEvent(ManageJoinLinkEvent.CreateTapped(rule, approval))
                                    },
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                )

                            else ->
                                JoinLinkCard(
                                    link = link,
                                    mutationInFlight = state.mutationInFlight,
                                    onCopy = { onCopy(link.url) },
                                    onShare = { onShare(link.url) },
                                    onEnabledChange = { enable ->
                                        onEvent(
                                            if (enable) {
                                                ManageJoinLinkEvent.EnableTapped
                                            } else {
                                                ManageJoinLinkEvent.DisableTapped
                                            },
                                        )
                                    },
                                    onJoinRuleChange = {
                                        onEvent(ManageJoinLinkEvent.JoinRuleChanged(it))
                                    },
                                    onRequireApprovalChange = {
                                        onEvent(ManageJoinLinkEvent.RequireApprovalChanged(it))
                                    },
                                    modifier = Modifier.verticalScroll(rememberScrollState()),
                                )
                        }
                }
            }
        }
    }
}
