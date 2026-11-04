package net.kikin.nubecita.core.feedcache

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val json =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

private fun postViewJson(uri: String): JsonObject =
    buildJsonObject {
        put("\$type", "app.bsky.feed.defs#postView")
        put("uri", uri)
        put("cid", "bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq")
        putJsonObject("author") {
            put("did", "did:plc:author")
            put("handle", "alice.bsky.social")
            put("displayName", "Alice")
        }
        putJsonObject("record") {
            put("\$type", "app.bsky.feed.post")
            put("text", "body of $uri")
            put("createdAt", "2026-04-15T19:51:12.861Z")
        }
        put("indexedAt", "2026-04-15T19:51:13.000Z")
    }

private fun storedPost(uri: String) =
    CachedPostEntity(
        accountDid = ACCOUNT,
        uri = uri,
        cid = "cid-$uri",
        authorDid = "did:plc:author",
        text = "body of $uri",
        postBlob = json.encodeToString(JsonObject.serializer(), postViewJson(uri)),
    )

private fun tombstone(
    uri: String,
    kind: String,
    author: String? = "did:plc:blocked",
) = CachedPostEntity(accountDid = ACCOUNT, uri = uri, authorDid = author, unavailable = kind)

private const val ACCOUNT = "did:plc:me"

private fun item(
    variant: String?,
    key: String,
    memberUris: List<String>,
    suppressed: Int = 0,
    ellipsis: Boolean? = null,
    rowType: String = FeedItemEntity.ROW_TYPE_ITEM,
): Pair<FeedItemEntity, List<FeedItemMemberEntity>> {
    val entity =
        FeedItemEntity(
            accountDid = ACCOUNT,
            feedType = "FOLLOWING",
            feedUri = "",
            position = 0,
            rowType = rowType,
            variant = variant,
            itemKey = key,
            leafUri = memberUris.lastOrNull(),
            suppressedReplyCount = suppressed,
            hasEllipsis = ellipsis,
        )
    val members =
        memberUris.mapIndexed { i, uri ->
            FeedItemMemberEntity(ACCOUNT, "FOLLOWING", "", 0, i, uri)
        }
    return entity to members
}

private fun rehydrate(
    pair: Pair<FeedItemEntity, List<FeedItemMemberEntity>>,
    posts: List<CachedPostEntity>,
): FeedItemUi? =
    pair.first.toFeedItemUi(
        members = pair.second,
        postsByUri = posts.associateBy { it.uri },
        viewerDid = ACCOUNT,
        prefs = ModerationPrefs.DEFAULT,
    )

internal class FeedProjectionMappersTest {
    @Test
    fun `a single round-trips with its suppressed count`() {
        val result = rehydrate(item("single", "at://p1", listOf("at://p1"), suppressed = 3), listOf(storedPost("at://p1")))

        val single = assertInstanceOf(FeedItemUi.Single::class.java, result)
        assertEquals("at://p1", single.post.id)
        assertEquals(3, single.suppressedReplyCount)
    }

    @Test
    fun `a cluster round-trips root parent leaf in ordinal order`() {
        val uris = listOf("at://root", "at://parent", "at://leaf")
        val result = rehydrate(item("cluster", "at://leaf", uris, ellipsis = true), uris.map(::storedPost))

        val cluster = assertInstanceOf(FeedItemUi.ReplyCluster::class.java, result)
        assertEquals("at://root", cluster.root.id)
        assertEquals("at://parent", cluster.parent.id)
        assertEquals("at://leaf", cluster.leaf.id)
        assertTrue(cluster.hasEllipsis)
    }

    @Test
    fun `hasEllipsis defaults to false when it was never stored`() {
        val uris = listOf("at://root", "at://parent", "at://leaf")
        val result = rehydrate(item("cluster", "at://leaf", uris, ellipsis = null), uris.map(::storedPost))

        assertEquals(false, assertInstanceOf(FeedItemUi.ReplyCluster::class.java, result).hasEllipsis)
    }

    @Test
    fun `a chain round-trips every post in render order`() {
        val uris = listOf("at://c1", "at://c2", "at://c3")
        val result = rehydrate(item("chain", "at://c3", uris), uris.map(::storedPost))

        val chain = assertInstanceOf(FeedItemUi.SelfThreadChain::class.java, result)
        assertEquals(uris, chain.posts.map { it.id })
    }

    @Test
    fun `member order comes from the ordinal, not the list order`() {
        // The write path may hand members over in any order; ordinal is the
        // contract, and a chain rendered out of order would be wrong rather
        // than merely untidy.
        val (entity, members) = item("chain", "at://c3", listOf("at://c1", "at://c2", "at://c3"))
        val shuffled = listOf(members[2], members[0], members[1])

        val chain =
            assertInstanceOf(
                FeedItemUi.SelfThreadChain::class.java,
                entity.toFeedItemUi(
                    members = shuffled,
                    postsByUri = listOf("at://c1", "at://c2", "at://c3").map(::storedPost).associateBy { it.uri },
                    viewerDid = ACCOUNT,
                    prefs = ModerationPrefs.DEFAULT,
                ),
            )

        assertEquals(listOf("at://c1", "at://c2", "at://c3"), chain.posts.map { it.id })
    }

    @Test
    fun `a chain reduced to one readable post becomes a Single`() {
        // Moderation or a missing row can thin a chain. A one-link chain is not
        // a chain, and rendering it as one would draw thread connectors around
        // a lone post.
        val uris = listOf("at://c1", "at://c2")
        val result = rehydrate(item("chain", "at://c2", uris), listOf(storedPost("at://c1")))

        assertInstanceOf(FeedItemUi.Single::class.java, result)
    }

    @Test
    fun `a cluster missing a slot is skipped rather than rendered partial`() {
        val uris = listOf("at://root", "at://parent", "at://leaf")
        // parent absent from the store
        val result = rehydrate(item("cluster", "at://leaf", uris), listOf(storedPost("at://root"), storedPost("at://leaf")))

        assertNull(result)
    }

    @Test
    fun `a blocked tombstone round-trips with its author`() {
        val result = rehydrate(item("blocked", "blocked:at://gone", listOf("at://gone")), listOf(tombstone("at://gone", "blocked")))

        val blocked = assertInstanceOf(FeedItemUi.Blocked::class.java, result)
        assertEquals("at://gone", blocked.uri)
        assertEquals("did:plc:blocked", blocked.authorDid)
    }

    @Test
    fun `a not-found tombstone round-trips`() {
        val result =
            rehydrate(item("notfound", "notfound:at://missing", listOf("at://missing")), listOf(tombstone("at://missing", "notfound", author = null)))

        assertEquals("at://missing", assertInstanceOf(FeedItemUi.NotFound::class.java, result).uri)
    }

    @Test
    fun `a seam row is not a FeedItemUi`() {
        // A seam is a row in this table but has no place in the shared sealed
        // type; the consuming feature maps it to its own UI item.
        val result = rehydrate(item(null, "seam:-1", emptyList(), rowType = FeedItemEntity.ROW_TYPE_SEAM), emptyList())

        assertNull(result)
    }

    @Test
    fun `an unreadable blob yields null rather than throwing`() {
        val broken = storedPost("at://p1").copy(postBlob = "{ not valid json")
        assertNull(rehydrate(item("single", "at://p1", listOf("at://p1")), listOf(broken)))
    }

    @Test
    fun `an unrecognized variant yields null`() {
        assertNull(rehydrate(item("something-new", "at://p1", listOf("at://p1")), listOf(storedPost("at://p1"))))
    }
}
