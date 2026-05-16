package net.kikin.nubecita.designsystem.component

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Screenshot baselines for [HighlightedText] covering the three
 * branches (no match, single match, multi-match case-insensitive)
 * across light + dark themes.
 */

@PreviewTest
@Preview(name = "highlighted-no-match-light", showBackground = true)
@Preview(name = "highlighted-no-match-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HighlightedTextNoMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            HighlightedText(
                text = "The quick brown fox jumps over the lazy dog",
                match = null,
            )
        }
    }
}

@PreviewTest
@Preview(name = "highlighted-single-match-light", showBackground = true)
@Preview(name = "highlighted-single-match-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HighlightedTextSingleMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            HighlightedText(
                text = "The quick brown fox jumps over the lazy dog",
                match = "fox",
            )
        }
    }
}

@PreviewTest
@Preview(name = "highlighted-multi-match-light", showBackground = true)
@Preview(name = "highlighted-multi-match-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun HighlightedTextMultiMatchScreenshot() {
    NubecitaTheme(dynamicColor = false) {
        Surface {
            HighlightedText(
                text = "Kotlin and KOTLIN and kotlin — same word, three cases",
                match = "kotlin",
            )
        }
    }
}
