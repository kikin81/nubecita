package net.kikin.nubecita.designsystem.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

private const val COMPACT_WIDTH_DP: Int = 360
private const val MEDIUM_WIDTH_DP: Int = 600
private const val EXPANDED_WIDTH_DP: Int = 840
private const val HEIGHT_DP: Int = 800

@Composable
private fun SampleVideoPlayerContent() {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color(0xFF1A1A24),
                                Color(0xFF0F0F14),
                                Color(0xFF000000),
                            ),
                    ),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center,
            ) {
                NubecitaIcon(
                    name = NubecitaIconName.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "120Hz Video Playback Surface",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.8f),
            )
        }
    }
}

@Composable
private fun SampleCommentsContent() {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "Comments (2)",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        HorizontalDivider(modifier = Modifier.padding(bottom = 12.dp))
        SampleCommentRow(
            author = "Alice Johnson",
            handle = "@alice.bsky.social",
            body = "This video comments sheet experience is super smooth and maintains 120Hz playback!",
        )
        Spacer(modifier = Modifier.height(16.dp))
        SampleCommentRow(
            author = "Bob Smith",
            handle = "@bob.bsky.social",
            body = "Two-pane side sheet on foldables is so much better than covering the whole video.",
        )
    }
}

@Composable
private fun SampleCommentRow(
    author: String,
    handle: String,
    body: String,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = author.take(1),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = author,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = handle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "video-comments-sheet-compact", widthDp = COMPACT_WIDTH_DP, heightDp = HEIGHT_DP)
@Composable
private fun VideoCommentsSheetScaffoldCompactPreview() {
    NubecitaCanvasPreviewTheme {
        VideoCommentsSheetScaffold(
            isCommentsOpen = true,
            onDismiss = {},
            commentsContent = { SampleCommentsContent() },
            videoContent = { SampleVideoPlayerContent() },
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "video-comments-sheet-medium", widthDp = MEDIUM_WIDTH_DP, heightDp = HEIGHT_DP)
@Composable
private fun VideoCommentsSheetScaffoldMediumPreview() {
    NubecitaCanvasPreviewTheme {
        VideoCommentsSheetScaffold(
            isCommentsOpen = true,
            onDismiss = {},
            commentsContent = { SampleCommentsContent() },
            videoContent = { SampleVideoPlayerContent() },
        )
    }
}

@OptIn(ExperimentalMaterial3AdaptiveApi::class, ExperimentalMaterial3Api::class)
@PreviewTest
@Preview(name = "video-comments-sheet-expanded", widthDp = EXPANDED_WIDTH_DP, heightDp = HEIGHT_DP)
@Composable
private fun VideoCommentsSheetScaffoldExpandedPreview() {
    NubecitaCanvasPreviewTheme {
        VideoCommentsSheetScaffold(
            isCommentsOpen = true,
            onDismiss = {},
            commentsContent = { SampleCommentsContent() },
            videoContent = { SampleVideoPlayerContent() },
        )
    }
}
