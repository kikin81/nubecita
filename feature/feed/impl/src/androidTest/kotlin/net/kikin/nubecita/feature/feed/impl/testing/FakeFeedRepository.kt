package net.kikin.nubecita.feature.feed.impl.testing

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import net.kikin.nubecita.feature.feed.impl.data.FeedRepository
import net.kikin.nubecita.feature.feed.impl.data.TimelinePage
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * In-memory [FeedRepository] for instrumentation tests. Hilt-injected via
 * `TestFeedRepositoryModule`'s `@TestInstallIn(replaces = [FeedRepositoryModule::class])`.
 *
 * Returns a fixed three-post timeline by default. Tests that need a
 * different page can mutate [page] before triggering a load — but the
 * default covers the basic render assertion in
 * `FeedScreenInstrumentationTest`.
 *
 * Doesn't honor cursors — every request returns the same page with
 * `nextCursor = null`, signaling end-of-feed. Pagination behavior is
 * out of scope for the first reference test; a follow-up can extend
 * this fake to track cursors if a paging test lands.
 */
@Singleton
internal class FakeFeedRepository
    @Inject
    constructor() : FeedRepository {
        @Volatile
        var page: TimelinePage = DEFAULT_TIMELINE

        @Volatile
        var failureToReturn: Throwable? = null

        override suspend fun getTimeline(
            cursor: String?,
            limit: Int,
        ): Result<TimelinePage> {
            failureToReturn?.let { return Result.failure(it) }
            return Result.success(page)
        }

        companion object {
            val DEFAULT_TIMELINE: TimelinePage =
                TimelinePage(
                    feedItems =
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
                        ).toImmutableList(),
                    nextCursor = null,
                )

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
        }
    }
