package net.kikin.nubecita.feature.composer.impl

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.feature.composer.impl.internal.ComposerLanguageChip

/**
 * Visual baselines for [ComposerLanguageChip]'s three label states.
 * Pinning `deviceLocaleTag = "en-US"` keeps the rendered display name
 * deterministic across CI runners regardless of the JVM's
 * `Locale.getDefault()` (Layoutlib defaults vary by manufacturer).
 *
 * Each fixture is multiplied by [PreviewNubecitaScreenPreviews]'s 6
 * device × theme buckets — Phone / Foldable / Tablet × Light / Dark.
 * Three fixtures × 6 = 18 baselines.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguageChipNullSelectionPreviews() {
    NubecitaTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ComposerLanguageChip(
                selectedLangs = null,
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguageChipSingleSelectionPreviews() {
    NubecitaTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ComposerLanguageChip(
                selectedLangs = listOf("ja-JP"),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguageChipEmptySelectionPreviews() {
    // Explicit empty list — distinct from null. Locks the visual for
    // the "user said no language" state, surfaced as the localized
    // "No language" string from R.string.composer_language_chip_none.
    NubecitaTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ComposerLanguageChip(
                selectedLangs = emptyList(),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguageChipMultiSelectionPreviews() {
    NubecitaTheme(dynamicColor = false) {
        Box(modifier = Modifier.padding(16.dp)) {
            ComposerLanguageChip(
                selectedLangs = listOf("en-US", "ja-JP", "es-MX"),
                deviceLocaleTag = "en-US",
                onClick = {},
            )
        }
    }
}
