package net.kikin.nubecita.core.common.avatar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins the [avatarHueFor] algorithm against literal expected values so
 * any drift in the seed shape, hashing, or modulo bounds surfaces in
 * the test suite rather than silently re-painting every avatar in the
 * app. Values captured by running
 * `Math.floorMod((did + (handle.firstOrNull()?.toString() ?: "")).hashCode(), 360)`
 * on Java 17; `String.hashCode` is defined by the JVM spec
 * (sum of `s[i] * 31^(n-1-i)`) so the numbers are stable across JVM
 * vendors and versions. The `?: ""` branch matters for empty handles:
 * without it, `firstOrNull().toString()` would emit the literal string
 * `"null"` and the empty-handle hue would land on a different value.
 *
 * The same `(did, handle) → hue` pairs lock the cross-screen
 * "same user paints identically everywhere" contract that Profile,
 * Chats, and Settings rely on. Bump these literals only when
 * intentionally rotating the algorithm — and never silently.
 */
internal class AvatarHueTest {
    @Test
    fun `golden values match the documented algorithm output`() {
        // Computed once on Java 17 against the production algorithm —
        // pins drift at the literal level.
        assertEquals(47, avatarHueFor(did = "did:plc:alice", handle = "alice.bsky.social"))
        assertEquals(307, avatarHueFor(did = "did:plc:bob", handle = "bob.bsky.social"))
        assertEquals(279, avatarHueFor(did = "did:plc:hatsune", handle = "miku.example"))
        assertEquals(357, avatarHueFor(did = "did:plc:xyz", handle = "  whitespace.first"))
        assertEquals(150, avatarHueFor(did = "did:plc:zzz", handle = "zander.example"))
    }

    @Test
    fun `empty handle uses did-only seed and stays within range`() {
        // Empty handle → firstOrNull() is null → seed = did + "" = did.
        // Confirms the function doesn't NPE on a blank handle and the
        // result still respects the 0..359 bound.
        val hue = avatarHueFor(did = "did:plc:emptyhandle", handle = "")
        assertEquals(231, hue)
        assertTrue(hue in 0..359)
    }

    @Test
    fun `hue is deterministic — repeated calls return the same value`() {
        // The function is pure; this is a smoke test catching any
        // accidental introduction of a counter, system clock, or
        // ThreadLocal RNG in the seed.
        val first = avatarHueFor("did:plc:repeat", "repeat.example")
        val second = avatarHueFor("did:plc:repeat", "repeat.example")
        val third = avatarHueFor("did:plc:repeat", "repeat.example")
        assertEquals(first, second)
        assertEquals(second, third)
    }

    @Test
    fun `result always lands in 0 to 359 — Math floorMod handles negative hashCodes`() {
        // `String.hashCode` can return negative values (Int overflow on
        // long strings); `Math.floorMod` returns a non-negative result
        // for every input. Exercise across a range of seeds that includes
        // ones whose raw hashCode happens to be negative.
        val seeds =
            listOf(
                "did:plc:a" to "alpha",
                "did:plc:z" to "zoo",
                "did:plc:" to "no-trailing",
                "did:plc:long-did-segment-that-pushes-hashCode-into-negative-territory" to "x",
                "did:plc:negative" to "ñoño.example", // non-ASCII first char
            )
        for ((did, handle) in seeds) {
            val hue = avatarHueFor(did, handle)
            assertTrue(hue in 0..359, "hue out of range for ($did, $handle): $hue")
        }
    }

    @Test
    fun `first char of handle is what spreads the hash — different first chars yield different hues for the same did`() {
        // Mixing the handle's first character into the seed is the
        // KDoc-documented mitigation against DID-similarity collisions.
        // Two handles that share everything but the first char must
        // produce different hues for the same DID.
        val did = "did:plc:samebase"
        val hueA = avatarHueFor(did, "alice.bsky.social")
        val hueB = avatarHueFor(did, "bob.bsky.social")
        // Not asserting specific values — only that the spread works.
        // Collision is theoretically possible (1-in-360 across infinite
        // pairs) but for these two literal inputs the production
        // algorithm yields distinct values.
        assertTrue(hueA != hueB, "expected different hues for different handle first chars: $hueA == $hueB")
    }
}
