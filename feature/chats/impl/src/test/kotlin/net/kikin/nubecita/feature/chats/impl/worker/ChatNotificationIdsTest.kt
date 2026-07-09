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
    fun `deepLinkUri addresses the conversation under the chat host`() {
        // Convo-addressed (nubecita://chat/convo/{convoId}) so a group notification
        // opens the group instead of the message sender's 1:1 DM (nubecita-g1ph).
        assertEquals("nubecita://chat/convo/c1", ChatNotificationIds.deepLinkUri("c1"))
    }
}
