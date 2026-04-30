package net.kikin.nubecita.feature.feed.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material3.Icon
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
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.feed.impl.R

/**
 * Detail-pane placeholder rendered by [`ListDetailSceneStrategy`] when the
 * back stack is `[Feed]` (no detail entry selected) on medium/expanded
 * widths. On compact, the strategy collapses to single-pane and this
 * Composable is not composed at all.
 *
 * Wired into `:feature:feed:impl`'s `@MainShell`-qualified
 * `EntryProviderInstaller` via
 * `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { FeedDetailPlaceholder() })`.
 *
 * Kept in `:feature:feed:impl` (not promoted to `:designsystem`) until a
 * second list-pane host needs the same shape — see the
 * `adopt-list-detail-scene-strategy` design doc.
 */
@Composable
internal fun FeedDetailPlaceholder(modifier: Modifier = Modifier) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.Article,
            // Decorative — the bodyLarge prompt below is the accessible label.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(ICON_SIZE),
        )
        Text(
            text = stringResource(R.string.feed_detail_placeholder_select),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val ICON_SIZE = 56.dp

@Preview(name = "FeedDetailPlaceholder — light", showBackground = true)
@Preview(
    name = "FeedDetailPlaceholder — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun FeedDetailPlaceholderPreview() {
    NubecitaTheme {
        FeedDetailPlaceholder()
    }
}
