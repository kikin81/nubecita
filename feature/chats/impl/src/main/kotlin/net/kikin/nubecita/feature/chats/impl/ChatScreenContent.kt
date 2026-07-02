package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.launch
import net.kikin.nubecita.designsystem.component.AvatarGroup
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.NubecitaWavyProgressIndicator
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.feature.chats.impl.ui.DaySeparatorChip
import net.kikin.nubecita.feature.chats.impl.ui.EmojiPickerDialog
import net.kikin.nubecita.feature.chats.impl.ui.MessageBubble
import net.kikin.nubecita.feature.chats.impl.ui.ReactionMenu

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
                    when (val header = state.header) {
                        is ChatHeader.Direct ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                ChatTopBarAvatar(header)
                                Text(
                                    text = header.displayName ?: header.handle,
                                    style = MaterialTheme.typography.titleMedium,
                                    modifier = Modifier.padding(start = 12.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        is ChatHeader.Group ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AvatarGroup(
                                    members = header.members,
                                    contentDescription = null,
                                    avatarSize = 32.dp,
                                )
                                Column(modifier = Modifier.padding(start = 12.dp)) {
                                    Text(
                                        text = header.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val count = header.memberCount ?: header.members.size
                                    Text(
                                        text =
                                            pluralStringResource(
                                                R.plurals.chat_group_member_count,
                                                count,
                                                count,
                                            ),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                    )
                                }
                            }
                        // Pre-load (Loading status): the title is empty until the convo resolves.
                        null -> Unit
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
                actions = {
                    // Group convos get a ⋮ overflow whose single item opens the
                    // group-details screen. Direct convos (and the pre-load null
                    // header) show no overflow.
                    if (state.header is ChatHeader.Group) {
                        var menuExpanded by remember { mutableStateOf(false) }
                        Box {
                            IconButton(onClick = { menuExpanded = true }) {
                                NubecitaIcon(
                                    name = NubecitaIconName.MoreVert,
                                    contentDescription = stringResource(R.string.chats_action_more),
                                )
                            }
                            DropdownMenu(
                                expanded = menuExpanded,
                                onDismissRequest = { menuExpanded = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.group_details_menu_item)) },
                                    onClick = {
                                        menuExpanded = false
                                        onEvent(ChatEvent.GroupDetailsTapped)
                                    },
                                )
                            }
                        }
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
                if (state.canPost) {
                    // Banner + composer share one inset owner (this Column carries the
                    // nav-bar + IME padding) so there's still exactly one IME layer.
                    Column(
                        modifier =
                            Modifier
                                .navigationBarsPadding()
                                .imePadding(),
                    ) {
                        state.replyingTo?.let { reply ->
                            ChatReplyBanner(
                                reply = reply,
                                header = state.header,
                                onCancel = { onEvent(ChatEvent.CancelReply) },
                            )
                        }
                        ChatComposerRow(
                            textFieldState = textFieldState,
                            isSendEnabled = state.isSendEnabled,
                            onSend = { onEvent(ChatEvent.Send) },
                            // Anchor to newest (index 0 under reverseLayout) when the
                            // composer gains focus, so the IME never hides the latest run.
                            onFocus = { scope.launch { listState.animateScrollToItem(0) } },
                        )
                    }
                } else {
                    CannotPostNotice(
                        modifier =
                            Modifier
                                .navigationBarsPadding()
                                .imePadding(),
                    )
                }
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
                            canPost = state.canPost,
                            onQuotedPostTap = { uri -> onEvent(ChatEvent.QuotedPostTapped(uri)) },
                            onRetrySend = { tempId -> onEvent(ChatEvent.RetrySend(tempId)) },
                            onReactionToggle = { messageId, emoji ->
                                onEvent(ChatEvent.ToggleReaction(messageId, emoji))
                            },
                            onReply = { messageId -> onEvent(ChatEvent.ReplyTo(messageId)) },
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

/**
 * Banner shown directly above the composer while a reply is in progress. Names the
 * quoted author ("You" for the viewer, else the resolved display name from the
 * [header]) with a one-line snippet, and a ✕ to cancel. Same `surfaceContainerHigh`
 * as the composer so the two read as one attached unit.
 */
@Composable
private fun ChatReplyBanner(
    reply: RepliedMessageUi,
    header: ChatHeader?,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val name =
        if (reply.isFromViewer) {
            stringResource(R.string.chat_reply_you)
        } else {
            replyAuthorName(header, reply.senderDid)
        }
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NubecitaIcon(
                name = NubecitaIconName.Reply,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.chat_reply_banner, name),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text =
                        if (reply.isDeleted) {
                            stringResource(R.string.chats_row_deleted_placeholder)
                        } else {
                            reply.text
                        },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCancel) {
                NubecitaIcon(
                    name = NubecitaIconName.Close,
                    contentDescription = stringResource(R.string.chat_reply_cancel_content_description),
                )
            }
        }
    }
}

/**
 * Best-effort display name for the DID being replied to: the direct peer's name,
 * or the matching group member's name; empty when unresolvable (the banner still
 * reads sensibly as "Replying to").
 */
private fun replyAuthorName(
    header: ChatHeader?,
    senderDid: String,
): String =
    when (header) {
        is ChatHeader.Direct -> header.displayName ?: header.handle
        is ChatHeader.Group ->
            header.members
                .firstOrNull { it.did == senderDid }
                ?.let { it.displayName ?: it.handle }
                .orEmpty()
        null -> ""
    }

/**
 * Shown in the composer slot when [ChatScreenViewState.canPost] is false (e.g. a
 * locked group, or the viewer is not a member). Replaces the editable composer
 * with a static, low-emphasis notice. The actual "is this postable" decision is
 * derived from the loaded convo in the ViewModel; this is the non-error disabled
 * presentation (a true send failure still routes through ShowSendError).
 */
