package net.kikin.nubecita.core.image

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.image.internal.CropExtractor
import net.kikin.nubecita.core.image.internal.CropGeometry
import net.kikin.nubecita.core.image.internal.drawCropScene

/**
 * A hand-rolled fixed-frame crop surface. Decodes [sourceUri] to a downscaled,
 * EXIF-oriented working bitmap, holds it behind a fixed frame ([shape]) the
 * user pans and pinch-zooms, and — constrained so the image always covers the
 * frame — extracts the framed region on confirm, JPEG-compressed under the
 * Bluesky blob cap (this surface only feeds `app.bsky.actor.profile`
 * avatar/banner, whose lexicon accepts only PNG/JPEG — not WebP), via [onCrop]
 * `(bytes, mimeType)`.
 *
 * Built on Compose foundation only (`detectTransformGestures` + a single
 * `Canvas`) and `android.graphics`; no telephoto, no third-party crop library.
 * The frame→source-rect math is the pure, unit-tested [CropGeometry]; the live
 * Canvas and the extractor share it so what you see is what you get.
 *
 * Chrome is deliberately minimal (Cancel / Use photo) — the hosting screen
 * supplies its own app bar. [onCancel] also fires if the image can't be
 * decoded.
 */
@Composable
fun CropImage(
    sourceUri: Uri,
    shape: CropShape,
    onCrop: (bytes: ByteArray, mimeType: String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The decode effect restarts on `sourceUri`, not on callback identity, so
    // capture the latest onCancel to avoid pinning a stale lambda.
    val currentOnCancel by rememberUpdatedState(onCancel)

    var working by remember(sourceUri) { mutableStateOf<Bitmap?>(null) }
    var scale by remember(sourceUri) { mutableFloatStateOf(CropGeometry.MIN_SCALE) }
    var offset by remember(sourceUri) { mutableStateOf(Offset.Zero) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var cropping by remember(sourceUri) { mutableStateOf(false) }

    // Wrap the working bitmap once per decode, not once per gesture frame — the
    // draw lambda runs every frame during a pan/zoom (120hz target).
    val imageBitmap = remember(working) { working?.asImageBitmap() }

    LaunchedEffect(sourceUri) {
        val decoded =
            withContext(Dispatchers.IO) {
                runCatching { CropExtractor.decodeWorkingBitmap(context, sourceUri) }.getOrNull()
            }
        working = decoded
        if (decoded == null) currentOnCancel() // unreadable / unsupported source → treat as aborted
    }

    // Keyed on the bitmap itself (captured into a local) so each decoded bitmap
    // is recycled exactly once — on dispose AND when replaced by a new source.
    DisposableEffect(working) {
        val bitmap = working
        onDispose { bitmap?.recycle() }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Canvas(
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { canvasSize = it }
                    .pointerInput(working) {
                        val bitmap = working ?: return@pointerInput
                        detectTransformGestures { _, pan, zoom, _ ->
                            val cw = canvasSize.width.toFloat()
                            val ch = canvasSize.height.toFloat()
                            if (cw <= 0f || ch <= 0f) return@detectTransformGestures
                            val (frameW, frameH) = CropGeometry.frameSize(cw, ch, shape.aspect, FRAME_INSET)
                            val nextScale = CropGeometry.clampScale(scale * zoom, MAX_SCALE)
                            val (nextX, nextY) =
                                CropGeometry.clampOffset(
                                    offsetX = offset.x + pan.x,
                                    offsetY = offset.y + pan.y,
                                    imageW = bitmap.width,
                                    imageH = bitmap.height,
                                    frameW = frameW,
                                    frameH = frameH,
                                    scale = nextScale,
                                )
                            scale = nextScale
                            offset = Offset(nextX, nextY)
                        }
                    },
        ) {
            drawCropScene(
                image = imageBitmap,
                shape = shape,
                scale = scale,
                offset = offset,
                frameInset = FRAME_INSET,
                scrimColor = SCRIM_COLOR,
                frameColor = Color.White,
            )
        }

        if (working == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White,
            )
        }

        Row(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) {
                Text(text = stringResource(R.string.crop_cancel), color = Color.White)
            }
            Button(
                onClick = {
                    val bitmap = working ?: return@Button
                    val cw = canvasSize.width.toFloat()
                    val ch = canvasSize.height.toFloat()
                    if (cw <= 0f || ch <= 0f) return@Button
                    // Snapshot the gesture state at tap time so a later pan/zoom
                    // can't change what gets extracted out from under the user.
                    val tappedScale = scale
                    val tappedOffset = offset
                    cropping = true
                    scope.launch {
                        try {
                            val (bytes, mime) =
                                withContext(Dispatchers.Default) {
                                    CropExtractor.extract(
                                        working = bitmap,
                                        shape = shape,
                                        canvasW = cw,
                                        canvasH = ch,
                                        scale = tappedScale,
                                        offsetX = tappedOffset.x,
                                        offsetY = tappedOffset.y,
                                        frameInset = FRAME_INSET,
                                    )
                                }
                            onCrop(bytes, mime)
                        } catch (cancellation: CancellationException) {
                            throw cancellation
                        } catch (t: Throwable) {
                            // Extraction failed (OOM / decode) — re-enable so the
                            // user can retry. Robust error surfacing lands in qr1q.7.
                        } finally {
                            cropping = false
                        }
                    }
                },
                enabled = working != null && !cropping,
            ) {
                Text(text = stringResource(R.string.crop_use_photo))
            }
        }
    }
}

/** Frame inset fraction of the surface — matches the extractor. */
private const val FRAME_INSET = 0.9f

/** Maximum pinch-zoom past cover-fit. */
private const val MAX_SCALE = 6f

private val SCRIM_COLOR = Color.Black.copy(alpha = 0.55f)
