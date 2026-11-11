package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.dao.FeedPartitionStateDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedPartitionStateEntity
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedViewPrefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val writerJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

private fun postJson(uri: String): JsonObject =
    buildJsonObject {
        put("\$type", "app.bsky.feed.defs#postView")
        put("uri", uri)
        put("cid", "bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq")
        putJsonObject("author") {
            put("did", "did:plc:stranger")
            put("handle", "stranger.bsky.social")
        }
        putJsonObject("record") {
            put("\$type", "app.bsky.feed.post")
            put("text", "text of $uri")
            put("createdAt", "2026-04-15T19:51:12.861Z")
        }
        put("indexedAt", "2026-04-15T19:51:13.000Z")
    }

/** A reply entry — the shape the reply tuners act on. */
private fun replyEntry(uri: String): FeedViewPost {
    val wire =
        buildJsonObject {
            put("\$type", "app.bsky.feed.defs#feedViewPost")
            put("post", postJson(uri))
            putJsonObject("reply") {
                put("root", postJson("at://did:plc:other/app.bsky.feed.post/root"))
                put("parent", postJson("at://did:plc:other/app.bsky.feed.post/parent"))
            }
        }
    return writerJson.decodeFromJsonElement(FeedViewPost.serializer(), wire)
}

/** A plain top-level entry — its own thread root, so two of these survive de-duplication together. */
private fun topLevelEntry(uri: String): FeedViewPost {
    val wire =
        buildJsonObject {
            put("\$type", "app.bsky.feed.defs#feedViewPost")
            put("post", postJson(uri))
        }
    return writerJson.decodeFromJsonElement(FeedViewPost.serializer(), wire)
}

internal class FeedProjectionWriterTest {
    private val feedItemDao = mockk<FeedItemDao>(relaxed = true)
    private val partitionStateDao = mockk<FeedPartitionStateDao>(relaxed = true)
    private val cachedPostDao = mockk<CachedPostDao>(relaxed = true)
    private val feedPostDao = mockk<FeedPostDao>(relaxed = true)
    private val sessionStateProvider =
        mockk<SessionStateProvider>().also {
            coEvery { it.state } returns MutableStateFlow(SessionState.SignedIn(handle = "me.bsky.social", did = "did:plc:me"))
        }
    private val moderationPreferences =
        mockk<ModerationPreferencesRepository>().also {
            coEvery { it.prefs } returns MutableStateFlow(ModerationPrefs.DEFAULT)
        }
    private val feedViewPreferences = mockk<FeedViewPreferencesRepository>()

    private fun writer() =
        FeedProjectionWriter(
            feedItemDao = feedItemDao,
            partitionStateDao = partitionStateDao,
            cachedPostDao = cachedPostDao,
            feedPostDao = feedPostDao,
            sessionStateProvider = sessionStateProvider,
            moderationPreferences = moderationPreferences,
            feedViewPreferences = feedViewPreferences,
        )

    private fun prefs(hideReplies: Boolean) {
        coEvery { feedViewPreferences.prefs } returns
            MutableStateFlow(FeedViewPrefs(hideReplies = hideReplies, hideRepliesByUnfollowed = false))
    }

    @Test
    fun `a destructive refresh forgets the pill's dismissal mark`() =
        runTest {
            // The mark is a position, and a refresh restarts positions at 0.
            // Keeping a mark of, say, -250 would outrank every later prepend
            // — nothing is written below -250 again — and the reader would
            // never see another new-posts pill (nubecita-3zol).
            prefs(hideReplies = false)
            writer().writeRefresh(FeedKey.following("did:plc:me"), listOf(topLevelEntry("at://p/1")))

            coVerify {
                partitionStateDao.updatePillDismissed("did:plc:me", "FOLLOWING", "", position = null)
            }
        }

    @Test
    fun `a reproject keeps the dismissal mark`() =
        runTest {
            // Unlike a refresh, this rebuilds from the partition's existing
            // lowest position, so the mark still points at a real place.
            prefs(hideReplies = false)
            writer().reproject(FeedKey.following("did:plc:me"))

            coVerify(exactly = 0) {
                partitionStateDao.updatePillDismissed(any(), any(), any(), any())
            }
        }

