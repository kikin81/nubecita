package net.kikin.nubecita.core.feedcache

import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.PostView
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.coVerifyOrder
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedPostDao
import net.kikin.nubecita.core.database.dao.FeedRemoteKeyDao
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedPostEntity
import net.kikin.nubecita.core.database.model.FeedRemoteKeyEntity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlinx.serialization.json.add as jsonAdd

internal class FeedRefresherTest {
    private val key = FeedKey.following("did:plc:me")
    private val networkSource = mockk<FeedNetworkSource>()
    private val feedPostDao = mockk<FeedPostDao>(relaxed = true)
    private val cachedPostDao = mockk<CachedPostDao>(relaxed = true)
    private val projectionWriter = mockk<FeedProjectionWriter>(relaxed = true)
    private val remoteKeyDao = mockk<FeedRemoteKeyDao>(relaxed = true)

    private val passthroughRunner =
        object : FeedCacheTransactionRunner {
            override suspend fun <T> run(block: suspend () -> T): T = block()
        }

    private val refresher =
        FeedRefresher(
            networkSource = networkSource,
            feedPostDao = feedPostDao,
            cachedPostDao = cachedPostDao,
            projectionWriter = projectionWriter,
            remoteKeyDao = remoteKeyDao,
            transactionRunner = passthroughRunner,
        )

    @Test
    fun `success clears the partition, writes page 1 + cursor, and returns the end-of-pagination flag`() =
        runTest {
            coEvery { networkSource.fetchPage(key, cursor = null) } returns
                Result.success(listOf(feedPost("a", "first"), feedPost("b", "second")) to "next-cursor")

            val postsSlot = slot<List<FeedPostEntity>>()
            val keySlot = slot<FeedRemoteKeyEntity>()
            coEvery { feedPostDao.upsert(capture(postsSlot)) } returns Unit
            coEvery { remoteKeyDao.upsert(capture(keySlot)) } returns Unit

            // A non-null next cursor and a non-empty page => not end of pagination.
            val result = refresher.refresh(key)

            assertEquals(Result.success(false), result)
            coVerify { feedPostDao.clearPartition("did:plc:me", "FOLLOWING", "") }
            // Page mapped to positions 0..n.
            assertEquals(listOf(0, 1), postsSlot.captured.map { it.position })
            assertEquals("next-cursor", keySlot.captured.nextCursor)
        }

    @Test
    fun `the page's posts are written before its envelopes`() =
        runTest {
            // Without this the refresher can stop writing cached_post and every
            // other test still passes: the dao is a relaxed mock, so a missing
            // call is silent. In production the read path inner-joins, so
            // envelopes without posts render an empty feed.
            coEvery { networkSource.fetchPage(key, cursor = null) } returns
                Result.success(listOf(feedPost("a", "first"), feedPost("b", "second")) to null)

            val storedSlot = slot<List<CachedPostEntity>>()
            coEvery { cachedPostDao.upsert(capture(storedSlot)) } returns Unit

            refresher.refresh(key)

            assertEquals(2, storedSlot.captured.size)
            assertTrue(storedSlot.captured.all { it.postBlob != null })
            coVerifyOrder {
                cachedPostDao.upsert(any())
                feedPostDao.upsert(any())
            }
        }

    @Test
    fun `the projection is written after the posts and envelopes`() =
        runTest {
            // projectionWriter is a relaxed mock, so without this the
            // projection write could be deleted and every other test here
            // would still pass while the feed rendered nothing.
            coEvery { networkSource.fetchPage(key, cursor = null) } returns
                Result.success(listOf(feedPost("a", "first")) to null)

            refresher.refresh(key)

            coVerifyOrder {
                cachedPostDao.upsert(any())
                feedPostDao.upsert(any())
                projectionWriter.writeRefresh(key, any())
            }
        }

    @Test
    fun `null next cursor reports end of pagination`() =
        runTest {
            coEvery { networkSource.fetchPage(key, cursor = null) } returns
                Result.success(listOf(feedPost("a", "only")) to null)

            assertEquals(Result.success(true), refresher.refresh(key))
        }

    @Test
    fun `fetch failure returns Result-failure and leaves the cache untouched`() =
        runTest {
            val boom = RuntimeException("network down")
            coEvery { networkSource.fetchPage(key, cursor = null) } returns Result.failure(boom)

            val result = refresher.refresh(key)

            assertTrue(result.isFailure)
            assertEquals(boom, result.exceptionOrNull())
            coVerify(exactly = 0) { feedPostDao.clearPartition(any(), any(), any()) }
            coVerify(exactly = 0) { feedPostDao.upsert(any()) }
            coVerify(exactly = 0) { remoteKeyDao.upsert(any()) }
        }

    @Test
    fun `CancellationException propagates and is not wrapped into a failure`() =
        runTest {
            coEvery { networkSource.fetchPage(key, cursor = null) } throws CancellationException("cancelled")

            // Assert propagation directly in the runTest scope — no runBlocking
            // (it would block the test thread and interfere with virtual time).
            // The CancellationException here is thrown by the fake, not a cancel
            // of the test's job, so catching it leaves the scope intact.
            var propagated = false
            try {
                refresher.refresh(key)
            } catch (_: CancellationException) {
                propagated = true
            }
            assertTrue(propagated, "CancellationException must propagate, not become a Result.failure")
            coVerify(exactly = 0) { feedPostDao.upsert(any()) }
        }

    private fun feedPost(
        tail: String,
        text: String,
    ): FeedViewPost = FeedViewPost(post = wirePost(tail, text))
}

private val refresherTestJson =
    Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

private fun wirePost(
    tail: String,
    text: String,
): PostView {
    val uri = "at://did:plc:abc/app.bsky.feed.post/$tail"
    val wire =
        buildJsonObject {
            put("\$type", "app.bsky.feed.defs#postView")
            put("uri", uri)
            put("cid", "bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq")
            putJsonObject("author") {
                put("did", "did:plc:other")
                put("handle", "alice.bsky.social")
                put("displayName", "Alice")
            }
            putJsonObject("record") {
                put("\$type", "app.bsky.feed.post")
                put("text", text)
                put("createdAt", "2026-04-15T19:51:12.861Z")
                putJsonArray("langs") { jsonAdd("en") }
            }
            put("indexedAt", "2026-04-15T19:51:13.000Z")
        }
    return refresherTestJson.decodeFromJsonElement(PostView.serializer(), wire)
}
