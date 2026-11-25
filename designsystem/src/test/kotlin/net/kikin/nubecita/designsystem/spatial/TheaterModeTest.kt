package net.kikin.nubecita.designsystem.spatial

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TheaterModeTest {
    private class FakeSpace : XrSpaceControls {
        val calls = mutableListOf<String>()

        override fun requestFullSpace() {
            calls += "full"
        }

        override fun requestHomeSpace() {
            calls += "home"
        }

        override fun setPassthroughOpacity(opacity: Float?) {
            calls += "passthrough=$opacity"
        }
    }

    private val space = FakeSpace()
    private val theater = TheaterMode(space)

    @Test
    fun fromHomeSpace_entersFullSpaceDark_andReturnsHome() {
        theater.onSpaceChanged(isFullSpace = false)
        theater.toggle()
        assertTrue(theater.isActive)
        theater.onSpaceChanged(isFullSpace = true)
        theater.toggle()
        assertFalse(theater.isActive)
        assertEquals(listOf("full", "passthrough=0.0", "passthrough=null", "home"), space.calls)
    }

    @Test
    fun fromFullSpace_onlyDims_andStaysInFullSpace() {
        theater.onSpaceChanged(isFullSpace = true)
        theater.toggle()
        theater.toggle()
        assertEquals(listOf("passthrough=0.0", "passthrough=null"), space.calls)
    }

    @Test
    fun leavingFullSpaceBySystemControls_endsTheaterMode_withoutRequestingHome() {
        theater.onSpaceChanged(isFullSpace = false)
        theater.toggle()
        theater.onSpaceChanged(isFullSpace = true)
        theater.onSpaceChanged(isFullSpace = false)
        assertFalse(theater.isActive)
        assertEquals(listOf("full", "passthrough=0.0", "passthrough=null"), space.calls)
    }

    @Test
    fun closingThePlayer_whileInTheaterMode_restoresHomeSpace() {
        theater.onSpaceChanged(isFullSpace = false)
        theater.toggle()
        theater.onSpaceChanged(isFullSpace = true)
        theater.exit()
        assertEquals(listOf("full", "passthrough=0.0", "passthrough=null", "home"), space.calls)
    }

    @Test
    fun closingThePlayer_withoutTheaterMode_changesNothing() {
        theater.onSpaceChanged(isFullSpace = true)
        theater.exit()
        assertEquals(emptyList<String>(), space.calls)
    }
}
