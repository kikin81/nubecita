package net.kikin.nubecita.core.videoupload.internal

import net.kikin.nubecita.core.auth.NoSessionException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.NullSource
import org.junit.jupiter.params.provider.ValueSource

class ServiceAuthAudienceTest {
    /**
     * The **upload leg's** audience is the user's own PDS, because what that
     * token authorises is the video service writing a blob into the user's
     * repository.
     *
     * This is NOT the audience for calls the video service answers itself —
     * `getUploadLimits` and `getJobStatus` take `did:web:video.bsky.app`. An
     * earlier version of this file asserted one audience for everything and
     * was wrong; the live service rejects a PDS-addressed token on those calls
     * with `invalid_token: invalid token audience`.
     */
    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "https://bsky.social,            did:web:bsky.social",
        "https://morel.us-east.host.bsky.network, did:web:morel.us-east.host.bsky.network",
        "https://pds.example.com:2583,   did:web:pds.example.com",
        "https://SELF.HOSTED.example,    did:web:self.hosted.example",
    )
    fun `audience is derived from the account's own PDS host`(
        pdsUrl: String,
        expected: String,
    ) {
        assertEquals(expected, audienceForPds(pdsUrl))
    }

    /**
     * The two audiences are genuinely different values, and conflating them is
     * the bug this pair of constants exists to prevent. Established against the
     * live service, not from documentation.
     */
    @Test
    fun `the upload audience and the video-service audience differ`() {
        assertEquals("did:web:bsky.social", audienceForPds("https://bsky.social"))
        assertEquals("did:web:video.bsky.app", VIDEO_SERVICE_DID)
        assert(audienceForPds("https://bsky.social") != VIDEO_SERVICE_DID)
    }

    /**
     * `SessionState.SignedIn.pdsUrl` is nullable — a freshly-restored session
     * may not carry it. Failing loudly beats minting a token with a malformed
     * audience that the service rejects without explanation.
     */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = ["", "   ", "not a url", "://missing-scheme"])
    fun `an unusable pds url fails rather than producing a malformed audience`(pdsUrl: String?) {
        assertThrows(NoSessionException::class.java) { audienceForPds(pdsUrl) }
    }
}
