package net.kikin.nubecita.core.posts.internal

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.data.models.ThreadItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * The bench thread fake serves a foreign thread for the timeline's foreign
 * posts and the gallery thread for everything else, which the marketing
 * capture and the deep-link repro both depend on.
 */
internal class BenchFakePostThreadRepositoryTest {
    @Test
    fun `a foreign timeline post opens its own thread, focused on the same post`() =
        runTest {
            val repo = BenchFakePostThreadRepository(StandardTestDispatcher(testScheduler))

            val thread = repo.getPostThread(TOKYO_POST_URI).getOrThrow()

            val focus = thread.filterIsInstance<ThreadItem.Focus>().single()
            assertEquals(TOKYO_POST_URI, focus.post.id)
            assertEquals(
                setOf("ja", "de", "en"),
                thread.filterIsInstance<ThreadItem.Reply>().flatMap { it.post.langs }.toSet(),
            )
        }

    @Test
    fun `every other uri still opens the gallery thread`() =
        runTest {
            val repo = BenchFakePostThreadRepository(StandardTestDispatcher(testScheduler))

            val thread = repo.getPostThread("at://anyone.bsky.social/app.bsky.feed.post/xyz").getOrThrow()

            assertEquals(
                benchGalleryPost.id,
                thread
                    .filterIsInstance<ThreadItem.Focus>()
                    .single()
                    .post.id,
            )
        }
}
