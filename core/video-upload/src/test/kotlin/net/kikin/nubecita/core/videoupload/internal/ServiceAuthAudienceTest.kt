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
     * The audience is the user's **own PDS**, not the video service.
     *
     * This is the parameter most likely to be "corrected" to
     * `did:web:video.bsky.app` by someone reading the code cold — it looks
     * wrong, and the service rejects it opaquely if you change it. The test
     * exists to make that a red build rather than a debugging session.
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

    @Test
    fun `audience is never the video service`() {
        val audience = audienceForPds("https://bsky.social")

        assertEquals("did:web:bsky.social", audience)
        assert(audience != "did:web:video.bsky.app") { "aud must be the PDS, not the video service" }
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
