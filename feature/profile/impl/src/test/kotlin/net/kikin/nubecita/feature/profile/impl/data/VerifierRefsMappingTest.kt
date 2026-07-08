package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.VerificationState
import io.github.kikin81.atproto.app.bsky.actor.VerificationView
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.Datetime
import io.github.kikin81.atproto.runtime.Did
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.time.Instant

internal class VerifierRefsMappingTest {
    private fun view(
        issuer: String,
        createdAt: String,
        isValid: Boolean = true,
    ) = VerificationView(
        uri = AtUri("at://$issuer/app.bsky.graph.verification/1"),
        isValid = isValid,
        issuer = Did(issuer),
        createdAt = Datetime(createdAt),
    )

    private fun state(vararg views: VerificationView) = VerificationState(verifiedStatus = "valid", trustedVerifierStatus = "none", verifications = views.toList())

    @Test
    fun `null verification yields no refs`() {
        assertTrue((null as VerificationState?).toVerifierRefs().isEmpty())
    }

    @Test
    fun `no verifications yields no refs`() {
        assertTrue(state().toVerifierRefs().isEmpty())
    }

    @Test
    fun `valid verifications map to did plus parsed date`() {
        val refs =
            state(
                view(issuer = "did:plc:nyt", createdAt = "2026-05-01T00:00:00Z"),
                view(issuer = "did:plc:bsky", createdAt = "2026-04-15T12:30:00Z"),
            ).toVerifierRefs()

        assertEquals(listOf("did:plc:nyt", "did:plc:bsky"), refs.map { it.did })
        assertEquals(Instant.parse("2026-05-01T00:00:00Z"), refs[0].verifiedAt)
        assertEquals(Instant.parse("2026-04-15T12:30:00Z"), refs[1].verifiedAt)
    }

    @Test
    fun `invalid verifications are dropped`() {
        val refs =
            state(
                view(issuer = "did:plc:valid", createdAt = "2026-05-01T00:00:00Z", isValid = true),
                view(issuer = "did:plc:invalid", createdAt = "2026-05-01T00:00:00Z", isValid = false),
            ).toVerifierRefs()

        assertEquals(listOf("did:plc:valid"), refs.map { it.did })
    }

    @Test
    fun `verifications with an unparseable date are skipped`() {
        val refs =
            state(
                view(issuer = "did:plc:ok", createdAt = "2026-05-01T00:00:00Z"),
                view(issuer = "did:plc:bad", createdAt = "not-a-date"),
            ).toVerifierRefs()

        assertEquals(listOf("did:plc:ok"), refs.map { it.did })
    }
}
