package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewWrapper
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.preview.NubecitaComponentPreview
import kotlin.time.Instant

/**
 * Screenshot baselines for the post overflow sheet body (nubecita-iwjz.3).
 * The `ModalBottomSheet` window itself can't be composed deterministically by
 * layoutlib, so these capture [PostOverflowGroupsContent] — the grouped cards —
 * for each visibility branch of [postOverflowGroups].
 */
private fun sheetPost(
    viewer: ViewerStateUi,
    text: String = "Sample post text",
): PostUi =
    PostUi(
        id = "screenshot-overflow-sheet",
        cid = "bafyreifake",
        author = AuthorUi(did = "did:plc:fake", handle = "alice.bsky.social", displayName = "Alice Chen", avatarUrl = null),
        createdAt = Instant.parse("2025-10-15T12:00:00Z"),
        text = text,
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(),
        viewer = viewer,
        repostedBy = null,
    )

@Composable
private fun SheetBody(post: PostUi) {
    PostOverflowGroupsContent(groups = postOverflowGroups(post), handle = post.author.handle, onAction = {})
}

@PreviewTest
@Preview(name = "own-post-light", showBackground = true)
@Preview(name = "own-post-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostOverflowSheetOwnPostScreenshot() {
    SheetBody(sheetPost(ViewerStateUi(isOwnPost = true)))
}

@PreviewTest
@Preview(name = "others-post-light", showBackground = true)
@Preview(name = "others-post-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostOverflowSheetOthersPostScreenshot() {
    SheetBody(sheetPost(ViewerStateUi()))
}

@PreviewTest
@Preview(name = "others-post-muted-blocked-light", showBackground = true)
@Preview(name = "others-post-muted-blocked-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostOverflowSheetMutedBlockedScreenshot() {
    SheetBody(sheetPost(ViewerStateUi(isAuthorMutedByViewer = true, isAuthorBlockedByViewer = true)))
}

@PreviewTest
@Preview(name = "thread-muted-light", showBackground = true)
@Preview(name = "thread-muted-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostOverflowSheetThreadMutedScreenshot() {
    SheetBody(sheetPost(ViewerStateUi(isThreadMuted = true)))
}

@PreviewTest
@Preview(name = "media-only-own-post-light", showBackground = true)
@Preview(name = "media-only-own-post-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostOverflowSheetMediaOnlyScreenshot() {
    SheetBody(sheetPost(ViewerStateUi(isOwnPost = true), text = ""))
}

@PreviewTest
@Preview(name = "own-post-pinned-light", showBackground = true)
@Preview(name = "own-post-pinned-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@PreviewWrapper(NubecitaComponentPreview::class)
@Composable
private fun PostOverflowSheetOwnPostPinnedScreenshot() {
    SheetBody(sheetPost(ViewerStateUi(isOwnPost = true, isPinned = true)))
}
