package net.kikin.nubecita.designsystem

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Test

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
        val light =
            NubecitaSemanticColors(
                success = NubecitaPalette.Success40,
                onSuccess = NubecitaPalette.Neutral100,
                successContainer = NubecitaPalette.Success90,
                onSuccessContainer = NubecitaPalette.Success40,
                warning = NubecitaPalette.Warning40,
                onWarning = NubecitaPalette.Neutral100,
            )
        assertEquals(NubecitaPalette.Success40, light.success)
    }

    private fun defaultTokens(): NubecitaTokens =
        NubecitaTokens(
            semanticColors =
                NubecitaSemanticColors(
                    success = NubecitaPalette.Success40,
                    onSuccess = NubecitaPalette.Neutral100,
                    successContainer = NubecitaPalette.Success90,
                    onSuccessContainer = NubecitaPalette.Success40,
                    warning = NubecitaPalette.Warning40,
                    onWarning = NubecitaPalette.Neutral100,
                ),
        )
}
