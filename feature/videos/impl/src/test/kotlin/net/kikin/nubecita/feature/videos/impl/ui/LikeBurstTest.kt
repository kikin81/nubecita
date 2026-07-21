package net.kikin.nubecita.feature.videos.impl.ui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

internal class LikeBurstTest {
    @Test
    fun `at start the heart is invisible and unscaled`() {
        val t = heartBurstTransform(progress = 0f, id = 2)
        assertEquals(0f, t.scale)
        assertEquals(0f, t.alpha)
        assertEquals(0f, t.translationYDp)
    }

    @Test
    fun `scale overshoots to 1_2 at the pop peak then settles to 1_0`() {
        assertEquals(1.2f, heartBurstTransform(0.2f, 2).scale, 0.001f)
        assertEquals(1.0f, heartBurstTransform(0.35f, 2).scale, 0.001f)
        assertEquals(1.0f, heartBurstTransform(1f, 2).scale, 0.001f)
    }

    @Test
    fun `alpha reaches full early, holds, then fades to zero`() {
        assertEquals(1f, heartBurstTransform(0.15f, 2).alpha, 0.001f)
        assertEquals(1f, heartBurstTransform(0.5f, 2).alpha, 0.001f)
        assertEquals(0f, heartBurstTransform(1f, 2).alpha, 0.001f)
    }

    @Test
    fun `the heart drifts up only in the back half`() {
        assertEquals(0f, heartBurstTransform(0.4f, 2).translationYDp, 0.001f)
        assertEquals(-48f, heartBurstTransform(1f, 2).translationYDp, 0.001f)
    }

    @Test
    fun `tilt is deterministic per id, spread around zero`() {
        assertEquals(-12f, heartBurstTransform(0.5f, 0).rotationDegrees)
        assertEquals(0f, heartBurstTransform(0.5f, 2).rotationDegrees)
        assertEquals(12f, heartBurstTransform(0.5f, 4).rotationDegrees)
        assertEquals(-12f, heartBurstTransform(0.5f, 5).rotationDegrees)
    }
}
