package net.kikin.nubecita.core.auth

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class OAuthSessionSerializerTest {
    @Test
    fun `round trips a fully populated session`() =
        runTest {
            val session = sampleSession()
            val out = ByteArrayOutputStream()
            OAuthSessionSerializer.writeTo(session, out)
            val decoded = OAuthSessionSerializer.readFrom(ByteArrayInputStream(out.toByteArray()))
            assertEquals(session, decoded)
            assertArrayEquals(session.dpopPrivateKey, decoded!!.dpopPrivateKey)
            assertArrayEquals(session.dpopPublicKey, decoded.dpopPublicKey)
        }

    @Test
    fun `read from empty input returns null`() =
        runTest {
            assertNull(OAuthSessionSerializer.readFrom(ByteArrayInputStream(ByteArray(0))))
        }

    @Test
    fun `read from garbage bytes returns null without throwing`() =
        runTest {
            val garbage = "{not valid json".encodeToByteArray()
            assertNull(OAuthSessionSerializer.readFrom(ByteArrayInputStream(garbage)))
        }

    @Test
    fun `write of null emits no bytes so subsequent read yields null`() =
        runTest {
            val out = ByteArrayOutputStream()
            OAuthSessionSerializer.writeTo(null, out)
            assertEquals(0, out.size())
            assertNull(OAuthSessionSerializer.readFrom(ByteArrayInputStream(out.toByteArray())))
        }

    @Test
    fun `decoder ignores unknown fields for forward compatibility`() =
        runTest {
            // Serialize a real session, then splice an unknown field into the JSON. Decode must succeed.
            val session = sampleSession()
            val out = ByteArrayOutputStream()
            OAuthSessionSerializer.writeTo(session, out)
            val original = out.toByteArray().decodeToString()
            val mutated = original.replaceFirst("{", "{\"futureField\":\"hello\",")
            assertNotEquals(original, mutated)
            val decoded = OAuthSessionSerializer.readFrom(ByteArrayInputStream(mutated.encodeToByteArray()))
            assertEquals(session, decoded)
        }
}
