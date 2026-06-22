package net.kikin.nubecita.feature.chats.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.common.time.rememberChatRelativeTimeText
import net.kikin.nubecita.designsystem.component.NubecitaAvatar
import net.kikin.nubecita.designsystem.component.avatarFallbackFor
import net.kikin.nubecita.feature.chats.impl.JoinRequestUi
import net.kikin.nubecita.feature.chats.impl.R

/**
 * One pending join request: requester avatar + name/handle + "requested {relative}", with an
 * Approve (primary) and Reject (text) action. While the request's call is in flight both actions
 * disable and Approve shows an in-button spinner.
 */
@Composable
internal fun JoinRequestRow(
    request: JoinRequestUi,
    inFlight: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val relative by rememberChatRelativeTimeText(then = request.requestedAt)
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NubecitaAvatar(
            model = request.avatarUrl,
            contentDescription = null,
            fallback = avatarFallbackFor(did = request.did, handle = request.handle, displayName = request.displayName),
        )
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                text = request.displayName ?: request.handle,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.join_requests_handle, request.handle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(R.string.join_requests_requested_relative, relative),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onReject, enabled = !inFlight) {
            Text(stringResource(R.string.join_requests_reject))
        }
        FilledTonalButton(onClick = onApprove, enabled = !inFlight) {
            if (inFlight) {
                // nubecita-allow-raw-progress: in-button micro-spinner with a tuned strokeWidth
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Text(stringResource(R.string.join_requests_approve))
            }
        }
    }
}
