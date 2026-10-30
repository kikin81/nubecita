package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.OutgoingPostStatusUi
import net.kikin.nubecita.data.models.OutgoingPostUi
import net.kikin.nubecita.designsystem.R

/**
 * A post the user just submitted, shown on top of a feed or profile list
 * before the server's copy of that list includes it.
 *
 * [OutgoingPostStatusUi.Sending] adds a "Sending…" row and
 * [OutgoingPostStatusUi.Failed] adds "Couldn't send" with Retry and Discard.
 * Until the post is [OutgoingPostStatusUi.Sent] it has no server URI, so the
 * card ignores [callbacks] (taps, likes, replies) and renders inert.
 */
@Composable
fun OutgoingPostCard(
    item: OutgoingPostUi,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
    callbacks: PostCallbacks = PostCallbacks.None,
    onImageClick: ((imageIndex: Int) -> Unit)? = null,
) {
    val sent = item.status == OutgoingPostStatusUi.Sent
    Column(modifier = modifier.fillMaxWidth()) {
        PostCard(
            post = item.post,
            callbacks = if (sent) callbacks else PostCallbacks.None,
            onImageClick = if (sent) onImageClick else null,
        )
        when (item.status) {
            OutgoingPostStatusUi.Sending -> SendingRow()
            OutgoingPostStatusUi.Failed -> FailedRow(onRetry = onRetry, onDiscard = onDiscard)
            OutgoingPostStatusUi.Sent -> Unit
        }
    }
}

@Composable
private fun SendingRow() {
    Row(
        modifier =
            Modifier
                .padding(start = STATUS_START_INSET, end = 20.dp, bottom = 14.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NubecitaWavyProgressIndicator(modifier = Modifier.size(16.dp))
        Text(
            text = stringResource(R.string.outgoing_post_sending),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FailedRow(
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .padding(start = STATUS_START_INSET, end = 8.dp, bottom = 6.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.outgoing_post_failed),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onDiscard) { Text(stringResource(R.string.outgoing_post_discard)) }
        TextButton(onClick = onRetry) { Text(stringResource(R.string.outgoing_post_retry)) }
    }
}

/** Lines the status row up with the post text: card padding + avatar + gap. */
private val STATUS_START_INSET = 20.dp + 40.dp + 12.dp
