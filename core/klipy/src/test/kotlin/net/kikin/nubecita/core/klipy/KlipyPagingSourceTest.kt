package net.kikin.nubecita.core.klipy

import androidx.paging.PagingSource
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.data.models.KlipyMediaPage
import net.kikin.nubecita.data.models.KlipyMediaUiFixtures
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class KlipyPagingSourceTest {
    @Test
    fun `first page with hasNext yields a Page whose nextKey is 2`() =
        runTest {
            val source = KlipyPagingSource { Result.success(page(hasNext = true)) }

            val result = source.load(refresh(key = null))

            assertTrue(result is PagingSource.LoadResult.Page)
            result as PagingSource.LoadResult.Page
            assertEquals(2, result.nextKey)
            assertNull(result.prevKey)
            assertEquals(1, result.data.size)
        }

    @Test
    fun `a terminal page has a null nextKey`() =
        runTest {
            val source = KlipyPagingSource { Result.success(KlipyMediaPage(persistentListOf(), hasNext = false)) }

            val result = source.load(refresh(key = null)) as PagingSource.LoadResult.Page

            assertNull(result.nextKey)
        }

    @Test
    fun `load uses the requested page key`() =
        runTest {
            var requested = -1
            val source =
                KlipyPagingSource { requestedPage ->
                    requested = requestedPage
                    Result.success(page(hasNext = true))
                }

            val result = source.load(append(key = 5)) as PagingSource.LoadResult.Page

            assertEquals(5, requested)
            assertEquals(6, result.nextKey)
        }

    @Test
    fun `a failed fetch yields a LoadResult Error`() =
        runTest {
            val boom = RuntimeException("boom")
            val source = KlipyPagingSource { Result.failure(boom) }

            val result = source.load(refresh(key = null))

            assertTrue(result is PagingSource.LoadResult.Error)
            assertEquals(boom, (result as PagingSource.LoadResult.Error).throwable)
        }

    private fun page(hasNext: Boolean) = KlipyMediaPage(persistentListOf(KlipyMediaUiFixtures.media()), hasNext = hasNext)

    private fun refresh(key: Int?) = PagingSource.LoadParams.Refresh(key, loadSize = 30, placeholdersEnabled = false)

    private fun append(key: Int) = PagingSource.LoadParams.Append(key, loadSize = 30, placeholdersEnabled = false)
}
