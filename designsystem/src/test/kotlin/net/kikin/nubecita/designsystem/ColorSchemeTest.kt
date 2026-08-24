package net.kikin.nubecita.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Contrast guarantees for the six brand [ColorScheme]s.
 *
 * These assert the *property* — every reachable foreground/background pair meets
 * its WCAG 2.1 floor — rather than pinning hex literals. A hex-pinning test
 * records what the palette **is**, not whether it is **correct**: the previous
 * version of this file asserted `primary == Sky50` and passed happily for the
 * entire lifetime of a real accessibility defect, in which the light accents sat
 * at tonal stop 50 and four pairs fell under 4.5:1.
 *
 * Contract: `openspec/specs/design-system` — "Accent roles are fixed at the
 * Material 3 tonal mapping with a contrast floor".
 */
class ColorSchemeTest {
    // ---- WCAG 2.1 contrast ------------------------------------------------

    /**
     * Relative luminance comes from Compose's own [luminance], which applies the
     * color space EOTF rather than assuming a fixed gamma — the same definition
     * WCAG uses. Hand-rolling the sRGB transfer function here would risk
     * disagreeing with the values the platform actually renders.
     */
    private fun contrastRatio(
        a: Color,
        b: Color,
    ): Double {
        val la = a.luminance().toDouble()
        val lb = b.luminance().toDouble()
        return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
    }

    private data class Scheme(
        val name: String,
        val scheme: ColorScheme,
    )

    private val allSchemes =
        listOf(
            Scheme("light", nubecitaLightColorScheme()),
            Scheme("light-medium-contrast", nubecitaLightMediumContrastColorScheme()),
            Scheme("light-high-contrast", nubecitaLightHighContrastColorScheme()),
            Scheme("dark", nubecitaDarkColorScheme()),
            Scheme("dark-medium-contrast", nubecitaDarkMediumContrastColorScheme()),
            Scheme("dark-high-contrast", nubecitaDarkHighContrastColorScheme()),
        )

    private data class Pairing(
        val label: String,
        val background: Color,
        val foreground: Color,
    )

    /**
     * Collects every violation across all six schemes rather than failing on the
     * first, so one run reports the complete picture. A partial failure list
     * invites fixing one pair at a time and re-running.
     */
    private fun assertNoViolations(
        minimum: Double,
        pairsOf: (ColorScheme) -> List<Pairing>,
    ) {
        val violations =
            allSchemes.flatMap { (schemeName, scheme) ->
                pairsOf(scheme).mapNotNull { (label, background, foreground) ->
                    val ratio = contrastRatio(background, foreground)
                    if (ratio < minimum) {
                        "  %-22s %-38s %.2f:1  (needs %.1f:1)".format(schemeName, label, ratio, minimum)
                    } else {
                        null
                    }
                }
            }

        assertTrue(violations.isEmpty()) {
            buildString {
                append("${violations.size} pair(s) below the WCAG 2.1 %.1f:1 floor:\n".format(minimum))
                violations.forEach { appendLine(it) }
            }
        }
    }

    // ---- Requirement: every on/container pair meets AA ---------------------

    @Test
    fun everyOnContainerPairMeetsAa() {
        assertNoViolations(minimum = 4.5) { s ->
            listOf(
                Pairing("primary / onPrimary", s.primary, s.onPrimary),
                Pairing("primaryContainer / onPrimaryContainer", s.primaryContainer, s.onPrimaryContainer),
                Pairing("secondary / onSecondary", s.secondary, s.onSecondary),
                Pairing("secondaryContainer / onSecondaryContainer", s.secondaryContainer, s.onSecondaryContainer),
                Pairing("tertiary / onTertiary", s.tertiary, s.onTertiary),
                Pairing("tertiaryContainer / onTertiaryContainer", s.tertiaryContainer, s.onTertiaryContainer),
                Pairing("error / onError", s.error, s.onError),
                Pairing("errorContainer / onErrorContainer", s.errorContainer, s.onErrorContainer),
                Pairing("surface / onSurface", s.surface, s.onSurface),
                Pairing("surface / onSurfaceVariant", s.surface, s.onSurfaceVariant),
                Pairing("surfaceContainerLow / onSurface", s.surfaceContainerLow, s.onSurface),
                Pairing("surfaceContainer / onSurface", s.surfaceContainer, s.onSurface),
                Pairing("surfaceContainerHigh / onSurface", s.surfaceContainerHigh, s.onSurface),
                Pairing("surfaceContainerHighest / onSurface", s.surfaceContainerHighest, s.onSurface),
                Pairing("inverseSurface / inverseOnSurface", s.inverseSurface, s.inverseOnSurface),
                Pairing("inverseSurface / inversePrimary", s.inverseSurface, s.inversePrimary),
            )
        }
    }

    // ---- Requirement: accents are legible as foreground on the surface -----

