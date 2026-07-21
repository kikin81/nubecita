package net.kikin.nubecita.feature.videos.impl.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.icon.NubecitaIcon
import net.kikin.nubecita.designsystem.icon.NubecitaIconName

/** One heart spawned by a double-tap, positioned at the touch point. */
internal data class HeartBurst(
    val id: Int,
    val position: Offset,
)

/** Per-frame transform of a burst heart, derived purely from animation progress. */
internal data class BurstTransform(
    val scale: Float,
    val alpha: Float,
    val translationYDp: Float,
    val rotationDegrees: Float,
)

/**
 * Pure transform for a heart at [progress] (0..1): pop in (scale overshoots to
 * 1.2 then settles to 1.0), hold, then fade out while drifting up. Extracted so
 * the motion shape is unit-tested without a running animation.
 *
 * Tilt is deterministic from [id] — organic variety without `Math.random`,
 * which is banned here and non-deterministic for tests.
 */
internal fun heartBurstTransform(
    progress: Float,
    id: Int,
): BurstTransform {
    val p = progress.coerceIn(0f, 1f)
    val scale =
        when {
            p <= 0.2f -> lerp(0f, 1.2f, p / 0.2f)
            p <= 0.35f -> lerp(1.2f, 1.0f, (p - 0.2f) / 0.15f)
            else -> 1.0f
        }
    val alpha =
        when {
            p <= 0.15f -> p / 0.15f
            p <= 0.5f -> 1f
            else -> lerp(1f, 0f, (p - 0.5f) / 0.5f)
        }
    val translationYDp = if (p <= 0.4f) 0f else lerp(0f, -48f, (p - 0.4f) / 0.6f)
    val rotationDegrees = ((id % 5) - 2) * 6f
    return BurstTransform(scale, alpha, translationYDp, rotationDegrees)
}

private fun lerp(
    a: Float,
    b: Float,
    t: Float,
): Float = a + (b - a) * t.coerceIn(0f, 1f)

/** Stateless heart visual for a fixed [transform] — the screenshot seam. */
@Composable
internal fun LikeBurstHeartContent(
    transform: BurstTransform,
    modifier: Modifier = Modifier,
) {
    NubecitaIcon(
        name = NubecitaIconName.Favorite,
        contentDescription = null,
        filled = true,
        tint = Color.White,
        opticalSize = HEART_SIZE,
        modifier =
            modifier.graphicsLayer {
                scaleX = transform.scale
                scaleY = transform.scale
                alpha = transform.alpha
                rotationZ = transform.rotationDegrees
                translationY = transform.translationYDp.dp.toPx()
            },
    )
}

/** Drives a [heart]'s progress 0->1 over [BURST_DURATION_MS], then [onFinish]. */
@Composable
internal fun LikeBurstHeart(
    heart: HeartBurst,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = remember(heart.id) { Animatable(0f) }
    // rememberUpdatedState: the effect is keyed on heart.id so it never restarts,
    // and onFinish is re-created each recomposition of the page. Reading it
    // directly would pin the first lambda (ktlint compose:lambda-param-in-effect).
    val currentOnFinished by rememberUpdatedState(onFinish)
    LaunchedEffect(heart.id) {
        progress.animateTo(1f, tween(durationMillis = BURST_DURATION_MS, easing = LinearEasing))
        currentOnFinished()
    }
    LikeBurstHeartContent(heartBurstTransform(progress.value, heart.id), modifier)
}

internal val HEART_SIZE = 100.dp
private const val BURST_DURATION_MS = 700
