package net.kikin.nubecita.wear.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnState

// The list's own side padding is 5.2% of the screen. Text needs to sit inside
// the square inscribed in the circle, (1 - 1/sqrt(2)) / 2 = 14.6% per side, to
// clear the edge over the height of the screen. Wear guidance asks for
// percentages, not fixed margins, so these scale with the watch.
private const val LIST_ITEM_EXTRA_INSET = 0.094f
private const val CARD_CONTENT_EXTRA_INSET = 0.04f

// Fraction of the list's height each fade covers, and how far into that band
// the content stays fully hidden: a line that near the top or bottom crosses
// the edge at any side margin.
private const val FADE_BAND = 0.16f
private const val FADE_HIDDEN_STOP = 0.4f

/** Extra side padding for plain text and images placed straight in a list on a round screen. */
@Composable
@ReadOnlyComposable
fun roundTextInset(): Dp = roundInset(LIST_ITEM_EXTRA_INSET)

/** Extra side padding for text inside a list card, on top of the card's own padding. */
@Composable
@ReadOnlyComposable
fun roundCardTextInset(): Dp = roundInset(CARD_CONTENT_EXTRA_INSET)

@Composable
@ReadOnlyComposable
private fun roundInset(fraction: Float): Dp {
    val configuration = LocalConfiguration.current
    return if (configuration.isScreenRound) (configuration.screenWidthDp * fraction).dp else 0.dp
}

/**
 * Fades the list out at the top and bottom of the screen, where no side margin
 * keeps a line inside the circle. Each end fades only while there is more to
 * scroll to, so a list at rest at its start or end shows its first or last item
 * in full.
 */
fun Modifier.roundEdgeFade(state: TransformingLazyColumnState): Modifier =
    graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            // Built once per size, so scrolling allocates nothing per frame.
            val bandHeight = size.height * FADE_BAND
            val bandSize = Size(size.width, bandHeight)
            val bottomTop = size.height - bandHeight
            val topFade = Brush.verticalGradient(0f to Color.Transparent, FADE_HIDDEN_STOP to Color.Transparent, 1f to Color.Black, endY = bandHeight)
            val bottomFade = Brush.verticalGradient(0f to Color.Black, 1f - FADE_HIDDEN_STOP to Color.Transparent, 1f to Color.Transparent, startY = bottomTop, endY = size.height)
            onDrawWithContent {
                drawContent()
                if (state.canScrollBackward) drawRect(brush = topFade, size = bandSize, blendMode = BlendMode.DstIn)
                if (state.canScrollForward) drawRect(brush = bottomFade, topLeft = Offset(0f, bottomTop), size = bandSize, blendMode = BlendMode.DstIn)
            }
        }
