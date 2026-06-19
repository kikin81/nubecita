package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.feature.chats.impl.ui.DaySeparatorChip
import net.kikin.nubecita.feature.chats.impl.ui.MessageBubble

/**
 * Stateless content for a single chat thread. The stateful entry
 * [ChatScreen] hosts the ViewModel; previews + screenshot tests render
 * this composable directly with fixture inputs.
 *
 * The thread `LazyColumn` runs `reverseLayout = true` so the newest message
 * sits at the visual bottom while the underlying list is newest-first.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatScreenContent(
    state: ChatScreenViewState,
    onEvent: (ChatEvent) -> Unit,
    textFieldState: TextFieldState,
    modifier: Modifier = Modifier,
    snackbarHostState: SnackbarHostState = remember { SnackbarHostState() },
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
        // Body inset = the full safe-drawing area MINUS the IME. The IME is owned
        // by exactly one layer — the `bottomBar` composer below (via imePadding) —
        // so the keyboard never squeezes the body or drags the TopAppBar up.
        // Excluding only `ime` (rather than narrowing to `systemBars`) keeps the
        // body clear of the display cutout too: with no orientation lock and
        // `layoutInDisplayCutoutMode=always`, a landscape side cutout would
        // otherwise occlude the message list / error states. When the composer is
        // absent (Loading / InitialError) the bottom inset also applies, so that
        // content clears the navigation bar.
        contentWindowInsets = WindowInsets.safeDrawing.exclude(WindowInsets.ime),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChatTopBarAvatar(state)
                        Text(
                            text = state.otherUserDisplayName ?: state.otherUserHandle,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 12.dp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { onEvent(ChatEvent.BackPressed) }) {
                        NubecitaIcon(
                            name = NubecitaIconName.ArrowBack,
                            contentDescription = stringResource(R.string.chat_back_content_description),
                            filled = true,
                            modifier = Modifier.mirror(),
                        )
                    }
                },
            )
        },
        bottomBar = {
            // The composer is available whenever the convo is loaded (including an
            // empty thread, so the first message can be sent). Loading / initial-
            // error states show no composer. Per M3 Scaffold's contract, a present
            // bottomBar owns the bottom inset (the body's bottom padding becomes the
            // bottomBar's measured height, NOT the raw inset) — so the composer self-
            // pins above the navigation bar when the keyboard is closed and above the
            // IME when it's open. `.navigationBarsPadding().imePadding()` chains so
            // each windowInsetsPadding consumes the previous: closed → nav-bar height;
            // open → full IME (which already subsumes the nav-bar area).
            if (state.status is ChatLoadStatus.Loaded) {
                ChatComposerRow(
                    textFieldState = textFieldState,
                    isSendEnabled = state.isSendEnabled,
                    onSend = { onEvent(ChatEvent.Send) },
                    // Anchor to newest (index 0 under reverseLayout) when the
                    // composer gains focus, so the IME never hides the latest run.
                    onFocus = { scope.launch { listState.animateScrollToItem(0) } },
                    modifier =
                        Modifier
                            .navigationBarsPadding()
                            .imePadding(),
                )
            }
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .consumeWindowInsets(padding),
        ) {
            when (val status = state.status) {
                ChatLoadStatus.Loading -> LoadingBody()
                is ChatLoadStatus.Loaded ->
                    if (status.items.isEmpty()) {
                        EmptyBody()
                    } else {
                        LoadedBody(
                            items = status.items,
                            listState = listState,
                            onQuotedPostTap = { uri -> onEvent(ChatEvent.QuotedPostTapped(uri)) },
                            onRetrySend = { tempId -> onEvent(ChatEvent.RetrySend(tempId)) },
                        )
                    }
                is ChatLoadStatus.InitialError ->
                    ErrorBody(status.error, onRetry = { onEvent(ChatEvent.RetryClicked) })
            }
        }
    }

    // Anchor to newest when a message is appended (optimistic send / refresh).
    // Keying on the newest item's key fires the scroll exactly when the head changes.
    val newestKey = (state.status as? ChatLoadStatus.Loaded)?.items?.firstOrNull()?.key
    LaunchedEffect(newestKey) {
        if (newestKey != null) listState.animateScrollToItem(0)
    }
}

/**
 * M3 Expressive composer row pinned below the thread. `surfaceContainerHigh`
 * per the surface-roles contract (raised affordance). The text field uses the
 * Compose `TextFieldState` overload (the VM owns the state per the editor
 * exception); the send control is disabled until [isSendEnabled].
 */
