package net.kikin.nubecita.feature.search.impl.data

import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.FeedItemUi
import net.kikin.nubecita.data.models.ImageUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Bench-flavor stand-in for [SearchPostsRepository]. Returns a fixed,
 * marketing-quality set of search-result posts regardless of the query
 * string, so the bench screenshot journey can drive the Posts tab to a
 * populated, deterministic state without any network call.
 *
 * Named with the `Bench` prefix to disambiguate from the `src/test`
 * [FakeSearchPostsRepository] (gate-driven, for unit tests) and the
 * `src/androidTest` `FakeSearchPostsRepository` — they live in different
 * source sets and serve different purposes.
 *
 * Behavior:
 *
 * - [searchPosts] ignores `query`, `cursor`, `limit`, and `sort`: it
 *   always returns the full [posts] fixture in a single page with
 *   `nextCursor = null`, so `SearchPostsViewModel.loadMore` short-circuits
 *   via its `endReached` guard before issuing a second-page call. The
 *   journey scrolls the loaded page; pagination variance is not what it
 *   measures.
 * - Never fails — the bench journey targets the populated Loaded state,
 *   not the error/retry path.
 *
 * Image fixtures reference `file:///android_asset/img/...` assets merged
 * into the bench APK's asset tree from `:app`'s bench source set. AGP
 * merges every module's bench-flavor assets, so the relative asset paths
 * resolve at runtime.
 */
