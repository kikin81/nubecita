package net.kikin.nubecita.feature.settings.impl

import android.content.res.Configuration
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.DownloadedLanguage
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme

/**
 * Baselines for the Translation screen.
 *
 * Two fixtures that differ in every axis the screen has: the master switch is
 * off in one and on in the other, both language lists are empty in one and
 * populated in the other, and the storage section is absent in one and carries
 * two packs with a total in the other.
 *
 * Driving the switch to both positions is not padding. A single always-off set
 * would render an inverted or dead switch identically to a correct one — and
 * the empty-state strings are the first thing a reader sees, so a set that
 * never showed them could pin their disappearance as correct.
 *
 * The pack sizes are the ones measured on device (Japanese 62 MB, German
 * 45.6 MB), so the baseline shows the real magnitude rather than a tidy
 * fiction — the whole reason this section exists is that the cost is larger
 * than the documentation suggests.
 */
@PreviewTest
@Preview(name = "translation-off-empty-light", showBackground = true, heightDp = 900)
@Composable
private fun TranslationOffEmptyScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            TranslationContent(state = TranslationState(), onEvent = {}, onBack = {})
        }
    }
}

@PreviewTest
@Preview(
    name = "translation-off-empty-dark",
    showBackground = true,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TranslationOffEmptyDarkScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            TranslationContent(state = TranslationState(), onEvent = {}, onBack = {})
        }
    }
}

@PreviewTest
@Preview(name = "translation-on-populated-light", showBackground = true, heightDp = 1100)
@Composable
private fun TranslationOnPopulatedScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            TranslationContent(state = populated(), onEvent = {}, onBack = {})
        }
    }
}

@PreviewTest
@Preview(
    name = "translation-on-populated-dark",
    showBackground = true,
    heightDp = 1100,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
)
@Composable
private fun TranslationOnPopulatedDarkScreenshot() {
    NubecitaCanvasPreviewTheme {
        Surface {
            TranslationContent(state = populated(), onEvent = {}, onBack = {})
        }
    }
}

private fun populated() =
    TranslationState(
        autoTranslateEnabled = true,
        autoTranslate = persistentListOf("de", "ja"),
        neverTranslate = persistentListOf("es"),
        downloaded =
            listOf(
                DownloadedLanguage("de", 45_600_000),
                DownloadedLanguage("ja", 62_000_000),
            ).toImmutableList(),
    )
