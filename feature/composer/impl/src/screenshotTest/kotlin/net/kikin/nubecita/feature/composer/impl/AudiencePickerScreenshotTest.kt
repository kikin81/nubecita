package net.kikin.nubecita.feature.composer.impl

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.posting.PostAudience
import net.kikin.nubecita.core.posting.ReplyAudience
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.feature.composer.impl.internal.AudiencePickerContent

/**
 * Visual baselines for the picker's content composable across all width
 * classes. The adaptive wrapper [net.kikin.nubecita.feature.composer.impl.internal.AudiencePicker]
 * is NOT rendered directly because Layoutlib doesn't render `ModalBottomSheet` /
 * `Popup` reliably; the same `AudiencePickerContent` is rendered inside both
 * wrappers in production, so locking the content's visual treatment is enough.
 *
 * Two fixtures × 6 buckets = 12 baselines: the wide-open default (Anyone +
 * quotes on), and a restricted render (a followers/mentioned combination with
 * quotes off and "save as default" checked) that exercises the radio, the
 * checked/disabled checkbox treatment, and the switch-off state.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun AudiencePickerDefaultPreviews() {
    NubecitaCanvasPreviewTheme {
        AudiencePickerContent(
            audience = PostAudience.DEFAULT,
            saveAsDefault = false,
            onAudienceChange = {},
            onSaveAsDefaultChange = {},
            onReset = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}

@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun AudiencePickerRestrictedPreviews() {
    NubecitaCanvasPreviewTheme {
        AudiencePickerContent(
            audience =
                PostAudience(
                    reply = ReplyAudience.Combination(followers = true, following = false, mentioned = true),
                    allowQuotes = false,
                ),
            saveAsDefault = true,
            onAudienceChange = {},
            onSaveAsDefaultChange = {},
            onReset = {},
            onConfirm = {},
            onDismiss = {},
        )
    }
}
