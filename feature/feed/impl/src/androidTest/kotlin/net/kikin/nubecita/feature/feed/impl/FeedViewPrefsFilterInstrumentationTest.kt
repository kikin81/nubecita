package net.kikin.nubecita.feature.feed.impl

import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.feeds.FeedViewPrefs
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.testing.android.MockEngineHandlerHolder
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.feature.feed.impl.data.DefaultFeedRepository
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/**
 * On-device end-to-end proof of the Following-feed reply filter (`nubecita-1fmx`).
 *
 * The JVM tests already cover the predicate ([net.kikin.nubecita.feature.feed.impl.data.FeedReplyFilterTest])
 * and the repository wiring ([net.kikin.nubecita.feature.feed.impl.data.FeedReplyFilterWiringTest]).
 * What only an instrumented test can prove is that the **real Hilt graph** actually
 * provides a [FeedViewPreferencesRepository] seeded with the filtering-on defaults,
 * and that `getTimeline` → decode → filter works on-device against the production
 * `HttpClient` configuration. A missing Hilt binding, or a default that silently
 * flipped to "filtering off", would fail here and nowhere else in the suite.
 *
 * The `getPreferences` → parse → publish half is covered on the JVM by
 * `FeedViewPreferencesRepositoryBoundaryTest` — it needs `refresh()`, which needs a
 * signed-in session this device does not have (see
 * [aPreferenceOfFalseDisablesFilteringOnDevice]).
 *
 * **Why the repository is constructed by hand rather than injected.** This source
 * set installs `TestFeedRepositoryModule`, a `@TestInstallIn` that replaces
 * `FeedRepositoryModule` with `FakeFeedRepository` for *every* `@HiltAndroidTest`
 * here. Injecting `FeedRepository` would therefore hand back the fake and this
 * test would pass no matter what the filter does. Constructing
 * [DefaultFeedRepository] from Hilt-injected collaborators sidesteps that while
 * keeping everything else real.
 *
 * `XrpcClientProvider` is stubbed because there is no signed-in session on a test
 * device — `authenticated()` would throw `NoSessionException`. The stub still
 * hands back an [XrpcClient] over the Hilt-provided [HttpClient], so the
 * production timeout / logging / header-sanitization configuration and the
 * `MockEngine` swap both remain in play. Auth is not what this test covers.
 */
@HiltAndroidTest
class FeedViewPrefsFilterInstrumentationTest {
    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var mockEngine: MockEngineHandlerHolder

    @Inject
    lateinit var httpClient: HttpClient

    /** Injected, not constructed — this is the binding the test exists to prove. */
    @Inject
    lateinit var feedViewPreferences: FeedViewPreferencesRepository

    @Inject
    lateinit var moderationPreferences: ModerationPreferencesRepository

    @Before
    fun setUp() {
        hiltRule.inject()
    }

    @After
    fun tearDown() {
        mockEngine.reset()
        feedViewPreferences.resetToDefault()
    }

    @Test
    fun hiltProvidesFeedViewPreferencesSeededWithTheOfficialDefaults() {
        // Proves the binding resolves at all, and that a reader observing before
        // any refresh sees hideRepliesByUnfollowed = true rather than an
        // unfiltered feed.
        assertTrue(feedViewPreferences.prefs.value.hideRepliesByUnfollowed)
    }

    /**
     * The headline case, driven by the **Hilt-provided** repository at its seeded
     * default — no `refresh()` needed, because the default already is
     * `hideRepliesByUnfollowed = true`. So the preference value reaching the
     * filter here comes from the real DI graph, not a test double.
     */
    @Test
    fun followingFeedKeepsExactlyTheEntriesThePredicateAllows() =
        runBlocking {
            routeRequests()

            val page = newRepository(feedViewPreferences).getTimeline(cursor = null).getOrThrow()

            // Three survive, two drop. Asserting the exact list means neither
            // "filter everything" nor "filter nothing" can pass.
            assertEquals(
                listOf(PLAIN_URI, SELF_THREAD_LEAF_URI, FOLLOWED_TO_FOLLOWED_URI),
                page.feedItems.map { it.leafId() },
            )
        }

    /**
     * The other direction: `hideRepliesByUnfollowed = false` must actually
     * disable filtering, proving the filter reads the preference rather than
     * hard-coding the behaviour.
     *
     * This one uses a local stub rather than the injected repository. Reaching a
     * non-default value requires `refresh()`, which goes through the real
     * `XrpcClientProvider` and throws `NoSessionException` — there is no signed-in
     * session on a test device, and the production auth binding lives in a
     * flavored module this source set cannot reference to replace. The
     * `getPreferences` → parse → publish half is covered on the JVM by
     * `FeedViewPreferencesRepositoryBoundaryTest`; what is exercised here is the
     * filter honouring a non-default value end-to-end on-device.
     */
    @Test
    fun aPreferenceOfFalseDisablesFilteringOnDevice() =
        runBlocking {
            routeRequests()
            val allOff = StubFeedViewPreferences(FeedViewPrefs.DEFAULT.copy(hideRepliesByUnfollowed = false))
            assertFalse(allOff.prefs.value.hideRepliesByUnfollowed)

            val page = newRepository(allOff).getTimeline(cursor = null).getOrThrow()

            assertEquals(
                listOf(
                    PLAIN_URI,
                    FOLLOWED_TO_STRANGER_URI,
                    SELF_THREAD_LEAF_URI,
                    FOLLOWED_TO_FOLLOWED_URI,
                    STRANGER_REPLY_URI,
                ),
                page.feedItems.map { it.leafId() },
            )
        }

