package net.kikin.nubecita.feature.chats.impl

import android.content.res.Configuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Screenshot baselines for [ChatSettingsScreenContent] — the full-screen
 * (Compact) presentation of the chat-settings radio group. The Medium/Expanded
 * dialog presentation is MainShell-side (the AdaptiveDialogSceneStrategy) and
 * verified on-device, not here.
 */
@PreviewTest
@Preview(name = "chat-settings-following-light", showBackground = true, heightDp = 480)
@Preview(name = "chat-settings-following-dark", showBackground = true, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatSettingsFollowingScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatSettingsScreenContent(
            state = ChatSettingsViewState(ChatSettingsLoadStatus.Loaded(selected = AllowIncoming.Following)),
            onEvent = {},
            onClose = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@PreviewTest
@Preview(name = "chat-settings-everyone-light", showBackground = true, heightDp = 480)
@Preview(name = "chat-settings-everyone-dark", showBackground = true, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatSettingsEveryoneScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatSettingsScreenContent(
            state = ChatSettingsViewState(ChatSettingsLoadStatus.Loaded(selected = AllowIncoming.Everyone)),
            onEvent = {},
            onClose = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@PreviewTest
@Preview(name = "chat-settings-loading-light", showBackground = true, heightDp = 480)
@Preview(name = "chat-settings-loading-dark", showBackground = true, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatSettingsLoadingScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatSettingsScreenContent(
            state = ChatSettingsViewState(ChatSettingsLoadStatus.Loading),
            onEvent = {},
            onClose = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}

@PreviewTest
@Preview(name = "chat-settings-error-light", showBackground = true, heightDp = 480)
@Preview(name = "chat-settings-error-dark", showBackground = true, heightDp = 480, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun ChatSettingsErrorScreenshot() {
    NubecitaCanvasPreviewTheme {
        ChatSettingsScreenContent(
            state = ChatSettingsViewState(ChatSettingsLoadStatus.LoadError),
            onEvent = {},
            onClose = {},
            snackbarHostState = remember { SnackbarHostState() },
        )
    }
}
