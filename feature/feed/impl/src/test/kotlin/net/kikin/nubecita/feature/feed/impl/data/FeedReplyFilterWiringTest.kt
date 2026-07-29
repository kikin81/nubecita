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
    private val followed2 = "did:plc:followedtwo00000000000000"
    private val stranger = "did:plc:stranger00000000000000000"
    private val stranger2 = "did:plc:strangertwo00000000000000"

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
     * The full discriminating matrix from `nubecita-1fmx.3`, run through the real
     * repository in one page.
     *
     * Asserting the exact surviving LIST (not a count) is what makes this
     * meaningful: a filter that drops everything, a filter that drops nothing,
     * and a filter that drops the wrong subset all fail. Three of these six must
     * survive and three must go.
     */
    @Test
    fun `the Following feed keeps exactly the entries the predicate allows`() =
        runTest {
            val repo = newRepo(fullMatrixFeedJson(), UnconfinedTestDispatcher(testScheduler))

            val page = repo.getTimeline(cursor = null).getOrThrow()

            assertEquals(
                listOf(plainPostUri, selfThreadLeafUri, followedToFollowedUri, repostedStrangerReplyUri),
                page.feedItems.map { it.leafId() },
            )
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

    // --- full-matrix URIs: the three that must survive ---
    private val plainPostUri get() = "at://$followed/app.bsky.feed.post/plain"
    private val selfThreadLeafUri get() = "at://$followed/app.bsky.feed.post/selfthread2"
    private val followedToFollowedUri get() = "at://$followed/app.bsky.feed.post/f2f"
    private val repostedStrangerReplyUri get() = "at://$stranger/app.bsky.feed.post/reposted"

    // --- ...and the three that must be dropped ---
    private val followedToStrangerUri get() = "at://$followed/app.bsky.feed.post/f2s"
    private val unfollowedAuthorReplyUri get() = "at://$stranger/app.bsky.feed.post/unfollowedreply"
    private val strangerToStrangerUri get() = "at://$stranger/app.bsky.feed.post/s2s"

    /**
     * Six entries covering every branch of the predicate, interleaved so a
     * position-based bug can't accidentally pass.
     *
     * MUST SURVIVE:
     * - plain non-reply by a followed account
     * - self-thread continuation by a followed account (branch 2)
     * - followed account replying to another followed account (branch 3)
     * - a repost of a stranger-to-stranger reply (reposts are exempt)
     *
     * MUST DROP:
     * - followed account replying to a stranger  <- the reported bug (branch 3)
     * - reply authored by someone not followed (branch 1)
     * - stranger replying to a stranger (branch 1)
     */
    private fun fullMatrixFeedJson(): String =
        """
        {
          "feed": [
            ${entry(plainPostUri, followed, following = true)},
            ${
            entry(
                followedToStrangerUri,
                followed,
                following = true,
                replyRootUri = "at://$stranger/app.bsky.feed.post/r1",
                replyParentUri = "at://$stranger/app.bsky.feed.post/p1",
                replyAuthorDid = stranger,
            )
        },
            ${
            entry(
                selfThreadLeafUri,
                followed,
                following = true,
                replyRootUri = "at://$followed/app.bsky.feed.post/selfthread1",
                replyParentUri = "at://$followed/app.bsky.feed.post/selfthread1",
                replyAuthorDid = followed,
                replyAuthorFollowing = true,
            )
        },
            ${
            entry(
                unfollowedAuthorReplyUri,
                stranger,
                following = false,
                replyRootUri = "at://$followed/app.bsky.feed.post/r2",
                replyParentUri = "at://$followed/app.bsky.feed.post/p2",
                replyAuthorDid = followed,
                replyAuthorFollowing = true,
            )
        },
            ${
            entry(
                followedToFollowedUri,
                followed,
                following = true,
                replyRootUri = "at://$followed2/app.bsky.feed.post/r3",
                replyParentUri = "at://$followed2/app.bsky.feed.post/p3",
                replyAuthorDid = followed2,
                replyAuthorFollowing = true,
            )
        },
            ${
            entry(
                strangerToStrangerUri,
                stranger,
                following = false,
                replyRootUri = "at://$stranger2/app.bsky.feed.post/r4",
                replyParentUri = "at://$stranger2/app.bsky.feed.post/p4",
                replyAuthorDid = stranger2,
            )
        },
            ${
            entry(
                repostedStrangerReplyUri,
                stranger,
                following = false,
                replyRootUri = "at://$stranger2/app.bsky.feed.post/r5",
                replyParentUri = "at://$stranger2/app.bsky.feed.post/p5",
                replyAuthorDid = stranger2,
                reposterDid = followed,
            )
        }
          ]
        }
        """.trimIndent()

    private fun FeedItemUi.leafId(): String =
        when (this) {
            is FeedItemUi.Single -> post.id
            is FeedItemUi.ReplyCluster -> leaf.id
            is FeedItemUi.SelfThreadChain -> posts.lastOrNull()?.id ?: error("SelfThreadChain with no posts")
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
        replyAuthorFollowing: Boolean = false,
        reposterDid: String? = null,
    ): String {
        fun followingBlock(isFollowing: Boolean) =
            if (isFollowing) """, "viewer": { "following": "at://$viewerDid/app.bsky.graph.follow/x" }""" else ""

        val viewerBlock = followingBlock(following)
        fun postView(
            u: String,
            did: String,
        ) = """
            {
              "${'$'}type": "app.bsky.feed.defs#postView",
              "uri": "$u",
              "cid": "bafyreifakecid000000000000000000000000000000000",
              "author": { "did": "$did", "handle": "x.bsky.social"${followingBlock(replyAuthorFollowing)} },
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
        val reasonBlock =
            reposterDid?.let {
                """
                "reason": {
                  "${'$'}type": "app.bsky.feed.defs#reasonRepost",
                  "by": { "did": "$it", "handle": "reposter.bsky.social" },
                  "indexedAt": "2026-04-26T12:00:00Z"
                },
                """.trimIndent()
            } ?: ""
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
              $reasonBlock
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
