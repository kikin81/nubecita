// ============================================================
// Nubecita — Spacing, motion + extra design tokens
// Access via LocalNubecita.current inside any composable.
// ============================================================
package app.nubecita.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class NubecitaSpacing(
    val s0:  Dp = 0.dp,
    val s1:  Dp = 4.dp,
    val s2:  Dp = 8.dp,
    val s3:  Dp = 12.dp,
    val s4:  Dp = 16.dp,
    val s5:  Dp = 20.dp,
    val s6:  Dp = 24.dp,
    val s7:  Dp = 28.dp,
    val s8:  Dp = 32.dp,
    val s10: Dp = 40.dp,
    val s12: Dp = 48.dp,
    val s16: Dp = 64.dp,
)

@Immutable
data class NubecitaMotion(
    // M3 Expressive easings
    val standard           : CubicBezierEasing = CubicBezierEasing(0.2f, 0f, 0f, 1f),
    val emphasizedDecel    : CubicBezierEasing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f),
    val emphasizedAccel    : CubicBezierEasing = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f),
)

// Compose's spring() is the closest equivalent to the CSS linear() spring.
// Use these as drop-in replacements:
//   spring(stiffness = NubecitaMotion.SpringFast.stiffness, dampingRatio = NubecitaMotion.SpringFast.damping)
object NubecitaSpring {
    val Fast   = SpringSpec(stiffness = Spring.StiffnessMediumLow, damping = Spring.DampingRatioLowBouncy)
    val Slow   = SpringSpec(stiffness = Spring.StiffnessLow,        damping = Spring.DampingRatioMediumBouncy)
    val Bouncy = SpringSpec(stiffness = Spring.StiffnessMediumLow, damping = Spring.DampingRatioHighBouncy)

    data class SpringSpec(val stiffness: Float, val damping: Float)
}

@Immutable
data class NubecitaTokens(
    val spacing: NubecitaSpacing = NubecitaSpacing(),
    val motion : NubecitaMotion  = NubecitaMotion(),
)

val LocalNubecita = staticCompositionLocalOf { NubecitaTokens() }
