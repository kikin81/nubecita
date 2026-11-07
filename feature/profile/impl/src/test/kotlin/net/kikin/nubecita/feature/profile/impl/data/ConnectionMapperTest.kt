package net.kikin.nubecita.feature.profile.impl.data

import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.VerifiedBadge
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class ConnectionMapperTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun profile(
        did: String = "did:plc:alice",
        viewer: String? = null,
        verification: String? = null,
    ): ProfileView =
        json.decodeFromString(
            ProfileView.serializer(),
            buildString {
                append("""{"did":"$did","handle":"alice.test","displayName":"Alice"""")
                viewer?.let { append(""","viewer":$it""") }
                verification?.let { append(""","verification":$it""") }
                append("}")
            },
        )

    @Test
    fun `maps viewer following to followUri`() {
        val uri = "at://did:plc:me/app.bsky.graph.follow/abc"

        val row = profile(viewer = """{"following":"$uri"}""").toConnectionRowUi(viewerDid = VIEWER)

        assertEquals(uri, row.followUri)
    }

    @Test
    fun `no viewer state means not following`() {
        val row = profile().toConnectionRowUi(viewerDid = VIEWER)

        assertNull(row.followUri)
        assertTrue(row.canFollow)
    }

    @Test
    fun `the viewer's own row cannot be followed`() {
        assertFalse(profile(did = VIEWER).toConnectionRowUi(viewerDid = VIEWER).canFollow)
    }

    @Test
    fun `an account the viewer blocks cannot be followed`() {
        val row = profile(viewer = """{"blocking":"at://did:plc:me/app.bsky.graph.block/b"}""").toConnectionRowUi(viewerDid = VIEWER)

        assertFalse(row.canFollow)
    }

    @Test
    fun `an account that blocks the viewer cannot be followed`() {
        assertFalse(profile(viewer = """{"blockedBy":true}""").toConnectionRowUi(viewerDid = VIEWER).canFollow)
    }

    @Test
    fun `a deleted account cannot be followed`() {
        val row =
            json
                .decodeFromString(ProfileView.serializer(), """{"did":"did:plc:gone","handle":"missing.invalid"}""")
                .toConnectionRowUi(viewerDid = VIEWER)

        assertFalse(row.canFollow)
    }

    @Test
    fun `verification maps to the badge`() {
        val row =
            profile(verification = """{"verifications":[],"verifiedStatus":"valid","trustedVerifierStatus":"none"}""")
                .toConnectionRowUi(viewerDid = VIEWER)

        assertEquals(VerifiedBadge.Verified, row.actor.verifiedBadge)
    }

    private companion object {
        const val VIEWER = "did:plc:me"
    }
}
