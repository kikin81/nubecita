package net.kikin.nubecita.core.common.spatial

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SpatialSessionEnvironmentTest {
    @Test
    fun `default spatial environment is a non-XR device`() {
        val environment = DefaultSpatialSessionEnvironment()

        assertFalse(environment.isXr)
        assertFalse(environment.isSpatialUiEnabled)
    }

    @Test
    fun `spatial environment preserves configured capabilities`() {
        val environment = DefaultSpatialSessionEnvironment(isXr = true, isSpatialUiEnabled = true)

        assertTrue(environment.isXr)
        assertTrue(environment.isSpatialUiEnabled)
    }
}
