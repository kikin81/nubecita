package net.kikin.nubecita.core.push

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TrustedVerifiersTest {
    @Test
    fun `set contains the official Bluesky verifier DID`() {
        // The verifier ecosystem currently has exactly one entry — Bluesky's
        // official verifier. The PushDispatcher silently drops verified /
        // unverified pushes whose actorDid isn't in this set, so a regression
        // that removed this DID would suppress all real verification pushes.
        assertTrue("did:plc:z72i7hdynmk6r22z27h6tvur" in TRUSTED_VERIFIERS)
        assertTrue(TRUSTED_VERIFIERS.isNotEmpty())
    }
}
