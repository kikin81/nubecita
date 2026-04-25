package net.kikin.nubecita.designsystem.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import net.kikin.nubecita.designsystem.NubecitaTheme

/**
 * Animated loading-state placeholder modifier — paints a horizontal
 * linear-gradient brush that translates across the receiver's bounds
 * continuously, cycling every [durationMillis].
 *
 * Reads `MaterialTheme.colorScheme` for its three gradient stops
 * (`surfaceContainerHighest` → `surfaceContainerHigh` →
 * `surfaceContainerHighest`), so light / dark / dynamic-color theme
 * switches update visible shimmers automatically without consumer wiring.
 *
 * **Order matters:** apply this modifier AFTER any `clip()` so the brush
 * is clipped to the shape, and inside a sized composable (Box/Row/Column
 * with `.size()` / `.fillMaxWidth().height()` / etc.) — the brush paints
 * the full bounds, so an unbounded receiver paints nothing visible.
 *
 * Replaces the role of the deprecated Accompanist `placeholder-material`
 * library. Implementation uses only stable Compose UI primitives
 * (`rememberInfiniteTransition`, `drawWithCache`, `Brush.linearGradient`)
 * so it has no deprecation tail.
 *
 * Example:
 * ```kotlin
 * Box(Modifier.size(40.dp).clip(CircleShape).shimmer())
 * Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(16.dp)).shimmer())
 * ```
 */
@Composable
fun Modifier.shimmer(durationMillis: Int = 1500): Modifier {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec =
            infiniteRepeatable(
                animation = tween(durationMillis = durationMillis, easing = LinearEasing),
                repeatMode = RepeatMode.Restart,
            ),
        label = "shimmer-translate",
    )
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val highlight = MaterialTheme.colorScheme.surfaceContainerHigh
    return this.drawWithCache {
        // Brush spans 2x the width so the highlight fully traverses the receiver
        // before the cycle restarts (the translate goes 0 -> 1, mapped to -size .. +size).
        val brushWidth = size.width * 2f
        val startX = -size.width + (brushWidth * translate)
        val brush =
            Brush.linearGradient(
                colors = listOf(base, highlight, base),
                start = Offset(x = startX, y = 0f),
                end = Offset(x = startX + size.width, y = 0f),
            )
        onDrawBehind {
            drawRect(brush = brush)
        }
    }
}

@Preview(name = "Shimmer modifier — circle + rectangle", showBackground = true)
@Composable
private fun ShimmerModifierPreview() {
    NubecitaTheme {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement =
                androidx.compose.foundation.layout.Arrangement
                    .spacedBy(16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(MaterialTheme.colorScheme.surface)
                        .then(Modifier)
                        .shimmer(),
            )
            Box(
                modifier =
                    Modifier
                        .width(120.dp)
                        .height(40.dp)
                        .shimmer(),
            )
        }
    }
}
