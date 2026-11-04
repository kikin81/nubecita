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
import net.kikin.nubecita.core.database.model.FeedItemEntity
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

internal class FeedProjectionWriterTest {
    private val feedItemDao = mockk<FeedItemDao>(relaxed = true)
    private val partitionStateDao = mockk<FeedPartitionStateDao>(relaxed = true)
    private val cachedPostDao = mockk<CachedPostDao>(relaxed = true)
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
            sessionStateProvider = sessionStateProvider,
            moderationPreferences = moderationPreferences,
            feedViewPreferences = feedViewPreferences,
        )

    private fun prefs(hideReplies: Boolean) {
        coEvery { feedViewPreferences.prefs } returns
            MutableStateFlow(FeedViewPrefs(hideReplies = hideReplies, hideRepliesByUnfollowed = false))
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
    fun `the projection records the preferences it was built with`() =
        runTest {
            prefs(hideReplies = false)
            val state = slot<net.kikin.nubecita.core.database.model.FeedPartitionStateEntity>()
            coEvery { partitionStateDao.upsert(capture(state)) } returns Unit

            writer().writeRefresh(FeedKey.following("did:plc:me"), listOf(replyEntry("at://p/1")))

            assertTrue(state.isCaptured, "a projection must record its preferences")
            assertTrue(
                state.captured.prefsFingerprint?.contains("replies=false") == true,
                "fingerprint should reflect the reply tuner, was ${state.captured.prefsFingerprint}",
            )
        }

    @Test
    fun `a different reply tuner yields a different fingerprint`() =
        runTest {
            // The whole mechanism rests on this: equal preferences must give
            // equal strings and different ones must differ, or a stale
            // projection either never refreshes or refreshes forever.
            val captured = mutableListOf<String?>()
            coEvery { partitionStateDao.upsert(any()) } answers {
                captured += firstArg<net.kikin.nubecita.core.database.model.FeedPartitionStateEntity>().prefsFingerprint
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
