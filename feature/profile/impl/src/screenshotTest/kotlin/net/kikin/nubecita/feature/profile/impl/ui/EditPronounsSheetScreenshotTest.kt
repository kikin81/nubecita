package net.kikin.nubecita.feature.profile.impl.ui

import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.feature.profile.impl.PronounsSheetState

/**
 * The pronouns sheet body ([EditPronounsSheetContent]) — rendered without the
 * window-level `ModalBottomSheet`, which the screenshot host can't draw.
 * Empty (Remove disabled), a preset selected, and over the 20-grapheme limit
 * (error tone, Done disabled).
 */
private val PRESETS = persistentListOf("she/her", "he/him", "they/them")

@PreviewTest
@Preview(name = "pronouns-sheet-empty-light", showBackground = true)
@Preview(name = "pronouns-sheet-empty-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PronounsSheetEmptyPreview() {
    NubecitaCanvasPreviewTheme {
        EditPronounsSheetContent(PronounsSheetState(), PRESETS, {}, {}, {}, {})
    }
}

@PreviewTest
@Preview(name = "pronouns-sheet-preset-selected-light", showBackground = true)
@Preview(name = "pronouns-sheet-preset-selected-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PronounsSheetPresetSelectedPreview() {
    NubecitaCanvasPreviewTheme {
        EditPronounsSheetContent(PronounsSheetState(input = "he/him", graphemes = 6), PRESETS, {}, {}, {}, {})
    }
}

@PreviewTest
@Preview(name = "pronouns-sheet-over-limit-light", showBackground = true)
@Preview(name = "pronouns-sheet-over-limit-dark", showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun PronounsSheetOverLimitPreview() {
    NubecitaCanvasPreviewTheme {
        EditPronounsSheetContent(
            PronounsSheetState(input = "they/them and also she/her", graphemes = 26),
            PRESETS,
            {},
            {},
            {},
            {},
        )
    }
}
