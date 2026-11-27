package net.kikin.nubecita.core.auth

import net.kikin.nubecita.core.analytics.RefreshErrorDetail
import net.kikin.nubecita.core.analytics.TokenRefreshOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class RefreshErrorDetailTest {
    @Test
    fun `the reference server's wording is sorted into the fixed list`() {
        val cases =
            listOf(
                Triple("invalid_grant", "Refresh token replayed", RefreshErrorDetail.RefreshTokenReplayed),
                Triple("invalid_grant", "Session expired", RefreshErrorDetail.SessionExpired),
                Triple("invalid_grant", "Refresh token expired", RefreshErrorDetail.RefreshTokenExpired),
                Triple("invalid_dpop_proof", "DPoP proof replayed", RefreshErrorDetail.DpopProofReplayed),
                Triple("invalid_dpop_proof", "Failed to verify DPoP proof: \"iat\" claim timestamp check failed", RefreshErrorDetail.DpopProofUnverified),
                Triple("invalid_dpop_proof", "DPoP \"htu\" mismatch", RefreshErrorDetail.DpopProofOther),
                Triple("invalid_token", "Invalid DPoP key binding", RefreshErrorDetail.DpopKeyBinding),
                Triple("invalid_grant", "Something this list has never seen", RefreshErrorDetail.Other),
                Triple("invalid_grant", null, RefreshErrorDetail.None),
                Triple("invalid_grant", "  ", RefreshErrorDetail.None),
                Triple(null, null, RefreshErrorDetail.None),
            )
        for ((error, description, expected) in cases) {
            assertEquals(expected, refreshErrorDetail(error, description), "error=$error description=$description")
        }
    }

    @Test
    fun `a proof the server had already seen is told apart from one it could not verify`() {
        // Same error code, opposite meaning: the first says the request
        // arrived twice and the earlier copy may have spent the refresh token.
        val replayed = refreshErrorDetail("invalid_dpop_proof", "DPoP proof replayed")
        val unverified = refreshErrorDetail("invalid_dpop_proof", "Failed to verify DPoP proof")

        assertEquals(RefreshErrorDetail.DpopProofReplayed, replayed)
        assertEquals(RefreshErrorDetail.DpopProofUnverified, unverified)
    }

    @Test
    fun `a rejection keeps its error code when it looks like one`() {
        val rejection = classifyRefreshRejection(401, """{"error":"invalid_dpop_proof","error_description":"DPoP proof replayed"}""")

        assertEquals(TokenRefreshOutcome.Rejected, rejection.outcome)
        assertEquals("invalid_dpop_proof", rejection.error)
        assertEquals("DPoP proof replayed", rejection.description)
    }

    @Test
    fun `an error field that is not a code is not kept`() {
        // A server can put anything here; only a short lowercase token is logged.
        val rejection = classifyRefreshRejection(400, """{"error":"did:plc:abc123 said no","error_description":"x"}""")

        assertNull(rejection.error)
    }
}
