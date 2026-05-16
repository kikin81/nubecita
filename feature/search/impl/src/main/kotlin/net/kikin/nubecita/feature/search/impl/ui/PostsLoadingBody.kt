package net.kikin.nubecita.feature.search.impl.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.component.PostCardShimmer
import net.kikin.nubecita.feature.search.impl.R

/**
 * Loading body for the Posts tab. Three shimmer post-cards. Mirrors
 * the feed's loading-state pattern — same shimmer component, same
 * count so the visual continuity carries across surfaces.
 *
 * Accessibility: the parent Column carries a contentDescription so
 * TalkBack announces "Searching posts" instead of three individual
 * empty cards.
 */
@Composable
internal fun PostsLoadingBody(modifier: Modifier = Modifier) {
    val description = stringResource(R.string.search_posts_loading_content_description)
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .semantics { contentDescription = description },
    ) {
        repeat(3) {
            PostCardShimmer()
        }
    }
}

@Preview(name = "PostsLoadingBody — light", showBackground = true)
@Preview(
    name = "PostsLoadingBody — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostsLoadingBodyPreview() {
    NubecitaTheme {
        PostsLoadingBody()
    }
}
