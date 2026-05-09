package net.kikin.nubecita.feature.composer.impl

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import kotlinx.collections.immutable.persistentListOf
import net.kikin.nubecita.core.posting.BLUESKY_LANGUAGE_TAGS
import net.kikin.nubecita.designsystem.NubecitaTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.feature.composer.impl.internal.LanguagePickerContent

/**
 * Visual baselines for the picker's content composable across all
 * width classes. The adaptive wrapper [LanguagePicker] is NOT rendered
 * directly because Layoutlib doesn't render `ModalBottomSheet` /
 * `Popup` reliably; the same `LanguagePickerContent` is rendered
 * inside both wrappers in production, so locking the content's visual
 * treatment is sufficient.
 *
 * Two fixtures × 6 buckets = 12 baselines: an initial-state render
 * with the device locale preselected, and a cap-reached render that
 * exercises the disabled-checkbox treatment for unchecked rows.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguagePickerInitialPreviews() {
    NubecitaTheme(dynamicColor = false) {
        LanguagePickerContent(
            allTags = BLUESKY_LANGUAGE_TAGS,
            draftSelection = persistentListOf("en"),
            deviceLocaleTag = "en-US",
            onToggle = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun LanguagePickerCapReachedPreviews() {
    NubecitaTheme(dynamicColor = false) {
        LanguagePickerContent(
            allTags = BLUESKY_LANGUAGE_TAGS,
            draftSelection = persistentListOf("en", "ja", "es"),
            deviceLocaleTag = "en-US",
            onToggle = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
