package net.kikin.nubecita.feature.notifications.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource

/**
 * Skeleton — real screen lands in `nubecita-1fy.1.8`.
 *
 * The full implementation will host the filter-chip row, the
 * `PullToRefreshBox { LazyColumn { … } }`, the `ActorListSheet`
 * bottom sheet, snackbar-routed errors, and the tab-exit
 * mark-read handshake. For now this renders a placeholder so
 * `NotificationsNavigationModule` has something concrete to point
 * its `@MainShell EntryProviderInstaller` at while the rest of the
 * `add-feature-notifications` epic lands.
 */
@Composable
internal fun NotificationsScreen(modifier: Modifier = Modifier) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { padding ->
        Box(
            modifier =
                Modifier
                    .padding(padding)
                    .fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = stringResource(R.string.notifications_placeholder_title))
        }
    }
}
