package net.kikin.nubecita.feature.chats.impl

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.testing.TestPager
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.feature.chats.impl.data.JoinRequestPage
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException
import kotlin.time.Instant

class JoinRequestPagingSourceTest {
    private val repo = FakeChatRepository()

    private fun joinRequest(did: String) = JoinRequestUi(did = did, handle = "$did.bsky.social", displayName = null, avatarUrl = null, requestedAt = Instant.parse("2026-06-22T10:00:00Z"))

    @Test
    fun `first load returns page with nextKey from cursor`() =
        runTest {
            repo.getJoinRequestsResult =
                Result.success(JoinRequestPage(requests = persistentListOf(joinRequest("did:a")), cursor = "next"))
            val source = JoinRequestPagingSource(convoId = "c1", repository = repo)
            val result = TestPager(PagingConfig(pageSize = 50), source).refresh() as PagingSource.LoadResult.Page
            assertEquals(listOf("did:a"), result.data.map { it.did })
            assertEquals("next", result.nextKey)
            assertNull(result.prevKey)
        }

    @Test
    fun `failure surfaces as LoadResult Error`() =
        runTest {
            repo.getJoinRequestsResult = Result.failure(IOException("down"))
            val source = JoinRequestPagingSource(convoId = "c1", repository = repo)
            val result = TestPager(PagingConfig(pageSize = 50), source).refresh()
            assertTrue(result is PagingSource.LoadResult.Error)
        }
}
