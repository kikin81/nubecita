package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.spacing
import net.kikin.nubecita.feature.search.impl.R
import net.kikin.nubecita.feature.search.impl.data.SearchPostsSort

/**
 * Top / Latest filter-chip strip for the Posts tab. Visible inside
 * `Loaded` and (per design D10) above the empty state. The "Filters"
 * chip from the design handoff is deferred to a follow-up epic.
 */
@Composable
internal fun PostsSortRow(
    activeSort: SearchPostsSort,
    onSelectSort: (SearchPostsSort) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    horizontal = MaterialTheme.spacing.s4,
                    vertical = MaterialTheme.spacing.s2,
                ),
        horizontalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.s2),
    ) {
        FilterChip(
            selected = activeSort == SearchPostsSort.TOP,
            onClick = { onSelectSort(SearchPostsSort.TOP) },
            label = { Text(stringResource(R.string.search_posts_sort_top)) },
        )
        FilterChip(
            selected = activeSort == SearchPostsSort.LATEST,
            onClick = { onSelectSort(SearchPostsSort.LATEST) },
            label = { Text(stringResource(R.string.search_posts_sort_latest)) },
        )
    }
}

@Preview(name = "PostsSortRow — Top active (light)", showBackground = true)
@Composable
private fun PostsSortRowTopPreview() {
    NubecitaTheme {
        PostsSortRow(activeSort = SearchPostsSort.TOP, onSelectSort = {})
    }
}

@Preview(
    name = "PostsSortRow — Latest active (dark)",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostsSortRowLatestDarkPreview() {
    NubecitaTheme {
        PostsSortRow(activeSort = SearchPostsSort.LATEST, onSelectSort = {})
    }
}
