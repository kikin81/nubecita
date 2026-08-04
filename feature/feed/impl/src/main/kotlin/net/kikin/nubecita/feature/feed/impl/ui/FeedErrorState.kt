package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.feed.impl.FeedError
import net.kikin.nubecita.feature.feed.impl.R

/**
 * Sticky initial-load error layout for the Following timeline.
 *
 * Hosted by `FeedScreen` when `FeedState.loadStatus is FeedLoadStatus.InitialError`
 * AND `posts.isEmpty()`. Refresh / append failures with existing data
 * stay non-sticky (snackbar via `FeedEffect.ShowError`) — see the
 * `feature-feed` spec's "Initial-load error is sticky in state" requirement.
 */
@Composable
internal fun FeedErrorState(
    error: FeedError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val copy = error.toCopy()
    Column(
        modifier =
            modifier
                .fillMaxSize()
                // contentPadding from host Scaffold (edge-to-edge inset).
                // Applied before internal chrome so retry button stays clear
                // of system bars without disturbing the centered layout.
                .padding(contentPadding)
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = copy.icon,
            contentDescription = stringResource(copy.iconDescriptionResId),
            tint = MaterialTheme.colorScheme.error,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(copy.titleResId),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(copy.bodyResId),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        // The upstream's own error text, quoted verbatim beneath our copy so
        // a viewer reporting the outage can repeat what the server said.
        // Server-supplied and untranslated by design — de-emphasised to
        // bodySmall/outline so it reads as a machine detail, not as our voice.
        copy.serverMessage?.let { serverMessage ->
            Text(
                text = stringResource(R.string.feed_error_server_message, serverMessage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                textAlign = TextAlign.Center,
            )
        }
        Button(onClick = onRetry) {
            Text(stringResource(R.string.feed_error_action))
        }
    }
}

private data class ErrorCopy(
    val icon: NubecitaIconName,
    val iconDescriptionResId: Int,
    val titleResId: Int,
    val bodyResId: Int,
    /**
     * Raw upstream message to quote under [bodyResId], or null to render
     * nothing. Only [FeedError.FeedOffline] carries one — the other
     * variants are diagnosed entirely on our side.
     */
    val serverMessage: String? = null,
)

private fun FeedError.toCopy(): ErrorCopy =
    when (this) {
        FeedError.Network ->
            ErrorCopy(
                icon = NubecitaIconName.WifiOff,
                iconDescriptionResId = R.string.feed_error_network_icon_description,
                titleResId = R.string.feed_error_network_title,
                bodyResId = R.string.feed_error_network_body,
            )
        FeedError.Unauthenticated ->
            ErrorCopy(
                icon = NubecitaIconName.LockPerson,
                iconDescriptionResId = R.string.feed_error_unauthenticated_icon_description,
                titleResId = R.string.feed_error_unauthenticated_title,
                bodyResId = R.string.feed_error_unauthenticated_body,
            )
        // Both feed-generator variants reuse the generic Error glyph: the
        // icon font ships no cloud-off / feed-off symbol, and vendoring one
        // means regenerating the whole font for a single codepoint. The
        // title and body carry the distinction, and the icon's
        // contentDescription still names the specific state for TalkBack.
        is FeedError.FeedOffline ->
            ErrorCopy(
                icon = NubecitaIconName.Error,
                iconDescriptionResId = R.string.feed_error_feed_offline_icon_description,
                titleResId = R.string.feed_error_feed_offline_title,
                bodyResId = R.string.feed_error_feed_offline_body,
                serverMessage = serverMessage,
            )
        FeedError.FeedNotFound ->
            ErrorCopy(
                icon = NubecitaIconName.Error,
                iconDescriptionResId = R.string.feed_error_feed_not_found_icon_description,
                titleResId = R.string.feed_error_feed_not_found_title,
                bodyResId = R.string.feed_error_feed_not_found_body,
            )
        is FeedError.Unknown ->
            ErrorCopy(
                icon = NubecitaIconName.Error,
                iconDescriptionResId = R.string.feed_error_unknown_icon_description,
                titleResId = R.string.feed_error_unknown_title,
                bodyResId = R.string.feed_error_unknown_body,
            )
    }

private val ICON_SIZE = 64.dp

@Preview(name = "Network — light", showBackground = true)
@Preview(name = "Network — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateNetworkPreview() {
    NubecitaTheme {
        FeedErrorState(error = FeedError.Network, onRetry = {})
    }
}

@Preview(name = "Unauthenticated — light", showBackground = true)
@Preview(name = "Unauthenticated — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateUnauthenticatedPreview() {
    NubecitaTheme {
        FeedErrorState(error = FeedError.Unauthenticated, onRetry = {})
    }
}

@Preview(name = "Unknown — light", showBackground = true)
@Preview(name = "Unknown — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateUnknownPreview() {
    NubecitaTheme {
        FeedErrorState(error = FeedError.Unknown(cause = null), onRetry = {})
    }
}

@Preview(name = "Feed offline — light", showBackground = true)
@Preview(name = "Feed offline — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateFeedOfflinePreview() {
    NubecitaTheme {
        FeedErrorState(error = FeedError.FeedOffline(serverMessage = "feed unavailable"), onRetry = {})
    }
}

@Preview(name = "Feed offline, no server message — light", showBackground = true)
@Preview(
    name = "Feed offline, no server message — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedErrorStateFeedOfflineNoMessagePreview() {
    NubecitaTheme {
        FeedErrorState(error = FeedError.FeedOffline(serverMessage = null), onRetry = {})
    }
}

@Preview(name = "Feed not found — light", showBackground = true)
@Preview(name = "Feed not found — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FeedErrorStateFeedNotFoundPreview() {
    NubecitaTheme {
        FeedErrorState(error = FeedError.FeedNotFound, onRetry = {})
    }
}
