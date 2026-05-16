package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilledTonalButton
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
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

/**
 * Empty-state body for the Posts tab. Per design D10: large icon,
 * display heading with the user's query, descriptive body, and two
 * actions — "Clear search" (tonal) and "Show {opposite-sort} posts"
 * (outlined). The outlined CTA label flips based on the current sort.
 *
 * Custom cloud illustration from the design handoff is out of scope;
 * Material Symbol [NubecitaIconName.Inbox] is the V1 visual fallback
 * because neither `SearchOff` nor `CloudOff` are in the vendored
 * icon-font subset.
 */
@Composable
internal fun PostsEmptyBody(
    currentQuery: String,
    currentSort: SearchPostsSort,
    onClearQuery: () -> Unit,
    onToggleSort: (SearchPostsSort) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val oppositeSort =
        when (currentSort) {
            SearchPostsSort.TOP -> SearchPostsSort.LATEST
            SearchPostsSort.LATEST -> SearchPostsSort.TOP
        }
    val oppositeLabelRes =
        when (oppositeSort) {
            SearchPostsSort.LATEST -> R.string.search_posts_empty_change_sort_to_latest
            SearchPostsSort.TOP -> R.string.search_posts_empty_change_sort_to_top
        }
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
            name = NubecitaIconName.Inbox,
            contentDescription = stringResource(R.string.search_posts_empty_icon_content_description),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            opticalSize = ICON_SIZE,
        )
        Text(
            text = stringResource(R.string.search_posts_empty_title, currentQuery),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.search_posts_empty_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
        ) {
            FilledTonalButton(onClick = onClearQuery) {
                Text(stringResource(R.string.search_posts_empty_clear))
            }
            OutlinedButton(onClick = { onToggleSort(oppositeSort) }) {
                Text(stringResource(oppositeLabelRes))
            }
        }
    }
}

private val ICON_SIZE = 96.dp

@Preview(name = "PostsEmptyBody — light, TOP active", showBackground = true)
@Composable
private fun PostsEmptyBodyTopPreview() {
    NubecitaTheme {
        PostsEmptyBody(
            currentQuery = "kotlin",
            currentSort = SearchPostsSort.TOP,
            onClearQuery = {},
            onToggleSort = {},
        )
    }
}

@Preview(
    name = "PostsEmptyBody — dark, LATEST active",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostsEmptyBodyLatestDarkPreview() {
    NubecitaTheme {
        PostsEmptyBody(
            currentQuery = "compose",
            currentSort = SearchPostsSort.LATEST,
            onClearQuery = {},
            onToggleSort = {},
        )
    }
}