    @Test
    fun `a follow-scoped feed applies the reply tuners`() =
        runTest {
            prefs(hideReplies = true)
            val rows = slot<List<FeedItemEntity>>()
            coEvery { feedItemDao.upsert(capture(rows)) } returns Unit

            writer().writeRefresh(FeedKey.following("did:plc:me"), listOf(replyEntry("at://p/1")))

            // Every entry was a reply and replies are hidden, so the partition
            // projects to nothing and no rows are written at all.
            coVerify(exactly = 0) { feedItemDao.upsert(any()) }
        }

    @Test
    fun `a generator feed is left unfiltered`() =
        runTest {
            // The same page and the same preferences, the opposite outcome. A
            // generator curates its own output; applying reply tuners to it
            // would strip out most of what the algorithm selected.
            prefs(hideReplies = true)
            val rows = slot<List<FeedItemEntity>>()
            coEvery { feedItemDao.upsert(capture(rows)) } returns Unit

            writer().writeRefresh(
                FeedKey("did:plc:me", FeedType.DISCOVER, "at://did:plc:gen/app.bsky.feed.generator/whats-hot"),
                listOf(replyEntry("at://p/1")),
            )

            assertTrue(rows.isCaptured, "a generator page must still project")
            assertEquals(1, rows.captured.size)
        }

    @Test
    fun `the refresh clears the partition before writing`() =
        runTest {
            // A REFRESH rebuilds; leaving the old projection would interleave
            // stale items with the new page at overlapping positions.
            prefs(hideReplies = false)

            writer().writeRefresh(FeedKey.following("did:plc:me"), listOf(replyEntry("at://p/1")))

            coVerify { feedItemDao.clearPartition("did:plc:me", "FOLLOWING", "") }
        }

    @Test
    fun `a prepend drops the page's overlap with what is already rendered`() =
        runTest {
            // A `since`-bounded catch-up overlaps the cached head by design, so
            // the boundary entries come back. The thread-root pass is exempt on
            // a prepend (D7) and would let them through; this pass is not, and
            // the overlap would otherwise render a second time (D29).
            prefs(hideReplies = false)
            coEvery { feedItemDao.renderedLeafUris("did:plc:me", "FOLLOWING", "") } returns listOf("at://p/1")
            val rows = slot<List<FeedItemEntity>>()
            coEvery { feedItemDao.upsert(capture(rows)) } returns Unit

            val written =
                writer().writePrepend(
                    FeedKey.following("did:plc:me"),
                    listOf(topLevelEntry("at://p/2"), topLevelEntry("at://p/1")),
                    basePosition = -250,
                )

            assertEquals(1, written)
            assertEquals(listOf("at://p/2"), rows.captured.map { it.leafUri })
        }

    @Test
    fun `a prepend that is pure overlap writes nothing`() =
        runTest {
            prefs(hideReplies = false)
            coEvery { feedItemDao.renderedLeafUris("did:plc:me", "FOLLOWING", "") } returns listOf("at://p/1")

            val written =
                writer().writePrepend(
                    FeedKey.following("did:plc:me"),
                    listOf(topLevelEntry("at://p/1")),
                    basePosition = -250,
                )

            assertEquals(0, written)
            coVerify(exactly = 0) { feedItemDao.upsert(any()) }
        }

    @Test
    fun `a prepend writes at the position the caller allocated`() =
        runTest {
            // The mediator owns the window arithmetic: only it knows how much
            // of the reserved slice the fill has already used.
            prefs(hideReplies = false)
            val rows = slot<List<FeedItemEntity>>()
            coEvery { feedItemDao.upsert(capture(rows)) } returns Unit

            val written =
                writer().writePrepend(
                    FeedKey.following("did:plc:me"),
                    listOf(topLevelEntry("at://p/1"), topLevelEntry("at://p/2")),
                    basePosition = -250,
                )

            assertEquals(2, written)
            assertEquals(listOf(-250, -249), rows.captured.map { it.position })
        }

    @Test
    fun `a prepend neither clears the partition nor seeds from it`() =
        runTest {
            // D7: the entries below the seam were fetched in an earlier
            // session, so an occurrence down there is one the reader already
            // scrolled past. Seeding from it would suppress the new reply and
            // the thread would never resurface — the regression that comes free
            // with persisting the projection.
            prefs(hideReplies = false)

            writer().writePrepend(
                FeedKey.following("did:plc:me"),
                listOf(replyEntry("at://p/1")),
                basePosition = -250,
            )

            coVerify(exactly = 0) { feedItemDao.clearPartition(any(), any(), any()) }
            coVerify(exactly = 0) { feedItemDao.seenThreadRoots(any(), any(), any()) }
            coVerify(exactly = 0) { feedItemDao.renderedPostUris(any(), any(), any()) }
            // No chain merge across the seam either.
            coVerify(exactly = 0) { feedItemDao.tailItem(any(), any(), any()) }
        }

