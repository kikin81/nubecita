package net.kikin.nubecita.designsystem

import org.junit.Assert.assertEquals
import org.junit.Test

class ColorSchemeTest {
    @Test
    fun lightPrimaryMatchesSky50() {
        val scheme = nubecitaLightColorScheme()
        assertEquals(NubecitaPalette.Sky50, scheme.primary)
    }

    @Test
    fun darkPrimaryMatchesSky80() {
        val scheme = nubecitaDarkColorScheme()
        assertEquals(NubecitaPalette.Sky80, scheme.primary)
    }
}
