package net.kikin.nubecita.designsystem.component

import androidx.compose.foundation.IndicationNodeFactory
import androidx.compose.foundation.interaction.FocusInteraction
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.node.DelegatableNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * A high-contrast outline drawn around a component while it holds focus, for keyboard and
 * XR controller navigation, where the ripple's 10% focus state layer is too faint to find.
 *
 * Draws on top of the content, in addition to whatever ripple the component already uses:
 * `Modifier.indication(source, ripple()).indication(source, FocusRingIndication(...))`.
 * Only [FocusInteraction] is tracked — a touch press never focuses a clickable, so phones
 * never see the ring.
 *
 * Node-based on purpose: the interaction stream is collected in the node's own coroutine
 * and only invalidates draw, so a feed full of action cells costs no recomposition and no
 * per-cell `collectIsFocusedAsState()` while scrolling.
 */
@Stable
internal class FocusRingIndication(
    private val shape: Shape,
    private val color: Color,
    private val width: Dp = FOCUS_RING_WIDTH,
) : IndicationNodeFactory {
    override fun create(interactionSource: InteractionSource): DelegatableNode = FocusRingNode(interactionSource, shape, color, width)

    override fun equals(other: Any?): Boolean = other is FocusRingIndication && other.shape == shape && other.color == color && other.width == width

    override fun hashCode(): Int = (shape.hashCode() * 31 + color.hashCode()) * 31 + width.hashCode()
}

private class FocusRingNode(
    private val interactionSource: InteractionSource,
    private val shape: Shape,
    private val color: Color,
    private val width: Dp,
) : Modifier.Node(),
    DrawModifierNode {
    private var focusCount = 0

    override fun onAttach() {
        coroutineScope.launch {
            interactionSource.interactions.collect { interaction ->
                val before = focusCount
                when (interaction) {
                    is FocusInteraction.Focus -> focusCount++
                    is FocusInteraction.Unfocus -> focusCount = (focusCount - 1).coerceAtLeast(0)
                }
                if ((before > 0) != (focusCount > 0)) invalidateDraw()
            }
        }
    }

    override fun onDetach() {
        focusCount = 0
    }

    override fun ContentDrawScope.draw() {
        drawContent()
        if (focusCount == 0) return
        val strokePx = width.toPx()
        // Built at the inset size and drawn shifted by half the stroke, so the whole ring
        // stays inside the component: a clipped parent (the pill's CircleShape) would
        // otherwise cut its outer half off.
        val inset = strokePx / 2f
        val ringSize = Size((size.width - strokePx).coerceAtLeast(0f), (size.height - strokePx).coerceAtLeast(0f))
        val outline = shape.createOutline(ringSize, layoutDirection, this)
        translate(left = inset, top = inset) {
            drawOutline(outline, color, style = Stroke(width = strokePx))
        }
    }
}

/** 2dp: visible from across a room on XR without crowding a 26dp pill on a tablet. */
private val FOCUS_RING_WIDTH = 2.dp
