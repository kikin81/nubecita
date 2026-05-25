package net.kikin.nubecita.core.push

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class PushDispatcherTest {
    private val dispatcher = PushDispatcher()

    @Test
    fun `well-formed payload addressed to the active session, unmuted, returns Show`() {
        val outcome =
            dispatcher.dispatch(
                data = likePayloadFor(recipient = ALICE_DID, actor = BOB_DID),
                activeSessionDid = ALICE_DID,
                isAppForeground = false,
                mutedActors = emptySet(),
            )

        assertInstanceOf(DispatchOutcome.Show::class.java, outcome)
        val show = outcome as DispatchOutcome.Show
        assertEquals(ALICE_DID, show.payload.recipientDid)
        assertEquals(BOB_DID, show.payload.actorDid)
    }

    @Test
    fun `unparseable payload drops with ParseFailed`() {
        val outcome =
            dispatcher.dispatch(
                data = mapOf("reason" to "like"), // missing required keys
                activeSessionDid = ALICE_DID,
                isAppForeground = false,
                mutedActors = emptySet(),
            )

        assertEquals(DispatchOutcome.Drop(DropReason.ParseFailed), outcome)
    }

    @Test
    fun `payload addressed to a different DID drops with RecipientMismatch`() {
        val outcome =
            dispatcher.dispatch(
                data = likePayloadFor(recipient = ALICE_DID, actor = BOB_DID),
                activeSessionDid = "did:plc:someone-else",
                isAppForeground = false,
                mutedActors = emptySet(),
            )

        assertEquals(DispatchOutcome.Drop(DropReason.RecipientMismatch), outcome)
    }

    @Test
    fun `payload arriving while signed out drops with RecipientMismatch`() {
        val outcome =
            dispatcher.dispatch(
                data = likePayloadFor(recipient = ALICE_DID, actor = BOB_DID),
                activeSessionDid = null,
                isAppForeground = false,
                mutedActors = emptySet(),
            )

        assertEquals(DispatchOutcome.Drop(DropReason.RecipientMismatch), outcome)
    }

    @Test
    fun `payload whose actorDid is muted drops with MutedActor`() {
        val outcome =
            dispatcher.dispatch(
                data = likePayloadFor(recipient = ALICE_DID, actor = BOB_DID),
                activeSessionDid = ALICE_DID,
                isAppForeground = false,
                mutedActors = setOf(BOB_DID),
            )

        assertEquals(DispatchOutcome.Drop(DropReason.MutedActor), outcome)
    }

    @Test
    fun `verified payload from a non-trusted verifier drops with UntrustedVerifier`() {
        val attacker = "did:plc:not-a-real-verifier"
        val outcome =
            dispatcher.dispatch(
                data =
                    mapOf(
                        "reason" to "verified",
                        "uri" to "at://$attacker/app.bsky.graph.verification/3kabc",
                        "actorDid" to attacker,
                        "recipientDid" to ALICE_DID,
                    ),
                activeSessionDid = ALICE_DID,
                isAppForeground = false,
                mutedActors = emptySet(),
            )

        assertEquals(DispatchOutcome.Drop(DropReason.UntrustedVerifier), outcome)
    }

    @Test
    fun `verified payload from the official Bluesky verifier returns Show`() {
        val outcome =
            dispatcher.dispatch(
                data =
                    mapOf(
                        "reason" to "verified",
                        "uri" to "at://$BLUESKY_VERIFIER_DID/app.bsky.graph.verification/3kabc",
                        "actorDid" to BLUESKY_VERIFIER_DID,
                        "recipientDid" to ALICE_DID,
                    ),
                activeSessionDid = ALICE_DID,
                isAppForeground = false,
                mutedActors = emptySet(),
            )

        assertInstanceOf(DispatchOutcome.Show::class.java, outcome)
    }

    @Test
    fun `payload arriving while app is foregrounded drops with AppForeground`() {
        val outcome =
            dispatcher.dispatch(
                data = likePayloadFor(recipient = ALICE_DID, actor = BOB_DID),
                activeSessionDid = ALICE_DID,
                isAppForeground = true,
                mutedActors = emptySet(),
            )

        assertEquals(DispatchOutcome.Drop(DropReason.AppForeground), outcome)
    }

    @Test
    fun `mute takes precedence over trusted-verifier check`() {
        // If the official verifier itself were ever muted (hypothetical),
        // we drop with MutedActor — mute runs before verifier in the chain.
        val outcome =
            dispatcher.dispatch(
                data =
                    mapOf(
                        "reason" to "verified",
                        "uri" to "at://$BLUESKY_VERIFIER_DID/app.bsky.graph.verification/3kabc",
                        "actorDid" to BLUESKY_VERIFIER_DID,
                        "recipientDid" to ALICE_DID,
                    ),
                activeSessionDid = ALICE_DID,
                isAppForeground = false,
                mutedActors = setOf(BLUESKY_VERIFIER_DID),
            )

        assertEquals(DispatchOutcome.Drop(DropReason.MutedActor), outcome)
    }

    @Test
    fun `untrusted-verifier check takes precedence over foreground drop`() {
        // Foreground is the LAST stage in the chain. A spoofed verification
        // received while the app is foregrounded must still surface as
        // UntrustedVerifier — that's the security-relevant signal we want
        // to retain even when the foreground UX preference would otherwise
        // suppress it.
        val attacker = "did:plc:not-a-real-verifier"
        val outcome =
            dispatcher.dispatch(
                data =
                    mapOf(
                        "reason" to "verified",
                        "uri" to "at://$attacker/app.bsky.graph.verification/3kabc",
                        "actorDid" to attacker,
                        "recipientDid" to ALICE_DID,
                    ),
                activeSessionDid = ALICE_DID,
                isAppForeground = true,
                mutedActors = emptySet(),
            )

        assertEquals(DispatchOutcome.Drop(DropReason.UntrustedVerifier), outcome)
    }

    companion object {
        private const val ALICE_DID = "did:plc:alice"
        private const val BOB_DID = "did:plc:bob"
        private const val BLUESKY_VERIFIER_DID = "did:plc:z72i7hdynmk6r22z27h6tvur"

        private fun likePayloadFor(
            recipient: String,
            actor: String,
        ): Map<String, String> =
            mapOf(
                "reason" to "like",
                "uri" to "at://$actor/app.bsky.feed.like/3kabc",
                "subject" to "at://$recipient/app.bsky.feed.post/3kxyz",
                "actorDid" to actor,
                "actorHandle" to "bob.bsky.social",
                "actorDisplayName" to "Bob",
                "recipientDid" to recipient,
            )
    }
}
