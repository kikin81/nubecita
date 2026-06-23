package net.kikin.nubecita.feature.chats.impl

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.json.Json
import net.kikin.nubecita.feature.chats.api.ManageJoinLink
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManageJoinLinkNavKeyTest {
    @Test
    fun `carries the convoId and is a NavKey`() {
        val key = ManageJoinLink(convoId = "c1")
        assertEquals("c1", key.convoId)
        assertTrue(key is NavKey)
    }

    @Test
    fun `round-trips through json`() {
        val key = ManageJoinLink(convoId = "convo-1")
        val json = Json.encodeToString(ManageJoinLink.serializer(), key)
        assertEquals(key, Json.decodeFromString(ManageJoinLink.serializer(), json))
    }
}
