package net.kikin.nubecita.core.posts.internal

import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.posts.PostRepository
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Instant

/**
 * Bench-flavor fake of [PostRepository] that returns a deterministic,
 * network-free post regardless of the requested AT-URI.
 *
 * This unblocks the fullscreen video player under the `bench` flavor: it
 * resolves the tapped post via [getPost], which in production hits
 * `app.bsky.feed.getPosts` (`DefaultPostRepository`) and errors when the
 * bench journey has no authenticated XRPC client. The fake hands back a
 * single asset-backed video post so the player can render offline.
 *
 * Selected over the production module by AGP source-set merging — see the
 * sibling bench `PostRepositoryModule` for the variant-split rationale.
 */
@Singleton
class BenchFakePostRepository
    @Inject
    constructor(
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PostRepository {
        override suspend fun getPost(uri: String): Result<PostUi> =
            withContext(ioDispatcher) {
                Result.success(BENCH_VIDEO_POST)
            }

        private companion object {
            // Fixed timestamp so the bench post renders deterministically.
            private val BENCH_VIDEO_POST =
                PostUi(
                    id = "at://did:plc:benchivy/app.bsky.feed.post/canyon",
                    cid = "bafyreiivyf00000000000000000000000000000000000000000001",
                    author =
                        AuthorUi(
                            did = "did:plc:benchivy",
                            handle = "ivy.fpv",
                            displayName = "Ivy Park",
                            avatarUrl = "file:///android_asset/img/avatars/hugo.jpg",
                        ),
                    createdAt = Instant.parse("2026-06-01T12:00:00Z"),
                    text =
                        "test flight over the canyon yesterday. half a battery on hard cuts " +
                            "between rim, slot, and the wash.",
                    facets = persistentListOf(),
                    embed =
                        EmbedUi.Video(
                            posterUrl = "file:///android_asset/img/posts/video-poster-3.jpg",
                            playlistUrl = "asset:///video/clip-3.mp4",
                            aspectRatio = 1.7777778f,
                            durationSeconds = 15,
                            altText = "Fast cuts between FPV drone shots over canyon terrain.",
                        ),
                    stats = PostStatsUi(replyCount = 9, repostCount = 2, likeCount = 41),
                    viewer = ViewerStateUi(),
                    repostedBy = null,
                )
        }
    }
