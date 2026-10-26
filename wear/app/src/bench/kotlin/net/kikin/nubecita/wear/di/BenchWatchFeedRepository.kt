package net.kikin.nubecita.wear.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.wearcontract.WearFeedKey
import net.kikin.nubecita.wear.R
import net.kikin.nubecita.wear.data.WatchFeedRepository
import net.kikin.nubecita.wear.data.db.WatchFeedEntity
import net.kikin.nubecita.wear.data.db.WatchPostEntity
import java.io.File
import javax.inject.Inject

/**
 * Bench flavor: a fixed, fictional feed for Play Store screenshots, with no
 * phone or Data Layer. Images ship as raw resources and are copied to files
 * once, because the real store hands the UI file paths.
 */
@Module
@InstallIn(SingletonComponent::class)
internal interface BenchWatchFeedModule {
    @Binds
    fun bindWatchFeedRepository(impl: BenchWatchFeedRepository): WatchFeedRepository
}

internal class BenchWatchFeedRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : WatchFeedRepository {
        override fun feeds(accountDid: String): Flow<List<WatchFeedEntity>> =
            flow {
                emit(
                    listOf(
                        WatchFeedEntity(accountDid, WearFeedKey.FOLLOWING, null, UPDATED_AT),
                        WatchFeedEntity(accountDid, PHOTOS_KEY, "Morning Photos", UPDATED_AT),
                    ),
                )
            }

        override fun posts(
            accountDid: String,
            feedKey: String,
        ): Flow<List<WatchPostEntity>> =
            flow {
                val files = images()
                val posts = if (feedKey == PHOTOS_KEY) photos(files) else following(files)
                emit(posts.mapIndexed { index, post -> post.copy(accountDid = accountDid, feedKey = feedKey, position = index) })
            }

        private suspend fun images(): Map<Int, String> =
            withContext(Dispatchers.IO) {
                val dir = File(context.filesDir, "bench_images").apply { mkdirs() }
                IMAGES.associateWith { res ->
                    val file = File(dir, "$res.webp")
                    if (!file.exists()) context.resources.openRawResource(res).use { input -> file.outputStream().use(input::copyTo) }
                    file.absolutePath
                }
            }

        private fun following(img: Map<Int, String>) =
            listOf(
                post("p1", "Ana Ruiz", "ana.example.social", img[R.raw.bench_avatar_1], "Golden hour from the pier tonight. Worth the cold hands.", 6, likes = 128, liked = true, thumb = img[R.raw.bench_thumb_sunset], images = 1),
                post("p2", "Ben Okafor", "ben.example.social", img[R.raw.bench_avatar_2], "Shipped the release. Time for a long walk without a laptop.", 18, likes = 42, replies = 7),
                post("p3", "Cleo Marsh", "cleo.example.social", img[R.raw.bench_avatar_3], "The lake was perfectly still this morning.", 41, likes = 310, reposts = 24, thumb = img[R.raw.bench_thumb_lake], images = 3, bookmarked = true),
                post("p4", "Ana Ruiz", "ana.example.social", img[R.raw.bench_avatar_1], "Anyone else read on their watch now? Tiny screen, big feelings.", 75, likes = 19, replies = 3, repostedBy = "ben.example.social"),
                post("p5", "Ben Okafor", "ben.example.social", img[R.raw.bench_avatar_2], "City lights from the train home.", 120, likes = 88, thumb = img[R.raw.bench_thumb_city], video = true),
            )

        private fun photos(img: Map<Int, String>) =
            listOf(
                post("q1", "Cleo Marsh", "cleo.example.social", img[R.raw.bench_avatar_3], "First light over the hills.", 12, likes = 204, thumb = img[R.raw.bench_thumb_sunset], images = 1),
                post("q2", "Ana Ruiz", "ana.example.social", img[R.raw.bench_avatar_1], "Still water, still mind.", 55, likes = 97, thumb = img[R.raw.bench_thumb_lake], images = 2),
            )

        private fun post(
            id: String,
            name: String,
            handle: String,
            avatar: String?,
            text: String,
            minutesAgo: Int,
            likes: Int = 0,
            replies: Int = 0,
            reposts: Int = 0,
            liked: Boolean = false,
            bookmarked: Boolean = false,
            repostedBy: String? = null,
            thumb: String? = null,
            images: Int = 0,
            video: Boolean = false,
        ) = WatchPostEntity(
            accountDid = "",
            feedKey = "",
            position = 0,
            uri = "at://did:plc:bench/app.bsky.feed.post/$id",
            cid = "bench-$id",
            authorDid = "did:plc:bench-$handle",
            authorHandle = handle,
            authorDisplayName = name,
            authorAvatarPath = avatar,
            text = text,
            createdAtMs = UPDATED_AT - minutesAgo * 60_000L,
            replyCount = replies,
            repostCount = reposts,
            likeCount = likes,
            repostedBy = repostedBy,
            likeUri = if (liked) "at://did:plc:bench/app.bsky.feed.like/$id" else null,
            bookmarked = bookmarked,
            imageCount = images,
            isVideo = video,
            hasContentWarning = false,
            thumbnailPath = thumb,
        )

        private companion object {
            const val PHOTOS_KEY = "uri-bench-photos"

            // Fixed so screenshots read "Updated …" against the bench clock.
            val UPDATED_AT: Long = System.currentTimeMillis() - 4 * 60_000L

            val IMAGES =
                listOf(
                    R.raw.bench_avatar_1,
                    R.raw.bench_avatar_2,
                    R.raw.bench_avatar_3,
                    R.raw.bench_thumb_sunset,
                    R.raw.bench_thumb_lake,
                    R.raw.bench_thumb_city,
                )
        }
    }