    @Test
    fun `the projection records the preferences it was built with`() =
        runTest {
            prefs(hideReplies = false)
            val row = statefulPartitionRow(initial = null)

            writer().writeRefresh(FeedKey.following("did:plc:me"), listOf(replyEntry("at://p/1")))

            val fingerprint = row()?.prefsFingerprint
            assertTrue(fingerprint != null, "a projection must record its preferences, on a partition with no row yet")
            assertTrue(
                fingerprint?.contains("replies=false") == true,
                "fingerprint should reflect the reply tuner, was $fingerprint",
            )
        }

    @Test
    fun `a different reply tuner yields a different fingerprint`() =
        runTest {
            // The whole mechanism rests on this: equal preferences must give
            // equal strings and different ones must differ, or a stale
            // projection either never refreshes or refreshes forever.
            val captured = mutableListOf<String?>()
            coEvery { partitionStateDao.savePrefsFingerprint(any(), any(), any(), any()) } answers {
                captured += arg<String?>(3)
            }

            prefs(hideReplies = false)
            writer().writeRefresh(FeedKey.following("did:plc:me"), listOf(replyEntry("at://p/1")))
            prefs(hideReplies = true)
            // hideReplies = true filters the page to nothing, so project a
            // generator page instead to still reach the fingerprint write.
            writer().writeRefresh(
                FeedKey("did:plc:me", FeedType.DISCOVER, "at://gen"),
                listOf(replyEntry("at://p/2")),
            )

            assertEquals(2, captured.size)
            assertTrue(captured[0] != captured[1], "fingerprints must differ: $captured")
        }

    /**
     * Backs [partitionStateDao] with one row that behaves like the table, so a
     * test asserts what the row holds afterwards rather than which DAO method
     * was called. A whole-row write and a targeted one look alike to a
     * `coVerify` on "a fingerprint was written"; they differ only in what they
     * leave behind in the other columns.
     */
    private fun statefulPartitionRow(initial: FeedPartitionStateEntity?): () -> FeedPartitionStateEntity? {
        var row: FeedPartitionStateEntity? = initial
        coEvery { partitionStateDao.state(any(), any(), any()) } answers { row }
        coEvery { partitionStateDao.upsert(any()) } answers { row = firstArg() }
        coEvery { partitionStateDao.insertIfAbsent(any()) } answers { if (row == null) row = firstArg() }
        coEvery { partitionStateDao.updatePrefsFingerprint(any(), any(), any(), any()) } answers {
            row = row?.copy(prefsFingerprint = arg(3))
        }
        // The DAO's @Transaction pair, reproduced: on a mock its default body
        // never runs, so leaving it relaxed would make every write a no-op.
        coEvery { partitionStateDao.savePrefsFingerprint(any(), any(), any(), any()) } answers {
            val current = row ?: FeedPartitionStateEntity(arg(0), arg(1), arg(2))
            row = current.copy(prefsFingerprint = arg(3))
        }
        coEvery { partitionStateDao.updatePillDismissed(any(), any(), any(), any()) } answers {
            row = row?.copy(pillDismissedPosition = arg(3))
        }
        return { row }
    }

    private fun readerMidFeed() =
        FeedPartitionStateEntity(
            accountDid = "did:plc:me",
            feedType = "FOLLOWING",
            feedUri = "",
            prefsFingerprint = "stale",
            anchorItemKey = "at://p/anchor",
            anchorPosition = -266,
            anchorOffset = 120,
            pillDismissedPosition = -500,
        )

    @Test
    fun `a prepend keeps the reader's place and the pill's dismissal mark`() =
        runTest {
            // The background worker and Paging's PREPEND both land here while
            // the reader has a place. Losing it on every new page is the
            // feature eating the thing it exists to keep.
            prefs(hideReplies = false)
            val row = statefulPartitionRow(readerMidFeed())

            writer().writePrepend(FeedKey.following("did:plc:me"), listOf(topLevelEntry("at://p/new")), basePosition = -750)

            val after = row()
            assertEquals("at://p/anchor", after?.anchorItemKey)
            assertEquals(-266, after?.anchorPosition)
            assertEquals(120, after?.anchorOffset)
            assertEquals(-500, after?.pillDismissedPosition)
            assertTrue(after?.prefsFingerprint != "stale", "the fingerprint should still be recorded")
        }