@Composable
private fun ChatComposerRow(
    textFieldState: TextFieldState,
    isSendEnabled: Boolean,
    onSend: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Inset handling lives entirely in the caller's `modifier` (the Scaffold
    // bottomBar slot passes `.navigationBarsPadding().imePadding()`). This row
    // only paints + lays out its controls; it adds no window-inset padding of
    // its own, so there's exactly one IME owner and no double-lift.
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                state = textFieldState,
                modifier =
                    Modifier
                        .weight(1f)
                        .onFocusChanged { if (it.isFocused) onFocus() },
                placeholder = { Text(text = stringResource(R.string.chat_composer_placeholder)) },
                lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 5),
                keyboardOptions =
                    KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send,
                    ),
                onKeyboardAction = { if (isSendEnabled) onSend() },
            )
            IconButton(
                onClick = onSend,
                enabled = isSendEnabled,
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.Send,
                    contentDescription = stringResource(R.string.chat_composer_send_content_description),
                    filled = true,
                )
            }
        }
    }
}

@Composable
private fun ChatTopBarAvatar(state: ChatScreenViewState) {
    NubecitaAvatar(
        model = state.otherUserAvatarUrl,
        contentDescription = null,
        size = 40.dp,
        fallback =
            avatarFallbackFor(
                did = state.otherUserDid,
                handle = state.otherUserHandle,
                displayName = state.otherUserDisplayName,
            ),
    )
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
            text = stringResource(R.string.chat_empty_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.chat_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LoadedBody(
    items: ImmutableList<ThreadItem>,
    listState: LazyListState,
    onQuotedPostTap: (quotedPostUri: String) -> Unit,
    onRetrySend: (tempId: String) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag("chat_thread_list"),
        state = listState,
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        // 2dp baseline matches ListItemDefaults.SegmentedGap (the M3 Expressive grouped-
        // row convention we use on the convo list). Same-sender bubble runs read as
        // tightly-grouped without the apparent ~8dp gap that comes from bodyLarge's
        // line-height excess stacking with a larger baseline.
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { _, it -> it.key },
            contentType = { _, it -> if (it is ThreadItem.Message) "msg" else "sep" },
        ) { position, item ->
            when (item) {
                is ThreadItem.Message -> {
                    // Cross-run gap lives on the SCREEN-TOP edge of an oldest-of-run
                    // item (runIndex == 0). With reverseLayout = true, source[i+1] (the
                    // older-run neighbor) renders above source[i] on screen, so the top
                    // edge of this Row is where the run boundary sits. 10.dp here +
                    // the LazyColumn's spacedBy(2.dp) baseline = 12.dp total cross-run
                    // gap. position < items.lastIndex skips the screen-topmost item
                    // (no neighbor above; only the TopAppBar).
                    val crossRunGap = if (item.runIndex == 0 && position < items.lastIndex) 10.dp else 0.dp
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = crossRunGap),
                        horizontalArrangement =
                            if (item.message.isOutgoing) Arrangement.End else Arrangement.Start,
                    ) {
                        MessageBubble(
                            message = item.message,
                            runIndex = item.runIndex,
                            runCount = item.runCount,
                            onQuotedPostTap = onQuotedPostTap,
                            onRetrySend = onRetrySend,
                        )
                    }
                }
                is ThreadItem.DaySeparator -> DaySeparatorChip(label = item.label)
            }
        }
    }
}

@Composable
private fun ErrorBody(
    error: ChatError,
    onRetry: () -> Unit,
) {
    val (titleRes, bodyRes, showRetry) =
        when (error) {
            ChatError.Network ->
                Triple(R.string.chats_error_network_title, R.string.chats_error_network_body, true)
            ChatError.NotEnrolled ->
                Triple(R.string.chats_error_not_enrolled_title, R.string.chats_error_not_enrolled_body, false)
            ChatError.ConvoNotFound ->
                Triple(R.string.chat_error_convo_not_found_title, R.string.chat_error_convo_not_found_body, false)
            ChatError.MessagesDisabled ->
                Triple(R.string.chat_error_messages_disabled_title, R.string.chat_error_messages_disabled_body, false)
            is ChatError.Unknown ->
                Triple(R.string.chats_error_unknown_title, R.string.chats_error_unknown_body, true)
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
