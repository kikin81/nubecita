package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.utils.io.ByteReadChannel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.feeds.FeedViewPrefs
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.moderation.ContentLabel
import net.kikin.nubecita.core.moderation.LabelVisibility
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPrefs
import net.kikin.nubecita.data.models.FeedItemUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Verifies the reply filter is actually WIRED into the repository, and wired to
 * the right feeds.
 *
 * `FeedReplyFilterTest` proves the predicate is correct in isolation; this file
 * proves `DefaultFeedRepository` calls it — and that `getFeed` (a generator /
 * custom feed) is deliberately left unfiltered, matching the official client,
 * which applies these tuners to `following` and `list...` only.
 *
 * The fixtures are discriminating: each response mixes an item that must survive
 * with one that must be dropped, so neither "filter everything" nor "filter
 * nothing" can pass.
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class FeedReplyFilterWiringTest {
    private val viewerDid = "did:plc:viewer00000000000000000000"
    private val followed = "did:plc:followed000000000000000000"
    private val stranger = "did:plc:stranger00000000000000000"

    @Test
    fun `getTimeline drops a followed account's reply to a stranger and keeps the plain post`() =
        runTest {
            val repo = newRepo(mixedFeedJson(), UnconfinedTestDispatcher(testScheduler))

            val page = repo.getTimeline(cursor = null).getOrThrow()

            assertEquals(listOf(keptPostUri), page.feedItems.map { it.leafId() })
        }

    @Test
    fun `getListFeed applies the same filter as the Following feed`() =
        runTest {
            val repo = newRepo(mixedFeedJson(), UnconfinedTestDispatcher(testScheduler))

            val page = repo.getListFeed(listUri = "at://$followed/app.bsky.graph.list/l", cursor = null).getOrThrow()

            assertEquals(listOf(keptPostUri), page.feedItems.map { it.leafId() })
        }

    /**
     * A feed generator curates its own output; filtering replies out of it would
     * remove most of what the algorithm deliberately selected.
     */
    @Test
    fun `getFeed does not filter a generator feed`() =
        runTest {
            val repo = newRepo(mixedFeedJson(), UnconfinedTestDispatcher(testScheduler))

            val page = repo.getFeed(feedUri = "at://$followed/app.bsky.feed.generator/g", cursor = null).getOrThrow()

            assertEquals(listOf(keptPostUri, droppedPostUri), page.feedItems.map { it.leafId() })
        }

    @Test
    fun `hideRepliesByUnfollowed disabled keeps the stranger reply on the Following feed`() =
        runTest {
            val repo =
                newRepo(
                    mixedFeedJson(),
                    UnconfinedTestDispatcher(testScheduler),
                    prefs = FeedViewPrefs.DEFAULT.copy(hideRepliesByUnfollowed = false),
                )

            val page = repo.getTimeline(cursor = null).getOrThrow()

            assertEquals(listOf(keptPostUri, droppedPostUri), page.feedItems.map { it.leafId() })
        }

    /**
     * A page whose entries are ALL filtered out must not surface as an empty
     * page while a cursor remains. `FeedViewModel.applyInitialPage` sets
     * `endReached = nextCursor == null || items.isEmpty()`, so an over-filtered
     * first page would otherwise strand the user on a permanently empty
     * Following feed; and `loadMore` would not grow the list, so the
     * near-bottom trigger might never re-fire. The repository tops up instead.
     */
    @Test
    fun `getTimeline tops up when a whole page is filtered away`() =
        runTest {
            val repo =
                newRepoWithPages(
                    listOf(
                        allFilteredPageJson(cursor = "page2"),
                        singleKeeperPageJson(cursor = null),
                    ),
                    UnconfinedTestDispatcher(testScheduler),
                )

            val page = repo.getTimeline(cursor = null).getOrThrow()

            assertEquals(listOf(keptPostUri), page.feedItems.map { it.leafId() })
            assertEquals(null, page.nextCursor)
        }

    @Test
    fun `top-up stops at the end of the feed and returns an empty page`() =
        runTest {
            val repo =
                newRepoWithPages(
                    listOf(allFilteredPageJson(cursor = null)),
                    UnconfinedTestDispatcher(testScheduler),
                )

            val page = repo.getTimeline(cursor = null).getOrThrow()

            assertEquals(emptyList<String>(), page.feedItems.map { it.leafId() })
            assertEquals(null, page.nextCursor)
        }

    // ---------- helpers ----------

    private fun newRepoWithPages(
        bodies: List<String>,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
    ): DefaultFeedRepository {
        var index = 0
        val engine =
            MockEngine {
                val body = bodies[minOf(index, bodies.lastIndex)]
                index++
                respond(
                    content = ByteReadChannel(body),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        val provider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient =
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient = HttpClient(engine),
                    )
            }
        return DefaultFeedRepository(
            provider,
            fakeModerationPrefs,
            FakeFeedViewPrefs(FeedViewPrefs.DEFAULT),
            fakeSession,
            dispatcher,
        )
    }

    private fun allFilteredPageJson(cursor: String?): String {
        val cursorField = cursor?.let { """"cursor": "$it",""" } ?: ""
        return """
            {
              $cursorField
              "feed": [
                ${
            entry(
                droppedPostUri,
                followed,
                following = true,
                replyRootUri = "at://$stranger/app.bsky.feed.post/root",
                replyParentUri = "at://$stranger/app.bsky.feed.post/parent",
                replyAuthorDid = stranger,
            )
        }
              ]
            }
            """.trimIndent()
    }

    private fun singleKeeperPageJson(cursor: String?): String {
        val cursorField = cursor?.let { """"cursor": "$it",""" } ?: ""
        return """
            {
              $cursorField
              "feed": [ ${entry(keptPostUri, followed, following = true)} ]
            }
            """.trimIndent()
    }

    private val keptPostUri get() = "at://$followed/app.bsky.feed.post/kept"
    private val droppedPostUri get() = "at://$followed/app.bsky.feed.post/dropped"

    private fun FeedItemUi.leafId(): String =
        when (this) {
            is FeedItemUi.Single -> post.id
            is FeedItemUi.ReplyCluster -> leaf.id
            is FeedItemUi.SelfThreadChain -> posts.last().id
            else -> error("unexpected item $this")
        }

    private fun newRepo(
        body: String,
        dispatcher: kotlinx.coroutines.CoroutineDispatcher,
        prefs: FeedViewPrefs = FeedViewPrefs.DEFAULT,
    ): DefaultFeedRepository {
        val engine =
            MockEngine {
                respond(
                    content = ByteReadChannel(body),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        val provider =
            object : XrpcClientProvider {
                override suspend fun authenticated(): XrpcClient =
                    XrpcClient(
                        baseUrl = "https://example.test",
                        httpClient = HttpClient(engine),
                    )
            }
        return DefaultFeedRepository(
            provider,
            fakeModerationPrefs,
            FakeFeedViewPrefs(prefs),
            fakeSession,
            dispatcher,
        )
    }

    /**
     * One post that must survive (a plain non-reply by a followed account) and
     * one that must be dropped (that same followed account replying into a
     * thread where root and parent are both strangers) — the exact case users
     * reported.
     */
    private fun mixedFeedJson(): String =
        """
        {
          "feed": [
            ${entry(keptPostUri, followed, following = true)},
            ${
            entry(
                droppedPostUri,
                followed,
                following = true,
                replyRootUri = "at://$stranger/app.bsky.feed.post/root",
                replyParentUri = "at://$stranger/app.bsky.feed.post/parent",
                replyAuthorDid = stranger,
            )
        }
          ]
        }
        """.trimIndent()

    private fun entry(
        uri: String,
        authorDid: String,
        following: Boolean = false,
        replyRootUri: String? = null,
        replyParentUri: String? = null,
        replyAuthorDid: String? = null,
    ): String {
        val viewerBlock = if (following) """, "viewer": { "following": "at://$viewerDid/app.bsky.graph.follow/x" }""" else ""
        fun postView(
            u: String,
            did: String,
        ) = """
            {
              "${'$'}type": "app.bsky.feed.defs#postView",
              "uri": "$u",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": { "did": "$did", "handle": "x.bsky.social" },
              "indexedAt": "2026-04-26T12:00:00Z",
              "record": { "${'$'}type": "app.bsky.feed.post", "text": "t", "createdAt": "2026-04-26T12:00:00Z" }
            }
            """.trimIndent()

        val replyBlock =
            if (replyRootUri == null || replyParentUri == null || replyAuthorDid == null) {
                ""
            } else {
                """"reply": { "root": ${postView(replyRootUri, replyAuthorDid)}, "parent": ${postView(
                    replyParentUri,
                    replyAuthorDid,
                )} },"""
            }
        return """
            {
              "post": {
                "uri": "$uri",
                "cid": "bafyreifakecid000000000000000000000000000000000",
                "author": { "did": "$authorDid", "handle": "a.bsky.social"$viewerBlock },
                "indexedAt": "2026-04-26T12:00:00Z",
                "record": { "${'$'}type": "app.bsky.feed.post", "text": "t", "createdAt": "2026-04-26T12:00:00Z" }
              },
              $replyBlock
              "indexedAt": "2026-04-26T12:00:00Z"
            }
            """.trimIndent()
    }

    private class FakeFeedViewPrefs(
        value: FeedViewPrefs,
    ) : FeedViewPreferencesRepository {
        override val prefs = MutableStateFlow(value)

        override suspend fun refresh() = Unit

        override fun resetToDefault() = Unit
    }

    private val fakeModerationPrefs =
        object : ModerationPreferencesRepository {
            override val prefs = MutableStateFlow(ModerationPrefs.DEFAULT)

            override suspend fun refresh() = Unit

            override fun resetToDefault() = Unit

            override suspend fun setAdultContentEnabled(enabled: Boolean) = Unit

            override suspend fun setVisibility(
                label: ContentLabel,
                visibility: LabelVisibility,
            ) = Unit
        }

    private val fakeSession =
        object : SessionStateProvider {
            override val state = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "v.test", did = viewerDid))

            override suspend fun refresh() = Unit
        }
}
