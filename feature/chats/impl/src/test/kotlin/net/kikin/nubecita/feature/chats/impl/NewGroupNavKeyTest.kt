package net.kikin.nubecita.feature.chats.impl

import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.feature.chats.api.NewGroup
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NewGroupNavKeyTest {
    @Test
    fun `NewGroup is a NavKey`() {
        assertTrue(NewGroup is NavKey)
    }
}
