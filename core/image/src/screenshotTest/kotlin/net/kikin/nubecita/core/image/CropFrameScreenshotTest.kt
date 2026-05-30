package net.kikin.nubecita.core.image

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import net.kikin.nubecita.core.image.internal.drawCropScene

/**
 * Screenshot baselines for the two crop frame shapes (the visually
 * load-bearing part of [CropImage]): the avatar **circle** mask and the
 * **3:1 banner** rectangle, each with its scrim cut-out.
 *
 * Rendered frame-only (no decoded photo, no Material theme) over a flat
 * fill so the result is deterministic: the bright fill shows through the
 * frame cut-out while the scrim darkens the rest, and the white frame
 * stroke outlines the shape. This isolates the geometry-driven frame
 * rendering from any image-decode or theming variance.
 */
private val FILL_COLOR = Color(0xFF4DB6AC)
private val SCRIM_COLOR = Color.Black.copy(alpha = 0.55f)
private const val FRAME_INSET = 0.9f

@PreviewTest
@Preview(name = "Crop frame — avatar circle", widthDp = 360, heightDp = 640)
@Composable
private fun CropFrameAvatarCirclePreview() {
    CropFrameFixture(CropShape.AvatarCircle)
}

@PreviewTest
@Preview(name = "Crop frame — banner 3:1", widthDp = 360, heightDp = 640)
@Composable
private fun CropFrameBannerPreview() {
    CropFrameFixture(CropShape.Banner)
}

@Composable
private fun CropFrameFixture(shape: CropShape) {
    Box(modifier = Modifier.fillMaxSize().background(FILL_COLOR)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCropScene(
                image = null,
                shape = shape,
                scale = 1f,
                offset = Offset.Zero,
                frameInset = FRAME_INSET,
                scrimColor = SCRIM_COLOR,
                frameColor = Color.White,
            )
        }
    }
}
