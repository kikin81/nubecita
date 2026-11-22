package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import java.time.ZoneOffset
import kotlin.time.Instant

/**
 * Baselines for [FocusPostCard], the container-less focused post: text only
 * with pronouns and a Follow toggle, the checked "Following" toggle, a post
 * with no counts (row hidden), and a long name + handle at a narrow width
 * (handle line ellipsizes, toggle keeps its size).
 *
 * Time is pinned (noon UTC, rendered in UTC) so the absolute timestamp is the
 * same on every host.
 */
private val NOON_UTC = Instant.parse("2026-11-21T12:00:00Z")

private fun focusPost(
    displayName: String = "Christian Alder",
    handle: String = "hejchristian.com",
    pronouns: String? = "he/him",
    stats: PostStatsUi = PostStatsUi(replyCount = 5, repostCount = 44, quoteCount = 5, likeCount = 193, bookmarkCount = 102),
    verified: VerifiedBadge = VerifiedBadge.None,
): PostUi =
    PostUi(
        id = "at://did:plc:focus/app.bsky.feed.post/focus",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author =
            AuthorUi(
                did = "did:plc:focus",
                handle = handle,
                displayName = displayName,
                avatarUrl = null,
                verifiedBadge = verified,
                pronouns = pronouns,
            ),
        createdAt = NOON_UTC,
        text = "I'm a sucker for a nice font.\nI'm a sucker for a nice font that's free.",
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = stats,
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

@PreviewTest
@Preview(name = "focus-follow-light", showBackground = true)
@Preview(name = "focus-follow-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusPostCardFollowScreenshot() {
    NubecitaCanvasPreviewTheme {
        FocusPostCard(post = focusPost(), followState = FocusFollowState.NotFollowing, zoneId = ZoneOffset.UTC)
    }
}

@PreviewTest
@Preview(name = "focus-following-light", showBackground = true)
@Preview(name = "focus-following-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusPostCardFollowingScreenshot() {
    NubecitaCanvasPreviewTheme {
        FocusPostCard(post = focusPost(), followState = FocusFollowState.Following, zoneId = ZoneOffset.UTC)
    }
}

@PreviewTest
@Preview(name = "focus-no-counts-no-pronouns-light", showBackground = true)
@Preview(name = "focus-no-counts-no-pronouns-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusPostCardNoCountsScreenshot() {
    NubecitaCanvasPreviewTheme {
        FocusPostCard(post = focusPost(pronouns = null, stats = PostStatsUi()), zoneId = ZoneOffset.UTC)
    }
}

@PreviewTest
@Preview(name = "focus-long-name-narrow-light", showBackground = true, widthDp = 320)
@Composable
private fun FocusPostCardLongNameNarrowScreenshot() {
    NubecitaCanvasPreviewTheme {
        FocusPostCard(
            post =
                focusPost(
                    displayName = "A Remarkably Long Display Name For Testing",
                    handle = "a-remarkably-long-handle.bsky.social",
                    pronouns = "they/them",
                    verified = VerifiedBadge.Verified,
                ),
            followState = FocusFollowState.NotFollowing,
            zoneId = ZoneOffset.UTC,
            modifier = Modifier.width(320.dp),
        )
    }
}