    /** A generator feed curates its own output and must not be filtered. */
    @Test
    fun generatorFeedIsNotFiltered() =
        runBlocking {
            routeRequests()

            val page =
                newRepository(feedViewPreferences)
                    .getFeed(feedUri = "at://$FOLLOWED/app.bsky.feed.generator/g", cursor = null)
                    .getOrThrow()

            assertEquals(
                listOf(
                    PLAIN_URI,
                    FOLLOWED_TO_STRANGER_URI,
                    SELF_THREAD_LEAF_URI,
                    FOLLOWED_TO_FOLLOWED_URI,
                    STRANGER_REPLY_URI,
                ),
                page.feedItems.map { it.leafId() },
            )
        }

    private class StubFeedViewPreferences(
        value: FeedViewPrefs,
    ) : FeedViewPreferencesRepository {
        override val prefs = kotlinx.coroutines.flow.MutableStateFlow(value)

        override suspend fun refresh() = Unit

        override fun resetToDefault() = Unit
    }

    // ---------- helpers ----------

    private fun newRepository(prefs: FeedViewPreferencesRepository) =
        DefaultFeedRepository(
            xrpcClientProvider =
                object : XrpcClientProvider {
                    override suspend fun authenticated(): XrpcClient = XrpcClient(baseUrl = "https://example.invalid", httpClient = httpClient)
                },
            moderationPreferences = moderationPreferences,
            feedViewPreferences = prefs,
            sessionStateProvider =
                object : SessionStateProvider {
                    override val state =
                        kotlinx.coroutines.flow.MutableStateFlow<SessionState>(
                            SessionState.SignedIn(handle = "viewer.test", did = VIEWER),
                        )

                    override suspend fun refresh() = Unit
                },
            dispatcher = Dispatchers.IO,
        )

    private fun routeRequests() {
        mockEngine.handler = { request ->
            when (request.url.encodedPath) {
                "/xrpc/app.bsky.feed.getTimeline", "/xrpc/app.bsky.feed.getFeed" -> jsonOk(FEED_BODY)
                else -> respondError(HttpStatusCode.NotFound, "unrouted: ${request.url.encodedPath}")
            }
        }
    }

    private fun io.ktor.client.engine.mock.MockRequestHandleScope.jsonOk(body: String) =
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )

    private fun FeedItemUi.leafId(): String =
        when (this) {
            is FeedItemUi.Single -> post.id
            is FeedItemUi.ReplyCluster -> leaf.id
            is FeedItemUi.SelfThreadChain -> posts.lastOrNull()?.id ?: error("SelfThreadChain with no posts")
            else -> error("unexpected feed item $this")
        }

    private companion object {
        const val VIEWER = "did:plc:viewer00000000000000000000"
        const val FOLLOWED = "did:plc:followed000000000000000000"
        const val FOLLOWED2 = "did:plc:followedtwo00000000000000"
        const val STRANGER = "did:plc:stranger00000000000000000"

        const val PLAIN_URI = "at://$FOLLOWED/app.bsky.feed.post/plain"
        const val FOLLOWED_TO_STRANGER_URI = "at://$FOLLOWED/app.bsky.feed.post/f2s"
        const val SELF_THREAD_LEAF_URI = "at://$FOLLOWED/app.bsky.feed.post/self2"
        const val FOLLOWED_TO_FOLLOWED_URI = "at://$FOLLOWED/app.bsky.feed.post/f2f"
        const val STRANGER_REPLY_URI = "at://$STRANGER/app.bsky.feed.post/s2s"

        private fun followingViewer() = """, "viewer": { "following": "at://$VIEWER/app.bsky.graph.follow/x" }"""

        private fun profile(
            did: String,
            following: Boolean,
        ) = """{ "did": "$did", "handle": "h.bsky.social"${if (following) followingViewer() else ""} }"""

        private fun postView(
            uri: String,
            did: String,
            following: Boolean,
        ) = """
            {
              "${'$'}type": "app.bsky.feed.defs#postView",
              "uri": "$uri",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": ${profile(did, following)},
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": { "${'$'}type": "app.bsky.feed.post", "text": "t", "createdAt": "2026-04-26T12:00:00Z" }
            }
            """.trimIndent()

        private fun entry(
            uri: String,
            did: String,
            following: Boolean,
            ancestorDid: String? = null,
            ancestorFollowing: Boolean = false,
        ): String {
            val reply =
                ancestorDid?.let {
                    val root = postView("at://$it/app.bsky.feed.post/root", it, ancestorFollowing)
                    val parent = postView("at://$it/app.bsky.feed.post/parent", it, ancestorFollowing)
                    """"reply": { "root": $root, "parent": $parent },"""
                } ?: ""
            return """
                {
                  "post": ${postView(uri, did, following)},
                  $reply
                  "indexedAt": "2026-04-26T12:00:00Z"
                }
                """.trimIndent()
        }

        /**
         * Must survive: plain post, followed self-thread, followed→followed reply.
         * Must drop: followed→stranger (the reported bug), stranger→stranger.
         */
        val FEED_BODY =
            """
            {
              "feed": [
                ${entry(PLAIN_URI, FOLLOWED, following = true)},
                ${entry(FOLLOWED_TO_STRANGER_URI, FOLLOWED, following = true, ancestorDid = STRANGER)},
                ${
                entry(
                    SELF_THREAD_LEAF_URI,
                    FOLLOWED,
                    following = true,
                    ancestorDid = FOLLOWED,
                    ancestorFollowing = true,
                )
            },
                ${
                entry(
                    FOLLOWED_TO_FOLLOWED_URI,
                    FOLLOWED,
                    following = true,
                    ancestorDid = FOLLOWED2,
                    ancestorFollowing = true,
                )
            },
                ${entry(STRANGER_REPLY_URI, STRANGER, following = false, ancestorDid = STRANGER)}
              ]
            }
            """.trimIndent()
    }
}
