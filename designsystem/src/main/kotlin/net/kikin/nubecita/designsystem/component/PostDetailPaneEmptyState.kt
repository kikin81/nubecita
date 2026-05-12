package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.icon.mirror
import net.kikin.nubecita.designsystem.spacing

/**
 * Detail-pane placeholder for [`ListDetailSceneStrategy`]-driven entries
 * whose back stack hasn't pushed a detail entry yet (on Medium / Expanded
 * widths). Compact widths collapse to single-pane and this Composable is
 * not composed at all.
 *
 * Callers wire it via
 * `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { PostDetailPaneEmptyState() })`.
 *
 * Promoted from `:feature:feed:impl`'s `FeedDetailPlaceholder` in Bead F
 * when Profile became the second caller. Both Feed and Profile delegate
 * to this; future post-list surfaces (Search results, hashtag, user
 * search) can reuse the same composable.
 */
@Composable
fun PostDetailPaneEmptyState(modifier: Modifier = Modifier) {
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
        NubecitaIcon(
            name = NubecitaIconName.Article,
            // Decorative — the bodyLarge prompt below is the accessible label.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
            modifier = Modifier.mirror(),
        )
        Text(
            text = stringResource(R.string.nubecita_detail_pane_select_post),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val ICON_SIZE = 56.dp

@Preview(name = "PostDetailPaneEmptyState — light", showBackground = true)
@Preview(
    name = "PostDetailPaneEmptyState — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostDetailPaneEmptyStatePreview() {
    NubecitaTheme {
        PostDetailPaneEmptyState()
    }
}
