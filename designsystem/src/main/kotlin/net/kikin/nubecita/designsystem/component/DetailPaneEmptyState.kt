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
 * Generic detail-pane placeholder for [`ListDetailSceneStrategy`]-driven
 * entries whose back stack hasn't pushed a detail entry yet (on Medium /
 * Expanded widths). Compact widths collapse to single-pane and this
 * Composable is not composed at all.
 *
 * Callers supply the [icon] + [message] for their surface and wire it via
 * `metadata = ListDetailSceneStrategy.listPane(detailPlaceholder = { DetailPaneEmptyState(icon, message) })`.
 * Post-list surfaces (Feed, Profile, Search) pass `Article` + "Select a
 * post to read"; Chats passes `ChatBubble` + "Select a conversation". One
 * agnostic component rather than a per-surface placeholder duplicated
 * across modules.
 *
 * Promoted from `:feature:feed:impl`'s `FeedDetailPlaceholder` and
 * generalized from the former post-specific `PostDetailPaneEmptyState`
 * when Chats became a non-post caller.
 *
 * @param icon Centered, decorative glyph (the [message] is the accessible
 *   label, so the icon's `contentDescription` is null). RTL-mirrored.
 * @param message Centered prompt rendered below the icon.
 */
@Composable
fun DetailPaneEmptyState(
    icon: NubecitaIconName,
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface)
                .padding(
                    horizontal = MaterialTheme.spacing.s6,
                    vertical = MaterialTheme.spacing.s8,
                ),
        verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        NubecitaIcon(
            name = icon,
            // Decorative — the bodyLarge prompt below is the accessible label.
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
            modifier = Modifier.mirror(),
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

private val ICON_SIZE = 56.dp

@Preview(name = "DetailPaneEmptyState post — light", showBackground = true)
@Preview(
    name = "DetailPaneEmptyState post — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailPaneEmptyStatePostPreview() {
    NubecitaTheme {
        DetailPaneEmptyState(
            icon = NubecitaIconName.Article,
            message = stringResource(R.string.nubecita_detail_pane_select_post),
        )
    }
}

@Preview(name = "DetailPaneEmptyState chat — light", showBackground = true)
@Preview(
    name = "DetailPaneEmptyState chat — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailPaneEmptyStateChatPreview() {
    NubecitaTheme {
        DetailPaneEmptyState(
            icon = NubecitaIconName.ChatBubble,
            message = stringResource(R.string.nubecita_detail_pane_select_conversation),
        )
    }
}
