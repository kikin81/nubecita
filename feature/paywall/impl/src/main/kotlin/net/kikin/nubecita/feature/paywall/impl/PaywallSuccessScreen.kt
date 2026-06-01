package net.kikin.nubecita.feature.paywall.impl

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kikin.nubecita.designsystem.NubecitaMotion
import net.kikin.nubecita.designsystem.component.NubecitaPrimaryButton
import net.kikin.nubecita.designsystem.motion
import net.kikin.nubecita.designsystem.semanticColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val CONFETTI_DURATION_MS = 1200
private const val CONFETTI_COUNT = 120

// Fixed seed → the burst is reproducible, so the pinned-frame screenshot
// baseline is stable (a freshly-seeded burst each run would be flaky).
private const val CONFETTI_SEED = 0x5E_ED

// Confetti (and the centered emoji) originate from ~42% down the screen.
private const val ORIGIN_Y_FRACTION = 0.42f

/**
 * Post-purchase "thank you" screen (nubecita-ykpc), reached only after a
 * **fresh** purchase via `PaywallSuccessRoute` (which replaced the paywall on
 * the back stack — see `PaywallNavigationModule`). Stateless of any ViewModel:
 * a celebratory 🎉 + a one-shot Compose confetti burst + a subtle haptic, and a
 * Continue button that pops back to the surface the user came from.
 *
 * Plays exactly once per route instance and is glitch-free across config
 * changes: the haptic is guarded by a `rememberSaveable` flag, and the confetti
 * `Animatable` is **initialized from** that flag so a rotation lands on the
 * completed (invisible) state rather than freezing the burst at the origin.
 * Reduce-motion short-circuits the whole thing to a static 🎉 (no animation,
 * no haptic).
 */
@Composable
internal fun PaywallSuccessScreen(
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val reduceMotion = MaterialTheme.motion == NubecitaMotion.Reduced
    val haptics = LocalHapticFeedback.current

    // Guards the haptic (fires exactly once) AND seeds the Animatable so a
    // config change doesn't replay or freeze the burst (see KDoc).
    var hasPlayed by rememberSaveable { mutableStateOf(false) }
    val progress = remember { Animatable(if (hasPlayed || reduceMotion) 1f else 0f) }

    LaunchedEffect(Unit) {
        if (!hasPlayed && !reduceMotion) {
            hasPlayed = true
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            progress.animateTo(targetValue = 1f, animationSpec = tween(CONFETTI_DURATION_MS))
        }
    }

    PaywallSuccessContent(
        // Deferred read: progress.value is read inside the Canvas draw lambda
        // (PaywallSuccessContent → ConfettiBurst), so each frame invalidates the
        // draw phase only — never recomposes this screen.
        progressProvider = { progress.value },
        confettiEnabled = !reduceMotion,
        onContinue = onContinue,
        modifier = modifier,
    )
}

/**
 * Stateless body — takes [progressProvider] (read in the draw phase) and
 * [confettiEnabled] so screenshot tests can pin a fixed frame and exercise the
 * reduce-motion (no-confetti) variant directly.
 */
@Composable
internal fun PaywallSuccessContent(
    progressProvider: () -> Float,
    confettiEnabled: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = confettiPalette()
    val particles = remember(palette) { confettiParticles(CONFETTI_COUNT, palette) }

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.surface,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            // Full-bleed burst behind the content (intentionally ignores insets).
            if (confettiEnabled) {
                ConfettiBurst(
                    particles = particles,
                    progressProvider = progressProvider,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Decorative — clear semantics so TalkBack skips the emoji
                // ("party popper") and lands on the headline/subline instead.
                Text(text = "🎉", fontSize = 72.sp, modifier = Modifier.clearAndSetSemantics {})
                Text(
                    text = stringResource(R.string.paywall_success_headline),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
                Text(
                    text = stringResource(R.string.paywall_success_subline),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            NubecitaPrimaryButton(
                onClick = onContinue,
                text = stringResource(R.string.paywall_success_continue),
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(innerPadding)
                        .consumeWindowInsets(innerPadding)
                        .padding(horizontal = 24.dp, vertical = 24.dp)
                        .fillMaxWidth()
                        .widthIn(max = 480.dp),
            )
        }
    }
}

/** A single confetti piece. Launch params are fixed; only animation `progress` changes. */
private data class ConfettiParticle(
    val angleRad: Float,
    /** Launch distance at progress=1, as a fraction of the canvas height. */
    val velocity: Float,
    val color: Color,
    val radiusDp: Float,
)

/**
 * Generate [count] particles deterministically (fixed [CONFETTI_SEED]) so the
 * burst is identical every run — required for a stable screenshot baseline.
 * Pure: no `MaterialTheme` / time / ambient randomness.
 */
private fun confettiParticles(
    count: Int,
    palette: List<Color>,
): List<ConfettiParticle> {
    val rng = Random(CONFETTI_SEED)
    return List(count) {
        ConfettiParticle(
            angleRad = (rng.nextFloat() * 2f * Math.PI.toFloat()),
            velocity = 0.35f + rng.nextFloat() * 0.55f,
            color = palette[rng.nextInt(palette.size)],
            radiusDp = 3f + rng.nextFloat() * 4f,
        )
    }
}

/**
 * One-shot radial confetti burst from the upper-centre. Reads [progressProvider]
 * **inside the draw lambda** so invalidation stays in the draw phase. Each
 * particle flies out along its angle and is pulled down by gravity, fading as
 * the burst completes; at progress=1 everything is fully transparent.
 */
@Composable
private fun ConfettiBurst(
    particles: List<ConfettiParticle>,
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val t = progressProvider().coerceIn(0f, 1f)
        val origin = Offset(x = size.width / 2f, y = size.height * ORIGIN_Y_FRACTION)
        val alpha = (1f - t).coerceIn(0f, 1f)
        if (alpha <= 0f) return@Canvas
        val gravity = 1.3f * size.height
        particles.forEach { particle ->
            val distance = particle.velocity * size.height * t
            val dx = cos(particle.angleRad) * distance
            val dy = sin(particle.angleRad) * distance + 0.5f * gravity * t * t
            drawCircle(
                color = particle.color,
                radius = particle.radiusDp.dp.toPx(),
                center = origin + Offset(dx, dy),
                alpha = alpha,
            )
        }
    }
}

/** Festive brand-palette mix for the confetti. */
@Composable
private fun confettiPalette(): List<Color> =
    listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.secondary,
        MaterialTheme.colorScheme.tertiary,
        MaterialTheme.semanticColors.likeAccent,
        MaterialTheme.semanticColors.repostAccent,
        MaterialTheme.semanticColors.supporterAccent,
    )
