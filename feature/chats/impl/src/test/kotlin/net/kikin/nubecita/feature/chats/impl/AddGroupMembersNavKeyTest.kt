package net.kikin.nubecita.feature.chats.impl

import androidx.navigation3.runtime.NavKey
import net.kikin.nubecita.feature.chats.api.AddGroupMembers
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AddGroupMembersNavKeyTest {
    @Test
    fun `carries the convoId and is a NavKey`() {
        val key = AddGroupMembers(convoId = "c1")
        assertEquals("c1", key.convoId)
        assertTrue(key is NavKey)
    }
}
