package net.kikin.nubecita.core.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Covers the expiry arithmetic of the debug-only auth harness (nubecita-zh7p).
 *
 * This source set is attached only under `-PauthTestHarness=true`, so these
 * tests compile and run only in a flagged build — CI runs them in the same step
 * that compiles the harness.
 */
@OptIn(ExperimentalEncodingApi::class)
class HarnessTokenExpiryTest {
    @Test
    fun `reads exp from a JWT access token`() {
        assertEquals(1_234L, HarnessTokenExpiry.of(jwt("""{"sub":"did:plc:abc","exp":1900000000}""")))
    }

    @Test
    fun `reads exp from a payload whose base64url needs padding`() {
        // Real tokens are unpadded base64url. A payload is padded to a multiple
        // of four, so the length mod 4 varies with the claim set — pad wrongly
        // and SOME tokens decode while others return null, which on device
        // looks like "this PDS issues opaque tokens".
        val lengths =
            (0..6).map { filler ->
                val token = jwt("""{"exp":1900000000,"pad":"${"x".repeat(filler)}"}""")
                token.split('.')[1].length % 4 to HarnessTokenExpiry.of(token)
            }
        lengths.forEach { (mod, exp) -> assertEquals(1_900_000_000L, exp, "payload length mod 4 = $mod") }
        // and the sweep really did cover the padded remainders, not just mod 0
        assertTrue(lengths.map { it.first }.toSet().size > 1, "sweep never varied the padding")
    }

    @Test
    fun `reads exp from a payload containing base64url's own alphabet`() {
        // '-' and '_' replace '+' and '/'. A decoder configured for standard
        // base64 throws on them, and the catch would turn that into null.
        val payload = """{"exp":1900000000,"aud":"${"ûÿ¾"}"}"""
        val token = jwt(payload)
        assertTrue(token.split('.')[1].any { it == '-' || it == '_' }, "payload has no base64url-specific chars")
        assertEquals(1_900_000_000L, HarnessTokenExpiry.of(token))
    }

    @Test
    fun `returns null for a JWT with no exp claim`() {
        assertNull(HarnessTokenExpiry.of(jwt("""{"sub":"did:plc:abc"}""")))
    }

    @Test
    fun `returns null for a non-numeric exp claim`() {
        assertNull(HarnessTokenExpiry.of(jwt("""{"exp":"soon"}""")))
    }

    @Test
    fun `returns null for the harness's own opaque poison token`() {
        // BadAccessToken's string. The whole reason that scenario cannot test
        // real expiry is that this returns null, so pin it.
        assertNull(HarnessTokenExpiry.of("nubecita-harness-deliberately-invalid-token"))
    }

    @Test
    fun `returns null for the wrong number of segments`() {
        assertNull(HarnessTokenExpiry.of("onlyone"))
        assertNull(HarnessTokenExpiry.of("two.parts"))
        assertNull(HarnessTokenExpiry.of("a.b.c.d"))
    }

    @Test
    fun `returns null when the payload is not base64`() {
        assertNull(HarnessTokenExpiry.of("header.not!valid!base64.signature"))
    }

    @Test
    fun `returns null when the payload is not JSON`() {
        assertNull(HarnessTokenExpiry.of("header.${encode("not json at all")}.signature"))
    }

    @Test
    fun `a token expiring in the future is still accepted`() {
        val left =
            HarnessTokenExpiry.secondsLeft(
                expEpochSeconds = 1_000_120,
                nowMillis = 1_000_000_000,
                clockOffsetSeconds = 0,
            )
        assertEquals(120L, left)
        assertTrue(left > 0, "a future token must not be treated as expired")
    }

    @Test
    fun `exp exactly now counts as expired`() {
        assertEquals(
            0L,
            HarnessTokenExpiry.secondsLeft(
                expEpochSeconds = 1_000_000,
                nowMillis = 1_000_000_000,
                clockOffsetSeconds = 0,
            ),
        )
    }

    @Test
    fun `exp one second past counts as expired`() {
        assertEquals(
            -1L,
            HarnessTokenExpiry.secondsLeft(
                expEpochSeconds = 999_999,
                nowMillis = 1_000_000_000,
                clockOffsetSeconds = 0,
            ),
        )
    }

    @Test
    fun `a device clock running behind the server does not make a live token look expired`() {
        // Device is 10 minutes slow, so the SDK adds +600 to reach server time.
        // Ignoring the offset would read this token as 300 s of life left when
        // the server has already rejected it for 300 s — the apply would be
        // refused and the scenario would never run.
        assertEquals(
            -300L,
            HarnessTokenExpiry.secondsLeft(
                expEpochSeconds = 1_000_300,
                nowMillis = 1_000_000_000,
                clockOffsetSeconds = 600,
            ),
        )
    }

    @Test
    fun `a device clock running ahead of the server keeps a live token live`() {
        // Device is 10 minutes fast (offset -600). Ignoring the offset would
        // call this token expired and the harness would write back a token the
        // server still accepts: nothing refreshes, and the run passes having
        // tested nothing. This is the failure the refusal exists to prevent.
        assertEquals(
            300L,
            HarnessTokenExpiry.secondsLeft(
                expEpochSeconds = 999_700,
                nowMillis = 1_000_000_000,
                clockOffsetSeconds = -600,
            ),
        )
    }

    @Test
    fun `the threshold is stricter than the SDK's thirty-second skew`() {
        // The SDK calls a token expired 30 s before exp. A token 29 s from
        // expiry is one the SDK would already refresh, and the harness must
        // still refuse it: the refusal's promise is that exp itself is past.
        val left =
            HarnessTokenExpiry.secondsLeft(
                expEpochSeconds = 1_000_029,
                nowMillis = 1_000_000_000,
                clockOffsetSeconds = 0,
            )
        assertTrue(left > 0, "29 s before exp must still be refused, not applied")
    }

    private fun jwt(payload: String): String = "${encode("""{"alg":"ES256"}""")}.${encode(payload)}.c2ln"

    private fun encode(value: String): String = Base64.UrlSafe.encode(value.toByteArray()).trimEnd('=')
}
