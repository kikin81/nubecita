package net.kikin.nubecita.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NubecitaThemeTest {
    @Test
    fun tokenDefaults_spacingS4_is16Dp() {
        val tokens = defaultTokens()
        assertEquals(16.dp, tokens.spacing.s4)
    }

    @Test
    fun tokenDefaults_elevationE2_is3Dp() {
        val tokens = defaultTokens()
        assertEquals(3.dp, tokens.elevation.e2)
    }

    @Test
    fun tokenDefaults_extendedShape_pillIsCircleShape() {
        val tokens = defaultTokens()
        assertSame(CircleShape, tokens.extendedShape.pill)
    }

    @Test
    fun motion_standardAndReduced_areDistinct() {
        assertNotEquals(NubecitaMotion.Standard, NubecitaMotion.Reduced)
    }

    @Test
    fun semanticColors_lightSuccess_isSuccess40() {
        val light = sampleSemanticColors()
        assertEquals(NubecitaPalette.Success40, light.success)
    }

    @Test
    fun semanticColors_likeAccent_isOpaqueColor() {
        // The like-accent token is a magenta/pink reserved for the like-
        // family social action. It must be opaque (alpha == 1f) so it
        // tints the heart glyph without alpha bleed onto the row backdrop.
        val light =
            sampleSemanticColors(
                likeAccent = Color(0xFFEC4899),
                repostAccent = Color(0xFF20BC70),
            )
        val dark =
            sampleSemanticColors(
                likeAccent = Color(0xFFF472B6),
                repostAccent = Color(0xFF4ADE80),
            )
        assertEquals(1f, light.likeAccent.alpha)
        assertEquals(1f, dark.likeAccent.alpha)
        // Light and dark variants are distinct so neither washes out
        // against its scheme's surface.
        assertNotEquals(light.likeAccent, dark.likeAccent)
    }

    @Test
    fun semanticColors_repostAccent_isOpaqueColor() {
        val light = sampleSemanticColors(repostAccent = Color(0xFF20BC70))
        val dark = sampleSemanticColors(repostAccent = Color(0xFF4ADE80))
        assertEquals(1f, light.repostAccent.alpha)
        assertEquals(1f, dark.repostAccent.alpha)
        assertNotEquals(light.repostAccent, dark.repostAccent)
    }

    @Test
    fun semanticColors_likeAndRepost_areDistinct() {
        // Like and repost tints must be visually distinguishable on the
        // same row — they share a stack layout and a user must tell them
        // apart at a glance. Asserting at least one channel diverges.
        val colors = sampleSemanticColors()
        assertTrue(
            colors.likeAccent != colors.repostAccent,
            "likeAccent and repostAccent must be distinct colors",
        )
    }

    private fun defaultTokens(): NubecitaTokens =
        NubecitaTokens(
            semanticColors = sampleSemanticColors(),
        )

    private fun sampleSemanticColors(
        likeAccent: Color = Color(0xFFEC4899),
        repostAccent: Color = Color(0xFF20BC70),
    ): NubecitaSemanticColors =
        NubecitaSemanticColors(
            success = NubecitaPalette.Success40,
            onSuccess = NubecitaPalette.Neutral100,
            successContainer = NubecitaPalette.Success90,
            onSuccessContainer = NubecitaPalette.Success40,
            warning = NubecitaPalette.Warning40,
            onWarning = NubecitaPalette.Neutral100,
            videoOverlayScrim = NubecitaPalette.Neutral0.copy(alpha = 0.8f),
            videoOverlayScrimSubtle = NubecitaPalette.Neutral0.copy(alpha = 0.4f),
            onVideoOverlay = NubecitaPalette.Neutral100,
            likeAccent = likeAccent,
            repostAccent = repostAccent,
        )
}
