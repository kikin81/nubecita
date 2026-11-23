package net.kikin.nubecita.feature.search.impl.data

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchFirstPagesTest {
    private val posts = FakeSearchPostsRepository()
    private val actors = FakeSearchActorsRepository()
    private val feeds = FakeSearchFeedsRepository()
    private val session = MutableStateFlow<SessionState>(SessionState.SignedIn(handle = "a.test", did = "did:plc:a"))

    private fun TestScope.newPages(): SearchFirstPages =
        SearchFirstPages(
            postsRepository = posts,
            actorRepository = actors,
            feedsRepository = feeds,
            scope = backgroundScope,
            sessionStateProvider =
                object : SessionStateProvider {
                    override val state = session

                    override suspend fun refresh() = Unit
                },
        )

    private suspend fun SearchFirstPages.loadAllThree() {
        posts("q", PostSearchFilters())
        people("q")
        feeds("q")
    }

    @Test
    fun `the same search is fetched once across callers`() =
        runTest {
            val pages = newPages()
            runCurrent()

            pages.loadAllThree()
            pages.loadAllThree()

            assertEquals(1, posts.callLog.size)
            assertEquals(1, actors.callLog.size)
            assertEquals(1, feeds.callLog.size)
        }

    @Test
    fun `different filters are a different search`() =
        runTest {
            val pages = newPages()
            runCurrent()

            pages.posts("q", PostSearchFilters())
            pages.posts("q", PostSearchFilters(followingOnly = true))

            assertEquals(2, posts.callLog.size)
        }

    @Test
    fun `refresh refetches and bumps version`() =
        runTest {
            val pages = newPages()
            runCurrent()
            pages.posts("q", PostSearchFilters())
            val before = pages.version.value

            pages.posts("q", PostSearchFilters(), refresh = true)

            assertEquals(2, posts.callLog.size)
            assertEquals(before + 1, pages.version.value)
        }

    @Test
    fun `signing out clears every cache and bumps version`() =
        runTest {
            val pages = newPages()
            runCurrent()
            pages.loadAllThree()
            val before = pages.version.value

            session.value = SessionState.SignedOut
            runCurrent()

            assertEquals(before + 1, pages.version.value)
            pages.loadAllThree()
            assertEquals(2, posts.callLog.size)
            assertEquals(2, actors.callLog.size)
            assertEquals(2, feeds.callLog.size)
        }

    @Test
    fun `a did change clears every cache`() =
        runTest {
            val pages = newPages()
            runCurrent()
            pages.loadAllThree()
            val before = pages.version.value

            session.value = SessionState.SignedIn(handle = "b.test", did = "did:plc:b")
            runCurrent()

            assertEquals(before + 1, pages.version.value)
            pages.loadAllThree()
            assertEquals(2, posts.callLog.size)
            assertEquals(2, actors.callLog.size)
            assertEquals(2, feeds.callLog.size)
        }

    @Test
    fun `a session update for the same did keeps the caches`() =
        runTest {
            val pages = newPages()
            runCurrent()
            pages.loadAllThree()
            val before = pages.version.value

            session.value = SessionState.SignedIn(handle = "renamed.test", did = "did:plc:a")
            runCurrent()

            assertEquals(before, pages.version.value)
            pages.loadAllThree()
            assertEquals(1, posts.callLog.size)
        }

    @Test
    fun `a session change before the collector runs is not missed`() =
        runTest {
            val pages = newPages()
            val before = pages.version.value

            session.value = SessionState.SignedIn(handle = "b.test", did = "did:plc:b")
            runCurrent()

            assertEquals(before + 1, pages.version.value)
        }

    @Test
    fun `a page cached for one did is not returned for another before the clear runs`() =
        runTest {
            val pages = newPages()
            pages.posts("q", PostSearchFilters())

            session.value = SessionState.SignedIn(handle = "b.test", did = "did:plc:b")
            pages.posts("q", PostSearchFilters())

            assertEquals(2, posts.callLog.size)
        }
}
