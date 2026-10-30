package net.kikin.nubecita.wear.input

import androidx.compose.runtime.Composable
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import androidx.wear.compose.ui.tooling.preview.WearPreviewFontScales
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.wear.ui.theme.NubecitaWearTheme

// Rendered without AppScaffold (its TimeText shows the wall clock).

private const val REPLY = "Running late, see you at 8 by the north entrance"

@PreviewTest
@WearPreviewDevices
@Composable
private fun TextReviewPreview() {
    NubecitaWearTheme { TextReviewScreen(title = "Your reply", text = REPLY, maxGraphemes = 1000, onSend = {}, onChange = {}) }
}

@PreviewTest
@WearPreviewFontScales
@Composable
private fun TextReviewFontScalesPreview() {
    NubecitaWearTheme { TextReviewScreen(title = "Your reply", text = REPLY, maxGraphemes = 1000, onSend = {}, onChange = {}) }
}

@PreviewTest
@WearPreviewDevices
@Composable
private fun TextReviewOverLimitPreview() {
    NubecitaWearTheme { TextReviewScreen(title = "New post", text = "Long run today. ".repeat(20), maxGraphemes = 300, onSend = {}, onChange = {}) }
}
