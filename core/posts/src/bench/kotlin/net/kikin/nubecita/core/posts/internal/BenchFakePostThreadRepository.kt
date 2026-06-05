package net.kikin.nubecita.core.posts.internal

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.kikin.nubecita.core.common.coroutines.IoDispatcher
import net.kikin.nubecita.core.posts.PostThreadRepository
import net.kikin.nubecita.data.models.ThreadItem
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor fake of [PostThreadRepository] that returns a deterministic,
 * network-free thread (a [benchGalleryPost] focus plus [benchGalleryReplies]) so
 * the post-detail screen renders offline under the `bench` flavor.
 *
 * Two payoffs:
 *  - The tablet list-detail layout now shows a real post in the detail pane
 *    instead of the empty "select post" placeholder — a better Play Store
 *    tablet screenshot.
 *  - Combined with [BenchFakePostRepository]'s handle-URI fidelity, it
 *    reproduces the deep-link media-gallery bug offline: this fake accepts the
 *    deep-link's **handle-based** URI (like the appview's `getPostThread`), so
 *    the thread loads, but the focus post's id is **DID-based**, so the fixed
 *    media-viewer launch (`getPost(focus.id)`) resolves while the buggy one
 *    (`getPost(route.postUri)` = handle) 404s.
 *
 * Returns the same thread for every URI — the bench flavor has exactly one
 * deep-linkable post-detail fixture. Selected over the production module by AGP
 * source-set merging; see the bench `PostThreadRepositoryModule`.
 */
@Singleton
internal class BenchFakePostThreadRepository
    @Inject
    constructor(
        @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher,
    ) : PostThreadRepository {
        override suspend fun getPostThread(uri: String): Result<ImmutableList<ThreadItem>> =
            withContext(ioDispatcher) {
                Result.success(BENCH_THREAD)
            }

        private companion object {
            private val BENCH_THREAD: ImmutableList<ThreadItem> =
                persistentListOf(
                    ThreadItem.Focus(post = benchGalleryPost),
                    ThreadItem.Reply(post = benchGalleryReplies[0], depth = 1),
                    ThreadItem.Reply(post = benchGalleryReplies[1], depth = 1),
                )
        }
    }
