package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.R
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/**
 * Screenshot baselines for [DetailPaneEmptyState]. Both surface variants
 * (post-list Article + chats ChatBubble) × two themes, widthDp = 400
 * (matches the detail-pane proportion at Medium screen widths; the
 * composable adapts to whatever pane size the strategy gives it).
 */
@PreviewTest
@Preview(name = "placeholder-post-light", showBackground = true, widthDp = 400, heightDp = 600)
@Preview(
    name = "placeholder-post-dark",
    showBackground = true,
    widthDp = 400,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailPaneEmptyStatePostScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        DetailPaneEmptyState(
            icon = NubecitaIconName.Article,
            message = stringResource(R.string.nubecita_detail_pane_select_post),
        )
    }
}

@PreviewTest
@Preview(name = "placeholder-chat-light", showBackground = true, widthDp = 400, heightDp = 600)
@Preview(
    name = "placeholder-chat-dark",
    showBackground = true,
    widthDp = 400,
    heightDp = 600,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun DetailPaneEmptyStateChatScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        DetailPaneEmptyState(
            icon = NubecitaIconName.ChatBubble,
            message = stringResource(R.string.nubecita_detail_pane_select_conversation),
        )
    }
}
