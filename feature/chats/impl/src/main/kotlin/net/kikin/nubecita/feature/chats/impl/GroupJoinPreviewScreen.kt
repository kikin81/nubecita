package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.feature.chats.impl.ui.GroupJoinPreviewCard

@Composable
internal fun GroupJoinPreviewScreen(
    viewModel: GroupJoinPreviewViewModel,
    onJoin: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val invalidLinkMsg = stringResource(R.string.group_join_error_invalid_link)
    val followMsg = stringResource(R.string.group_join_error_follow_required)
    val cannotRejoinMsg = stringResource(R.string.group_join_error_cannot_rejoin)
    val fullMsg = stringResource(R.string.add_members_error_full)
    val genericMsg = stringResource(R.string.group_join_error_generic)

    val currentOnJoin by rememberUpdatedState(onJoin)

    LaunchedEffect(Unit) {
        // showSnackbar suspends until the snackbar is dismissed (~4s); launch it in a
        // child coroutine so a queued effect isn't head-of-line blocked.
        // Mirrors ManageJoinLinkScreen's effect collector.
        val effectScope = this
        viewModel.effects.collect { effect ->
            when (effect) {
                is GroupJoinPreviewEffect.ShowError -> {
                    val msg =
                        when (effect.error) {
                            ChatError.InvalidInviteLink -> invalidLinkMsg
                            ChatError.FollowRequiredToJoin -> followMsg
                            ChatError.CannotRejoin -> cannotRejoinMsg
                            ChatError.GroupFull -> fullMsg
                            else -> genericMsg
                        }
                    effectScope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        snackbarHostState.showSnackbar(msg)
                    }
                }

                is GroupJoinPreviewEffect.NavigateToConvo -> currentOnJoin(effect.convoId)
            }
        }
    }

    GroupJoinPreviewScreenContent(
        state = state,
        onEvent = viewModel::handleEvent,
        onClose = onClose,
        snackbarHostState = snackbarHostState,
        modifier = modifier,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GroupJoinPreviewScreenContent(
    state: GroupJoinPreviewViewState,
    onEvent: (GroupJoinPreviewEvent) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.group_join_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        NubecitaIcon(
                            name = NubecitaIconName.Close,
                            contentDescription = stringResource(R.string.group_join_close),
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
                        .fillMaxSize()
                        .widthIn(max = 600.dp)
                        .align(Alignment.TopCenter),
            ) {
                when (val status = state.status) {
                    GroupJoinPreviewStatus.Loading ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            NubecitaWavyProgressIndicator()
                        }

                    is GroupJoinPreviewStatus.Error ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            TextButton(onClick = { onEvent(GroupJoinPreviewEvent.Retry) }) {
                                Text(stringResource(R.string.new_chat_retry))
                            }
                        }

                    is GroupJoinPreviewStatus.Loaded ->
                        GroupJoinPreviewCard(
                            info = status.info,
                            joinInFlight = state.joinInFlight,
                            onJoin = { onEvent(GroupJoinPreviewEvent.JoinTapped) },
                            modifier = Modifier.verticalScroll(rememberScrollState()),
                        )

                    GroupJoinPreviewStatus.RequestSent ->
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState()),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                NubecitaIcon(
                                    name = NubecitaIconName.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier =
                                        Modifier
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer)
                                            .padding(24.dp)
                                            .size(48.dp),
                                )
                                Text(
                                    text = stringResource(R.string.group_join_sent_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(top = 16.dp),
                                )
                                Text(
                                    text = stringResource(R.string.group_join_sent_body),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 8.dp),
                                )
                                Button(onClick = onClose, modifier = Modifier.padding(top = 24.dp)) {
                                    Text(stringResource(R.string.group_join_done))
                                }
                            }
                        }
                }
            }
        }
    }
}