    /**
     * Separate from the pairing test above because these are the pairs an audit
     * most easily omits: an accent used as body text or an icon on the screen
     * canvas, rather than as a fill behind its own `on*` role. Two of the four
     * defects that motivated this contract lived here.
     */
    @Test
    fun accentsAreLegibleAsForegroundOnSurface() {
        assertNoViolations(minimum = 4.5) { s ->
            listOf(
                Pairing("surface / primary", s.surface, s.primary),
                Pairing("surface / secondary", s.surface, s.secondary),
                Pairing("surface / tertiary", s.surface, s.tertiary),
            )
        }
    }

    // ---- Requirement: outline meets the non-text threshold -----------------

    @Test
    fun outlineMeetsNonTextThreshold() {
        assertNoViolations(minimum = 3.0) { s ->
            listOf(Pairing("surface / outline", s.surface, s.outline))
        }
    }

    // ---- Requirement: fixed accent roles are brand-derived -----------------

    /**
     * `lightColorScheme()` / `darkColorScheme()` default the twelve fixed accent
     * roles to `ColorLightTokens` / `ColorDarkTokens` — the stock Material
     * baseline palette. They were previously left unassigned, so
     * `colorScheme.primaryFixed` resolved to a baseline purple despite the
     * requirement that no Material default remain reachable.
     *
     * No Material 3 component reads a fixed role today, so nothing rendered
     * wrongly; this guards the latent trap.
     */
    @Test
    fun fixedAccentRolesAreBrandDerived() {
        val expected =
            listOf(
                "primaryFixed" to NubecitaPalette.Sky90,
                "primaryFixedDim" to NubecitaPalette.Sky80,
                "onPrimaryFixed" to NubecitaPalette.Sky10,
                "onPrimaryFixedVariant" to NubecitaPalette.Sky30,
                "secondaryFixed" to NubecitaPalette.Lagoon90,
                "secondaryFixedDim" to NubecitaPalette.Lagoon80,
                "onSecondaryFixed" to NubecitaPalette.Lagoon10,
                "onSecondaryFixedVariant" to NubecitaPalette.Lagoon30,
                "tertiaryFixed" to NubecitaPalette.Orchid90,
                "tertiaryFixedDim" to NubecitaPalette.Orchid80,
                "onTertiaryFixed" to NubecitaPalette.Orchid10,
                "onTertiaryFixedVariant" to NubecitaPalette.Orchid30,
            )

        val mismatches =
            allSchemes.flatMap { (schemeName, s) ->
                val actual =
                    mapOf(
                        "primaryFixed" to s.primaryFixed,
                        "primaryFixedDim" to s.primaryFixedDim,
                        "onPrimaryFixed" to s.onPrimaryFixed,
                        "onPrimaryFixedVariant" to s.onPrimaryFixedVariant,
                        "secondaryFixed" to s.secondaryFixed,
                        "secondaryFixedDim" to s.secondaryFixedDim,
                        "onSecondaryFixed" to s.onSecondaryFixed,
                        "onSecondaryFixedVariant" to s.onSecondaryFixedVariant,
                        "tertiaryFixed" to s.tertiaryFixed,
                        "tertiaryFixedDim" to s.tertiaryFixedDim,
                        "onTertiaryFixed" to s.onTertiaryFixed,
                        "onTertiaryFixedVariant" to s.onTertiaryFixedVariant,
                    )
                expected.mapNotNull { (role, want) ->
                    if (actual.getValue(role) != want) "  $schemeName $role = ${actual.getValue(role)}, expected $want" else null
                }
            }

        assertTrue(mismatches.isEmpty()) {
            "Fixed accent role(s) not sourced from the brand ramps:\n" + mismatches.joinToString("\n")
        }
    }

    // ---- Requirement: semantic accents survive the deeper dark surface -----

    /**
     * The dark surface moved from HCT tone 6 to tone 3. The semantic accents are
     * independent app constants rather than brand-ramp stops, so a deeper canvas
     * could in principle strand them — this pins that it does not.
     */
    @Test
    fun semanticAccentsMeetContrastOnDarkSurface() {
        val surface = nubecitaDarkColorScheme().surface
        val semantic = nubecitaSemanticColors(darkTheme = true)
        val pairs =
            listOf(
                "likeAccent" to semantic.likeAccent,
                "repostAccent" to semantic.repostAccent,
                "supporterAccent" to semantic.supporterAccent,
                "success" to semantic.success,
                "warning" to semantic.warning,
            )

        val violations =
            pairs.mapNotNull { (name, color) ->
                val ratio = contrastRatio(surface, color)
                if (ratio < 4.5) "  %-16s %.2f:1 on dark surface (needs 4.5:1)".format(name, ratio) else null
            }

        assertTrue(violations.isEmpty()) {
            "Semantic accent(s) below AA on the deepened dark surface:\n" + violations.joinToString("\n")
        }
    }

    // ---- Spec-pinned values ------------------------------------------------

    /**
     * The two values the spec names explicitly. Everything else is asserted as a
     * property; these are pinned because the requirement states the exact hex.
     */
    @Test
    fun lightPrimaryIsSkyTone40() {
        assertEquals(NubecitaPalette.Sky40, nubecitaLightColorScheme().primary)
    }

    @Test
    fun darkPrimaryIsSkyTone80() {
        assertEquals(NubecitaPalette.Sky80, nubecitaDarkColorScheme().primary)
    }
}
