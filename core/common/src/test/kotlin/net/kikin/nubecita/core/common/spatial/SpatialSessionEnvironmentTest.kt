package net.kikin.nubecita.core.common.spatial

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpatialSessionEnvironmentTest {
    @Test
    fun `default spatial environment has all capabilities disabled`() {
        val environment = DefaultSpatialSessionEnvironment()

        assertFalse(environment.isSpatialUiEnabled)
        assertFalse(environment.isContent3dEnabled)
        assertFalse(environment.isAppEnvironmentEnabled)
        assertFalse(environment.isPassthroughControlEnabled)
        assertFalse(environment.isSpatialAudioEnabled)
    }

    @Test
    fun `spatial environment preserves configured capabilities`() {
        val environment =
            DefaultSpatialSessionEnvironment(
                isSpatialUiEnabled = true,
                isContent3dEnabled = false,
                isAppEnvironmentEnabled = true,
                isPassthroughControlEnabled = true,
                isSpatialAudioEnabled = true,
            )

        assertTrue(environment.isSpatialUiEnabled)
        assertFalse(environment.isContent3dEnabled)
        assertTrue(environment.isAppEnvironmentEnabled)
        assertTrue(environment.isPassthroughControlEnabled)
        assertTrue(environment.isSpatialAudioEnabled)
    }
}
