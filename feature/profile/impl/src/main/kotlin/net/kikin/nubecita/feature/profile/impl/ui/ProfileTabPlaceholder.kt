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
 * Bead D placeholder for the Replies / Media tabs whose real bodies
 * land in Bead E. Communicates the temporary state without disabling
 * the pill tabs — the user can still tap the tab and see something
 * intentional rather than a half-empty body.
 *
 * Bead E deletes this Composable; its call sites in
 * ProfileScreenContent are replaced with the real tab body LazyListScope
 * extensions.
 */
@Composable
internal fun ProfileTabPlaceholder(
    tab: ProfileTab,
    modifier: Modifier = Modifier,
) {
    val titleRes =
        when (tab) {
            ProfileTab.Replies -> R.string.profile_tab_placeholder_replies_title
            ProfileTab.Media -> R.string.profile_tab_placeholder_media_title
            // Posts tab never renders this placeholder in Bead D — the
            // tab body is fully functional. Defensive default: use the
            // Replies copy if a caller incorrectly routes Posts here.
            ProfileTab.Posts -> R.string.profile_tab_placeholder_replies_title
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
            text = stringResource(R.string.profile_tab_placeholder_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
