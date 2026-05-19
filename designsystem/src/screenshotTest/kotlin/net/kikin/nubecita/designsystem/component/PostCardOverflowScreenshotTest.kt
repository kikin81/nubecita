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
 * Screenshot baselines for the oftc.2 PostCard overflow affordance.
 *
 * The DropdownMenu open-state is intentionally NOT captured here —
 * `DropdownMenu` renders inside an overlay window that Layoutlib doesn't
 * compose deterministically (per `OwnProfileActionsRowScreenshotTest`'s
 * KDoc). The open-state coverage lives in the instrumentation test
 * (`PostCardClickModelTest`).
 *
 * Captured here:
 * - icon-absent: `onOverflowAction = null` — the action row renders 4
 *   cells only (no 5th overflow affordance).
 * - icon-present: `onOverflowAction` wired — the action row renders 5
 *   cells, the rightmost being the More-options icon.
 * - viewer-moderation permutations (muted-author, blocked-author,
 *   neutral) — locked here so any future divergence in the action-row
 *   layout caused by these flags is caught at baseline review (today
 *   none of these flags affect the closed-state row, but the matrix
 *   protects against accidental regressions when oftc.3 / .4 / .5
 *   start rendering badges or color cues).
 */

private fun overflowScreenshotAuthor(): AuthorUi =
    AuthorUi(
        did = "did:plc:fakedid000000000000000",
        handle = "alice.bsky.social",
        displayName = "Alice Chen",
        avatarUrl = null,
    )

private fun overflowScreenshotPost(viewer: ViewerStateUi = ViewerStateUi(isLikedByViewer = true)): PostUi =
    PostUi(
        id = "screenshot-overflow",
        cid = "bafyreifakefakefakefakefakefakefakefakefakefake",
        author = overflowScreenshotAuthor(),
        // Pinned to a Pleistocene-era instant so rememberRelativeTimeText
        // hits the absolute-date bucket (> 7 days). Mirrors
        // PostCardScreenshotTest's pinned timestamp.
        createdAt = Instant.parse("2025-10-15T12:00:00Z"),
        text = "Sample post content for the overflow-affordance fixtures — body text is constant so the action row geometry is the only varying surface.",
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(replyCount = 12, repostCount = 4, likeCount = 86),
        viewer = viewer,
        repostedBy = null,
    )

@PreviewTest
@Preview(name = "overflow-icon-absent-light", showBackground = true)
@Preview(name = "overflow-icon-absent-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardOverflowIconAbsentScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        // PostCallbacks.None has onOverflowAction = null — locks the
        // 4-cell action-row shape used by preview / placeholder hosts.
        PostCard(post = overflowScreenshotPost(), callbacks = PostCallbacks.None)
    }
}

@PreviewTest
@Preview(name = "overflow-icon-present-light", showBackground = true)
@Preview(name = "overflow-icon-present-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardOverflowIconPresentScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post = overflowScreenshotPost(),
            callbacks = PostCallbacks(onOverflowAction = { _, _ -> }),
        )
    }
}

@PreviewTest
@Preview(name = "overflow-viewer-muted-author-light", showBackground = true)
@Preview(
    name = "overflow-viewer-muted-author-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostCardOverflowViewerMutedAuthorScreenshot() {
    // The Dropdown is closed (Layoutlib limitation), but the viewer
    // state still flows through to the closed-state row. The fixture is
    // an explicit "muted-author" snapshot so any future visual cue
    // (e.g. a badge on the overflow icon) lands here for review.
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post = overflowScreenshotPost(viewer = ViewerStateUi(isAuthorMutedByViewer = true)),
            callbacks = PostCallbacks(onOverflowAction = { _, _ -> }),
        )
    }
}

@PreviewTest
@Preview(name = "overflow-viewer-blocked-author-light", showBackground = true)
@Preview(
    name = "overflow-viewer-blocked-author-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostCardOverflowViewerBlockedAuthorScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post = overflowScreenshotPost(viewer = ViewerStateUi(isAuthorBlockedByViewer = true)),
            callbacks = PostCallbacks(onOverflowAction = { _, _ -> }),
        )
    }
}

@PreviewTest
@Preview(name = "overflow-viewer-neutral-light", showBackground = true)
@Preview(
    name = "overflow-viewer-neutral-dark",
    showBackground = true,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun PostCardOverflowViewerNeutralScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCard(
            post = overflowScreenshotPost(viewer = ViewerStateUi()),
            callbacks = PostCallbacks(onOverflowAction = { _, _ -> }),
        )
    }
}
