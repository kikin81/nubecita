package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
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
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.NubecitaTheme
import kotlin.time.Instant

/**
 * Screenshot baselines for [ThreadCluster] — the with/without-ellipsis
 * branches, the direct-reply-to-root collapse, and a video on the root
 * (rendered through the context video slot), each in light and dark.
 *
 * Each fixture uses three distinct authors so the cluster shape is
 * visible: connector lines join three post bodies, with the optional
 * [ThreadFold] between root and parent in the with-ellipsis variant.
 */

private val FIXED_CREATED_AT: Instant = Instant.parse("2025-10-15T12:00:00Z")

private fun rootAuthor(): AuthorUi =
    AuthorUi(
        did = "did:plc:fakeroot00000000000000",
        handle = "rootauthor.bsky.social",
        displayName = "Root Author",
        avatarUrl = null,
    )

private fun parentAuthor(): AuthorUi =
    AuthorUi(
        did = "did:plc:fakeparent000000000000",
        handle = "parentauthor.bsky.social",
        displayName = "Parent Author",
        avatarUrl = null,
    )

private fun leafAuthor(): AuthorUi =
    AuthorUi(
        did = "did:plc:fakeleaf0000000000000",
        handle = "leafauthor.bsky.social",
        displayName = "Leaf Author",
        avatarUrl = null,
    )

private fun fixedPost(
    id: String,
    author: AuthorUi,
    text: String,
): PostUi =
    PostUi(
        id = id,
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author = author,
        createdAt = FIXED_CREATED_AT,
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 4, repostCount = 1, likeCount = 12),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )

private fun root(): PostUi =
    fixedPost(
        id = "at://root/post",
        author = rootAuthor(),
        text = "Starting a thread on what makes a good Bluesky client.",
    )

private fun parent(): PostUi =
    fixedPost(
        id = "at://parent/post",
        author = parentAuthor(),
        text = "Native scroll performance and reliable hydration.",
    )

private fun leaf(): PostUi =
    fixedPost(
        id = "at://leaf/post",
        author = leafAuthor(),
        text = "Agreed — also lossless image rendering and good thread context.",
    )

@PreviewTest
@Preview(name = "without-ellipsis-light", showBackground = true)
@Preview(name = "without-ellipsis-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadClusterWithoutEllipsisScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ThreadCluster(root = root(), parent = parent(), leaf = leaf(), hasEllipsis = false)
    }
}

@PreviewTest
@Preview(name = "with-ellipsis-light", showBackground = true)
@Preview(name = "with-ellipsis-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadClusterWithEllipsisScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        ThreadCluster(root = root(), parent = parent(), leaf = leaf(), hasEllipsis = true)
    }
}

@PreviewTest
@Preview(name = "direct-reply-to-root-light", showBackground = true)
@Preview(name = "direct-reply-to-root-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadClusterDirectReplyToRootScreenshot() {
    // When the reply is direct to the root post (replyRef.parent.uri ==
    // replyRef.root.uri), the cluster collapses to root + leaf — no
    // duplicate parent slot. Common case: a self-reply where the user
    // continues a thought, or any direct reply to the OP.
    NubecitaTheme(dynamicColor = false) {
        // Pass the same post as both root and parent — simulates the
        // direct-reply-to-root wire shape after the mapper runs.
        ThreadCluster(root = root(), parent = root(), leaf = leaf(), hasEllipsis = false)
    }
}

@PreviewTest
@Preview(name = "video-on-root-light", showBackground = true)
@Preview(name = "video-on-root-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ThreadClusterVideoOnRootScreenshot() {
    // The root carries a video and the leaf is a plain-text reply. The root
    // must take the context slot — before nubecita-2e8h it got none, and its
    // video rendered as nothing at all. `:designsystem` can't reach the feed's
    // real video embed, so the slot paints a stub block: a regression drops
    // the block and the baseline no longer matches.
    val videoRoot =
        root().copy(
            embed =
                EmbedUi.Video(
                    posterUrl = null,
                    playlistUrl = "https://video.bsky.app/fixture/playlist.m3u8",
                    aspectRatio = 16f / 9f,
                    durationSeconds = null,
                    altText = null,
                ),
        )
    NubecitaTheme(dynamicColor = false) {
        ThreadCluster(
            root = videoRoot,
            parent = videoRoot,
            leaf = leaf(),
            hasEllipsis = false,
            contextVideoEmbedSlot = { _, _, _ ->
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
            },
        )
    }
}
