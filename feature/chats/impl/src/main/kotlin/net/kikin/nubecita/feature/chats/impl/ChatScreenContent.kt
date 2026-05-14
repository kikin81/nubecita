package net.kikin.nubecita.feature.chats.impl

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.collections.immutable.ImmutableList
import net.kikin.nubecita.designsystem.component.NubecitaAsyncImage
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
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
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
                            contentDescription = "Back",
                            filled = true,
                            modifier = Modifier.mirror(),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(padding),
        ) {
            when (val status = state.status) {
                ChatLoadStatus.Loading -> LoadingBody()
                is ChatLoadStatus.Loaded ->
                    if (status.items.isEmpty()) {
                        EmptyBody()
                    } else {
                        LoadedBody(status.items)
                    }
                is ChatLoadStatus.InitialError ->
                    ErrorBody(status.error, onRetry = { onEvent(ChatEvent.RetryClicked) })
            }
        }
    }
}

@Composable
private fun ChatTopBarAvatar(state: ChatScreenViewState) {
    val hueColor = Color.hsv(state.otherUserAvatarHue.toFloat(), saturation = 0.5f, value = 0.55f)
    val initial =
        (state.otherUserDisplayName ?: state.otherUserHandle)
            .firstOrNull { it.isLetterOrDigit() }
            ?.uppercase() ?: "?"
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(hueColor),
        contentAlignment = Alignment.Center,
    ) {
        if (state.otherUserAvatarUrl != null) {
            NubecitaAsyncImage(
                model = state.otherUserAvatarUrl,
                contentDescription = null,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape),
            )
        } else {
            Text(
                text = initial,
                color = if (hueColor.luminance() > 0.5f) Color.Black else Color.White,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
            )
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
            text = "No messages yet",
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = "Once you exchange messages with this person they'll appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun LoadedBody(items: ImmutableList<ThreadItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        itemsIndexed(
            items = items,
            key = { _, it -> it.key },
            contentType = { _, it -> if (it is ThreadItem.Message) "msg" else "sep" },
        ) { position, item ->
            when (item) {
                is ThreadItem.Message -> {
                    // runIndex == 0 is the OLDEST message of the run; with reverseLayout
                    // that's the visually top-most. Add a cross-run gap above it (rendered
                    // as bottom padding because the next item in newest-first list order
                    // belongs to the previous, older run).
                    val crossRunGap = if (item.runIndex == 0 && position < items.lastIndex) 8.dp else 0.dp
                    // 1:1 DMs only in V1 — the peer's identity is already established in the
                    // TopAppBar (avatar + display name), so we don't repeat it per message.
                    // No per-row avatar slot means same-sender runs stack at the bubble's
                    // intrinsic height; spacedBy(4.dp) + the cross-run +8.dp bottom-padding
                    // on runIndex==0 produces the GChat-style tight intra-run / loose
                    // cross-run rhythm without the dead 40dp slot adding vertical gaps.
                    // When/if group chats land, the data model already carries `showAvatar`
                    // on `ThreadItem.Message` — the slot can be reintroduced here gated on it.
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(bottom = crossRunGap),
                        horizontalArrangement =
                            if (item.message.isOutgoing) Arrangement.End else Arrangement.Start,
                    ) {
                        MessageBubble(
                            message = item.message,
                            runIndex = item.runIndex,
                            runCount = item.runCount,
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
    val (title, body, showRetry) =
        when (error) {
            ChatError.Network ->
                Triple(
                    "Network error",
                    "Couldn't load this thread. Check your connection and try again.",
                    true,
                )
            ChatError.NotEnrolled ->
                Triple(
                    "Enable direct messages",
                    "Your Bluesky account hasn't opted into direct messages. Enable chat in the official Bluesky app's settings to start using DMs.",
                    false,
                )
            ChatError.ConvoNotFound ->
                Triple(
                    "No conversation yet",
                    "You don't have a conversation with this user yet.",
                    false,
                )
            is ChatError.Unknown ->
                Triple(
                    "Something went wrong",
                    "An unexpected error occurred. Try again in a moment.",
                    true,
                )
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
            text = title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )
        if (showRetry) {
            OutlinedButton(onClick = onRetry) {
                Text("Try again")
            }
        }
    }
}
