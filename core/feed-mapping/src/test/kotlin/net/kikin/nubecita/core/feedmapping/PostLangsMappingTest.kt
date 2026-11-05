package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.feed.PostView
import kotlinx.serialization.json.Json
import net.kikin.nubecita.data.models.EmbedUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Pins `app.bsky.feed.post.langs` → [net.kikin.nubecita.data.models.PostUi.langs].
 *
 * The field is a translation *hint* and the hint is only useful if it survives
 * the projection faithfully — including the two shapes that mean "we don't
 * know" (absent, and present-but-empty), which must stay distinguishable from
 * "the reader's language" at the call site. The mapper collapses both to an
 * empty list on purpose; these tests pin that collapse so it is a decision
 * rather than an accident.
 */
internal class PostLangsMappingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    @Test
    fun `a declared single language projects through`() {
        val mapped = decode(postJson(langs = """"langs": ["ja"],""")).toPostUiCore()
        assertNotNull(mapped)
        assertEquals(listOf("ja"), mapped!!.langs)
    }

    @Test
    fun `multiple declared languages keep their wire order`() {
        val mapped = decode(postJson(langs = """"langs": ["es", "en", "pt-BR"],""")).toPostUiCore()
        assertNotNull(mapped)
        assertEquals(listOf("es", "en", "pt-BR"), mapped!!.langs)
    }

    @Test
    fun `region subtags are preserved verbatim`() {
        // The mapper must not normalise: `es-419` and `es` are different hints,
        // and subtag matching is the consumer's job (nubecita-s6xk.2), not the
        // projection's. Folding here would silently lose information.
        val mapped = decode(postJson(langs = """"langs": ["es-419"],""")).toPostUiCore()
        assertNotNull(mapped)
        assertEquals(listOf("es-419"), mapped!!.langs)
    }

    @Test
    fun `an absent langs field projects to empty, not null`() {
        val mapped = decode(postJson(langs = "")).toPostUiCore()
        assertNotNull(mapped)
        assertTrue(mapped!!.langs.isEmpty())
    }

    @Test
    fun `an explicitly empty langs array projects to empty`() {
        val mapped = decode(postJson(langs = """"langs": [],""")).toPostUiCore()
        assertNotNull(mapped)
        assertTrue(mapped!!.langs.isEmpty())
    }

    @Test
    fun `langs does not disturb the rest of the projection`() {
        // Guards against a mapper edit that satisfies langs by breaking a
        // neighbouring field — the projection is one expression and every
        // field in it shares a blast radius.
        val mapped = decode(postJson(langs = """"langs": ["de"],""")).toPostUiCore()
        assertNotNull(mapped)
        assertEquals("hello", mapped!!.text)
        assertEquals("at://did:plc:fake/app.bsky.feed.post/p1", mapped.id)
        assertInstanceOf(EmbedUi.Empty::class.java, mapped.embed)
    }

    private fun decode(jsonString: String): PostView = json.decodeFromString(PostView.serializer(), jsonString)

    private fun postJson(langs: String): String =
        """
        {
          "uri": "at://did:plc:fake/app.bsky.feed.post/p1",
          "cid": "bafyreifakecid000000000000000000000000000000000",
          "author": {
            "did": "did:plc:fake",
            "handle": "fake.bsky.social"
          },
          "indexedAt": "2026-04-26T12:00:00Z",
          "record": {
            "${'$'}type": "app.bsky.feed.post",
            "text": "hello",
            $langs
            "createdAt": "2026-04-26T12:00:00Z"
          }
        }
        """.trimIndent()
}
