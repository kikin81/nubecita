package net.kikin.nubecita.core.postinteractions.ui.comments

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.component.AvatarFallback
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

private const val PREVIEW_WIDTH_DP = 360

@PreviewTest
@Preview(name = "docked-comment-pill-default", widthDp = PREVIEW_WIDTH_DP)
@Composable
private fun DockedCommentPillDefaultPreview() {
    NubecitaCanvasPreviewTheme {
        Box(modifier = Modifier.width(PREVIEW_WIDTH_DP.dp)) {
            DockedCommentPill(
                onClick = {},
                avatarFallback = AvatarFallback(hue = 210, initial = 'U'),
            )
        }
    }
}

@PreviewTest
@Preview(name = "docked-comment-pill-custom-placeholder", widthDp = PREVIEW_WIDTH_DP)
@Composable
private fun DockedCommentPillCustomPlaceholderPreview() {
    NubecitaCanvasPreviewTheme {
        Box(modifier = Modifier.width(PREVIEW_WIDTH_DP.dp)) {
            DockedCommentPill(
                onClick = {},
                avatarFallback = AvatarFallback(hue = 140, initial = 'A'),
                placeholderText = "Reply to @alice.bsky.social…",
            )
        }
    }
}
