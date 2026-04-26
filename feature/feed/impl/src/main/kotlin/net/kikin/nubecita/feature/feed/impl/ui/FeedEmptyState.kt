package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.feed.impl.R

/**
 * Idle + empty-list state for the Following timeline.
 *
 * Stateless. Hosted by `FeedScreen` when the VM has resolved `Idle`
 * with no posts (e.g., a freshly-authed account that follows nobody).
 * The Refresh button dispatches `FeedEvent.Refresh` via [onRefresh] —
 * useful for the rare case where the empty result was a transient
 * server-side condition.
 */
@Composable
internal fun FeedEmptyState(
    modifier: Modifier = Modifier,
    onRefresh: () -> Unit = {},
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Outlined.Inbox,
            contentDescription = stringResource(R.string.feed_empty_icon_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text = stringResource(R.string.feed_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.feed_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        OutlinedButton(onClick = onRefresh) {
            Text(stringResource(R.string.feed_empty_action))
        }
    }
}

private val ICON_SIZE = 64.dp

@Preview(name = "Empty — light", showBackground = true)
@Preview(name = "Empty — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedEmptyStatePreview() {
    NubecitaTheme {
        FeedEmptyState()
    }
}
