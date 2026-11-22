package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.runtime.XrpcError
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * A definitive "this profile is gone" answer drops the cached copy; anything
 * transient keeps it on screen.
 */
internal class ProfileErrorsTest {
    @Test
    fun `deactivated, taken down and not-found profiles are definitively gone`() {
        assertTrue(XrpcError("AccountDeactivated", "Account is deactivated", 400).isDefinitiveProfileGone())
        assertTrue(XrpcError("AccountTakedown", "Account has been suspended", 400).isDefinitiveProfileGone())
        assertTrue(XrpcError("InvalidRequest", "Profile not found", 400).isDefinitiveProfileGone())
    }

    @Test
    fun `network and server failures are not definitive`() {
        assertFalse(IOException("offline").isDefinitiveProfileGone())
        assertFalse(XrpcError("InternalServerError", "boom", 502).isDefinitiveProfileGone())
        assertFalse(XrpcError("InvalidRequest", "Error: actor must be a valid did or a handle", 400).isDefinitiveProfileGone())
    }
}
