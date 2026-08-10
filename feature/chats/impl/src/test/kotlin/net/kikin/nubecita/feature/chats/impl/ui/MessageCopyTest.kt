package net.kikin.nubecita.feature.chats.impl.ui

import net.kikin.nubecita.feature.chats.impl.MessageUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import kotlin.time.Instant

/**
 * The Copy row is offered only when there is something to copy. A menu entry
 * that silently does nothing is worse than an absent one, and both empty cases
 * are reachable: a deleted message keeps an empty `text`, and a message whose
 * only content is a quoted-post embed never had any.
 */
internal class MessageCopyTest {
    private fun message(
        text: String,
        isDeleted: Boolean = false,
    ) = MessageUi(
        id = "m1",
        senderDid = "did:plc:alice",
        isOutgoing = false,
        text = text,
        isDeleted = isDeleted,
        sentAt = Instant.parse("2026-01-01T12:00:00Z"),
    )

    @Test
    fun `offers copy for a message with text`() {
        var copied: String? = null
        val action = message(text = "hello there").copyActionOrNull { copied = it }

        assertNotNull(action, "a message with text must offer Copy")
        action!!()

        assertEquals("hello there", copied)
    }

    @Test
    fun `no copy action for a deleted message`() {
        assertNull(message(text = "", isDeleted = true).copyActionOrNull {})
    }

    @Test
    fun `no copy action for an embed-only message`() {
        assertNull(message(text = "").copyActionOrNull {})
    }

    @Test
    fun `no copy action for whitespace-only text`() {
        // Nothing useful lands on the clipboard, so don't advertise the action.
        assertNull(message(text = "   \n ").copyActionOrNull {})
    }
}
