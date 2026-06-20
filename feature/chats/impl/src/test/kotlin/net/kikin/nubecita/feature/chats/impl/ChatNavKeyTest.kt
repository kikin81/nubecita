package net.kikin.nubecita.feature.chats.impl

import net.kikin.nubecita.feature.chats.api.Chat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ChatNavKeyTest {
    @Test
    fun `requires at least one identifier`() {
        assertThrows(IllegalArgumentException::class.java) { Chat() }
        Chat(convoId = "c1") // does not throw
        Chat(otherUserDid = "did:x") // does not throw
    }
}