@Singleton
internal class BenchFakeSearchPostsRepository
    @Inject
    constructor() : SearchPostsRepository {
        override suspend fun searchPosts(
            query: String,
            cursor: String?,
            limit: Int,
            sort: SearchPostsSort,
        ): Result<SearchPostsPage> =
            Result.success(
                SearchPostsPage(
                    items = posts.toImmutableList(),
                    nextCursor = null,
                ),
            )

        private val posts: List<FeedItemUi.Single> =
            listOf(
                searchHit(
                    id = "at://did:plc:alice/app.bsky.feed.post/1",
                    author = alice,
                    text =
                        "Pour-over notes: this Yirgacheffe lot is all jasmine and stone fruit. " +
                            "Best brew I've had this month.",
                    createdAt = "2026-05-30T08:12:00Z",
                    stats = PostStatsUi(replyCount = 12, repostCount = 34, likeCount = 287, quoteCount = 3),
                    embed =
                        imageEmbed(
                            "img/posts/coffee-yirgacheffe.jpg",
                            altText = "A pour-over coffee dripping into a glass carafe",
                        ),
                ),
                searchHit(
                    id = "at://did:plc:bob/app.bsky.feed.post/2",
                    author = bob,
                    text =
                        "Made it to the ridge overlook just before the clouds rolled in. " +
                            "Worth every switchback.",
                    createdAt = "2026-05-29T17:45:00Z",
                    stats = PostStatsUi(replyCount = 8, repostCount = 52, likeCount = 410, quoteCount = 6),
                    embed =
                        imageEmbed(
                            "img/posts/ridge-overlook.jpg",
                            altText = "A mountain ridge overlook above a cloud layer",
                        ),
                ),
                searchHit(
                    id = "at://did:plc:carmen/app.bsky.feed.post/3",
                    author = carmen,
                    text =
                        "Today's sketchbook page. Trying to loosen up my line work — " +
                            "less erasing, more committing.",
                    createdAt = "2026-05-29T11:20:00Z",
                    stats = PostStatsUi(replyCount = 21, repostCount = 19, likeCount = 533, quoteCount = 4),
                    embed =
                        imageEmbed(
                            "img/posts/sketchbook-page.jpg",
                            altText = "An open sketchbook page with loose pencil drawings",
                        ),
                ),
                searchHit(
                    id = "at://did:plc:diego/app.bsky.feed.post/4",
                    author = diego,
                    text =
                        "First ripe tomatoes off the truss. Grew these from seed in February — " +
                            "patience pays off.",
                    createdAt = "2026-05-28T19:03:00Z",
                    stats = PostStatsUi(replyCount = 15, repostCount = 9, likeCount = 198, quoteCount = 1),
                    embed =
                        imageEmbed(
                            "img/posts/tomato-truss.jpg",
                            altText = "Ripe red tomatoes still on the truss",
                        ),
                ),
                searchHit(
                    id = "at://did:plc:elena/app.bsky.feed.post/5",
                    author = elena,
                    text =
                        "Shipped the new search ranking today. Latency down 40%, recall up. " +
                            "Small team, big week.",
                    createdAt = "2026-05-28T14:30:00Z",
                    stats = PostStatsUi(replyCount = 33, repostCount = 71, likeCount = 642, quoteCount = 12),
                ),
                searchHit(
                    id = "at://did:plc:fede/app.bsky.feed.post/6",
                    author = fede,
                    text =
                        "Hot take: a good changelog is a love letter to your future self. " +
                            "Write it like someone will read it. They will.",
                    createdAt = "2026-05-27T22:11:00Z",
                    stats = PostStatsUi(replyCount = 47, repostCount = 128, likeCount = 905, quoteCount = 24),
                ),
                searchHit(
                    id = "at://did:plc:gabe/app.bsky.feed.post/7",
                    author = gabe,
                    text =
                        "Reminder that the best camera is the one you already carry. " +
                            "Shot this on a phone, on a walk, on a Tuesday.",
                    createdAt = "2026-05-27T09:50:00Z",
                    stats = PostStatsUi(replyCount = 6, repostCount = 14, likeCount = 156, quoteCount = 0),
                ),
                searchHit(
                    id = "at://did:plc:hugo/app.bsky.feed.post/8",
                    author = hugo,
                    text =
                        "Three years on Bluesky and the feeds keep getting better. " +
                            "Custom algorithms were the unlock for me.",
                    createdAt = "2026-05-26T16:40:00Z",
                    stats = PostStatsUi(replyCount = 18, repostCount = 40, likeCount = 374, quoteCount = 5),
                ),
            )

        private companion object {
            // CID is the same placeholder used across the bench/test post
            // fixtures — it's never validated by the renderer, only carried.
            const val FAKE_CID = "bafyreifakecid000000000000000000000000000000000"

            val alice = author("alice", "alice.bsky.social", "Alice Chen")
            val bob = author("bob", "bob.trailmix.social", "Bob Okafor")
            val carmen = author("carmen", "carmen.draws", "Carmen Ruiz")
            val diego = author("diego", "diego.garden", "Diego Santos")
            val elena = author("elena", "elena.dev", "Elena Volkova")
            val fede = author("fede", "fede.writes", "Federico Marin")
            val gabe = author("gabe", "gabe.shoots", "Gabe Nakamura")
            val hugo = author("hugo", "hugo.bsky.social", "Hugo Bauer")

            fun author(
                slug: String,
                handle: String,
                displayName: String,
            ): AuthorUi =
                AuthorUi(
                    did = "did:plc:$slug",
                    handle = handle,
                    displayName = displayName,
                    avatarUrl = "file:///android_asset/img/avatars/$slug.jpg",
                )

            fun imageEmbed(
                assetPath: String,
                altText: String,
            ): EmbedUi.Images =
                EmbedUi.Images(
                    items =
                        persistentListOf(
                            ImageUi(
                                fullsizeUrl = "file:///android_asset/$assetPath",
                                thumbUrl = "file:///android_asset/$assetPath",
                                altText = altText,
                                aspectRatio = 1.5f,
                            ),
                        ),
                )

            fun searchHit(
                id: String,
                author: AuthorUi,
                text: String,
                createdAt: String,
                stats: PostStatsUi,
                embed: EmbedUi = EmbedUi.Empty,
            ): FeedItemUi.Single =
                FeedItemUi.Single(
                    post =
                        PostUi(
                            id = id,
                            cid = FAKE_CID,
                            author = author,
                            createdAt = Instant.parse(createdAt),
                            text = text,
                            facets = persistentListOf(),
                            embed = embed,
                            stats = stats,
                            viewer = ViewerStateUi(),
                            repostedBy = null,
                        ),
                )
        }
    }
