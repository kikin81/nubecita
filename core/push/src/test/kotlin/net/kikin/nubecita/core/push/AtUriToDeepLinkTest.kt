package net.kikin.nubecita.core.push

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class AtUriToDeepLinkTest {
    @Test
    fun `post-shaped AT-URI translates to a profile-post deep link carrying the did and rkey`() {
        val result =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = "at://did:plc:alice/app.bsky.feed.post/3kxyz",
                recipientDid = "did:plc:bob",
            )

        assertEquals("nubecita://profile/did:plc:alice/post/3kxyz", result)
    }

    @Test
    fun `follow-record AT-URI translates to the follower's profile, discarding the rkey`() {
        val result =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = "at://did:plc:alice/app.bsky.graph.follow/3kabc",
                recipientDid = "did:plc:bob",
            )

        assertEquals("nubecita://profile/did:plc:alice", result)
    }

    @Test
    fun `verification-record AT-URI translates to the recipient's profile, NOT the verifier in the authority slot`() {
        // The authority of a verification AT-URI is the VERIFIER's DID (the
        // actor who issued the verification record). The tap target is the
        // recipient — the user whose status changed — which only the caller
        // knows via recipientDid. This is the load-bearing invariant the
        // helper exists to encode.
        val verifier = "did:plc:z72i7hdynmk6r22z27h6tvur"
        val recipient = "did:plc:alice"
        val result =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = "at://$verifier/app.bsky.graph.verification/3kvrf",
                recipientDid = recipient,
            )

        assertEquals("nubecita://profile/$recipient", result)
    }

    @Test
    fun `verification AT-URI without a recipientDid returns null rather than routing to the verifier`() {
        // Defensive: routing to the verifier would land the user on Bluesky's
        // official verifier profile every time they tap a verification push
        // missing recipient context. Better to no-op the tap than mislead.
        val result =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = "at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.graph.verification/3kvrf",
                recipientDid = null,
            )

        assertNull(result)
    }

    @Test
    fun `returns null when input does not start with the at scheme`() {
        val result =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = "https://bsky.app/profile/alice.bsky.social/post/3kxyz",
                recipientDid = null,
            )

        assertNull(result)
    }

    @Test
    fun `returns null when the AT-URI has no authority`() {
        val result =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = "at:///app.bsky.feed.post/3kxyz",
                recipientDid = null,
            )

        assertNull(result)
    }

    @Test
    fun `returns null when the collection NSID is one this helper does not translate`() {
        val result =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = "at://did:plc:alice/app.bsky.feed.like/3kabc",
                recipientDid = null,
            )

        assertNull(result)
    }

    @Test
    fun `returns null when the segment count is wrong`() {
        val missingRkey =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = "at://did:plc:alice/app.bsky.feed.post",
                recipientDid = null,
            )
        val extraSegment =
            AtUriToDeepLink.toNubecitaDeepLink(
                atUri = "at://did:plc:alice/app.bsky.feed.post/3kxyz/extra",
                recipientDid = null,
            )

        assertNull(missingRkey)
        assertNull(extraSegment)
    }
}
