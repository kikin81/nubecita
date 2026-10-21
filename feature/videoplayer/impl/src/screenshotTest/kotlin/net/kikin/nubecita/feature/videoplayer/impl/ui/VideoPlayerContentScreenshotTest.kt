package net.kikin.nubecita.feature.videoplayer.impl.ui

import android.content.res.Configuration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.postinteractions.ui.comments.CommentItemUi
import net.kikin.nubecita.core.postinteractions.ui.comments.VideoCommentsSheetContent
import net.kikin.nubecita.core.postinteractions.ui.comments.VideoCommentsState
import net.kikin.nubecita.core.postinteractions.ui.comments.VideoCommentsStatus
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.adaptive.VideoCommentsSheetScaffold
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerError
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerLoadStatus
import net.kikin.nubecita.feature.videoplayer.impl.VideoPlayerState
import kotlin.time.Instant

/**
 * Screenshot baselines for [VideoPlayerContent] covering every
 * `VideoPlayerLoadStatus` variant the screen renders × light/dark:
 *
 * - **Resolving**: centered progress indicator over black.
 * - **Ready playing**: chrome visible, pause icon active, seek bar
 *   mid-track. Player param is null — layoutlib can't construct
 *   `PlayerSurface`, and `VideoPlayerContent` skips it when player is
 *   null so the layout still renders the poster + chrome layers.
 * - **Ready paused**: same as playing but `isPlaying = false`; locks
 *   the play-icon swap.
 * - **Ready chrome hidden**: chrome `AnimatedVisibility` collapsed —
 *   guards against an accidental "always visible" regression that
 *   would block the video underneath.
 * - **Error**: centered error layout with Retry button.
 *
 * The fixed `heightDp` matches the search-feature screenshot tests so
 * the fullscreen surface renders against a phone-shaped canvas
 * instead of layoutlib's default infinite height.
 */

private const val CANVAS_HEIGHT_DP = 600
private const val COMPACT_WIDTH_DP = 412
private const val MEDIUM_WIDTH_DP = 700
private const val EXPANDED_WIDTH_DP = 1000
private const val DETAIL_CANVAS_HEIGHT_DP = 640

