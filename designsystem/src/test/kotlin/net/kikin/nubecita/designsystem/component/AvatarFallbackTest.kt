package net.kikin.nubecita.designsystem.component

import net.kikin.nubecita.core.common.avatar.avatarHueFor
import net.kikin.nubecita.data.models.AuthorUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AvatarFallbackTest {
    private fun author(
        displayName: String,
        handle: String = "alice.bsky.social",
    ) = AuthorUi(did = "did:plc:alice", handle = handle, displayName = displayName, avatarUrl = null)

    @Test
    fun `initial is the first letter-or-digit of displayName, uppercased`() {
        assertEquals('A', avatarFallbackFor(author("alice")).initial)
        assertEquals('B', avatarFallbackFor(author("🎉 bob")).initial)
    }

    @Test
    fun `blank displayName falls back to the handle`() {
        assertEquals('A', avatarFallbackFor(author(displayName = "   ", handle = "alice.test")).initial)
    }

    @Test
    fun `no letter-or-digit anywhere yields a null initial`() {
        assertNull(avatarFallbackFor(author(displayName = "###", handle = "!!!")).initial)
    }

    @Test
    fun `hue matches avatarHueFor for the did and handle`() {
        val a = author("alice")
        assertEquals(avatarHueFor(a.did, a.handle), avatarFallbackFor(a).hue)
    }

    @Test
    fun `primitive overload falls back to handle when displayName is null`() {
        assertEquals('C', avatarFallbackFor(did = "did:plc:c", handle = "carol.test", displayName = null).initial)
    }

    @Test
    fun `primitive overload falls back to handle when displayName is empty`() {
        assertEquals('D', avatarFallbackFor(did = "did:plc:d", handle = "dave.test", displayName = "").initial)
    }
}
