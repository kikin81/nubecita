package net.kikin.nubecita.core.feedcache

import androidx.paging.PagingData
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Bench-flavor [FeedRepository]: emits a fixed set of in-process sample posts so
 * the Glance feed widget renders offline (no session, no network, no Room) in
 * the `bench` flavor (nubecita-epe3). Every sample carries a valid post AT-URI
 * so the widget's rows are clickable (each maps to a `nubecita://…` deep link),
 * which is exactly the path the Android-11 list-adapter trampoline crash
 * (nubecita-ew77) exercises. No media (empty embed) so the widget never touches
 * the thumbnail loader, keeping the render fully offline.
 *
 * The maintenance operations are no-ops — the bench cache is immutable sample
 * data — and [pagedFeed] wraps the same head so a bench feed screen (if wired)
 * shows the same posts.
 */
@Singleton
internal class FakeFeedRepository
    @Inject
    constructor() : FeedRepository {
        override fun pagedFeed(feedKey: FeedKey): Flow<PagingData<PostUi>> = flowOf(PagingData.from(SAMPLE_POSTS))

        override fun head(
            feedKey: FeedKey,
            n: Int,
        ): Flow<List<PostUi>> = flowOf(SAMPLE_POSTS.take(n))

        override suspend fun refresh(feedKey: FeedKey): Result<Boolean> = Result.success(true)

        override suspend fun trimToCap(
            feedKey: FeedKey,
            cap: Int,
        ) = Unit

        override suspend fun clearAccount(accountDid: String) = Unit

        private companion object {
            private fun samplePost(
                didSuffix: String,
                rkey: String,
                handle: String,
                displayName: String,
                createdAt: String,
                body: String,
                stats: PostStatsUi,
            ): PostUi =
                PostUi(
                    id = "at://did:plc:$didSuffix/app.bsky.feed.post/$rkey",
                    cid = "bafyreibench${didSuffix}00000000000000000000000000000000000000",
                    author =
                        AuthorUi(
                            did = "did:plc:$didSuffix",
                            handle = handle,
                            displayName = displayName,
                            avatarUrl = "file:///android_asset/img/avatars/$didSuffix.jpg",
                        ),
                    createdAt = Instant.parse(createdAt),
                    text = body,
                    facets = persistentListOf(),
                    embed = EmbedUi.Empty,
                    stats = stats,
                    viewer = ViewerStateUi(),
                    repostedBy = null,
                )

            val SAMPLE_POSTS: List<PostUi> =
                listOf(
                    samplePost(
                        didSuffix = "benchmaya000000000000000",
                        rkey = "widgetpost001",
                        handle = "maya.bsky.social",
                        displayName = "Maya Okonkwo",
                        createdAt = "2026-07-14T15:20:00Z",
                        body = "morning light on the ridge before the trail filled up. worth the 5am alarm.",
                        stats = PostStatsUi(replyCount = 4, repostCount = 7, likeCount = 58),
                    ),
                    samplePost(
                        didSuffix = "benchleo0000000000000000",
                        rkey = "widgetpost002",
                        handle = "leo.dev",
                        displayName = "Leo Nakamura",
                        createdAt = "2026-07-14T13:05:00Z",
                        body = "shipped the offline cache today. 120hz scrolling holds even on the cheap test device. 🐳",
                        stats = PostStatsUi(replyCount = 12, repostCount = 3, likeCount = 91),
                    ),
                    samplePost(
                        didSuffix = "benchsofia00000000000000",
                        rkey = "widgetpost003",
                        handle = "sofia.art",
                        displayName = "Sofía Herrera",
                        createdAt = "2026-07-14T11:40:00Z",
                        body = "new zine drop this weekend. printed the covers by hand, they smell like ink and possibility.",
                        stats = PostStatsUi(replyCount = 6, repostCount = 15, likeCount = 132),
                    ),
                    samplePost(
                        didSuffix = "benchtomas00000000000000",
                        rkey = "widgetpost004",
                        handle = "tomas.bsky.social",
                        displayName = "Tomás ",
                        createdAt = "2026-07-14T09:15:00Z",
                        body = "reminder that the small commits compound. one honest test at a time.",
                        stats = PostStatsUi(replyCount = 2, repostCount = 1, likeCount = 27),
                    ),
                )
        }
    }
