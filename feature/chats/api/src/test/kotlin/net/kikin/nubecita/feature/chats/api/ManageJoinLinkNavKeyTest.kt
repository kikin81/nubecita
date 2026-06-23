package net.kikin.nubecita.feature.chats.api

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManageJoinLinkNavKeyTest {
    @Test
    fun `round-trips through json`() {
        val key = ManageJoinLink(convoId = "convo-1")
        val json = Json.encodeToString(ManageJoinLink.serializer(), key)
        assertEquals(key, Json.decodeFromString(ManageJoinLink.serializer(), json))
    }
}
