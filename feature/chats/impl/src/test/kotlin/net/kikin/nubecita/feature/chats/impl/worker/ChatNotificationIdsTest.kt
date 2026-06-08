package net.kikin.nubecita.feature.chats.impl.worker

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Test

internal class ChatNotificationIdsTest {
    @Test
    fun `notifyId is stable per convo`() {
        assertEquals(ChatNotificationIds.notifyId("c1"), ChatNotificationIds.notifyId("c1"))
    }

    @Test
    fun `notifyId differs across convos`() {
        assertNotEquals(ChatNotificationIds.notifyId("c1"), ChatNotificationIds.notifyId("c2"))
    }

    @Test
    fun `notifyId never collides with the summary id`() {
        assertNotEquals(ChatNotificationIds.SUMMARY_ID, ChatNotificationIds.notifyId("c1"))
    }

    @Test
    fun `deepLinkUri embeds the other user DID under the chat host`() {
        assertEquals("nubecita://chat/did:plc:alice", ChatNotificationIds.deepLinkUri("did:plc:alice"))
    }
}
