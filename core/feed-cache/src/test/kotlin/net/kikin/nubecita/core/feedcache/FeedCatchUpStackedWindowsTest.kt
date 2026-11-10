package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.TreeMap

/**
 * A multi-page catch-up into a partition that earlier catch-ups have already
 * prepended into — the state every daily reader's partition is in.
 *
 * The position queries are answered from one in-memory table rather than
 * stubbed per call. Stubbing them is how this went unnoticed: the existing
 * resume test hands `maxNegativePosition` a value that already assumes a single
 * prepend block, so it can only ever confirm that assumption.
 *
 * Each page runs through a fresh [FeedCatchUp], so the resume is derived from
 * stored state alone — the same position a resume after process death is in.
 */
internal class FeedCatchUpStackedWindowsTest {
    private val key = FeedKey.following(ACCOUNT)

    /** `feed_post` for the partition: position → uri tail. */
    private val entries = TreeMap<Int, String>()
    private var remoteKey: FeedRemoteKeyEntity? = null

    private val feedPostDao =
        mockk<FeedPostDao>(relaxed = true).also { dao ->
            coEvery { dao.maxPosition(ACCOUNT, TYPE, URI) } answers { entries.lastKeyOrNull() }
            coEvery { dao.minPosition(ACCOUNT, TYPE, URI) } answers { entries.firstKeyOrNull() }
            coEvery { dao.maxNegativePosition(ACCOUNT, TYPE, URI) } answers { entries.lowerKey(0) }
            coEvery { dao.firstPositionAtOrAbove(ACCOUNT, TYPE, URI, any()) } answers { entries.ceilingKey(arg(3)) }
            coEvery { dao.upsert(any()) } answers {
                firstArg<List<FeedPostEntity>>().forEach { entries[it.position] = it.uri.substringAfterLast('/') }
            }
        }

    private val remoteKeyDao =
        mockk<FeedRemoteKeyDao>(relaxed = true).also { dao ->
            coEvery { dao.remoteKey(ACCOUNT, TYPE, URI) } answers { remoteKey }
            coEvery { dao.updatePrependCursors(ACCOUNT, TYPE, URI, any(), any(), any()) } answers {
                remoteKey =
                    remoteKey?.copy(
                        boundarySince = arg(3),
                        fillCursor = arg(4),
                        pendingBoundary = arg(5),
                    )
            }
        }

    private val projectionWriter =
        mockk<FeedProjectionWriter>(relaxed = true).also {
            // Every entry renders, so each call stops after its first page and
            // the second page is genuinely a resume.
            coEvery { it.writePrepend(any(), any(), any()) } answers { secondArg<List<FeedViewPost>>().size }
        }

    private val networkSource = mockk<FeedNetworkSource>()

    private val transactionRunner =
        object : FeedCacheTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    private fun freshCatchUp() =
        FeedCatchUp(
            networkSource = networkSource,
            feedPostDao = feedPostDao,
            feedItemDao = mockk<FeedItemDao>(relaxed = true),
            cachedPostDao = mockk<CachedPostDao>(relaxed = true),
            projectionWriter = projectionWriter,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
        )

    @Test
    fun `a resumed page lands with its own catch-up, above earlier catch-ups`() =
        runTest {
            // The refresh's page, then two earlier catch-ups that each brought
            // a handful of entries into their own reserved window.
            (0..4).forEach { entries[it] = "head-$it" }
            entries[-250] = "earlier-1a"
            entries[-500] = "earlier-2a"
            entries[-499] = "earlier-2b"
            remoteKey = remoteKeyEntity(boundarySince = "boundary-0")

            coEvery { networkSource.fetchPage(key, cursor = null, since = "boundary-0", limit = any()) } returns
                Result.success(FeedNetworkPage(listOf(post("new-1"), post("new-2"), post("new-3")), "fill-1", "newest"))
            // The server hands back `since`: the range is exhausted after this page.
            coEvery { networkSource.fetchPage(key, cursor = "fill-1", since = "boundary-0", limit = any()) } returns
                Result.success(FeedNetworkPage(listOf(post("new-4"), post("new-5")), "boundary-0", null))

            assertEquals(CatchUpOutcome.MORE_PENDING, freshCatchUp().catchUp(key).getOrThrow())
            assertEquals(CatchUpOutcome.CAUGHT_UP, freshCatchUp().catchUp(key).getOrThrow())

            // Display order is ascending position. Everything this catch-up
            // fetched is newer than anything already stored, so all of it,
            // in fetch order, must come before the earlier windows.
            assertEquals(
                listOf(
                    "new-1",
                    "new-2",
                    "new-3",
                    "new-4",
                    "new-5",
                    "earlier-2a",
                    "earlier-2b",
                    "earlier-1a",
                    "head-0",
                    "head-1",
                    "head-2",
                    "head-3",
                    "head-4",
                ),
                entries.values.toList(),
            )
        }

    private fun remoteKeyEntity(boundarySince: String) =
        FeedRemoteKeyEntity(
            accountDid = ACCOUNT,
            feedType = TYPE,
            feedUri = URI,
            nextCursor = "append-cursor",
            boundarySince = boundarySince,
            fillCursor = null,
            pendingBoundary = null,
        )

    private fun <K, V> TreeMap<K, V>.firstKeyOrNull(): K? = if (isEmpty()) null else firstKey()

    private fun <K, V> TreeMap<K, V>.lastKeyOrNull(): K? = if (isEmpty()) null else lastKey()

    private companion object {
        const val ACCOUNT = "did:plc:me"
        const val TYPE = "FOLLOWING"
        const val URI = ""
    }
}

private val stackedWindowsJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

/** A minimal wire [FeedViewPost] whose post uri ends in [tail]. */
private fun post(tail: String): FeedViewPost {
    val wire =
        buildJsonObject {
            put("\$type", "app.bsky.feed.defs#postView")
            put("uri", "at://did:plc:abc/app.bsky.feed.post/$tail")
            put("cid", "bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq")
            putJsonObject("author") {
                put("did", "did:plc:abc")
                put("handle", "alice.bsky.social")
                put("displayName", "Alice")
            }
            putJsonObject("record") {
                put("\$type", "app.bsky.feed.post")
                put("text", "post $tail")
                put("createdAt", "2026-04-15T19:51:12.861Z")
                putJsonArray("langs") { add("en") }
            }
            put("indexedAt", "2026-04-15T19:51:13.000Z")
        }
    return FeedViewPost(post = stackedWindowsJson.decodeFromJsonElement(PostView.serializer(), wire))
}
