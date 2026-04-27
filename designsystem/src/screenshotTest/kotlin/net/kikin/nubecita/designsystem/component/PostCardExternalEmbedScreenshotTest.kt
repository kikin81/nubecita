package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [PostCardExternalEmbed] covering the two
 * render branches (with-thumb / no-thumb) across light + dark themes.
 *
 * The with-thumb shot uses the `NubecitaAsyncImage` placeholder painter
 * (preview tooling doesn't hit the network), so the baseline verifies
 * card geometry + text layout + corner clipping rather than the
 * thumbnail content itself.
 */

@PreviewTest
@Preview(name = "with-thumb-light", showBackground = true)
@Preview(name = "with-thumb-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardExternalEmbedWithThumbScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardExternalEmbed(
            uri = PREVIEW_URI,
            title = PREVIEW_TITLE,
            description = PREVIEW_DESCRIPTION,
            thumbUrl = PREVIEW_THUMB,
            onTap = {},
        )
    }
}

@PreviewTest
@Preview(name = "no-thumb-light", showBackground = true)
@Preview(name = "no-thumb-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PostCardExternalEmbedNoThumbScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        PostCardExternalEmbed(
            uri = PREVIEW_URI,
            title = PREVIEW_TITLE,
            description = PREVIEW_DESCRIPTION,
            thumbUrl = null,
            onTap = {},
        )
    }
}

private const val PREVIEW_URI: String = "https://www.theverge.com/tech/elon-altman-court-battle"
private const val PREVIEW_TITLE: String = "Elon Musk and Sam Altman's court battle over the future of OpenAI"
private const val PREVIEW_DESCRIPTION: String = "The billionaire battle goes to court."
private const val PREVIEW_THUMB: String = "https://example.com/preview-external-thumb.jpg"
