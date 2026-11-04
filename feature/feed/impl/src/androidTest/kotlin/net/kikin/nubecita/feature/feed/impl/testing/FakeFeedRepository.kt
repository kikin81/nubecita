package net.kikin.nubecita.feature.feed.impl.testing

import androidx.paging.PagingData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.kikin.nubecita.core.feedcache.CatchUpOutcome
import net.kikin.nubecita.core.feedcache.FeedAnchor
import net.kikin.nubecita.core.feedcache.FeedKey
import net.kikin.nubecita.core.feedcache.FeedRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * In-memory `:core:feed-cache` [FeedRepository] for instrumentation tests,
 * swapped in by `TestFeedRepositoryModule`.
 *
 * Serves a fixed three-post timeline as a single page. Tests that need
 * different content assign [items] before the screen composes — the default
 * covers the render assertions in `FeedScreenInstrumentationTest`.
 *
 * One page, no cursors: these tests assert rendering and tap routing, and
 * pagination is covered where it lives now, by `:core:feed-cache`'s own tests
 * against the mediator and the `PagingSource`.
 */
@Singleton
internal class FakeFeedRepository
    @Inject
    constructor() : FeedRepository {
        @Volatile
        var items: List<FeedItemUi> = DEFAULT_ITEMS

        override fun pagedFeed(
            feedKey: FeedKey,
            initialPosition: Int?,
        ): Flow<PagingData<FeedItemUi>> = flowOf(PagingData.from(items))

        override fun head(
            feedKey: FeedKey,
            n: Int,
        ): Flow<List<PostUi>> = flowOf(emptyList())

        override suspend fun refresh(feedKey: FeedKey): Result<Boolean> = Result.success(true)

        override suspend fun catchUp(feedKey: FeedKey): Result<CatchUpOutcome> = Result.success(CatchUpOutcome.NOT_POSSIBLE)

        /** The place to restore to, as a stored anchor would have been resolved. */
        @Volatile
        var storedAnchor: FeedAnchor? = null

        /** The last place recorded by the screen, for the record half of the round trip. */
        @Volatile
        var savedAnchor: Pair<String, Int>? = null

        override suspend fun anchor(feedKey: FeedKey): FeedAnchor? = storedAnchor

        override suspend fun saveAnchor(
            feedKey: FeedKey,
            itemKey: String,
            offset: Int,
        ) {
            savedAnchor = itemKey to offset
        }

        override suspend fun clearAnchor(feedKey: FeedKey) {
            storedAnchor = null
            savedAnchor = null
        }

        override suspend fun trimToCap(
            feedKey: FeedKey,
            cap: Int,
            anchorItemPosition: Int?,
            ceiling: Int,
        ) = Unit

        override suspend fun clearAccount(accountDid: String) = Unit

        companion object {
            val DEFAULT_ITEMS: List<FeedItemUi> =
                listOf(
                    singlePost(
                        id = "at://did:plc:alice/app.bsky.feed.post/post1",
                        cid = "bafyreitest1",
                        authorHandle = "alice.bsky.social",
                        authorDisplayName = "Alice",
                        text = "Hello world from alice",
                    ),
                    singlePost(
                        id = "at://did:plc:bob/app.bsky.feed.post/post2",
                        cid = "bafyreitest2",
                        authorHandle = "bob.bsky.social",
                        authorDisplayName = "Bob",
                        text = "Bluesky is fun",
                    ),
                    singlePost(
                        id = "at://did:plc:carol/app.bsky.feed.post/post3",
                        cid = "bafyreitest3",
                        authorHandle = "carol.bsky.social",
                        authorDisplayName = "Carol",
                        text = "Three posts in this feed",
                    ),
                )

            /** [count] posts, enough rows to scroll through. */
            fun posts(count: Int): List<FeedItemUi> =
                (0 until count).map { index ->
                    singlePost(
                        id = "at://did:plc:scroll/app.bsky.feed.post/p$index",
                        cid = "bafyreiscroll$index",
                        authorHandle = "scroller.bsky.social",
                        authorDisplayName = "Scroller",
                        text = "scroll fixture post number $index",
                    )
                }

            private fun singlePost(
                id: String,
                cid: String,
                authorHandle: String,
                authorDisplayName: String,
                text: String,
            ): FeedItemUi.Single =
                FeedItemUi.Single(
                    post =
                        PostUi(
                            id = id,
                            cid = cid,
                            author =
                                AuthorUi(
                                    did = id.substringAfter("at://").substringBefore("/"),
                                    handle = authorHandle,
                                    displayName = authorDisplayName,
                                    avatarUrl = null,
                                ),
                            createdAt = Instant.fromEpochSeconds(1_700_000_000),
                            text = text,
                            facets = persistentListOf(),
                            embed = EmbedUi.Empty,
                            stats = PostStatsUi(),
                            viewer = ViewerStateUi(),
                            repostedBy = null,
                        ),
                )

            /**
             * Single-post timeline whose only post carries an
             * `EmbedUi.Video`. Used by the feed → fullscreen instrumented
             * test to exercise the autoplay-coordinator bind and the
             * `onVideoTap → onNavigateToVideoPlayer` wiring.
             *
             * Tests should pass a `playlistUrl` under the RFC 6761
             * `.invalid` TLD (e.g. `https://example.invalid/...`) so
             * ExoPlayer's HLS load fails fast at DNS resolution and
             * never reaches a real server. The tap-routing assertion
             * the test cares about doesn't depend on successful
             * playback.
             */
            fun singleVideoPostTimeline(
                postUri: String,
                playlistUrl: String,
                posterUrl: String?,
                altText: String,
            ): List<FeedItemUi> =
                listOf(
                    FeedItemUi.Single(
                        post =
                            PostUi(
                                id = postUri,
                                cid = "bafyreitestvideo",
                                author =
                                    AuthorUi(
                                        did = postUri.substringAfter("at://").substringBefore("/"),
                                        handle = "video.bsky.social",
                                        displayName = "Video Author",
                                        avatarUrl = null,
                                    ),
                                createdAt = Instant.fromEpochSeconds(1_700_000_000),
                                text = "Post with a video",
                                facets = persistentListOf(),
                                embed =
                                    EmbedUi.Video(
                                        posterUrl = posterUrl,
                                        playlistUrl = playlistUrl,
                                        aspectRatio = 16f / 9f,
                                        durationSeconds = null,
                                        altText = altText,
                                    ),
                                stats = PostStatsUi(),
                                viewer = ViewerStateUi(),
                                repostedBy = null,
                            ),
                    ),
                )
        }
    }
