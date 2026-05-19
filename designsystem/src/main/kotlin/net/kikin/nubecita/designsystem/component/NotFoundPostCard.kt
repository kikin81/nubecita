package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R

/**
 * Tombstone row rendered in place of a normal [PostCard] when the AppView
 * returns `app.bsky.feed.defs#notFoundPost` — the post was deleted or
 * never existed. No author info on the wire, no recovery action; this
 * is a plain notice.
 *
 * Visual treatment mirrors [BlockedPostCard]'s surface + italic-text
 * conventions so the two tombstone variants read as siblings in a feed.
 * No trailing affordance — there's nothing the user can do to recover a
 * deleted post.
 */
@Composable
public fun NotFoundPostCard(modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text(
                text = stringResource(R.string.tombstone_not_found_post),
                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Preview(name = "NotFoundPostCard — light", showBackground = true)
@Preview(
    name = "NotFoundPostCard — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun NotFoundPostCardPreview() {
    NubecitaTheme {
        NotFoundPostCard()
    }
}
