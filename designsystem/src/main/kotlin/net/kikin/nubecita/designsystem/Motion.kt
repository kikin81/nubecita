package net.kikin.nubecita.designsystem

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Immutable

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Immutable
internal class NubecitaMotionScheme(
    private val isReduced: Boolean = false,
) : MotionScheme {
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> =
        if (isReduced) {
            tween(durationMillis = 150, easing = LinearEasing)
        } else {
            spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
        }

    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> =
        if (isReduced) {
            tween(durationMillis = 100, easing = LinearEasing)
        } else {
            spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow)
        }

    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> =
        if (isReduced) {
            tween(durationMillis = 250, easing = LinearEasing)
        } else {
            spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow)
        }

    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> =
        if (isReduced) {
            tween(durationMillis = 150, easing = LinearEasing)
        } else {
            tween(durationMillis = 300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f))
        }

    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = defaultEffectsSpec()

    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = defaultEffectsSpec()
}

// Keeping this for ergonomic access via MaterialTheme.motion if needed,
// though MaterialExpressiveTheme will use the MotionScheme above.
data class NubecitaMotion(
    val defaultSpatial: FiniteAnimationSpec<Float>,
    val slowSpatial: FiniteAnimationSpec<Float>,
    val bouncy: FiniteAnimationSpec<Float>,
    val defaultEffects: FiniteAnimationSpec<Float>,
    val defaultEmphasized: FiniteAnimationSpec<Float>,
) {
    companion object {
        val Standard =
            NubecitaMotion(
                defaultSpatial = spring(dampingRatio = 0.65f, stiffness = Spring.StiffnessMediumLow),
                slowSpatial = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessLow),
                bouncy =
                    keyframes {
                        durationMillis = 600
                        0f at 0
                        0.25f at 30
                        0.63f at 60
                        1.06f at 100
                        1.20f at 130
                        1.25f at 145
                        1.22f at 165
                        1.08f at 205
                        1.00f at 250
                        0.97f at 280
                        0.98f at 340
                        1f at 600
                    },
                defaultEffects = tween(durationMillis = 300, easing = CubicBezierEasing(0.2f, 0f, 0f, 1f)),
                defaultEmphasized = tween(durationMillis = 500, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)),
            )

        val Reduced =
            NubecitaMotion(
                defaultSpatial = tween(durationMillis = 150, easing = LinearEasing),
                slowSpatial = tween(durationMillis = 250, easing = LinearEasing),
                bouncy = tween(durationMillis = 150, easing = LinearEasing),
                defaultEffects = tween(durationMillis = 150, easing = LinearEasing),
                defaultEmphasized = tween(durationMillis = 250, easing = LinearEasing),
            )
    }
}
