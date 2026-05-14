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
import androidx.compose.ui.res.stringResource
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
                            contentDescription = stringResource(R.string.chat_back_content_description),
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
private fun LoadedBody(items: ImmutableList<ThreadItem>) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
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
