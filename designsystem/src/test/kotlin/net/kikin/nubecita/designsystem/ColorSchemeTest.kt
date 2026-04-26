package net.kikin.nubecita.designsystem

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

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
