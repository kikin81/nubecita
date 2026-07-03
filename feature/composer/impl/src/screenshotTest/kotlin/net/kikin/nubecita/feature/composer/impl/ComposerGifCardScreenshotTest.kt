package net.kikin.nubecita.feature.composer.impl

import androidx.compose.runtime.Composable
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.data.models.KlipyMediaUiFixtures
import net.kikin.nubecita.designsystem.preview.NubecitaCanvasPreviewTheme
import net.kikin.nubecita.designsystem.preview.PreviewNubecitaScreenPreviews
import net.kikin.nubecita.feature.composer.impl.internal.ComposerGifCard

/**
 * Baselines for the composer's picked-GIF preview card (the render + dismiss ✕).
 * The animated cell renders the placeholder `ColorPainter` in the screenshot
 * host (no network), which is deterministic.
 */
@PreviewTest
@PreviewNubecitaScreenPreviews
@Composable
private fun ComposerGifCardPreviews() {
    NubecitaCanvasPreviewTheme {
        ComposerGifCard(
            gif = KlipyMediaUiFixtures.media(title = "Happy Cat"),
            onRemove = {},
        )
    }
}
