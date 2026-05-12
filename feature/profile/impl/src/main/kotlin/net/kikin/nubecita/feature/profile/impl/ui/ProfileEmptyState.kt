package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.feature.profile.impl.ProfileTab
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Per-tab empty state. Used when `TabLoadStatus.Loaded.items.isEmpty()`.
 * Bead D consumes this for the Posts tab; Bead E reuses for Replies /
 * Media — the per-tab copy is keyed off the `tab` parameter so the
 * call sites in Bead E don't need to change anything.
 */
@Composable
internal fun ProfileEmptyState(
    tab: ProfileTab,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        when (tab) {
            ProfileTab.Posts -> R.string.profile_posts_empty_title
            ProfileTab.Replies -> R.string.profile_replies_empty_title
            ProfileTab.Media -> R.string.profile_media_empty_title
        }
    val bodyRes =
        when (tab) {
            ProfileTab.Posts -> R.string.profile_posts_empty_body
            ProfileTab.Replies -> R.string.profile_replies_empty_body
            ProfileTab.Media -> R.string.profile_media_empty_body
        }
    Column(
        modifier = modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = stringResource(titleRes),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(bodyRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
