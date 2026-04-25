package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * PostCard-shaped loading skeleton.
 *
 * Geometrically mirrors [PostCard]'s layout so a `LazyColumn` of
 * `PostCardShimmer()` looks like the same list of `PostCard` items will
 * occupy once data arrives — no perceived layout shift on data load.
 *
 * Stateless and previewable. Renders avatar circle (40dp matching
 * [NubecitaAvatar]), display-name + handle bars, two body-text bars, an
 * optional image-shaped placeholder (180dp tall matching the
 * [PostCardImageEmbed] baseline), and four small action-row dots — each
 * shape using [Modifier.shimmer].
 *
 * Use this composable INSTEAD of `PostCard` for loading-list slots; do
 * not pass placeholder data to PostCard itself. PostCard is loaded-state-
 * only by design (see the openspec change `add-postcard-component`,
 * design Decision 9).
 */
@Composable
fun PostCardShimmer(
    modifier: Modifier = Modifier,
    showImagePlaceholder: Boolean = false,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SkeletonBody(showImagePlaceholder = showImagePlaceholder)
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SkeletonBody(showImagePlaceholder: Boolean) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Avatar
            Box(
                modifier =
                    Modifier
                        .size(AVATAR_SIZE)
                        .clip(CircleShape)
                        .shimmer(),
            )
            Column(modifier = Modifier.fillMaxWidth()) {
                // Display-name + handle bar
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.6f)
                            .height(NAME_BAR_HEIGHT)
                            .clip(BAR_SHAPE)
                            .shimmer(),
                )
                Spacer(Modifier.height(8.dp))
                // Body line 1 (full width)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(BODY_BAR_HEIGHT)
                            .clip(BAR_SHAPE)
                            .shimmer(),
                )
                Spacer(Modifier.height(6.dp))
                // Body line 2 (shorter)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth(0.4f)
                            .height(BODY_BAR_HEIGHT)
                            .clip(BAR_SHAPE)
                            .shimmer(),
                )
                if (showImagePlaceholder) {
                    Spacer(Modifier.height(10.dp))
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(IMAGE_PLACEHOLDER_HEIGHT)
                                .clip(IMAGE_SHAPE)
                                .shimmer(),
                    )
                }
                Spacer(Modifier.height(10.dp))
                // Action-row dots
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    repeat(times = 4) {
                        Box(
                            modifier =
                                Modifier
                                    .size(ACTION_DOT_SIZE)
                                    .clip(CircleShape)
                                    .shimmer(),
                        )
                    }
                }
            }
        }
    }
}

private val AVATAR_SIZE = 40.dp
private val NAME_BAR_HEIGHT = 16.dp
private val BODY_BAR_HEIGHT = 14.dp
private val IMAGE_PLACEHOLDER_HEIGHT = 180.dp
private val ACTION_DOT_SIZE = 18.dp
private val BAR_SHAPE = RoundedCornerShape(6.dp)
private val IMAGE_SHAPE = RoundedCornerShape(16.dp)

@Preview(name = "PostCardShimmer — no image", showBackground = true)
@Composable
private fun PostCardShimmerNoImagePreview() {
    NubecitaTheme {
        PostCardShimmer()
    }
}

@Preview(name = "PostCardShimmer — with image", showBackground = true)
@Composable
private fun PostCardShimmerWithImagePreview() {
    NubecitaTheme {
        PostCardShimmer(showImagePlaceholder = true)
    }
}

@Preview(
    name = "PostCardShimmer — dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostCardShimmerDarkPreview() {
    NubecitaTheme {
        PostCardShimmer(showImagePlaceholder = true)
    }
}

@Preview(name = "PostCardShimmer — list", showBackground = true)
@Composable
private fun PostCardShimmerListPreview() {
    NubecitaTheme {
        Column {
            repeat(times = 3) { index ->
                PostCardShimmer(showImagePlaceholder = (index == 1))
            }
        }
    }
}
