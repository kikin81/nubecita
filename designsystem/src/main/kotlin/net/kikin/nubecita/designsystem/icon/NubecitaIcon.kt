@file:OptIn(ExperimentalTextApi::class)

package net.kikin.nubecita.designsystem.icon

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.kikin.nubecita.designsystem.R

/**
 * Renders a glyph from the Material Symbols Rounded variable font.
 * The composable replaces every `Icon(imageVector = Icons.X.Y, …)`
 * call site in the app — see `nubecita-68g` and the design doc at
 * `docs/superpowers/specs/2026-05-10-material-symbols-rounded-migration-design.md`.
 *
 * # Axes
 *
 * Material Symbols exposes four continuous axes; this API surfaces
 * them as parameters with sensible defaults:
 *
 * - [filled] — FILL axis (0f outlined / 1f filled). V1 ships a
 *   Boolean for the active/inactive distinction; an animated `Float`
 *   overload is a follow-up.
 * - [weight] — wght axis (100..700). Default 400 matches Google
 *   Material Symbols default; lighter for ambient hints, heavier for
 *   primary actions.
 * - [grade] — GRAD axis (-25..200). Default 0; -25 is low-emphasis
 *   on dark surfaces, 200 is high-emphasis to compensate for color
 *   contrast loss.
 * - [opticalSize] — opsz axis (20.dp..48.dp). Default 24.dp matches
 *   the Compose `Icon` default. Per-call overrides let dense lists
 *   shrink and feature CTAs grow.
 *
 * # Baseline alignment
 *
 * Rendering through `Text` (vs `androidx.compose.material3.Icon`'s
 * Painter) subjects the glyph to font metrics: ascent, descent,
 * leading, and Compose's default `includeFontPadding = true`. Three
 * mitigations applied here, per the design doc's gotcha section:
 *
 * 1. `includeFontPadding = false` strips the platform's default
 *    24sp-line-height padding so the glyph fills the text run
 *    without baked-in margins.
 * 2. `lineHeight = fontSize` locks line height to the icon's display
 *    size so descenders don't push the box taller than expected.
 * 3. The `Box(modifier = Modifier.size(opticalSize), contentAlignment
 *    = Alignment.Center)` wrapper centers the glyph regardless of
 *    residual font metrics. Vertical drift is caught by the
 *    `NubecitaIconShowcaseScreenshotTest` baselines.
 *
 * # Accessibility
 *
 * `contentDescription` is the canonical a11y label; pass `null`
 * for purely decorative icons (matches Compose `Icon` semantics).
 * The descendant `Text` uses `clearAndSetSemantics`-equivalent
 * scoping (the codepoint string is not exposed) — TalkBack reads
 * exactly the localized phrase.
 */
@Composable
fun NubecitaIcon(
    name: NubecitaIconName,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
    weight: Int = 400,
    grade: Int = 0,
    opticalSize: Dp = 24.dp,
    tint: Color = LocalContentColor.current,
) {
    val a11yModifier =
        if (contentDescription != null) {
            Modifier.semantics {
                this.contentDescription = contentDescription
                this.role = Role.Image
            }
        } else {
            Modifier
        }
    val fontFamily =
        remember(filled, weight, grade, opticalSize) {
            FontFamily(
                Font(
                    resId = R.font.material_symbols_rounded,
                    variationSettings =
                        FontVariation.Settings(
                            FontVariation.Setting("FILL", if (filled) 1f else 0f),
                            FontVariation.weight(weight),
                            FontVariation.Setting("GRAD", grade.toFloat()),
                            FontVariation.Setting("opsz", opticalSize.value),
                        ),
                ),
            )
        }
    Box(
        modifier = modifier.then(a11yModifier).size(opticalSize),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            modifier = Modifier.clearAndSetSemantics { },
            text = name.codepoint,
            color = tint,
            fontFamily = fontFamily,
            fontSize = opticalSize.value.sp,
            // Locked line height + zero font padding — see the
            // composable's KDoc for the full reasoning.
            style =
                TextStyle(
                    lineHeight = opticalSize.value.sp,
                    platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
        )
    }
}
