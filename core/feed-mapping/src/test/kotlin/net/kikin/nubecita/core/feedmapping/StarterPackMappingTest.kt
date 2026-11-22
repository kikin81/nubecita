package net.kikin.nubecita.core.feedmapping

import io.github.kikin81.atproto.app.bsky.graph.StarterPackView
import io.github.kikin81.atproto.app.bsky.graph.StarterPackViewBasic
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class StarterPackMappingTest {
    private val json =
        Json {
            ignoreUnknownKeys = true
            explicitNulls = false
        }

    private val creator = """"creator":{"did":"did:plc:c","handle":"creator.test"}"""

    private fun view(extra: String): StarterPackView =
        json.decodeFromString(
            StarterPackView.serializer(),
            """{"uri":"at://did:plc:c/app.bsky.graph.starterpack/3k","cid":"bafyreie6lwqlqayknghcimbpc5hec7npn5otdlgds7ugauyiwjktc423ne",$creator,"indexedAt":"2025-01-01T00:00:00Z"$extra}""",
        )

    private fun basic(extra: String): StarterPackViewBasic =
        json.decodeFromString(
            StarterPackViewBasic.serializer(),
            """{"uri":"at://did:plc:c/app.bsky.graph.starterpack/3k","cid":"bafyreie6lwqlqayknghcimbpc5hec7npn5otdlgds7ugauyiwjktc423ne",$creator,"indexedAt":"2025-01-01T00:00:00Z"$extra}""",
        )

    private fun member(
        did: String,
        avatar: String?,
    ) = """{"uri":"at://did:plc:c/app.bsky.graph.listitem/$did","subject":{"did":"did:plc:$did","handle":"$did.test"${avatar?.let { ""","avatar":"$it"""" } ?: ""}}}"""

    @Test
    fun `a view maps name, creator, member count and the first four member avatars`() {
        val pack =
            view(
                ""","record":{"name":"Android devs","list":"at://did:plc:c/app.bsky.graph.list/1"},""" +
                    """"list":{"uri":"at://did:plc:c/app.bsky.graph.list/1","cid":"bafyreie6lwqlqayknghcimbpc5hec7npn5otdlgds7ugauyiwjktc423ne","name":"x","purpose":"app.bsky.graph.defs#referencelist","listItemCount":72,"indexedAt":"2025-01-01T00:00:00Z"},""" +
                    """"listItemsSample":[${(1..6).joinToString(",") { member("m$it", "https://cdn.test/$it.jpg") }}]""",
            ).toStarterPackUi()!!

        assertEquals("at://did:plc:c/app.bsky.graph.starterpack/3k", pack.uri)
        assertEquals("Android devs", pack.name)
        assertEquals("creator.test", pack.creatorHandle)
        assertEquals(72, pack.memberCount)
        assertEquals((1..4).map { "https://cdn.test/$it.jpg" }, pack.avatarUrls)
    }

    @Test
    fun `members without an avatar are skipped in the sample`() {
        val pack =
            view(
                ""","record":{"name":"P"},"listItemsSample":[${member("a", null)},${member("b", "https://cdn.test/b.jpg")}]""",
            ).toStarterPackUi()!!

        assertEquals(listOf("https://cdn.test/b.jpg"), pack.avatarUrls)
    }

    @Test
    fun `a view with no list and no sample has no count and no avatars`() {
        val pack = view(""","record":{"name":"P"}""").toStarterPackUi()!!

        assertNull(pack.memberCount)
        assertEquals(emptyList<String>(), pack.avatarUrls)
    }

    @Test
    fun `a view without a usable name falls back to the list name, then is dropped`() {
        val withListName =
            view(""","record":{},"list":{"uri":"at://did:plc:c/app.bsky.graph.list/1","cid":"bafyreie6lwqlqayknghcimbpc5hec7npn5otdlgds7ugauyiwjktc423ne","name":"List name","purpose":"app.bsky.graph.defs#referencelist","indexedAt":"2025-01-01T00:00:00Z"}""")
        assertEquals("List name", withListName.toStarterPackUi()!!.name)

        assertNull(view(""","record":{"name":"   "}""").toStarterPackUi())
    }

    @Test
    fun `a basic view maps its list item count and has no avatars`() {
        val pack = basic(""","record":{"name":"Basic"},"listItemCount":9""").toStarterPackUi()!!

        assertEquals("Basic", pack.name)
        assertEquals(9, pack.memberCount)
        assertEquals(emptyList<String>(), pack.avatarUrls)
    }
}
