package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
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
 * Screenshot baselines for [ThreadCluster] — two visual branches
 * (with-ellipsis, without-ellipsis) × light/dark = 4 baselines.
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
