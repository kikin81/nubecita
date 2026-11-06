package net.kikin.nubecita.feature.profile.impl

import androidx.paging.PagingSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.data.models.ActorUi
import net.kikin.nubecita.feature.profile.impl.data.ConnectionPage
import net.kikin.nubecita.feature.profile.impl.data.ConnectionRowUi
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

internal class ProfileConnectionsPagingSourceTest {
    private fun row(did: String) =
        ConnectionRowUi(
            actor = ActorUi(did = did, handle = "$did.test", displayName = null, avatarUrl = null),
            followUri = null,
            canFollow = true,
        )

    private fun page(
        vararg dids: String,
        cursor: String? = null,
    ) = ConnectionPage(dids.map(::row).toImmutableList(), cursor)

    private fun refresh() = PagingSource.LoadParams.Refresh<String>(key = null, loadSize = 50, placeholdersEnabled = false)

    private fun append(key: String) = PagingSource.LoadParams.Append(key = key, loadSize = 50, placeholdersEnabled = false)

    @Test
    fun `the first page uses no cursor and passes on the next one`() =
        runTest {
            val cursors = mutableListOf<String?>()
            val source =
                ProfileConnectionsPagingSource { cursor ->
                    cursors += cursor
                    Result.success(page("a", cursor = "next"))
                }

            val result = assertInstanceOf(PagingSource.LoadResult.Page::class.java, source.load(refresh()))

            assertEquals(listOf<String?>(null), cursors)
            assertEquals("next", result.nextKey)
        }

    @Test
    fun `no cursor ends the list`() =
        runTest {
            val source = ProfileConnectionsPagingSource { Result.success(page("a")) }

            val result = assertInstanceOf(PagingSource.LoadResult.Page::class.java, source.load(refresh()))

            assertNull(result.nextKey)
        }

    /** A keyed LazyColumn crashes on a repeated key, and the DID is the key. */
    @Test
    fun `an account repeated on a later page is dropped`() =
        runTest {
            val source =
                ProfileConnectionsPagingSource { cursor ->
                    Result.success(if (cursor == null) page("a", "b", cursor = "next") else page("b", "c"))
                }

            source.load(refresh())
            val second = assertInstanceOf(PagingSource.LoadResult.Page::class.java, source.load(append("next")))

            assertEquals(listOf("c"), second.data.map { (it as ConnectionRowUi).actor.did })
        }

    @Test
    fun `a failure is reported to Paging`() =
        runTest {
            val source = ProfileConnectionsPagingSource { Result.failure(RuntimeException("boom")) }

            assertInstanceOf(PagingSource.LoadResult.Error::class.java, source.load(refresh()))
        }

    @Test
    fun `an empty list is a page, not an error`() =
        runTest {
            val source = ProfileConnectionsPagingSource { Result.success(ConnectionPage(persistentListOf(), null)) }

            assertInstanceOf(PagingSource.LoadResult.Page::class.java, source.load(refresh()))
        }
}
