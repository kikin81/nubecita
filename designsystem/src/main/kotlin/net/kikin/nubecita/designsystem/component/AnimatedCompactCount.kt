package net.kikin.nubecita.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import net.kikin.nubecita.core.common.text.rememberCompactCount

/**
 * Locale-aware count display that animates the digit transition for
 * a single ±1 user-initiated delta and snaps for everything else.
 *
 * The "careful rule" from `nubecita-4dg`:
 *
 * 1. Animate only when [animateUserDelta] is true — i.e. the host
 *    has signalled that the latest count change came from a tap on
 *    this card in this session.
 * 2. AND the absolute delta from the previous frame is exactly 1
 *    (the digit-roll's natural envelope).
 * 3. AND both the previous and the new counts produce literal
 *    (non-suffixed) representations — under 1000 in any locale that
 *    starts compacting at the thousands threshold. This carves out
 *    the format-flip cases: 999 → 1K snaps, 1.1K → 1.2K snaps,
 *    1234 → 1235 has no count change to animate at all.
 *
 * When all three conditions hold, the new digits slide in vertically
 * (from below for an increment, from above for a decrement) while
 * the old digits slide out the opposite direction. Otherwise the
 * text snaps with no transition.
 *
 * The decision is taken from `prior` to `target` once per [count]
 * change. The host doesn't have to clear [animateUserDelta] between
 * frames — a stale `true` is harmless because the rule's clauses (2)
 * and (3) prevent re-animation when nothing visibly changes.
 */
@Composable
internal fun AnimatedCompactCount(
    count: Long,
    animateUserDelta: Boolean,
    color: Color,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
) {
    val formatted = rememberCompactCount(count)
    // Track the previous frame's count. `SideEffect` writes after the
    // composition that read the old value, so this-frame's read sees
    // last-frame's value and the AnimatedContent transitionSpec gets the
    // correct decision.
    var priorCount by remember { mutableLongStateOf(count) }
    val delta = count - priorCount
    val shouldAnimate =
        animateUserDelta &&
            (delta == 1L || delta == -1L) &&
            isLiteral(priorCount) &&
            isLiteral(count)
    SideEffect { priorCount = count }

    AnimatedContent(
        targetState = formatted,
        transitionSpec = {
            if (shouldAnimate) {
                // increment → enter from below (positive Y), exit upward;
                // decrement → mirror.
                val direction = if (delta > 0L) 1 else -1
                (
                    slideInVertically(animationSpec = tween(DURATION_MS)) { fullHeight ->
                        fullHeight * direction
                    } + fadeIn(animationSpec = tween(DURATION_MS))
                ) togetherWith (
                    slideOutVertically(animationSpec = tween(DURATION_MS)) { fullHeight ->
                        fullHeight * -direction
                    } + fadeOut(animationSpec = tween(DURATION_MS))
                )
            } else {
                EnterTransition.None togetherWith ExitTransition.None
            }
        },
        label = "AnimatedCompactCount",
        modifier = modifier,
    ) { text ->
        Text(text = text, style = style, color = color)
    }
}

/**
 * The compact format flips to a suffixed form (K, M, mil, Lakh, …) at
 * locale-specific thresholds. en-US and most Latin locales start at
 * 1000; Hindi starts at 100,000 (1 Lakh). Using 1000 as the literal
 * threshold is right for the common case and conservatively SNAP-s
 * for locales whose literal range extends further — over-snap is
 * fine; an erroneous animation that crosses a format boundary is not.
 */
private fun isLiteral(count: Long): Boolean = count in 0 until 1000

private const val DURATION_MS = 220
