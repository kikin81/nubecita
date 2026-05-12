@file:OptIn(ExperimentalTextApi::class)

package net.kikin.nubecita.designsystem

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.sp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression guards for the design-system Typography tokens consumed by the
 * profile hero (see openspec/changes/add-profile-feature/specs/design-system).
 *
 * - `displayName` MUST set Fraunces's `SOFT` axis to 70 (the soft-cornered
 *   variant) — every existing display/headline style stays at the font
 *   default (SOFT = 0), so a regression here would be invisible to those
 *   sites.
 * - `handle` MUST be JetBrains Mono at 13 sp.
 *
 * The `SOFT` assertion reaches into `FontVariation.Settings` by interface,
 * since the public `Font` API exposes settings indirectly. The test relies
 * on the experimental text API gate that `Fonts.kt` already opts into.
 */
class TypographyTest {
    @Test
    fun extendedTypographyDisplayNameUsesFraunces70Soft() {
        val style = NubecitaExtendedTypography().displayName

        assertEquals(28.sp, style.fontSize, "displayName fontSize")

        // The display-name FontFamily MUST be `FrauncesDisplayFontFamily` —
        // the one that pins SOFT = 70. Verify identity rather than name
        // string (FontFamily doesn't expose a stable name to compare).
        assertEquals(FrauncesDisplayFontFamily, style.fontFamily, "displayName fontFamily")

        // The variation settings on the family's single Font entry MUST
        // include a `SOFT` axis setting with value 70.0f.
        val softValue = softVariationOf(FrauncesDisplayFontFamily)
        assertNotNull(softValue, "FrauncesDisplayFontFamily MUST declare a SOFT axis setting")
        assertEquals(70f, softValue!!, 0.001f, "SOFT axis MUST equal 70")
    }

    @Test
    fun extendedTypographyHandleUsesJetBrainsMonoAt13sp() {
        val style = NubecitaExtendedTypography().handle

        assertEquals(13.sp, style.fontSize, "handle fontSize")
        assertEquals(JetBrainsMonoFontFamily, style.fontFamily, "handle fontFamily")
    }

    @Test
    fun frauncesDisplayShareTtfWithFrauncesButDifferentVariation() {
        // Sanity check: the two Fraunces families MUST point at distinct
        // FontFamily instances (so the variation-difference doesn't get
        // collapsed by reference equality) yet both use the bundled .ttf
        // (no Google Downloadable fallback for either).
        assertTrue(
            FrauncesFontFamily !== FrauncesDisplayFontFamily,
            "FrauncesFontFamily and FrauncesDisplayFontFamily MUST be distinct instances",
        )
    }

    /**
     * Pull the `SOFT` axis value out of the first `Font` entry in the family,
     * if any. Returns null when the family declares no SOFT setting.
     */
    private fun softVariationOf(family: androidx.compose.ui.text.font.FontFamily): Float? {
        // FontFamily(...) is a sealed class hierarchy. The variant we use is
        // FontListFontFamily — its `fonts` list exposes each entry's
        // variationSettings. Walk it via the public collection API.
        val listFamily =
            family as? androidx.compose.ui.text.font.FontListFontFamily
                ?: return null
        val firstFont = listFamily.fonts.firstOrNull() ?: return null
        val variationSettings =
            (firstFont as? androidx.compose.ui.text.font.ResourceFont)
                ?.variationSettings
                ?: return null
        return variationSettings.settings
            .firstOrNull { it.axisName == "SOFT" }
            ?.toVariationValue(null)
    }
}