@PreviewTest
@Preview(name = "resolving-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "resolving-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentResolvingScreenshot() {
    NubecitaCanvasPreviewTheme {
        VideoPlayerContent(
            state = VideoPlayerState(loadStatus = VideoPlayerLoadStatus.Resolving),
            player = null,
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-playing-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "ready-playing-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentReadyPlayingScreenshot() {
    NubecitaCanvasPreviewTheme {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    aspectRatio = 16f / 9f,
                    isPlaying = true,
                    isMuted = false,
                    positionMs = 5_400L,
                    durationMs = 30_000L,
                    chromeVisible = true,
                ),
            player = null,
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-popout-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "ready-popout-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentReadyPopOutScreenshot() {
    // Pop-out / enter-PiP affordance shown (PiP-capable device) — locks the
    // glyph into the mute + PiP utility row under play/pause (nubecita-6rdb.6);
    // when onPopOut is null (other previews) mute sits there alone.
    NubecitaCanvasPreviewTheme {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    aspectRatio = 16f / 9f,
                    isPlaying = true,
                    isMuted = false,
                    positionMs = 5_400L,
                    durationMs = 30_000L,
                    chromeVisible = true,
                ),
            player = null,
            onEvent = {},
            onPopOut = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-paused-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "ready-paused-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentReadyPausedScreenshot() {
    NubecitaCanvasPreviewTheme {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    aspectRatio = 16f / 9f,
                    isPlaying = false,
                    isMuted = false,
                    positionMs = 5_400L,
                    durationMs = 30_000L,
                    chromeVisible = true,
                ),
            player = null,
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "ready-chrome-hidden-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "ready-chrome-hidden-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentReadyChromeHiddenScreenshot() {
    NubecitaCanvasPreviewTheme {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    aspectRatio = 16f / 9f,
                    isPlaying = true,
                    isMuted = true,
                    positionMs = 12_000L,
                    durationMs = 30_000L,
                    chromeVisible = false,
                ),
            player = null,
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "pip-chrome-suppressed-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "pip-chrome-suppressed-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentPipScreenshot() {
    // In PiP the chrome is suppressed even though `chromeVisible = true` — the
    // floating window shows only the video. Distinct from ready-chrome-hidden
    // (which hides via chromeVisible = false); this locks the isInPip override.
    NubecitaCanvasPreviewTheme {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Ready,
                    aspectRatio = 16f / 9f,
                    isPlaying = true,
                    isMuted = false,
                    positionMs = 5_400L,
                    durationMs = 30_000L,
                    chromeVisible = true,
                ),
            player = null,
            onEvent = {},
            isInPip = true,
        )
    }
}

@PreviewTest
@Preview(name = "error-network-light", showBackground = true, heightDp = CANVAS_HEIGHT_DP)
@Preview(name = "error-network-dark", showBackground = true, heightDp = CANVAS_HEIGHT_DP, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun VideoPlayerContentErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        VideoPlayerContent(
            state =
                VideoPlayerState(
                    loadStatus = VideoPlayerLoadStatus.Error(error = VideoPlayerError.Network),
                ),
            player = null,
            onEvent = {},
        )
    }
}

private fun sampleComment(
    id: String,
    handle: String,
    displayName: String,
    text: String,
    likeCount: Long = 0,
    isLiked: Boolean = false,
    depth: Int = 1,
): CommentItemUi {
    val author =
        AuthorUi(
            did = "did:plc:$id",
            handle = handle,
            displayName = displayName,
            avatarUrl = null,
        )
    val post =
        PostUi(
            id = id,
            cid = "cid_$id",
            author = author,
            createdAt = Instant.parse("2026-10-20T10:00:00Z"),
            text = text,
            facets = persistentListOf(),
            embed = EmbedUi.Empty,
            stats = PostStatsUi(likeCount = likeCount.toInt()),
            viewer =
                ViewerStateUi(
                    isLikedByViewer = isLiked,
                    likeUri = if (isLiked) "at://like/$id" else null,
                ),
            repostedBy = null,
        )
    return CommentItemUi(
        id = id,
        cid = "cid_$id",
        author = author,
        text = text,
        createdAt = Instant.parse("2026-10-20T10:00:00Z"),
        likeCount = likeCount,
        isLiked = isLiked,
        depth = depth,
        post = post,
    )
}

private fun sampleCommentsState(): VideoCommentsState =
    VideoCommentsState(
        status =
            VideoCommentsStatus.Loaded(
                comments =
                    persistentListOf(
                        sampleComment(
                            id = "c1",
                            handle = "alice.bsky.social",
                            displayName = "Alice",
                            text = "This player and comments sheet look incredible!",
                            likeCount = 12,
                            isLiked = true,
                        ),
                        sampleComment(
                            id = "c2",
                            handle = "bob.bsky.social",
                            displayName = "Bob",
                            text = "120Hz smooth animations across phone and foldable.",
                            likeCount = 4,
                            isLiked = false,
                        ),
                    ),
            ),
    )

private fun sampleVideoPlayerState(isCommentsOpen: Boolean = true): VideoPlayerState =
    VideoPlayerState(
        loadStatus = VideoPlayerLoadStatus.Ready,
        aspectRatio = 16f / 9f,
        isPlaying = true,
        isMuted = false,
        positionMs = 5_400L,
        durationMs = 30_000L,
        chromeVisible = true,
        isCommentsOpen = isCommentsOpen,
        author =
            AuthorUi(
                did = "did:plc:sana",
                handle = "sana.nubecita.app",
                displayName = "Sana Okafor",
                avatarUrl = null,
            ),
        stats =
            PostStatsUi(
                likeCount = 2400,
                repostCount = 128,
                replyCount = 42,
            ),
        viewer =
            ViewerStateUi(
                isLikedByViewer = true,
                isRepostedByViewer = false,
            ),
    )

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "detail-player-comments-compact", widthDp = COMPACT_WIDTH_DP, heightDp = DETAIL_CANVAS_HEIGHT_DP)
@Composable
private fun VideoPlayerDetailWithCommentsCompactScreenshot() {
    NubecitaCanvasPreviewTheme {
        VideoCommentsSheetScaffold(
            isCommentsOpen = true,
            onDismiss = {},
            commentsContent = {
                VideoCommentsSheetContent(
                    state = sampleCommentsState(),
                    onLikeClick = {},
                    onRetryClick = {},
                    onRefresh = {},
                    onOpenComposer = {},
                    onAuthorClick = {},
                    onCommentClick = {},
                )
            },
            videoContent = {
                VideoPlayerContent(
                    state = sampleVideoPlayerState(isCommentsOpen = true),
                    player = null,
                    onEvent = {},
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "detail-player-comments-medium", widthDp = MEDIUM_WIDTH_DP, heightDp = DETAIL_CANVAS_HEIGHT_DP)
@Composable
private fun VideoPlayerDetailWithCommentsMediumScreenshot() {
    NubecitaCanvasPreviewTheme {
        VideoCommentsSheetScaffold(
            isCommentsOpen = true,
            onDismiss = {},
            commentsContent = {
                VideoCommentsSheetContent(
                    state = sampleCommentsState(),
                    onLikeClick = {},
                    onRetryClick = {},
                    onRefresh = {},
                    onOpenComposer = {},
                    onAuthorClick = {},
                    onCommentClick = {},
                )
            },
            videoContent = {
                VideoPlayerContent(
                    state = sampleVideoPlayerState(isCommentsOpen = true),
                    player = null,
                    onEvent = {},
                )
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "detail-player-comments-expanded", widthDp = EXPANDED_WIDTH_DP, heightDp = DETAIL_CANVAS_HEIGHT_DP)
@Composable
private fun VideoPlayerDetailWithCommentsExpandedScreenshot() {
    NubecitaCanvasPreviewTheme {
        VideoCommentsSheetScaffold(
            isCommentsOpen = true,
            onDismiss = {},
            commentsContent = {
                VideoCommentsSheetContent(
                    state = sampleCommentsState(),
                    onLikeClick = {},
                    onRetryClick = {},
                    onRefresh = {},
                    onOpenComposer = {},
                    onAuthorClick = {},
                    onCommentClick = {},
                )
            },
            videoContent = {
                VideoPlayerContent(
                    state = sampleVideoPlayerState(isCommentsOpen = true),
                    player = null,
                    onEvent = {},
                )
            },
        )
    }
}
