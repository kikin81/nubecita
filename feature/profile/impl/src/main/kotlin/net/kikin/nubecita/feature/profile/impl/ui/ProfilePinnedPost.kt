package net.kikin.nubecita.feature.profile.impl.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.designsystem.component.PostCallbacks
import net.kikin.nubecita.feature.profile.impl.R

/**
 * Contributes the pinned-post slot at the top of the Posts tab — a "Pinned"
 * label above an identical [ProfileFeedPostCard]. Rendered only on the Posts tab
 * (not Replies/Media/Likes), for any profile that has a resolved pinned post.
 *
 * Interaction-animation cues (`animateLikeTap`/`animateRepostTap`) are always
 * off here: the like/repost tap markers are keyed to the main feed's last-tap
 * URIs, and firing them on the pinned copy as well would double-animate.
 */
internal fun LazyListScope.pinnedPostItem(
    post: PostUi,
    callbacks: PostCallbacks,
    onImageTap: (post: PostUi, imageIndex: Int) -> Unit,
    onQuotedImageTap: (quotedPostUri: String, imageIndex: Int) -> Unit,
    onVideoTap: (postUri: String) -> Unit,
    isMediaRevealed: Boolean,
    onRevealMedia: (postId: String) -> Unit,
) {
    item(key = "pinned-post", contentType = "pinned-post") {
        Column {
            PinnedPostLabel(modifier = Modifier.padding(start = 56.dp, top = 8.dp, bottom = 2.dp))
            ProfileFeedPostCard(
                post = post,
                callbacks = callbacks,
                onImageTap = onImageTap,
                onQuotedImageTap = onQuotedImageTap,
                onVideoTap = onVideoTap,
                animateLikeTap = false,
                animateRepostTap = false,
                isMediaRevealed = isMediaRevealed,
                onRevealMedia = { onRevealMedia(post.id) },
            )
        }
    }
}

@Composable
private fun PinnedPostLabel(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.profile_pinned_post_label),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}
