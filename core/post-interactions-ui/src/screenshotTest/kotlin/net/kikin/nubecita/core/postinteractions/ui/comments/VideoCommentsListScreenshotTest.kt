package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.common.time.LocalClock
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.VerifiedBadge
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import kotlin.time.Clock
import kotlin.time.Instant

private const val PREVIEW_WIDTH_DP = 360
private const val PREVIEW_HEIGHT_DP = 640

private val FIXTURE_NOW: Instant = Instant.parse("2026-10-20T12:00:00Z")
private val FIXTURE_CREATED_AT: Instant = Instant.parse("2026-10-20T10:00:00Z")

private object FixtureClock : Clock {
    override fun now(): Instant = FIXTURE_NOW
}

private fun sampleComment(
    id: String,
    handle: String,
    displayName: String,
    text: String,
    likeCount: Long = 0,
    isLiked: Boolean = false,
    depth: Int = 1,
    verifiedBadge: VerifiedBadge = VerifiedBadge.None,
): CommentItemUi {
    val author =
        AuthorUi(
            did = "did:plc:$id",
            handle = handle,
            displayName = displayName,
            avatarUrl = null,
            verifiedBadge = verifiedBadge,
        )
    val post =
        PostUi(
            id = id,
            cid = "cid_$id",
            author = author,
            createdAt = FIXTURE_CREATED_AT,
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
        createdAt = FIXTURE_CREATED_AT,
        likeCount = likeCount,
        isLiked = isLiked,
        depth = depth,
        post = post,
    )
}

@PreviewTest
@Preview(name = "video-comments-list-loaded", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun VideoCommentsListLoadedPreview() {
    val comments =
        persistentListOf(
            sampleComment(
                id = "comment1",
                handle = "alice.bsky.social",
                displayName = "Alice Johnson",
                text = "This vertical player comment sheet is super fluid and preserves continuous video playback!",
                likeCount = 24,
                isLiked = true,
                depth = 1,
                verifiedBadge = VerifiedBadge.Verified,
            ),
            sampleComment(
                id = "comment2",
                handle = "bob.bsky.social",
                displayName = "Bob Smith",
                text = "Agreed, gesture isolation with the vertical pager feels totally native.",
                likeCount = 5,
                isLiked = false,
                depth = 2,
            ),
            sampleComment(
                id = "comment3",
                handle = "charlie.bsky.social",
                displayName = "Charlie",
                text = "Love the two-pane side sheet layout on foldables as well.",
                likeCount = 0,
                isLiked = false,
                depth = 1,
            ),
        )

    val state =
        VideoCommentsState(
            postUri = "at://did:plc:author/app.bsky.feed.post/123",
            detent = VideoCommentsDetent.PartiallyExpanded,
            status = VideoCommentsStatus.Loaded(comments),
            comments = comments,
        )

    NubecitaCanvasPreviewTheme {
        CompositionLocalProvider(LocalClock provides FixtureClock) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                VideoCommentsList(
                    state = state,
                    onLikeClick = {},
                    onRetryClick = {},
                    onRefresh = {},
                )
            }
        }
    }
}

@PreviewTest
@Preview(name = "video-comments-list-empty", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun VideoCommentsListEmptyPreview() {
    val state =
        VideoCommentsState(
            postUri = "at://did:plc:author/app.bsky.feed.post/empty",
            detent = VideoCommentsDetent.PartiallyExpanded,
            status = VideoCommentsStatus.Empty,
            comments = persistentListOf(),
        )

    NubecitaCanvasPreviewTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            VideoCommentsList(
                state = state,
                onLikeClick = {},
                onRetryClick = {},
                onRefresh = {},
            )
        }
    }
}

@PreviewTest
@Preview(name = "video-comments-list-error", widthDp = PREVIEW_WIDTH_DP, heightDp = PREVIEW_HEIGHT_DP)
@Composable
private fun VideoCommentsListErrorPreview() {
    val state =
        VideoCommentsState(
            postUri = "at://did:plc:author/app.bsky.feed.post/error",
            detent = VideoCommentsDetent.PartiallyExpanded,
            status = VideoCommentsStatus.Error(message = "Unable to connect to the network. Please check your connection."),
            comments = persistentListOf(),
        )

    NubecitaCanvasPreviewTheme {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            VideoCommentsList(
                state = state,
                onLikeClick = {},
                onRetryClick = {},
                onRefresh = {},
            )
        }
    }
}
