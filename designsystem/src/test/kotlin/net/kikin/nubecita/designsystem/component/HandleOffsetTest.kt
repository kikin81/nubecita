package net.kikin.nubecita.designsystem.component

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class HandleOffsetTest {
    @Test
    fun `the handle offset is taken from the end of the formatted text`() {
        assertEquals(10, handleOffsetIn(text = "usuario: @usuario", handle = "usuario"))
    }

    @Test
    fun `no offset when the handle is not in the text`() {
        assertNull(handleOffsetIn(text = "@alice", handle = "bob"))
    }
}
