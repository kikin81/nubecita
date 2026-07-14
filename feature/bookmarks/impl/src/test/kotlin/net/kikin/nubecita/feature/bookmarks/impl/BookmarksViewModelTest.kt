package net.kikin.nubecita.feature.bookmarks.impl

import app.cash.turbine.test
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.bookmarks.BookmarksPage
import net.kikin.nubecita.core.postinteractions.PostInteractionState
import net.kikin.nubecita.core.testing.MainDispatcherExtension
import net.kikin.nubecita.data.models.AuthorUi
import net.kikin.nubecita.data.models.EmbedUi
import net.kikin.nubecita.data.models.PostStatsUi
import net.kikin.nubecita.data.models.PostUi
import net.kikin.nubecita.data.models.ViewerStateUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.io.IOException
import kotlin.time.Instant

class BookmarksViewModelTest {
    @RegisterExtension
    val mainDispatcher = MainDispatcherExtension()

    private val cache = FakePostInteractionsCache()
    private val handler = FakePostInteractionHandler(cache)

    private fun buildVm(repo: FakeBookmarkRepository) = BookmarksViewModel(repo, cache, handler)

    @Test
    fun `initial load maps the first page to Loaded`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeBookmarkRepository(
                    onGetBookmarks = {
                        Result.success(
                            BookmarksPage(
                                posts = persistentListOf(post("a"), post("b")),
                                cursor = "c1",
                            ),
                        )
                    },
                )
            val vm = buildVm(repo)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is BookmarksLoadStatus.Loaded, "a non-empty first page MUST be Loaded")
            status as BookmarksLoadStatus.Loaded
            assertEquals(listOf("at://a", "at://b"), status.items.map { it.id })
            assertEquals("c1", status.nextCursor)
            assertTrue(!status.endReached, "a non-null cursor MUST leave endReached false")
            assertEquals(listOf<String?>(null), repo.requestedCursors, "first load requests cursor=null")
            assertEquals(1, cache.seedCalls.get(), "the fetched page MUST seed the interactions cache")
        }

    @Test
    fun `empty first page maps to Empty`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBookmarkRepository { Result.success(BookmarksPage(persistentListOf(), null)) }
            val vm = buildVm(repo)
            advanceUntilIdle()

            assertEquals(BookmarksLoadStatus.Empty, vm.uiState.value.loadStatus)
        }

    @Test
    fun `network failure maps to InitialError(Network)`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBookmarkRepository { Result.failure(IOException("offline")) }
            val vm = buildVm(repo)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is BookmarksLoadStatus.InitialError)
            assertEquals(BookmarksError.Network, (status as BookmarksLoadStatus.InitialError).error)
        }

    @Test
    fun `Retry re-fetches after an initial error`() =
        runTest(mainDispatcher.dispatcher) {
            var fail = true
            val repo =
                FakeBookmarkRepository { if (fail) Result.failure(IOException()) else Result.success(BookmarksPage(persistentListOf(post("a")), null)) }
            val vm = buildVm(repo)
            advanceUntilIdle()
            assertTrue(vm.uiState.value.loadStatus is BookmarksLoadStatus.InitialError)

            fail = false
            vm.handleEvent(BookmarksEvent.Retry)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus
            assertTrue(status is BookmarksLoadStatus.Loaded)
            assertEquals(listOf("at://a"), (status as BookmarksLoadStatus.Loaded).items.map { it.id })
        }

    @Test
    fun `LoadMore appends the next page and advances the cursor`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeBookmarkRepository { cursor ->
                    when (cursor) {
                        null -> Result.success(BookmarksPage(persistentListOf(post("a")), "c1"))
                        "c1" -> Result.success(BookmarksPage(persistentListOf(post("b")), null))
                        else -> Result.failure(IllegalStateException("unexpected cursor $cursor"))
                    }
                }
            val vm = buildVm(repo)
            advanceUntilIdle()

            vm.handleEvent(BookmarksEvent.LoadMore)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus as BookmarksLoadStatus.Loaded
            assertEquals(listOf("at://a", "at://b"), status.items.map { it.id })
            assertNull(status.nextCursor)
            assertTrue(status.endReached, "a null next cursor MUST set endReached")
            assertTrue(!status.isAppending)
            assertEquals(listOf<String?>(null, "c1"), repo.requestedCursors)
        }

    @Test
    fun `LoadMore drops duplicate post ids across pages`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeBookmarkRepository { cursor ->
                    when (cursor) {
                        null -> Result.success(BookmarksPage(persistentListOf(post("a"), post("b")), "c1"))
                        // Page 2 re-includes "b" (overlapping cursor page).
                        else -> Result.success(BookmarksPage(persistentListOf(post("b"), post("c")), null))
                    }
                }
            val vm = buildVm(repo)
            advanceUntilIdle()
            vm.handleEvent(BookmarksEvent.LoadMore)
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus as BookmarksLoadStatus.Loaded
            assertEquals(
                listOf("at://a", "at://b", "at://c"),
                status.items.map { it.id },
                "a re-included post id MUST be deduped, keeping the existing item",
            )
        }

    @Test
    fun `cache emission projects onto loaded items`() =
        runTest(mainDispatcher.dispatcher) {
            val repo =
                FakeBookmarkRepository {
                    Result.success(BookmarksPage(persistentListOf(post("a")), null))
                }
            val vm = buildVm(repo)
            advanceUntilIdle()

            // Simulate a like toggled on another surface for post "a".
            cache.emit(
                persistentMapOf(
                    "at://a" to PostInteractionState(viewerLikeUri = "at://like/a", likeCount = 5L),
                ),
            )
            advanceUntilIdle()

            val status = vm.uiState.value.loadStatus as BookmarksLoadStatus.Loaded
            val post = status.items.single()
            assertTrue(post.viewer.isLikedByViewer, "the cache like MUST project onto the rendered item")
            assertEquals(5, post.stats.likeCount)
        }

    @Test
    fun `LoadMore is a no-op once endReached`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBookmarkRepository { Result.success(BookmarksPage(persistentListOf(post("a")), null)) }
            val vm = buildVm(repo)
            advanceUntilIdle()
            assertTrue((vm.uiState.value.loadStatus as BookmarksLoadStatus.Loaded).endReached)

            vm.handleEvent(BookmarksEvent.LoadMore)
            advanceUntilIdle()

            assertEquals(listOf<String?>(null), repo.requestedCursors, "LoadMore past endReached MUST NOT fetch")
        }

    @Test
    fun `PostTapped emits NavigateToPost`() =
        runTest(mainDispatcher.dispatcher) {
            val repo = FakeBookmarkRepository { Result.success(BookmarksPage(persistentListOf(post("a")), null)) }
            val vm = buildVm(repo)
            advanceUntilIdle()

            vm.effects.test {
                vm.handleEvent(BookmarksEvent.PostTapped("at://a"))
                assertEquals(BookmarksEffect.NavigateToPost("at://a"), awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }
}

private fun post(id: String): PostUi =
    PostUi(
        id = "at://$id",
        cid = "bafyreifakecid00000000000000000000000000000000$id",
        author =
            AuthorUi(
                did = "did:plc:$id",
                handle = "$id.bsky.social",
                displayName = "User $id",
                avatarUrl = null,
            ),
        createdAt = Instant.parse("2026-04-25T12:00:00Z"),
        text = "Bookmarked post $id",
        facets = persistentListOf(),
        embed = EmbedUi.Empty,
        stats = PostStatsUi(),
        viewer = ViewerStateUi(),
        repostedBy = null,
    )
