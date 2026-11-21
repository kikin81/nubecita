package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.actor.ProfileView
import io.github.kikin81.atproto.app.bsky.actor.ProfileViewBasic
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

/**
 * The actor → [net.kikin.nubecita.data.models.AuthorUi] projection carries
 * the profile's free-form pronouns and the viewer's follow-record URI, both
 * straight from the wire (`app.bsky.actor.defs#profileViewBasic` /
 * `#profileView`).
 */
internal class AuthorUiMappingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private fun basic(extra: String): ProfileViewBasic =
        json.decodeFromString(
            ProfileViewBasic.serializer(),
            """{"did":"did:plc:a","handle":"a.test"$extra}""",
        )

    private fun full(extra: String): ProfileView =
        json.decodeFromString(
            ProfileView.serializer(),
            """{"did":"did:plc:a","handle":"a.test"$extra}""",
        )

    @Test
    fun `toAuthorUi maps pronouns and follow uri`() {
        val author =
            basic(
                ""","pronouns":"he/him","viewer":{"following":"at://did:plc:me/app.bsky.graph.follow/3k"}""",
            ).toAuthorUi()

        assertEquals("he/him", author.pronouns)
        assertEquals("at://did:plc:me/app.bsky.graph.follow/3k", author.viewerFollowUri)
    }

    @Test
    fun `toAuthorUi trims pronouns and treats blank as absent`() {
        assertEquals("she/her", basic(""","pronouns":"  she/her """").toAuthorUi().pronouns)
        assertNull(basic(""","pronouns":"   """").toAuthorUi().pronouns)
    }

    @Test
    fun `toAuthorUi without pronouns or viewer maps both to null`() {
        val author = basic("").toAuthorUi()

        assertNull(author.pronouns)
        assertNull(author.viewerFollowUri)
    }

    @Test
    fun `ProfileView toAuthorUi maps pronouns and follow uri`() {
        val author =
            full(
                ""","pronouns":"they/them","viewer":{"following":"at://did:plc:me/app.bsky.graph.follow/9z"}""",
            ).toAuthorUi()

        assertEquals("they/them", author.pronouns)
        assertEquals("at://did:plc:me/app.bsky.graph.follow/9z", author.viewerFollowUri)
    }
}
