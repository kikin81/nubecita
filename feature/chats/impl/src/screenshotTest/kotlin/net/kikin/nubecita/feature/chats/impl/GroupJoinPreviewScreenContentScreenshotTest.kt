package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for [GroupJoinPreviewScreenContent]'s error state. A dead link
 * (disabled / invalid code) explains itself with no Retry; a transient failure keeps Retry.
 */
@PreviewTest
@Preview(name = "group-join-error-invalid-link-light", showBackground = true, heightDp = 720)
@Preview(name = "group-join-error-invalid-link-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupJoinPreviewInvalidLinkScreenshot() = Fixture(ChatError.InvalidInviteLink)

@PreviewTest
@Preview(name = "group-join-error-generic-light", showBackground = true, heightDp = 720)
@Preview(name = "group-join-error-generic-dark", showBackground = true, heightDp = 720, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun GroupJoinPreviewGenericErrorScreenshot() = Fixture(ChatError.Network)

@Composable
private fun Fixture(error: ChatError) {
    NubecitaCanvasPreviewTheme {
        GroupJoinPreviewScreenContent(
            state = GroupJoinPreviewViewState(status = GroupJoinPreviewStatus.Error(error)),
            onEvent = {},
            onClose = {},
        )
    }
}
