package net.kikin.nubecita.core.common.navigation

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Unit tests for the shared-image overlay clip's radius arithmetic.
 *
 * The clip itself cannot be exercised here — its `Path` is backed by
 * `android.graphics.Path`, which a plain JVM test has no implementation for —
 * so the part with actual logic is split out and tested directly. The clamp is
 * the reason this exists: it guards against an animation spec that undershoots
 * past `0.dp`, which the current critically-damped default never does, so
 * without a test nothing would ever demonstrate that it behaves.
 */
class SharedImageCornerRadiusTest {
    @Test
    fun `converts dp to px at the given density`() {
        assertEquals(32f, sharedImageCornerRadiusPx(16.dp, Density(2f)))
    }

    @Test
    fun `scales with density`() {
        assertEquals(48f, sharedImageCornerRadiusPx(16.dp, Density(3f)))
    }

    @Test
    fun `zero dp is zero px`() {
        assertEquals(0f, sharedImageCornerRadiusPx(0.dp, Density(3f)))
    }

    @Test
    fun `clamps an undershooting radius to zero`() {
        assertEquals(0f, sharedImageCornerRadiusPx((-4).dp, Density(3f)))
    }

    @Test
    fun `clamps rather than returning a negative of the same magnitude`() {
        val undershoot = sharedImageCornerRadiusPx((-16).dp, Density(2f))
        assertEquals(0f, undershoot)
    }

    @Test
    fun `card and viewer resting radii convert as expected`() {
        val density = Density(3f)
        assertEquals(48f, sharedImageCornerRadiusPx(SharedImageCorners.CARD, density))
        assertEquals(0f, sharedImageCornerRadiusPx(SharedImageCorners.VIEWER, density))
    }

    @Test
    fun `card radius is larger than viewer radius so the transition has something to animate`() {
        assert(SharedImageCorners.CARD > SharedImageCorners.VIEWER)
    }
}
