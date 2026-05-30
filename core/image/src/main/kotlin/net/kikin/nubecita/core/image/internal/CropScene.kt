package net.kikin.nubecita.core.image.internal

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.core.image.CropShape
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Draws the whole crop scene in one pass so the displayed image and the
 * extracted region share identical geometry ([CropGeometry]): the image
 * positioned under the current pan/zoom, a scrim everywhere except the frame
 * cut-out, and the frame border (circle for avatar, rect for banner).
 *
 * [image] is null while decoding (or in a frame-only preview) — the frame and
 * scrim still draw so the surface never flashes empty.
 *
 * Shared by the live `CropImage` Canvas and the screenshot fixtures, which is
 * why it's a plain `DrawScope` extension with explicit colors rather than a
 * composable.
 */
internal fun DrawScope.drawCropScene(
    image: ImageBitmap?,
    shape: CropShape,
    scale: Float,
    offset: Offset,
    frameInset: Float,
    scrimColor: Color,
    frameColor: Color,
) {
    val (frameW, frameH) = CropGeometry.frameSize(size.width, size.height, shape.aspect, frameInset)
    val frameLeft = (size.width - frameW) / 2f
    val frameTop = (size.height - frameH) / 2f
    val frameRect = Rect(frameLeft, frameTop, frameLeft + frameW, frameTop + frameH)

    if (image != null) {
        val k =
            CropGeometry.baseScale(image.width, image.height, frameW, frameH) *
                max(scale, CropGeometry.MIN_SCALE)
        val dispW = image.width * k
        val dispH = image.height * k
        val (cx, cy) =
            CropGeometry.clampOffset(offset.x, offset.y, image.width, image.height, frameW, frameH, scale)
        val imgLeft = size.width / 2f + cx - dispW / 2f
        val imgTop = size.height / 2f + cy - dispH / 2f
        drawImage(
            image = image,
            dstOffset = IntOffset(imgLeft.roundToInt(), imgTop.roundToInt()),
            dstSize = IntSize(dispW.roundToInt(), dispH.roundToInt()),
        )
    }

    // Scrim everywhere except the frame: outer rect + inner shape with an
    // even-odd fill leaves the framed region untouched.
    val scrim =
        Path().apply {
            addRect(Rect(0f, 0f, size.width, size.height))
            if (shape.circular) addOval(frameRect) else addRect(frameRect)
            fillType = PathFillType.EvenOdd
        }
    drawPath(scrim, scrimColor)

    val stroke = Stroke(width = 2.dp.toPx())
    if (shape.circular) {
        drawCircle(color = frameColor, radius = frameW / 2f, center = frameRect.center, style = stroke)
    } else {
        drawRect(color = frameColor, topLeft = frameRect.topLeft, size = frameRect.size, style = stroke)
    }
}