@Composable
private fun CannotPostNotice(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = stringResource(R.string.chat_cannot_post_notice),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun ChatTopBarAvatar(header: ChatHeader.Direct) {
    NubecitaAvatar(
        model = header.avatarUrl,
        contentDescription = null,
        size = 40.dp,
        fallback =
            avatarFallbackFor(
                did = header.did,
                handle = header.handle,
                displayName = header.displayName,
            ),
    )
}

@Composable
private fun LoadingBody() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        NubecitaWavyProgressIndicator()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LoadedBody(
    items: ImmutableList<ThreadItem>,
    listState: LazyListState,
    canPost: Boolean,
    onQuotedPostTap: (quotedPostUri: String) -> Unit,
    onRetrySend: (tempId: String) -> Unit,
    onReactionToggle: (messageId: String, emoji: String) -> Unit,
    onReply: (messageId: String) -> Unit,
) {
    // Tracks which message (by id) currently shows the long-press quick-react menu.
    var reactionMenuFor by remember { mutableStateOf<String?>(null) }
    // Tracks which message (by id) currently shows the full emoji picker dialog.
    var pickerFor by remember { mutableStateOf<String?>(null) }
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
                    val sender = item.sender
                    val message = item.message
                    val canReact = canPost && message.sendStatus == MessageSendStatus.Sent && !message.isDeleted
                    val reactLongPressLabel = stringResource(R.string.chat_react_long_press)
                    if (sender != null) {
                        // GROUP incoming: an avatar gutter (avatar painted only on the
                        // first-of-run bubble, an equal-width spacer on the rest so all
                        // bubbles in the run align under the avatar) + the sender name
                        // above the first bubble. `sender != null` already implies
                        // `!isOutgoing` (the mapper only resolves incoming senders).
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = crossRunGap),
                            horizontalArrangement = Arrangement.Start,
                            verticalAlignment = Alignment.Top,
                        ) {
                            if (item.showAvatar) {
                                NubecitaAvatar(
                                    model = sender.avatarUrl,
                                    contentDescription = null,
                                    size = 28.dp,
                                    fallback =
                                        avatarFallbackFor(
                                            did = sender.did,
                                            handle = sender.handle,
                                            displayName = sender.displayName,
                                        ),
                                )
                            } else {
                                Spacer(Modifier.size(28.dp))
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                if (item.showAvatar) {
                                    Text(
                                        text = sender.displayName,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                                    )
                                }
                                MessageBubble(
                                    message = item.message,
                                    runIndex = item.runIndex,
                                    runCount = item.runCount,
                                    onQuotedPostTap = onQuotedPostTap,
                                    onRetrySend = onRetrySend,
                                    onReactionToggle = { emoji ->
                                        onReactionToggle(item.message.id, emoji)
                                    },
                                    modifier =
                                        Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = { if (canReact) reactionMenuFor = message.id },
                                            onLongClickLabel = reactLongPressLabel,
                                        ),
                                )
                                if (reactionMenuFor == message.id) {
                                    ReactionMenu(
                                        onPick = { emoji ->
                                            onReactionToggle(message.id, emoji)
                                            reactionMenuFor = null
                                        },
                                        onMore = {
                                            pickerFor = message.id
                                            reactionMenuFor = null
                                        },
                                        onReply = {
                                            onReply(message.id)
                                            reactionMenuFor = null
                                        },
                                        onDismiss = { reactionMenuFor = null },
                                    )
                                }
                                if (pickerFor == message.id) {
                                    EmojiPickerDialog(
                                        onEmojiPicked = { emoji ->
                                            onReactionToggle(message.id, emoji)
                                            pickerFor = null
                                        },
                                        onDismiss = { pickerFor = null },
                                    )
                                }
                            }
                        }
                    } else {
                        // Outgoing, or 1:1 incoming — bare (unchanged).
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
                                onReactionToggle = { emoji ->
                                    onReactionToggle(item.message.id, emoji)
                                },
                                modifier =
                                    Modifier.combinedClickable(
                                        onClick = {},
                                        onLongClick = { if (canReact) reactionMenuFor = message.id },
                                        onLongClickLabel = reactLongPressLabel,
                                    ),
                            )
                            if (reactionMenuFor == message.id) {
                                ReactionMenu(
                                    onPick = { emoji ->
                                        onReactionToggle(message.id, emoji)
                                        reactionMenuFor = null
                                    },
                                    onMore = {
                                        pickerFor = message.id
                                        reactionMenuFor = null
                                    },
                                    onReply = {
                                        onReply(message.id)
                                        reactionMenuFor = null
                                    },
                                    onDismiss = { reactionMenuFor = null },
                                )
                            }
                            if (pickerFor == message.id) {
                                EmojiPickerDialog(
                                    onEmojiPicked = { emoji ->
                                        onReactionToggle(message.id, emoji)
                                        pickerFor = null
                                    },
                                    onDismiss = { pickerFor = null },
                                )
                            }
                        }
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
            ChatError.GroupFull,
            ChatError.FollowRequiredToAdd,
            ChatError.InsufficientPermission,
            ChatError.InvalidInviteLink,
            ChatError.FollowRequiredToJoin,
            ChatError.CannotRejoin,
            ->
                // Member-management and join-flow error variants never reach the thread-load ErrorBody
                // (they surface from add/remove-member and join-request surfaces, not resolveConvo/getMessages).
                // Map to the generic, non-retryable fallback to keep this `when` exhaustive.
                Triple(R.string.chats_error_unknown_title, R.string.chats_error_unknown_body, false)
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
