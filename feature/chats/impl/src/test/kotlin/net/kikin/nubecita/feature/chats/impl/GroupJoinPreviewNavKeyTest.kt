package net.kikin.nubecita.feature.chats.impl

import kotlinx.serialization.json.Json
import net.kikin.nubecita.feature.chats.api.GroupJoinPreview
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class GroupJoinPreviewNavKeyTest {
    @Test
    fun `round-trips through json and carries the code`() {
        val key = GroupJoinPreview(code = "abc123")
        assertEquals("abc123", key.code)
        val json = Json.encodeToString(GroupJoinPreview.serializer(), key)
        assertEquals(key, Json.decodeFromString(GroupJoinPreview.serializer(), json))
    }
}