    @Test
    fun `an append keeps the reader's place and the pill's dismissal mark`() =
        runTest {
            // Scrolling down is the commonest write of all: every page loaded
            // below the reader would otherwise forget where they are.
            prefs(hideReplies = false)
            val row = statefulPartitionRow(readerMidFeed())

            writer().writeAppend(FeedKey.following("did:plc:me"), listOf(topLevelEntry("at://p/older")))

            val after = row()
            assertEquals("at://p/anchor", after?.anchorItemKey)
            assertEquals(-266, after?.anchorPosition)
            assertEquals(120, after?.anchorOffset)
            assertEquals(-500, after?.pillDismissedPosition)
            assertTrue(after?.prefsFingerprint != "stale", "the fingerprint should still be recorded")
        }

    @Test
    fun `a stored entry round-trips its reason and reply through the context blob`() {
        // The reconstruction the whole local re-projection rests on. Rebuilt
        // from the blob the write path produced, not from hand-built JSON, so
        // this fails if either side drifts.
        val wire = replyEntry("at://p/1")
        val key = FeedKey.following("did:plc:me")
        val envelope = wire.toFeedPostEntity(key, position = 0)
        val post = wire.toCachedPosts(key).first { it.uri == envelope.uri }

        val rebuilt = envelope.toFeedViewPostOrNull(post)

        assertTrue(rebuilt != null, "entry should reconstruct")
        assertEquals("at://p/1", rebuilt?.post?.uri?.raw)
        // reply is an open union; the slots must come back as real posts, not
        // as the Unknown variant, or linksTo and the cluster shape misread them.
        assertEquals(
            "at://did:plc:other/app.bsky.feed.post/root",
            (rebuilt?.reply?.root as? io.github.kikin81.atproto.app.bsky.feed.PostView)?.uri?.raw,
        )
        assertEquals(
            "at://did:plc:other/app.bsky.feed.post/parent",
            (rebuilt?.reply?.parent as? io.github.kikin81.atproto.app.bsky.feed.PostView)?.uri?.raw,
        )
    }

    @Test
    fun `an entry with no reason or reply stores no context blob`() {
        // The column is for entries that have entry-level context; a plain
        // single should not pay for an empty JSON object.
        val plain =
            writerJson.decodeFromJsonElement(
                FeedViewPost.serializer(),
                buildJsonObject {
                    put("\$type", "app.bsky.feed.defs#feedViewPost")
                    put("post", postJson("at://plain/1"))
                },
            )

        assertEquals(null, plain.toFeedPostEntity(FeedKey.following("did:plc:me"), position = 0).entryContextBlob)
    }

    @Test
    fun `reproject rebuilds the projection from stored rows with no fetch`() =
        runTest {
            prefs(hideReplies = false)
            val key = FeedKey.following("did:plc:me")
            val wire = replyEntry("at://p/1")
            val envelope = wire.toFeedPostEntity(key, position = 0)
            val posts = wire.toCachedPosts(key)
            coEvery { feedPostDao.entriesIn(any(), any(), any()) } returns listOf(envelope)
            coEvery { cachedPostDao.byUris(any(), any()) } returns posts
            val rows = slot<List<FeedItemEntity>>()
            coEvery { feedItemDao.upsert(capture(rows)) } returns Unit

            writer().reproject(key)

            assertTrue(rows.isCaptured, "re-projection must write items")
            assertEquals(1, rows.captured.size)
            // Items only: the partition's seam markers are not the projection's
            // to rebuild (nubecita-2xk0.3).
            coVerify { feedItemDao.clearProjectedItems("did:plc:me", "FOLLOWING", "") }
            coVerify(exactly = 0) { feedItemDao.clearPartition(any(), any(), any()) }
        }

    @Test
    fun `reproject applies the new preferences`() =
        runTest {
            // The point of the whole mechanism: the reader hid replies, and the
            // stored partition reflects it without refetching anything.
            prefs(hideReplies = true)
            val key = FeedKey.following("did:plc:me")
            val wire = replyEntry("at://p/1")
            coEvery { feedPostDao.entriesIn(any(), any(), any()) } returns
                listOf(wire.toFeedPostEntity(key, position = 0))
            coEvery { cachedPostDao.byUris(any(), any()) } returns wire.toCachedPosts(key)

            writer().reproject(key)

            coVerify(exactly = 0) { feedItemDao.upsert(any()) }
        }

