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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.XrpcClientProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

/**
 * Unit tests for [DefaultSuggestionsRepository].
 *
 * Same MockEngine-driven shape as [DefaultSearchFeedsRepositoryTest] and
 * [DefaultSearchPostsRepositoryTest] — stand up a real [XrpcClient] backed
 * by Ktor [MockEngine] and assert on the parsed [Result] (mapping + error
 * path) and on `engine.requestHistory.single().url` (wire format).
 *
 * Coverage:
 *  - getSuggestedAccounts: viewer.following → isFollowing/followUri mapping.
 *  - getSuggestedAccounts: knownFollowers → mutualsCount + mutualAvatarUrls.
 *  - getSuggestedAccounts: null viewer → isFollowing=false, followUri=null, mutualsCount=0.
 *  - getSuggestedAccounts: NSID + limit on the wire.
 *  - getSuggestedAccounts: IOException → Result.failure; CancellationException propagates.
 *  - getSuggestedFeeds: GeneratorView → SuggestedFeedUi mapping.
 *  - getSuggestedFeeds: blank description normalized to null.
 *  - getSuggestedFeeds: IOException → Result.failure; CancellationException propagates.
 *  - getFeedPreview: posts → FeedPreviewPostUi (author + text + thumbnail).
 *  - getFeedPreview: limit respected (only first [limit] posts returned).
 *  - getFeedPreview: malformed post record filtered out (mapNotNull).
 *  - getFeedPreview: IOException → Result.failure; CancellationException propagates.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DefaultSuggestionsRepositoryTest {
    // ── getSuggestedAccounts ──────────────────────────────────────────────────

    @Test
    fun getSuggestedAccounts_withFollowing_mapsIsFollowingAndFollowUri() =
        runTest {
            val (_, repo) = newRepo { _ -> okJson(ACCOUNTS_RESPONSE_FOLLOWING) }

            val accounts = repo.getSuggestedAccounts(limit = 5).getOrThrow()

            assertEquals(1, accounts.size)
            val alice = accounts[0]
            assertEquals("did:plc:alice", alice.did)
            assertEquals("alice.bsky.social", alice.handle)
            assertEquals("Alice", alice.displayName)
            assertEquals("https://cdn.example/alice.jpg", alice.avatarUrl)
            assertTrue(alice.isFollowing, "viewer.following present → isFollowing must be true")
            assertEquals("at://did:plc:me/app.bsky.graph.follow/abc123", alice.followUri)
        }

    @Test
    fun getSuggestedAccounts_withKnownFollowers_mapsMutualsCountAndAvatars() =
        runTest {
            val (_, repo) = newRepo { _ -> okJson(ACCOUNTS_RESPONSE_FOLLOWING) }

            val alice = repo.getSuggestedAccounts(limit = 5).getOrThrow()[0]

            assertEquals(2, alice.mutualsCount)
            assertEquals(1, alice.mutualAvatarUrls.size)
            assertEquals("https://cdn.example/mutual1.jpg", alice.mutualAvatarUrls[0])
        }

    @Test
    fun getSuggestedAccounts_noViewer_isFollowingFalseAndMutualsZero() =
        runTest {
            val (_, repo) = newRepo { _ -> okJson(ACCOUNTS_RESPONSE_NO_VIEWER) }

            val accounts = repo.getSuggestedAccounts(limit = 5).getOrThrow()

            assertEquals(1, accounts.size)
            val bob = accounts[0]
            assertFalse(bob.isFollowing, "no viewer → isFollowing must be false")
            assertNull(bob.followUri)
            assertEquals(0, bob.mutualsCount)
            assertTrue(bob.mutualAvatarUrls.isEmpty())
        }

    @Test
    fun getSuggestedAccounts_passesLimitOnWire() =
        runTest {
            val (engine, repo) = newRepo { _ -> okJson("""{"actors": []}""") }

            repo.getSuggestedAccounts(limit = 7)

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            assertTrue(url.contains("app.bsky.actor.getSuggestions"), "NSID must appear in URL: $url")
            assertTrue(url.contains("limit=7"), "limit=7 must appear in URL: $url")
        }

    @Test
    fun getSuggestedAccounts_ioException_returnsFailure() =
        runTest {
            val (_, repo) = newRepo { _ -> throw IOException("network down") }

            val result = repo.getSuggestedAccounts(limit = 5)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun getSuggestedAccounts_cancellation_propagates() {
        val (_, repo) = newRepo { _ -> throw CancellationException("cancelled") }

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.getSuggestedAccounts(limit = 5) }
        }
    }

    // ── getSuggestedFeeds ─────────────────────────────────────────────────────

    @Test
    fun getSuggestedFeeds_happyPath_mapsAllFields() =
        runTest {
            val (_, repo) = newRepo { _ -> okJson(FEEDS_RESPONSE) }

            val feeds = repo.getSuggestedFeeds(limit = 5).getOrThrow()

            assertEquals(1, feeds.size)
            val feed = feeds[0]
            assertEquals("at://did:plc:gen/app.bsky.feed.generator/science", feed.uri)
            assertEquals("Science", feed.displayName)
            assertEquals("creator.bsky.social", feed.creatorHandle)
            assertEquals("https://cdn.example/sci.jpg", feed.avatarUrl)
            assertEquals("Science posts", feed.description)
            assertFalse(feed.isPinned, "isPinned defaults to false")
        }

    @Test
    fun getSuggestedFeeds_blankDescription_normalizedToNull() =
        runTest {
            val (_, repo) = newRepo { _ -> okJson(FEEDS_RESPONSE_BLANK_DESCRIPTION) }

            val feed = repo.getSuggestedFeeds(limit = 5).getOrThrow()[0]

            assertNull(feed.description)
            assertNull(feed.avatarUrl)
        }

    @Test
    fun getSuggestedFeeds_passesLimitOnWire() =
        runTest {
            val (engine, repo) = newRepo { _ -> okJson("""{"feeds": []}""") }

            repo.getSuggestedFeeds(limit = 9)

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            assertTrue(url.contains("app.bsky.feed.getSuggestedFeeds"), "NSID must appear in URL: $url")
            assertTrue(url.contains("limit=9"), "limit=9 must appear in URL: $url")
        }

    @Test
    fun getSuggestedFeeds_ioException_returnsFailure() =
        runTest {
            val (_, repo) = newRepo { _ -> throw IOException("network down") }

            val result = repo.getSuggestedFeeds(limit = 5)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun getSuggestedFeeds_cancellation_propagates() {
        val (_, repo) = newRepo { _ -> throw CancellationException("cancelled") }

        assertThrows(CancellationException::class.java) {
            runBlocking { repo.getSuggestedFeeds(limit = 5) }
        }
    }

    // ── getFeedPreview ────────────────────────────────────────────────────────

    @Test
    fun getFeedPreview_happyPath_mapsAuthorAndText() =
        runTest {
            val (_, repo) = newRepo { _ -> okJson(PREVIEW_RESPONSE_ONE_POST) }

            val posts = repo.getFeedPreview(feedUri = "at://did:plc:gen/app.bsky.feed.generator/sci", limit = 3).getOrThrow()

            assertEquals(1, posts.size)
            val post = posts[0]
            assertEquals("poster.bsky.social", post.authorHandle)
            assertEquals("https://cdn.example/poster.jpg", post.authorAvatarUrl)
            assertEquals("Preview post text", post.text)
            assertNull(post.thumbnailUrl)
        }

    @Test
    fun getFeedPreview_withImageEmbed_mapsThumbnailUrl() =
        runTest {
            val (_, repo) = newRepo { _ -> okJson(PREVIEW_RESPONSE_WITH_IMAGE) }

            val post = repo.getFeedPreview(feedUri = "at://did:plc:gen/app.bsky.feed.generator/sci", limit = 3).getOrThrow()[0]

            assertEquals("https://cdn.example/thumb.jpg", post.thumbnailUrl)
        }

    @Test
    fun getFeedPreview_limitRespected_returnsAtMostLimitPosts() =
        runTest {
            // Response has 4 posts but limit=2; repo must cap at 2
            val (_, repo) = newRepo { _ -> okJson(PREVIEW_RESPONSE_FOUR_POSTS) }

            val posts = repo.getFeedPreview(feedUri = "at://did:plc:gen/app.bsky.feed.generator/sci", limit = 2).getOrThrow()

            assertEquals(2, posts.size)
        }

    @Test
    fun getFeedPreview_malformedRecord_filteredOut() =
        runTest {
            // Response has 2 posts: one well-formed and one with a missing `text` field
            // (invalid `app.bsky.feed.post`). The malformed post must be silently filtered.
            val (_, repo) = newRepo { _ -> okJson(PREVIEW_RESPONSE_WITH_MALFORMED) }

            val posts = repo.getFeedPreview(feedUri = "at://did:plc:gen/app.bsky.feed.generator/sci", limit = 3).getOrThrow()

            assertEquals(1, posts.size)
            assertEquals("good post", posts[0].text)
        }

    @Test
    fun getFeedPreview_passesFeedUriAndLimitOnWire() =
        runTest {
            val (engine, repo) = newRepo { _ -> okJson("""{"feed": []}""") }

            repo.getFeedPreview(feedUri = "at://did:plc:gen/app.bsky.feed.generator/sci", limit = 3)

            val url =
                engine.requestHistory
                    .single()
                    .url
                    .toString()
            assertTrue(url.contains("app.bsky.feed.getFeed"), "NSID must appear in URL: $url")
            assertTrue(url.contains("limit=3"), "limit=3 must appear in URL: $url")
            assertTrue(url.contains("feed="), "feed param must appear in URL: $url")
        }

    @Test
    fun getFeedPreview_ioException_returnsFailure() =
        runTest {
            val (_, repo) = newRepo { _ -> throw IOException("network down") }

            val result = repo.getFeedPreview(feedUri = "at://did:plc:gen/app.bsky.feed.generator/sci", limit = 3)

            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull() is IOException)
        }

    @Test
    fun getFeedPreview_cancellation_propagates() {
        val (_, repo) = newRepo { _ -> throw CancellationException("cancelled") }

        assertThrows(CancellationException::class.java) {
            runBlocking {
                repo.getFeedPreview(feedUri = "at://did:plc:gen/app.bsky.feed.generator/sci", limit = 3)
            }
        }
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private fun newRepo(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ): Pair<MockEngine, DefaultSuggestionsRepository> {
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
        val repo =
            DefaultSuggestionsRepository(
                xrpcClientProvider = provider,
                dispatcher = Dispatchers.Unconfined,
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
        /**
         * One ProfileView with viewer.following set and one knownFollower.
         */
        val ACCOUNTS_RESPONSE_FOLLOWING =
            """
            {
              "actors": [
                {
                  "${'$'}type": "app.bsky.actor.defs#profileView",
                  "did": "did:plc:alice",
                  "handle": "alice.bsky.social",
                  "displayName": "Alice",
                  "avatar": "https://cdn.example/alice.jpg",
                  "viewer": {
                    "${'$'}type": "app.bsky.actor.defs#viewerState",
                    "following": "at://did:plc:me/app.bsky.graph.follow/abc123",
                    "knownFollowers": {
                      "${'$'}type": "app.bsky.actor.defs#knownFollowers",
                      "count": 2,
                      "followers": [
                        {
                          "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                          "did": "did:plc:mutual1",
                          "handle": "mutual1.bsky.social",
                          "avatar": "https://cdn.example/mutual1.jpg"
                        }
                      ]
                    }
                  }
                }
              ]
            }
            """.trimIndent()

        /**
         * One ProfileView with no viewer block at all — not following.
         */
        val ACCOUNTS_RESPONSE_NO_VIEWER =
            """
            {
              "actors": [
                {
                  "${'$'}type": "app.bsky.actor.defs#profileView",
                  "did": "did:plc:bob",
                  "handle": "bob.bsky.social"
                }
              ]
            }
            """.trimIndent()

        /**
         * One GeneratorView with all fields populated.
         */
        val FEEDS_RESPONSE =
            """
            {
              "feeds": [
                {
                  "${'$'}type": "app.bsky.feed.defs#generatorView",
                  "uri": "at://did:plc:gen/app.bsky.feed.generator/science",
                  "cid": "bafyreitest1",
                  "did": "did:plc:gen",
                  "displayName": "Science",
                  "description": "Science posts",
                  "avatar": "https://cdn.example/sci.jpg",
                  "indexedAt": "2026-01-01T00:00:00Z",
                  "creator": {
                    "${'$'}type": "app.bsky.actor.defs#profileView",
                    "did": "did:plc:creator",
                    "handle": "creator.bsky.social"
                  }
                }
              ]
            }
            """.trimIndent()

        /**
         * GeneratorView with blank description and no avatar — normalized to null.
         */
        val FEEDS_RESPONSE_BLANK_DESCRIPTION =
            """
            {
              "feeds": [
                {
                  "${'$'}type": "app.bsky.feed.defs#generatorView",
                  "uri": "at://did:plc:gen/app.bsky.feed.generator/x",
                  "cid": "bafyreitest1",
                  "did": "did:plc:gen",
                  "displayName": "No Desc",
                  "description": "   ",
                  "indexedAt": "2026-01-01T00:00:00Z",
                  "creator": {
                    "${'$'}type": "app.bsky.actor.defs#profileView",
                    "did": "did:plc:creator",
                    "handle": "creator.bsky.social"
                  }
                }
              ]
            }
            """.trimIndent()

        /**
         * Single well-formed FeedViewPost without an embed.
         */
        val PREVIEW_RESPONSE_ONE_POST =
            """
            {
              "feed": [
                {
                  "${'$'}type": "app.bsky.feed.defs#feedViewPost",
                  "post": {
                    "${'$'}type": "app.bsky.feed.defs#postView",
                    "uri": "at://did:plc:poster/app.bsky.feed.post/p1",
                    "cid": "bafyreitest1",
                    "author": {
                      "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                      "did": "did:plc:poster",
                      "handle": "poster.bsky.social",
                      "avatar": "https://cdn.example/poster.jpg"
                    },
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "Preview post text",
                      "createdAt": "2026-01-01T00:00:00Z"
                    },
                    "indexedAt": "2026-01-01T00:00:00Z"
                  }
                }
              ]
            }
            """.trimIndent()

        /**
         * Single FeedViewPost with an images embed — thumbnail should map.
         */
        val PREVIEW_RESPONSE_WITH_IMAGE =
            """
            {
              "feed": [
                {
                  "${'$'}type": "app.bsky.feed.defs#feedViewPost",
                  "post": {
                    "${'$'}type": "app.bsky.feed.defs#postView",
                    "uri": "at://did:plc:poster/app.bsky.feed.post/p2",
                    "cid": "bafyreitest1",
                    "author": {
                      "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                      "did": "did:plc:poster",
                      "handle": "poster.bsky.social"
                    },
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "Post with image",
                      "createdAt": "2026-01-01T00:00:00Z"
                    },
                    "embed": {
                      "${'$'}type": "app.bsky.embed.images#view",
                      "images": [
                        {
                          "${'$'}type": "app.bsky.embed.images#viewImage",
                          "thumb": "https://cdn.example/thumb.jpg",
                          "fullsize": "https://cdn.example/full.jpg",
                          "alt": "A test image"
                        }
                      ]
                    },
                    "indexedAt": "2026-01-01T00:00:00Z"
                  }
                }
              ]
            }
            """.trimIndent()

        /**
         * Four well-formed posts — use with limit=2 to verify the cap.
         */
        val PREVIEW_RESPONSE_FOUR_POSTS =
            """
            {
              "feed": [
                ${(1..4).joinToString(",\n") { i ->
                """
                  {
                    "${'$'}type": "app.bsky.feed.defs#feedViewPost",
                    "post": {
                      "${'$'}type": "app.bsky.feed.defs#postView",
                      "uri": "at://did:plc:poster/app.bsky.feed.post/p$i",
                      "cid": "bafyreitest$i",
                      "author": {
                        "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                        "did": "did:plc:poster",
                        "handle": "poster.bsky.social"
                      },
                      "record": {
                        "${'$'}type": "app.bsky.feed.post",
                        "text": "Post $i",
                        "createdAt": "2026-01-01T00:00:00Z"
                      },
                      "indexedAt": "2026-01-01T00:00:00Z"
                    }
                  }
                """
            }}
              ]
            }
            """.trimIndent()

        /**
         * Two posts: one well-formed and one with a missing `text` field.
         * The malformed post must be filtered out by mapNotNull.
         */
        val PREVIEW_RESPONSE_WITH_MALFORMED =
            """
            {
              "feed": [
                {
                  "${'$'}type": "app.bsky.feed.defs#feedViewPost",
                  "post": {
                    "${'$'}type": "app.bsky.feed.defs#postView",
                    "uri": "at://did:plc:poster/app.bsky.feed.post/good",
                    "cid": "bafyreitest1",
                    "author": {
                      "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                      "did": "did:plc:poster",
                      "handle": "poster.bsky.social"
                    },
                    "record": {
                      "${'$'}type": "app.bsky.feed.post",
                      "text": "good post",
                      "createdAt": "2026-01-01T00:00:00Z"
                    },
                    "indexedAt": "2026-01-01T00:00:00Z"
                  }
                },
                {
                  "${'$'}type": "app.bsky.feed.defs#feedViewPost",
                  "post": {
                    "${'$'}type": "app.bsky.feed.defs#postView",
                    "uri": "at://did:plc:poster/app.bsky.feed.post/bad",
                    "cid": "bafyreitest2",
                    "author": {
                      "${'$'}type": "app.bsky.actor.defs#profileViewBasic",
                      "did": "did:plc:poster",
                      "handle": "poster.bsky.social"
                    },
                    "record": {
                      "${'$'}type": "app.bsky.feed.post"
                    },
                    "indexedAt": "2026-01-01T00:00:00Z"
                  }
                }
              ]
            }
            """.trimIndent()
    }
}
