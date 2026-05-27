package net.kikin.nubecita.feature.notifications.impl.ui

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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.notifications.impl.NotificationsError
import net.kikin.nubecita.feature.notifications.impl.NotificationsTestTags
import net.kikin.nubecita.feature.notifications.impl.R

/**
 * Full-screen initial-load error layout for `NotificationsScreen`.
 *
 * Hosted when the screen's `NotificationsScreenViewState` resolves to
 * `InitialError(error)` — meaning the first page failed AND no items
 * are available to fall back on. Refresh / append failures with items
 * present stay non-sticky (snackbar via `NotificationsEffect.ShowError`).
 *
 * Mirrors `:feature:feed:impl/FeedErrorState`'s shape: icon + title +
 * body + retry button, with copy varying by [NotificationsError]
 * variant.
 */
@Composable
internal fun NotificationsInitialError(
    error: NotificationsError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val copy = error.toCopy()
    Column(
        modifier =
            modifier
                .fillMaxSize()
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
        Button(
            onClick = onRetry,
            modifier = Modifier.testTag(NotificationsTestTags.ERROR_RETRY),
        ) {
            Text(stringResource(R.string.notifications_error_action))
        }
    }
}

private data class ErrorCopy(
    val icon: NubecitaIconName,
    val iconDescriptionResId: Int,
    val titleResId: Int,
    val bodyResId: Int,
)

private fun NotificationsError.toCopy(): ErrorCopy =
    when (this) {
        NotificationsError.Network ->
            ErrorCopy(
                icon = NubecitaIconName.WifiOff,
                iconDescriptionResId = R.string.notifications_error_network_icon_description,
                titleResId = R.string.notifications_error_network_title,
                bodyResId = R.string.notifications_error_network_body,
            )
        NotificationsError.Unauthenticated ->
            ErrorCopy(
                icon = NubecitaIconName.LockPerson,
                iconDescriptionResId = R.string.notifications_error_unauthenticated_icon_description,
                titleResId = R.string.notifications_error_unauthenticated_title,
                bodyResId = R.string.notifications_error_unauthenticated_body,
            )
        NotificationsError.Unknown ->
            ErrorCopy(
                icon = NubecitaIconName.Error,
                iconDescriptionResId = R.string.notifications_error_unknown_icon_description,
                titleResId = R.string.notifications_error_unknown_title,
                bodyResId = R.string.notifications_error_unknown_body,
            )
    }

private val ICON_SIZE = 64.dp

@Preview(name = "InitialError — Network (light)", showBackground = true)
@Preview(name = "InitialError — Network (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsInitialErrorNetworkPreview() {
    NubecitaTheme {
        NotificationsInitialError(error = NotificationsError.Network, onRetry = {})
    }
}

@Preview(name = "InitialError — Unauthenticated (light)", showBackground = true)
@Preview(name = "InitialError — Unauthenticated (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsInitialErrorUnauthPreview() {
    NubecitaTheme {
        NotificationsInitialError(error = NotificationsError.Unauthenticated, onRetry = {})
    }
}

@Preview(name = "InitialError — Unknown (light)", showBackground = true)
@Preview(name = "InitialError — Unknown (dark)", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsInitialErrorUnknownPreview() {
    NubecitaTheme {
        NotificationsInitialError(error = NotificationsError.Unknown, onRetry = {})
    }
}
