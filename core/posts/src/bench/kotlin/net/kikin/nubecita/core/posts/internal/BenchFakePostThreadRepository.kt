package net.kikin.nubecita.core.posts.internal

import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toPersistentList
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
            /**
             * Focus → replies. The focus post is deliberately the FIRST row (a
             * top-level post, no ancestors) — the common shape when a post is
             * tapped from the feed, and the one the marketing screenshot journey
             * captures (`a09PostDetail` opens this thread at scroll 0, so the
             * gallery card must lead the frame).
             *
             * [benchThreadFillerReplies] pads the thread below the focus so the
             * list is actually SCROLLABLE — without enough content underneath, the
             * focus card can never be scrolled up under the app bar and the
             * scroll-reactive toolbar's swap is unreachable on device. They sit
             * below the fold at scroll 0, so they don't affect the marketing
             * capture.
             *
             * NOTE: this fake returns a thread with NO ancestors, so the
             * "focus post is itself a reply" branch of the toolbar (focus at a
             * non-zero index) is not reachable on bench. That branch is covered by
             * `shouldShowAuthorInBar`'s JVM unit tests instead.
             */
            private val BENCH_THREAD: ImmutableList<ThreadItem> =
                buildList {
                    add(ThreadItem.Focus(post = benchGalleryPost))
                    benchGalleryReplies.forEach { add(ThreadItem.Reply(post = it, depth = 1)) }
                    benchThreadFillerReplies.forEach { add(ThreadItem.Reply(post = it, depth = 1)) }
                }.toPersistentList()
        }
    }
