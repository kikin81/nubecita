package net.kikin.nubecita.core.posts.internal

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.data.models.ThreadItem
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
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

    // ---------- the numbered author thread (the timeline's benchthread1 … 5) ----------

    private fun TestScope.repo() = BenchFakePostThreadRepository(StandardTestDispatcher(testScheduler))

    private fun ThreadItem.describe(): String =
        when (this) {
            is ThreadItem.Ancestor -> "ancestor:${post.threadPosition?.index}"
            is ThreadItem.Focus -> "focus:${post.threadPosition?.index}"
            is ThreadItem.Reply -> if (post.threadPosition != null) "thread:${post.threadPosition?.index}@$depth" else "reply@$depth"
            is ThreadItem.MoreReplies -> "more:${postUri.substringAfterLast('/')}"
            is ThreadItem.MoreParents -> "parents"
            ThreadItem.OtherReplies -> "other"
            is ThreadItem.Blocked -> "blocked"
            is ThreadItem.NotFound -> "notfound"
        }

    @Test
    fun `the first thread post opens the thread in order, cut after two more posts`() =
        runTest {
            val thread = repo().getOrderedThread(benchThreadPostUri(1)).getOrThrow()

            assertEquals(
                listOf("focus:1", "thread:2@1", "thread:3@2", "more:benchthread3", "reply@1", "reply@1", "other"),
                thread.map { it.describe() },
            )
            assertEquals(
                listOf(5, 5, 5),
                thread.mapNotNull {
                    when (it) {
                        is ThreadItem.Focus -> it.post.threadPosition?.count
                        is ThreadItem.Reply -> it.post.threadPosition?.count
                        else -> null
                    }
                },
            )
        }

    @Test
    fun `a middle thread post opens with the earlier posts above and the rest below`() =
        runTest {
            val thread = repo().getOrderedThread(benchThreadPostUri(3)).getOrThrow()

            assertEquals(
                listOf("ancestor:1", "ancestor:2", "focus:3", "thread:4@1", "thread:5@2", "reply@1", "reply@1", "other"),
                thread.map { it.describe() },
            )
            assertEquals(
                benchThreadPostUri(3),
                thread
                    .filterIsInstance<ThreadItem.Focus>()
                    .single()
                    .post.id,
            )
        }

    @Test
    fun `following the read-more row reaches the rest of the thread`() =
        runTest {
            val first = repo().getOrderedThread(benchThreadPostUri(1)).getOrThrow()
            val next = first.filterIsInstance<ThreadItem.MoreReplies>().single().postUri

            val continued = repo().getOrderedThread(next).getOrThrow()

            assertEquals(listOf(4, 5), continued.filterIsInstance<ThreadItem.Reply>().mapNotNull { it.post.threadPosition?.index })
        }

    @Test
    fun `the thread's left-out replies load as replies that are not already shown`() =
        runTest {
            val shown =
                repo()
                    .getOrderedThread(benchThreadPostUri(1))
                    .getOrThrow()
                    .filterIsInstance<ThreadItem.Reply>()
                    .map { it.post.id }

            val others = repo().getOtherReplies(benchThreadPostUri(1)).getOrThrow()

            assertEquals(2, others.size)
            assertTrue(others.all { it is ThreadItem.Reply && it.post.id !in shown })
        }

    @Test
    fun `a post outside the author thread has no left-out replies and keeps its usual thread`() =
        runTest {
            assertTrue(repo().getOtherReplies(TOKYO_POST_URI).getOrThrow().isEmpty())
            assertEquals(
                repo().getPostThread(TOKYO_POST_URI).getOrThrow(),
                repo().getOrderedThread(TOKYO_POST_URI).getOrThrow(),
            )
        }
}
