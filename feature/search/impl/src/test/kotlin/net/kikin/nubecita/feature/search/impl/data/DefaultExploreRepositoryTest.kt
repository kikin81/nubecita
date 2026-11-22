package net.kikin.nubecita.feature.search.impl.data

import io.github.kikin81.atproto.runtime.NoAuth
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.feature.search.impl.DiscoverSectionStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class DefaultExploreRepositoryTest {
    private val session = MutableStateFlow<SessionState>(signedIn("did:plc:me"))
    private var actorsBody = ACTORS
    private var feedsBody = FEEDS
    private var postsBody = FEED_POSTS

    @Test
    fun `each endpoint decodes into its section`() =
        runTest {
            val (_, repo) = newRepo { route(it) }

            repo.load()

            val content = repo.content.value
            assertEquals(DiscoverSectionStatus.Loaded, content.trendsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, content.feedsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, content.accountsStatus)

            assertEquals(2, content.trends.size)
            assertEquals(
                TrendUi(
                    topic = "8f0df89d5bec",
                    displayName = "ESPN analyst dies",
                    link = "/profile/did:plc:qrz/feed/8f0df89d5bec",
                    category = "sports",
                    postCount = 218L,
                    isHot = false,
                ),
                content.trends[0],
            )
            assertTrue(content.trends[1].isHot, "status hot marks a trend hot")

            val feed = content.feeds.single()
            assertEquals("at://did:plc:z72/app.bsky.feed.generator/with-friends", feed.feed.uri)
            assertEquals("With Friends", feed.feed.displayName)
            assertEquals("bsky.app", feed.feed.creatorHandle)
            assertEquals(FeedPreviewStatusIdle, feed.previewStatus)

            val account = content.accounts.single()
            assertEquals("did:plc:lth", account.did)
            assertEquals("rotopat.bsky.social", account.handle)
            assertFalse(account.isFollowing)
        }

    @Test
    fun `the requests carry the documented limits`() =
        runTest {
            val (engine, repo) = newRepo { route(it) }

            repo.load()

            val urls = engine.requestHistory.map { it.url.toString() }
            assertTrue(urls.single { "getTrends" in it }.contains("limit=10"), urls.toString())
            assertTrue(urls.single { "getSuggestedFeeds" in it }.contains("limit=8"), urls.toString())
            assertTrue(urls.single { "getSuggestedUsersForExplore" in it }.contains("limit=12"), urls.toString())
        }

    @Test
    fun `a missing required field fails only that section`() =
        runTest {
            val (_, repo) =
                newRepo {
                    if ("getTrends" in it.url.toString()) okJson("""{"recIdStr": "1"}""") else route(it)
                }

            repo.load()

            val content = repo.content.value
            assertEquals(DiscoverSectionStatus.Error, content.trendsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, content.feedsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, content.accountsStatus)
        }

    @Test
    fun `an empty response marks the section Empty`() =
        runTest {
            val (_, repo) =
                newRepo {
                    if ("getSuggestedUsersForExplore" in it.url.toString()) okJson("""{"actors": []}""") else route(it)
                }

            repo.load()

            assertEquals(DiscoverSectionStatus.Empty, repo.content.value.accountsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, repo.content.value.trendsStatus)
        }

    @Test
    fun `two concurrent loads hit each endpoint once`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val (engine, repo) =
                newRepo {
                    gate.await()
                    route(it)
                }

            val loads = listOf(async { repo.load() }, async { repo.load() })
            testScheduler.runCurrent()
            gate.complete(Unit)
            loads.awaitAll()

            assertEquals(3, engine.requestHistory.size, engine.requestHistory.map { it.url.encodedPath }.toString())
            assertEquals(DiscoverSectionStatus.Loaded, repo.content.value.trendsStatus)
        }

    @Test
    fun `a load once loaded is a no-op until forced`() =
        runTest {
            val (engine, repo) = newRepo { route(it) }

            repo.load()
            repo.load()
            assertEquals(3, engine.requestHistory.size)

            repo.load(force = true)
            assertEquals(6, engine.requestHistory.size)
        }

    @Test
    fun `every endpoint failing leaves all sections Error and a forced load recovers`() =
        runTest {
            var failing = true
            val seen = mutableListOf<String>()
            val (_, repo) =
                newRepo {
                    seen += it.url.encodedPath
                    if (failing) throw IOException("network down") else route(it)
                }

            repo.load()

            var content = repo.content.value
            assertEquals(DiscoverSectionStatus.Error, content.trendsStatus)
            assertEquals(DiscoverSectionStatus.Error, content.feedsStatus)
            assertEquals(DiscoverSectionStatus.Error, content.accountsStatus)
            assertEquals(3, seen.size)

            failing = false
            repo.load(force = true)

            content = repo.content.value
            assertEquals(DiscoverSectionStatus.Loaded, content.trendsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, content.feedsStatus)
            assertEquals(DiscoverSectionStatus.Loaded, content.accountsStatus)
        }

    @Test
    fun `an unforced load retries only the sections that are not loaded`() =
        runTest {
            var failFeeds = true
            val seen = mutableListOf<String>()
            val (_, repo) =
                newRepo {
                    seen += it.url.encodedPath
                    if (failFeeds && "getSuggestedFeeds" in it.url.toString()) throw IOException("x") else route(it)
                }
            repo.load()
            assertEquals(DiscoverSectionStatus.Error, repo.content.value.feedsStatus)

            failFeeds = false
            repo.load()

            assertEquals(DiscoverSectionStatus.Loaded, repo.content.value.feedsStatus)
            assertEquals(4, seen.size, "only the failed feeds section was fetched again")
        }

    @Test
    fun `a did change resets content`() =
        runTest {
            val (_, repo) = newRepo { route(it) }
            repo.load()
            assertEquals(DiscoverSectionStatus.Loaded, repo.content.value.trendsStatus)

            session.value = signedIn("did:plc:other")

            assertEquals(ExploreContent(), repo.content.value)
        }

    @Test
    fun `signing out resets content`() =
        runTest {
            val (_, repo) = newRepo { route(it) }
            repo.load()

            session.value = SessionState.SignedOut

            assertEquals(ExploreContent(), repo.content.value)
        }

    @Test
    fun `a load started for the old account does not write after the reset`() =
        runTest {
            val gate = CompletableDeferred<Unit>()
            val (_, repo) =
                newRepo {
                    gate.await()
                    route(it)
                }
            val stale = async { repo.load() }
            testScheduler.runCurrent()

            session.value = signedIn("did:plc:other")
            gate.complete(Unit)
            stale.await()

            assertEquals(ExploreContent(), repo.content.value, "the old account's result must not land")
        }

    @Test
    fun `a dismissed account stays out of content until the account changes`() =
        runTest {
            val (_, repo) = newRepo { route(it) }
            repo.load()

            repo.dismissAccount("did:plc:lth")
            assertTrue(
                repo.content.value.accounts
                    .isEmpty(),
            )

            repo.load(force = true)
            assertTrue(
                repo.content.value.accounts
                    .isEmpty(),
                "a refresh does not resurface it",
            )
            assertEquals(DiscoverSectionStatus.Empty, repo.content.value.accountsStatus)
        }

    @Test
    fun `a refresh keeps the loaded preview of a feed that is still suggested`() =
        runTest {
            val (_, repo) = newRepo { route(it) }
            repo.load()
            val uri =
                repo.content.value.feeds
                    .single()
                    .feed.uri

            repo.loadFeedPreview(uri)
            assertEquals(
                FeedPreviewStatusLoaded,
                repo.content.value.feeds
                    .single()
                    .previewStatus,
            )

            repo.load(force = true)

            val feed =
                repo.content.value.feeds
                    .single()
            assertEquals(FeedPreviewStatusLoaded, feed.previewStatus)
            assertEquals(
                "hello",
                feed.preview
                    ?.single()
                    ?.text,
            )
        }

    @Test
    fun `a feed preview is fetched once`() =
        runTest {
            val (engine, repo) = newRepo { route(it) }
            repo.load()
            val uri =
                repo.content.value.feeds
                    .single()
                    .feed.uri

            repo.loadFeedPreview(uri)
            repo.loadFeedPreview(uri)

            assertEquals(1, engine.requestHistory.count { "getFeed" in it.url.encodedPath && "getFeedGenerator" !in it.url.encodedPath })
        }

    @Test
    fun `resolveDid passes a DID through without a call`() =
        runTest {
            val (engine, repo) = newRepo { route(it) }

            val result = repo.resolveDid("did:plc:abc")

            assertEquals("did:plc:abc", result.getOrThrow())
            assertTrue(engine.requestHistory.isEmpty())
        }

    @Test
    fun `resolveDid resolves a handle`() =
        runTest {
            val (engine, repo) = newRepo { route(it) }

            val result = repo.resolveDid("bsky.app")

            assertEquals("did:plc:resolved", result.getOrThrow())
            assertTrue(
                engine.requestHistory
                    .single()
                    .url
                    .toString()
                    .contains("handle=bsky.app"),
            )
        }

    @Test
    fun `resolveDid fails when the handle does not resolve`() =
        runTest {
            val (_, repo) = newRepo { throw IOException("nope") }

            assertTrue(repo.resolveDid("nobody.test").isFailure)
        }

    @Test
    fun `an account the viewer follows carries its follow uri and mutuals`() =
        runTest {
            actorsBody =
                """
                {"actors": [{
                  "did": "did:plc:alice", "handle": "alice.bsky.social", "displayName": "Alice",
                  "avatar": "https://cdn.example/alice.jpg",
                  "viewer": {
                    "following": "at://did:plc:me/app.bsky.graph.follow/abc123",
                    "knownFollowers": {"count": 2, "followers": [
                      {"did": "did:plc:mutual1", "handle": "mutual1.bsky.social", "avatar": "https://cdn.example/mutual1.jpg"}
                    ]}
                  }
                }]}
                """.trimIndent()
            val (_, repo) = newRepo { route(it) }

            repo.load()

            val alice =
                repo.content.value.accounts
                    .single()
            assertTrue(alice.isFollowing)
            assertEquals("at://did:plc:me/app.bsky.graph.follow/abc123", alice.followUri)
            assertEquals(2, alice.mutualsCount)
            assertEquals(listOf("https://cdn.example/mutual1.jpg"), alice.mutualAvatarUrls.toList())
        }

    @Test
    fun `a blank feed description is normalized to null`() =
        runTest {
            feedsBody =
                """
                {"feeds": [{
                  "uri": "at://did:plc:gen/app.bsky.feed.generator/x", "cid": "bafyreitest1", "did": "did:plc:gen",
                  "displayName": "No Desc", "description": "   ", "indexedAt": "2026-01-01T00:00:00Z",
                  "creator": {"did": "did:plc:creator", "handle": "creator.bsky.social"}
                }]}
                """.trimIndent()
            val (_, repo) = newRepo { route(it) }

            repo.load()

            val feed =
                repo.content.value.feeds
                    .single()
                    .feed
            assertEquals(null, feed.description)
            assertEquals(null, feed.avatarUrl)
        }

    @Test
    fun `a preview maps a thumbnail and drops a malformed post`() =
        runTest {
            val post = { n: String, record: String, embed: String ->
                """{"post": {"uri": "at://did:plc:p/app.bsky.feed.post/$n", "cid": "bafyreitest1",
                  "author": {"did": "did:plc:p", "handle": "poster.bsky.social"}, "record": $record, $embed
                  "indexedAt": "2026-01-01T00:00:00Z"}}"""
            }
            val good = { text: String -> """{"${'$'}type": "app.bsky.feed.post", "text": "$text", "createdAt": "2026-01-01T00:00:00Z"}""" }
            val image =
                """"embed": {"${'$'}type": "app.bsky.embed.images#view", "images": [
                  {"thumb": "https://cdn.example/thumb.jpg", "fullsize": "https://cdn.example/full.jpg", "alt": "x"}]},"""
            postsBody =
                """{"feed": [
                  ${post("1", good("with image"), image)},
                  ${post("2", """{"${'$'}type": "app.bsky.feed.post"}""", "")},
                  ${post("3", good("plain"), "")},
                  ${post("4", good("fourth"), "")},
                  ${post("5", good("fifth"), "")}
                ]}"""
            val (_, repo) = newRepo { route(it) }
            repo.load()

            repo.loadFeedPreview(
                repo.content.value.feeds
                    .single()
                    .feed.uri,
            )

            val preview =
                repo.content.value.feeds
                    .single()
                    .preview!!
            assertEquals(listOf("with image", "plain"), preview.map { it.text })
            assertEquals("https://cdn.example/thumb.jpg", preview[0].thumbnailUrl)
            assertEquals(null, preview[1].thumbnailUrl)
        }

    @Test
    fun `a failed preview marks that feed Error and is not retried`() =
        runTest {
            var previewCalls = 0
            val (_, repo) =
                newRepo {
                    if (it.url.encodedPath.endsWith("app.bsky.feed.getFeed")) {
                        previewCalls++
                        throw IOException("x")
                    } else {
                        route(it)
                    }
                }
            repo.load()
            val uri =
                repo.content.value.feeds
                    .single()
                    .feed.uri

            repo.loadFeedPreview(uri)
            repo.loadFeedPreview(uri)

            assertEquals(
                net.kikin.nubecita.feature.search.impl.FeedPreviewStatus.Error,
                repo.content.value.feeds
                    .single()
                    .previewStatus,
            )
            assertEquals(1, previewCalls)
        }

    // --- helpers ---

    private fun MockRequestHandleScope.route(request: HttpRequestData): HttpResponseData {
        val path = request.url.encodedPath
        return when {
            path.endsWith("app.bsky.unspecced.getTrends") -> okJson(TRENDS)
            path.endsWith("app.bsky.unspecced.getSuggestedFeeds") -> okJson(feedsBody)
            path.endsWith("app.bsky.unspecced.getSuggestedUsersForExplore") -> okJson(actorsBody)
            path.endsWith("com.atproto.identity.resolveHandle") -> okJson("""{"did": "did:plc:resolved"}""")
            path.endsWith("app.bsky.feed.getFeed") -> okJson(postsBody)
            else -> respond("unexpected $path", HttpStatusCode.NotFound)
        }
    }

    private fun newRepo(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData): Pair<MockEngine, DefaultExploreRepository> {
        val engine = MockEngine(handler)
        val xrpcClient =
            XrpcClient(
                baseUrl = "https://test.invalid",
                httpClient = HttpClient(engine),
                authProvider = NoAuth,
            )
        val provider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient = xrpcClient
            }
        val sessions =
            object : SessionStateProvider {
                override val state = session

                override suspend fun refresh() = Unit
            }
        val repo =
            DefaultExploreRepository(
                xrpcClientProvider = provider,
                sessionStateProvider = sessions,
                dispatcher = Dispatchers.Unconfined,
                scope = CoroutineScope(Dispatchers.Unconfined),
            )
        return engine to repo
    }

    private fun MockRequestHandleScope.okJson(body: String): HttpResponseData =
        respond(
            content = ByteReadChannel(body),
            status = HttpStatusCode.OK,
            headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
        )

    private companion object {
        val FeedPreviewStatusIdle = net.kikin.nubecita.feature.search.impl.FeedPreviewStatus.Idle
        val FeedPreviewStatusLoaded = net.kikin.nubecita.feature.search.impl.FeedPreviewStatus.Loaded

        const val TRENDS = """
            {
              "recIdStr": "1132253958041129392",
              "trends": [
                {
                  "topic": "8f0df89d5bec",
                  "displayName": "ESPN analyst dies",
                  "description": "The longtime analyst.",
                  "link": "/profile/did:plc:qrz/feed/8f0df89d5bec",
                  "startedAt": "2026-11-22T15:14:24.316049+00:00",
                  "postCount": 218,
                  "status": "trending",
                  "category": "sports",
                  "actors": [
                    { "did": "did:plc:qhk", "handle": "jnierob.bsky.social", "displayName": "Jacob" }
                  ]
                },
                {
                  "topic": "lunar eclipse",
                  "displayName": "Lunar eclipse",
                  "link": "/search?q=lunar%20eclipse",
                  "startedAt": "2026-11-22T10:00:00.000Z",
                  "postCount": 5000,
                  "status": "hot",
                  "actors": []
                }
              ]
            }
        """

        const val FEEDS = """
            {
              "feeds": [
                {
                  "uri": "at://did:plc:z72/app.bsky.feed.generator/with-friends",
                  "cid": "bafyreig6odglhywpzwm36p4h5jzlc7kbiushd5wbf44woap2kab74fwrju",
                  "did": "did:web:discover.bsky.app",
                  "creator": { "did": "did:plc:z72", "handle": "bsky.app", "displayName": "Bluesky" },
                  "displayName": "With Friends",
                  "description": "Popular with friends.",
                  "likeCount": 10,
                  "indexedAt": "2026-01-01T00:00:00Z"
                }
              ]
            }
        """

        const val ACTORS = """
            {
              "recIdStr": "1",
              "actors": [
                { "did": "did:plc:lth", "handle": "rotopat.bsky.social", "displayName": "Patrick Daugherty" }
              ]
            }
        """

        const val FEED_POSTS = """
            {
              "feed": [
                {
                  "post": {
                    "uri": "at://did:plc:a/app.bsky.feed.post/1",
                    "cid": "bafyreig6odglhywpzwm36p4h5jzlc7kbiushd5wbf44woap2kab74fwrju",
                    "author": { "did": "did:plc:a", "handle": "a.bsky.social" },
                    "record": { "${'$'}type": "app.bsky.feed.post", "text": "hello", "createdAt": "2026-01-01T00:00:00Z" },
                    "indexedAt": "2026-01-01T00:00:00Z"
                  }
                }
              ]
            }
        """
    }
}
