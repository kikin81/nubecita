package net.kikin.nubecita.feature.notifications.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import net.kikin.nubecita.feature.notifications.impl.NotificationsTestTags
import net.kikin.nubecita.feature.notifications.impl.R

/**
 * Empty state surfaced by `NotificationsScreen` when the load completes
 * with zero items. Stateless. Mirrors `:feature:feed:impl`'s
 * `FeedEmptyState` shape so the two surfaces read consistently.
 *
 * No "Refresh" CTA — the host wraps this composable in a `PullToRefreshBox`
 * and the empty body is laid out inside a `verticalScroll` so the pull
 * gesture works on the empty surface too. An explicit button would
 * duplicate the affordance and invite uncovered edge-cases. If telemetry
 * shows users don't discover pull-to-refresh, we can add one in a
 * follow-up.
 */
@Composable
internal fun NotificationsEmptyState(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    Column(
        modifier =
            modifier
                .testTag(NotificationsTestTags.EMPTY_STATE)
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
            name = NubecitaIconName.Notifications,
            contentDescription = stringResource(R.string.notifications_empty_icon_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(R.string.notifications_empty_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.notifications_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val ICON_SIZE = 64.dp

@Preview(name = "Empty — light", showBackground = true)
@Preview(name = "Empty — dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun NotificationsEmptyStatePreview() {
    NubecitaTheme {
        NotificationsEmptyState()
    }
}
