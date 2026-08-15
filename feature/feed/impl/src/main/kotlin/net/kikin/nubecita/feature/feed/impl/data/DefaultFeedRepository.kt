package net.kikin.nubecita.feature.feed.impl.data

import io.github.kikin81.atproto.app.bsky.feed.FeedService
import io.github.kikin81.atproto.app.bsky.feed.FeedViewPost
import io.github.kikin81.atproto.app.bsky.feed.GetFeedRequest
import io.github.kikin81.atproto.app.bsky.feed.GetListFeedRequest
import io.github.kikin81.atproto.app.bsky.feed.GetTimelineRequest
import io.github.kikin81.atproto.runtime.AtUri
import io.github.kikin81.atproto.runtime.XrpcClient
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.common.coroutines.runCatchingCancellable
import net.kikin.nubecita.core.feeds.FeedViewPreferencesRepository
import net.kikin.nubecita.core.moderation.ModerationPreferencesRepository
import timber.log.Timber
import javax.inject.Inject

class DefaultFeedRepository
    @Inject
    constructor(
        private val xrpcClientProvider: XrpcClientProvider,
        private val moderationPreferences: ModerationPreferencesRepository,
        private val feedViewPreferences: FeedViewPreferencesRepository,
        private val sessionStateProvider: SessionStateProvider,
        @param:IoDispatcher private val dispatcher: CoroutineDispatcher,
    ) : FeedRepository {
        override suspend fun getTimeline(
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> =
            fetchPage("getTimeline", cursor, followScoped = true) { client, pageCursor ->
                FeedService(client)
                    .getTimeline(
                        GetTimelineRequest(
                            cursor = pageCursor,
                            limit = limit.toLong(),
                        ),
                    ).let { response -> response.feed to response.cursor }
            }

        override suspend fun getFeed(
            feedUri: String,
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> =
            // followScoped = false: a generator curates its own output, so the
            // viewer's reply/repost/quote tuners must NOT be applied — filtering
            // them here would strip most of what the algorithm selected. Matches
            // the official client, which runs these tuners on `following` and
            // `list...` descriptors only, never on `feedgen...`.
            fetchPage("getFeed", cursor, followScoped = false) { client, pageCursor ->
                FeedService(client)
                    .getFeed(
                        GetFeedRequest(
                            feed = AtUri(feedUri),
                            cursor = pageCursor,
                            limit = limit.toLong(),
                        ),
                    ).let { response -> response.feed to response.cursor }
            }

        override suspend fun getListFeed(
            listUri: String,
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> =
            // List feeds share the Following feed's tuners in the official
            // client, so they are follow-scoped too.
            fetchPage("getListFeed", cursor, followScoped = true) { client, pageCursor ->
                FeedService(client)
                    .getListFeed(
                        GetListFeedRequest(
                            list = AtUri(listUri),
                            cursor = pageCursor,
                            limit = limit.toLong(),
                        ),
                    ).let { response -> response.feed to response.cursor }
            }

        /**
         * Shared fetch-and-map pipeline for all three feed kinds. [block]
         * issues the kind-specific XRPC call against an authenticated
         * client and returns the wire `(feed, cursor)` pair; this builds
         * the one canonical [TimelinePage] shape via the same
         * `toFeedItemsUi()` mapper. No kind-specific mapping branch — the
         * three kinds all decode `List<FeedViewPost>`.
         */
        private suspend fun fetchPage(
            operation: String,
            cursor: String?,
            followScoped: Boolean,
            block: suspend (client: XrpcClient, cursor: String?) -> Pair<List<FeedViewPost>, String?>,
        ): Result<TimelinePage> =
            withContext(dispatcher) {
                runCatchingCancellable {
                    val client = xrpcClientProvider.authenticated()
                    // Read the cached prefs + viewer DID once per page; the
                    // mapper drops hard-filtered timeline posts and covers
                    // warned media off the render path. A cold cache reads the
                    // fail-safe DEFAULT (adult off).
                    val prefs = moderationPreferences.prefs.value
                    val viewerDid = (sessionStateProvider.state.value as? SessionState.SignedIn)?.did
                    val feedViewPrefs = feedViewPreferences.prefs.value

                    val feed = mutableListOf<FeedViewPost>()
                    var pageCursor = cursor
                    var nextCursor: String?
                    var rounds = 0
                    do {
                        val (wireFeed, responseCursor) = block(client, pageCursor)
                        nextCursor = responseCursor
                        pageCursor = responseCursor
                        // Apply the viewer's feed-view preferences BEFORE mapping,
                        // while the wire entries still carry the reply ancestry the
                        // predicate needs (parent / grandparent / root authors and
                        // their viewer.following) — that context is lost once an
                        // entry degrades to a FeedItemUi.Single.
                        feed +=
                            if (followScoped) {
                                wireFeed.filter { it.shouldDisplayInFollowingFeed(feedViewPrefs, viewerDid) }
                            } else {
                                wireFeed
                            }
                        rounds++
                        // Top-up: an aggressively filtered page can come back empty
                        // while the feed still has pages left. Returning that empty
                        // page would strand the caller — FeedViewModel treats an
                        // empty initial page as endReached, and an empty appended
                        // page doesn't grow the list so the near-bottom trigger may
                        // never re-fire. Keep pulling until something survives.
                        // Bounded so a viewer whose whole timeline filters out can't
                        // spin through the entire feed in one call.
                    } while (feed.isEmpty() && nextCursor != null && rounds < MAX_TOP_UP_ROUNDS)

                    TimelinePage(
                        feedItems = feed.toFeedItemsUi(prefs, viewerDid),
                        nextCursor = nextCursor,
                        wirePosts = feed.toImmutableList(),
                    )
                }.onFailure { throwable ->
                    // Surfaces the throwable identity that FeedViewModel's
                    // toFeedError() collapses into FeedError.Unknown — without
                    // this, the cold-start "Something went wrong" screen tells
                    // us nothing about which exception the atproto stack
                    // produced. See nubecita-09o.
                    // javaClass.name (not ::class.qualifiedName) so anonymous /
                    // local classes still produce a non-null identifier — this
                    // log exists specifically to identify the throwable, so
                    // "null" here would defeat the entire diagnostic.
                    Timber.tag(TAG).w(
                        throwable,
                        "%s(cursor=%s) failed: %s",
                        operation,
                        cursor,
                        throwable.javaClass.name,
                    )
                }
            }

        private companion object {
            const val TAG = "FeedRepository"

            /**
             * Upper bound on extra network round-trips spent looking for a page
             * that survives filtering. Caps worst-case latency and request count
             * for a viewer whose timeline filters out almost entirely; if we
             * still have nothing after this many rounds we return the empty page
             * and let the next LoadMore continue from the cursor.
             */
            const val MAX_TOP_UP_ROUNDS = 3
        }
    }
