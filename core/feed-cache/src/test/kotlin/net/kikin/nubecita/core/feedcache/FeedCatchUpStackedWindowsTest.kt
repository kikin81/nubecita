package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsEvent
import net.kikin.nubecita.core.analytics.AnalyticsScreen
import net.kikin.nubecita.core.analytics.FeedCatchUpExhausted
import net.kikin.nubecita.core.analytics.UserProperty
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.TreeMap
import net.kikin.nubecita.core.analytics.FeedType as AnalyticsFeedType

/**
 * A multi-page catch-up into a partition that earlier catch-ups have already
 * prepended into — the state every daily reader's partition is in.
 *
 * The position queries are answered from one in-memory table rather than
 * stubbed per call. Stubbing them is how this went unnoticed: the resume test
 * used to hand the old `maxNegativePosition` query a value that already assumed
 * a single prepend block, so it could only ever confirm that assumption.
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
            coEvery { dao.firstPositionAtOrAbove(ACCOUNT, TYPE, URI, any()) } answers { entries.ceilingKey(arg(3)) }
            coEvery { dao.countInRange(ACCOUNT, TYPE, URI, any(), any()) } answers { entries.subMap(arg(3), arg(4)).size }
            coEvery { dao.upsert(any()) } answers {
                firstArg<List<FeedPostEntity>>().forEach { entries[it.position] = it.uri.substringAfterLast('/') }
            }
        }

    private val remoteKeyDao =
        mockk<FeedRemoteKeyDao>(relaxed = true).also { dao ->
            coEvery { dao.remoteKey(ACCOUNT, TYPE, URI) } answers { remoteKey }
            coEvery { dao.updatePrependCursors(ACCOUNT, TYPE, URI, any(), any(), any(), any()) } answers {
                remoteKey =
                    remoteKey?.copy(
                        boundarySince = arg(3),
                        fillCursor = arg(4),
                        pendingBoundary = arg(5),
                        fillNextPosition = arg(6),
                    )
            }
        }

    /** `feed_item` positions holding a projected item (seams aside). */
    private val items = sortedSetOf<Int>()

    /** `feed_item` seam rows: position → whether the marker is passive (exhausted). */
    private val seams = TreeMap<Int, Boolean>()

    /** `feed_item_member` rows per position. A projected item has at least one. */
    private val members = mutableMapOf<Int, Int>()

    /** Item positions a write replaced while they were already taken. */
    private val overwrittenItems = mutableListOf<Int>()

    /** How many of each page's entries the projection renders; all of them by default. */
    private var renders: (Int) -> Int = { it }

    private val feedItemDao =
        mockk<FeedItemDao>(relaxed = true).also { dao ->
            coEvery { dao.firstItemPositionAtOrAbove(ACCOUNT, TYPE, URI, any()) } answers { items.ceiling(arg(3)) }
            // The DAO contract after this change: only the active seam goes.
            coEvery { dao.clearSeam(ACCOUNT, TYPE, URI) } answers { seams.entries.removeIf { !it.value } }
            coEvery { dao.deleteMembersUnderSeams(ACCOUNT, TYPE, URI) } answers { members.keys.removeAll(seams.keys) }
            coEvery { dao.upsert(any()) } answers {
                firstArg<List<FeedItemEntity>>().forEach { row ->
                    if (row.rowType == FeedItemEntity.ROW_TYPE_SEAM) {
                        // Same primary key: a seam written here replaces the item.
                        if (items.remove(row.position)) overwrittenItems += row.position
                        seams[row.position] = row.seamExhausted == true
                    } else {
                        items += row.position
                    }
                }
            }
        }

    private val projectionWriter =
        mockk<FeedProjectionWriter>(relaxed = true).also {
            // Like the real write, the block of items starts at the base
            // position, one member row each, replacing whatever row was there.
            coEvery { it.writePrepend(any(), any(), any()) } answers {
                val count = renders(secondArg<List<FeedViewPost>>().size)
                val base = thirdArg<Int>()
                (base until base + count).forEach { position ->
                    seams.remove(position)
                    if (!items.add(position)) overwrittenItems += position
                    members[position] = 1
                }
                count
            }
        }

    /** A stored post with its projected item, as a refresh or earlier fill leaves it. */
    private fun storedPost(
        position: Int,
        tail: String = "p$position",
    ) {
        entries[position] = tail
        items += position
        members[position] = 1
    }

    /** Every `fetchPage` call made: the `since` and `cursor` it was asked for. */
    private val fetches = mutableListOf<Pair<String?, String?>>()

    /**
     * Serves `limit` entries per page for any `since`, never exhausting the
     * range — more new posts than any window holds. Each page reports a
     * `startCursor` (`newest-<page>`) unless [withStartCursor] is false.
     */
    private fun endlessRange(withStartCursor: Boolean = true) {
        coEvery { networkSource.fetchPage(key, cursor = any(), since = any(), limit = any()) } answers {
            fetches += arg<String?>(2) to arg<String?>(1)
            val page = fetches.size
            val limit = arg<Int>(3)
            Result.success(
                FeedNetworkPage(
                    (1..limit).map { post("p$page-$it") },
                    "fill-$page",
                    if (withStartCursor) "newest-$page" else null,
                ),
            )
        }
    }

    /** Runs catch-ups until one stops asking for more. */
    private suspend fun catchUpUntilSettled(): CatchUpOutcome {
        while (true) {
            val outcome = freshCatchUp().catchUp(key).getOrThrow()
            if (outcome != CatchUpOutcome.MORE_PENDING) return outcome
        }
    }

    private val loggedEvents = mutableListOf<AnalyticsEvent>()
    private val recordingAnalytics =
        object : AnalyticsClient {
            override fun log(event: AnalyticsEvent) {
                loggedEvents += event
            }

            override fun setUserProperty(property: UserProperty) = Unit

            override fun logScreen(screen: AnalyticsScreen) = Unit
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
            feedItemDao = feedItemDao,
            cachedPostDao = mockk<CachedPostDao>(relaxed = true),
            projectionWriter = projectionWriter,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = transactionRunner,
            analytics = recordingAnalytics,
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

    @Test
    fun `a fill stops short of an item the entries table no longer accounts for`() =
        runTest {
            // feed_post and feed_item are not packed alike. Here the entry at
            // -260 is gone — deleted upstream, say, while it was a cluster's
            // context post — but the item that showed it is still on screen.
            // Counting only entries, the slots from -262 up to -251 look free.
            entries[-250] = "earlier-1a"
            (0..4).forEach { entries[it] = "head-$it" }
            items += -260
            items += (-250..-245)
            remoteKey =
                remoteKeyEntity(boundarySince = "boundary-0").copy(
                    fillCursor = "fill-1",
                    pendingBoundary = "newest",
                    fillNextPosition = -262,
                )
            coEvery { networkSource.fetchPage(key, cursor = "fill-1", since = "boundary-0", limit = any()) } answers {
                val limit = arg<Int>(3)
                Result.success(FeedNetworkPage((1..minOf(limit, 5)).map { post("new-$it") }, "fill-2", null))
            }

            freshCatchUp().catchUp(key).getOrThrow()

            assertEquals(emptyList<Int>(), overwrittenItems, "a prepend replaced items the reader can see")
        }

    @Test
    fun `two catch-ups on one partition run one after the other`() =
        runTest {
            // Paging's PREPEND and a "show newest" can ask at once. Interleaved,
            // both would read the same stored resume point and write their
            // pages over each other.
            (0..4).forEach { entries[it] = "head-$it" }
            remoteKey = remoteKeyEntity(boundarySince = "boundary-0")
            val firstFetchEntered = CompletableDeferred<Unit>()
            val releaseFirstFetch = CompletableDeferred<Unit>()
            var fetches = 0
            coEvery { networkSource.fetchPage(key, cursor = any(), since = "boundary-0", limit = any()) } coAnswers {
                fetches++
                if (fetches == 1) {
                    firstFetchEntered.complete(Unit)
                    releaseFirstFetch.await()
                }
                Result.success(FeedNetworkPage(listOf(post("new-$fetches")), "fill-$fetches", "newest-$fetches"))
            }

            val first = async { freshCatchUp().catchUp(key) }
            firstFetchEntered.await()
            val second = async { freshCatchUp().catchUp(key) }
            testScheduler.advanceUntilIdle()
            assertEquals(1, fetches, "the second catch-up fetched while the first was still writing")

            releaseFirstFetch.complete(Unit)
            first.await()
            second.await()
            assertEquals(2, fetches)
        }

    // --- markers never replace a post (nubecita-2xk0.1) ---------------------

    @Test
    fun `a full window leaves its bottom slot for the marker`() =
        runTest {
            // On device the window filled right up to the head, and the marker
            // was written over the post sitting there.
            (0..4).forEach { storedPost(it) }
            remoteKey = remoteKeyEntity(boundarySince = "boundary-0")
            endlessRange()

            catchUpUntilSettled()

            assertEquals(emptyList<Int>(), overwrittenItems, "a write replaced a post")
            assertTrue(-1 !in entries, "the slot above the head holds an entry")
            assertEquals(mapOf(-1 to true), seams.toMap(), "the passive marker is not in the reserved slot")
            // Every post the refresh wrote is still there.
            assertTrue(items.containsAll((0..4).toList()))
        }

    @Test
    fun `the active seam sits where the next fill page starts`() =
        runTest {
            // A page whose projection dropped a row: base + projected is a slot
            // that holds an entry, which the old seam write landed on.
            (0..4).forEach { storedPost(it) }
            remoteKey = remoteKeyEntity(boundarySince = "boundary-0")
            renders = { entries -> entries - 1 }
            coEvery { networkSource.fetchPage(key, cursor = null, since = "boundary-0", limit = any()) } returns
                Result.success(FeedNetworkPage(listOf(post("n1"), post("n2"), post("n3")), "fill-1", "newest"))

            freshCatchUp().catchUp(key).getOrThrow()

            val base = -FeedCatchUp.CATCH_UP_ENTRY_BUDGET
            assertEquals(mapOf(base + 3 to false), seams.toMap())
            assertTrue(base + 3 !in entries)
            assertEquals(base + 3, remoteKey?.fillNextPosition)
        }

    @Test
    fun `a marker is not written over a post when no slot is free`() =
        runTest {
            // The state a full window left behind before the slot was reserved:
            // entries packed right down to the head, the fill still pending.
            (-1250..-1001).forEach { storedPost(it) }
            (-1000..-996).forEach { storedPost(it) }
            remoteKey =
                remoteKeyEntity(boundarySince = "boundary-0").copy(
                    fillCursor = "fill-5",
                    pendingBoundary = "newest",
                    fillNextPosition = -1000,
                )

            freshCatchUp().catchUp(key).getOrThrow()

            assertEquals(emptyList<Int>(), overwrittenItems)
            assertTrue(items.containsAll((-1250..-996).toList()))
        }

    @Test
    fun `member rows the old marker write orphaned are removed`() =
        runTest {
            // The Fold: a marker at -1000 over the post that was there, and that
            // post's member row still under it.
            (-1000..-996).forEach { storedPost(it) }
            items -= -1000
            seams[-1000] = true
            remoteKey = remoteKeyEntity(boundarySince = "boundary-0")
            endlessRange()

            freshCatchUp().catchUp(key).getOrThrow()

            assertTrue(-1000 !in members, "a member row is still under the marker")
            assertEquals(true, seams[-1000], "the existing passive marker was removed")
        }

    // --- a capped fill completes instead of stalling (nubecita-2xk0.2) -------

    @Test
    fun `a capped fill completes with a gap, and the next catch-up fetches from the newest`() =
        runTest {
            (0..4).forEach { storedPost(it) }
            remoteKey = remoteKeyEntity(boundarySince = "boundary-0")
            endlessRange()

            assertEquals(CatchUpOutcome.CAUGHT_UP_WITH_GAP, catchUpUntilSettled())

            // The fill is done: the boundary is the newest page it fetched,
            // and nothing is left pending to resume into the full window.
            assertEquals("newest-1", remoteKey?.boundarySince)
            assertEquals(null, remoteKey?.fillCursor)
            assertEquals(null, remoteKey?.fillNextPosition)
            assertEquals(null, remoteKey?.pendingBoundary)

            val fetchesBefore = fetches.size
            freshCatchUp().catchUp(key).getOrThrow()

            assertEquals(fetchesBefore + 1, fetches.size, "the catch-up after a capped fill fetched nothing")
            assertEquals("newest-1" to null, fetches.last(), "it should start fresh from the promoted boundary")
            // Into a new window above everything cached.
            assertTrue(entries.firstKey() < -FeedCatchUp.CATCH_UP_ENTRY_BUDGET)
        }

    @Test
    fun `a partition already stalled recovers on its next catch-up`() =
        runTest {
            // The Fold after 1.375.1: a full window, the fill still pending, and
            // every catch-up returning without a fetch.
            (-1250..-996).forEach { storedPost(it) }
            remoteKey =
                remoteKeyEntity(boundarySince = "boundary-0").copy(
                    fillCursor = "fill-5",
                    pendingBoundary = "newest",
                    fillNextPosition = -1000,
                )
            endlessRange()

            assertEquals(CatchUpOutcome.CAUGHT_UP_WITH_GAP, freshCatchUp().catchUp(key).getOrThrow())
            assertEquals("newest", remoteKey?.boundarySince)
            assertEquals(null, remoteKey?.fillCursor)

            freshCatchUp().catchUp(key).getOrThrow()
            assertEquals(listOf("newest" to null), fetches, "the recovered partition fetched nothing")
        }

    @Test
    fun `a capped fill with no pending boundary still clears and fetches again`() =
        runTest {
            // Unreachable for getTimeline, which always reports a cursor; handled
            // anyway, because a null here would otherwise restart the stall.
            (0..4).forEach { storedPost(it) }
            remoteKey = remoteKeyEntity(boundarySince = "boundary-0")
            endlessRange(withStartCursor = false)

            assertEquals(CatchUpOutcome.CAUGHT_UP_WITH_GAP, catchUpUntilSettled())
            assertEquals("boundary-0", remoteKey?.boundarySince)
            assertEquals(null, remoteKey?.fillCursor)
            assertEquals(null, remoteKey?.fillNextPosition)

            val fetchesBefore = fetches.size
            freshCatchUp().catchUp(key).getOrThrow()
            assertEquals(fetchesBefore + 1, fetches.size)
            assertEquals("boundary-0" to null, fetches.last())
        }

    @Test
    fun `a second overflow keeps the first gap marked`() =
        runTest {
            (0..4).forEach { storedPost(it) }
            remoteKey = remoteKeyEntity(boundarySince = "boundary-0")
            endlessRange()

            assertEquals(CatchUpOutcome.CAUGHT_UP_WITH_GAP, catchUpUntilSettled())
            assertEquals(CatchUpOutcome.CAUGHT_UP_WITH_GAP, catchUpUntilSettled())

            // One passive marker per completed window, each in its own reserved
            // slot, and the first one where it was.
            assertEquals(mapOf(-251 to true, -1 to true), seams.toMap())
            assertEquals(emptyList<Int>(), overwrittenItems)
        }

    @Test
    fun `reaching the budget is counted once, and an exhausted range is not`() =
        runTest {
            (0..4).forEach { storedPost(it) }
            remoteKey = remoteKeyEntity(boundarySince = "boundary-0")
            endlessRange()

            catchUpUntilSettled()

            assertEquals(
                listOf(FeedCatchUpExhausted(AnalyticsFeedType.Following, postsFetched = FeedCatchUp.CATCH_UP_ENTRY_BUDGET - 1)),
                loggedEvents,
            )

            // A fill that reaches the reader's posts is an ordinary catch-up.
            loggedEvents.clear()
            coEvery { networkSource.fetchPage(key, cursor = any(), since = any(), limit = any()) } returns
                Result.success(FeedNetworkPage(listOf(post("last")), "newest-1", "newest-x"))
            freshCatchUp().catchUp(key).getOrThrow()
            assertEquals(emptyList<AnalyticsEvent>(), loggedEvents)
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