    // --- re-projection keeps markers (nubecita-2xk0.3) ----------------------

    /**
     * Serves [entries] (position → wire entry) and [markers] to a
     * re-projection, and returns every item row it writes.
     */
    private fun reprojectionOf(
        entries: Map<Int, FeedViewPost>,
        markers: List<Int>,
    ): MutableList<FeedItemEntity> {
        val key = FeedKey.following("did:plc:me")
        coEvery { feedPostDao.entriesIn(any(), any(), any()) } returns
            entries.toSortedMap().map { (position, wire) -> wire.toFeedPostEntity(key, position) }
        coEvery { cachedPostDao.byUris(any(), any()) } returns entries.values.flatMap { it.toCachedPosts(key) }
        coEvery { feedItemDao.seamPositions(any(), any(), any()) } returns markers
        val written = mutableListOf<FeedItemEntity>()
        coEvery { feedItemDao.upsert(any()) } answers { written += firstArg<List<FeedItemEntity>>() }
        return written
    }

    @Test
    fun `a re-projection keeps the markers and keeps each item on its side of them`() =
        runTest {
            prefs(hideReplies = false)
            // Two completed windows, each closed by a passive marker, above the
            // refresh's head.
            val older = (-500..-497).associateWith { topLevelEntry("at://p/older$it") }
            val newer = (-250..-247).associateWith { topLevelEntry("at://p/newer$it") }
            val head = (0..1).associateWith { topLevelEntry("at://p/head$it") }
            val written = reprojectionOf(older + newer + head, markers = listOf(-251, -1))

            writer().reproject(FeedKey.following("did:plc:me"))

            coVerify(exactly = 0) { feedItemDao.clearPartition(any(), any(), any()) }
            coVerify { feedItemDao.clearProjectedItems("did:plc:me", "FOLLOWING", "") }
            val byPost = written.associate { it.leafUri to it.position }
            assertTrue(older.keys.all { byPost.getValue("at://p/older$it") < -251 }, "older items moved below a marker: $byPost")
            assertTrue(newer.keys.all { byPost.getValue("at://p/newer$it") in -250..-2 }, "newer items left their segment: $byPost")
            assertTrue(head.keys.all { byPost.getValue("at://p/head$it") >= 0 }, "head items left their segment: $byPost")
        }

    @Test
    fun `a re-projection writes no item onto an active fill's next slot`() =
        runTest {
            prefs(hideReplies = false)
            // A fill in progress: its pages above the active seam at -245,
            // which is where the next page will be written.
            val fill = (-250..-246).associateWith { topLevelEntry("at://p/fill$it") }
            val head = (0..1).associateWith { topLevelEntry("at://p/head$it") }
            val written = reprojectionOf(fill + head, markers = listOf(-245))

            writer().reproject(FeedKey.following("did:plc:me"))

            assertTrue(written.none { it.position == -245 }, "an item was written onto the active seam's slot")
            assertTrue(written.filter { it.leafUri.orEmpty().contains("fill") }.all { it.position < -245 })
        }

    @Test
    fun `grouping stops at a marker`() =
        runTest {
            prefs(hideReplies = false)
            // Two replies into the same thread, one either side of a gap. A
            // prepend kept both — the later one is a new event — and so does
            // the rebuild.
            val written =
                reprojectionOf(
                    mapOf(-250 to replyEntry("at://p/reply-after-gap"), 0 to replyEntry("at://p/reply-before-gap")),
                    markers = listOf(-1),
                )

            writer().reproject(FeedKey.following("did:plc:me"))

            assertEquals(2, written.size, "one item per side of the marker: $written")
        }

    @Test
    fun `follow-scope is decided per feed type, exhaustively`() {
        // The property is the single place the decision lives, so pin it here
        // rather than inferring it from writer behaviour alone.
        assertTrue(FeedType.FOLLOWING.isFollowScoped)
        assertTrue(FeedType.LIST.isFollowScoped)
        assertEquals(false, FeedType.DISCOVER.isFollowScoped)
        assertEquals(false, FeedType.CUSTOM.isFollowScoped)
    }
}
