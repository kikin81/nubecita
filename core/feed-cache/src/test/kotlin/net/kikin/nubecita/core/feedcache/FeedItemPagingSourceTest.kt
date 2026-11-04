package net.kikin.nubecita.core.feedcache

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.NubecitaDatabase
import net.kikin.nubecita.core.database.dao.CachedPostDao
import net.kikin.nubecita.core.database.dao.FeedItemDao
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import net.kikin.nubecita.core.moderation.ModerationPrefs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class FeedItemPagingSourceTest {
    private val key = FeedKey.following(ACCOUNT)
    private val feedItemDao = mockk<FeedItemDao>(relaxed = true)
    private val cachedPostDao = mockk<CachedPostDao>(relaxed = true)
    private val database = mockk<NubecitaDatabase>(relaxed = true)

    private fun source(initialPosition: Int? = null) =
        FeedItemPagingSource(
            feedKey = key,
            feedItemDao = feedItemDao,
            cachedPostDao = cachedPostDao,
            database = database,
            initialPosition = initialPosition,
            viewerDid = { ACCOUNT },
            prefs = { ModerationPrefs.DEFAULT },
        )

    /** Rows at the given positions, each a single-post item. */
    private fun stub(
        positions: List<Int>,
        min: Int = positions.min(),
    ) {
        val rows =
            positions.map { position ->
                FeedItemEntity(
                    accountDid = ACCOUNT,
                    feedType = "FOLLOWING",
                    feedUri = "",
                    position = position,
                    rowType = FeedItemEntity.ROW_TYPE_ITEM,
                    variant = "single",
                    itemKey = "at://post/$position",
                    leafUri = "at://post/$position",
                    suppressedReplyCount = 0,
                )
            }
        coEvery { feedItemDao.minPosition(any(), any(), any()) } returns min
        coEvery { feedItemDao.itemsFrom(any(), any(), any(), any(), any()) } answers {
            val from = arg<Int>(3)
            val limit = arg<Int>(4)
            rows.filter { it.position >= from }.take(limit)
        }
        coEvery { feedItemDao.itemsBefore(any(), any(), any(), any(), any()) } answers {
            val before = arg<Int>(3)
            val limit = arg<Int>(4)
            rows.filter { it.position < before }.sortedByDescending { it.position }.take(limit)
        }
        coEvery { feedItemDao.membersOf(any(), any(), any(), any()) } answers {
            val position = arg<Int>(3)
            listOf(FeedItemMemberEntity(ACCOUNT, "FOLLOWING", "", position, 0, "at://post/$position"))
        }
        coEvery { cachedPostDao.byUris(any(), any()) } answers {
            arg<List<String>>(1).map { uri ->
                CachedPostEntity(accountDid = ACCOUNT, uri = uri, postBlob = postBlob(uri))
            }
        }
    }

    @Test
    fun `refresh reads from the start of the partition`() =
        runTest {
            stub(listOf(0, 1, 2))

            val page = source().load(refresh(null, loadSize = 2)) as PagingSource.LoadResult.Page

            assertEquals(2, page.data.size)
            // nextKey is the position of the first unread row, not a count.
            assertEquals(2, page.nextKey)
            assertNull(page.prevKey)
        }

    @Test
    fun `an initial position starts the first load mid-partition`() =
        runTest {
            // This is how a restored anchor enters Paging: the first frame is
            // already in place rather than scrolled there afterwards.
            stub(listOf(0, 1, 2, 3))

            val page = source(initialPosition = 2).load(refresh(null, loadSize = 2)) as PagingSource.LoadResult.Page

            assertEquals(listOf("at://post/2", "at://post/3"), page.data.map { it.key })
            // Rows exist above, so the reader can scroll back towards newer items.
            assertEquals(2, page.prevKey)
        }

    @Test
    fun `prepend reads the window above the key, in display order`() =
        runTest {
            stub(listOf(-2, -1, 0, 1))

            val page = source().load(prepend(0, loadSize = 2)) as PagingSource.LoadResult.Page

            assertEquals(listOf("at://post/-2", "at://post/-1"), page.data.map { it.key })
            assertEquals(0, page.nextKey)
        }

    @Test
    fun `the refresh key is a stored position, not an index`() =
        runTest {
            // The whole reason this source is hand-written. Room's generated
            // source answers with an OFFSET, so rows inserted above move the
            // reader; a position does not.
            stub(listOf(0, 1, 2))
            val src = source()
            val page = src.load(refresh(null, loadSize = 3)) as PagingSource.LoadResult.Page

            val refreshKey =
                src.getRefreshKey(
                    PagingState(
                        pages = listOf(page),
                        anchorPosition = 1,
                        config = PagingConfig(pageSize = 3),
                        leadingPlaceholderCount = 0,
                    ),
                )

            assertEquals(1, refreshKey)
        }

    @Test
    fun `the refresh key still names the same item after rows are inserted above`() =
        runTest {
            // The regression this change exists to prevent. The reader is on
            // the item at position 1; a prepend then writes rows at -3..-1.
            // An index-based key would now name a different item.
            stub(listOf(0, 1, 2))
            val src = source()
            val page = src.load(refresh(null, loadSize = 3)) as PagingSource.LoadResult.Page
            val state =
                PagingState(
                    pages = listOf(page),
                    anchorPosition = 1,
                    config = PagingConfig(pageSize = 3),
                    leadingPlaceholderCount = 0,
                )
            val before = src.getRefreshKey(state)

            // Three rows arrive above, so the item the reader is on is now the
            // fourth in the partition rather than the second.
            stub(listOf(-3, -2, -1, 0, 1, 2))
            val after = src.getRefreshKey(state)

            assertEquals(before, after)
            assertEquals(1, after)
            // And reloading at that key returns the same item.
            val reloaded = src.load(refresh(after, loadSize = 1)) as PagingSource.LoadResult.Page
            assertEquals("at://post/1", reloaded.data.single().key)
        }

    @Test
    fun `an empty partition yields an empty page rather than an error`() =
        runTest {
            coEvery { feedItemDao.minPosition(any(), any(), any()) } returns null

            val page = source().load(refresh(null, loadSize = 5)) as PagingSource.LoadResult.Page

            assertTrue(page.data.isEmpty())
            assertNull(page.nextKey)
            assertNull(page.prevKey)
        }

    private fun refresh(
        key: Int?,
        loadSize: Int,
    ) = PagingSource.LoadParams.Refresh(key, loadSize, placeholdersEnabled = false)

    private fun prepend(
        key: Int,
        loadSize: Int,
    ) = PagingSource.LoadParams.Prepend(key, loadSize, placeholdersEnabled = false)

    private companion object {
        const val ACCOUNT = "did:plc:me"

        fun postBlob(uri: String): String =
            """{"${'$'}type":"app.bsky.feed.defs#postView","uri":"$uri",""" +
                """"cid":"bafyreiekyd2wqraqliwm3qolheg6txryqncxhf7zkdlqbogqlj6szorvhq",""" +
                """"author":{"did":"did:plc:author","handle":"a.bsky.social"},""" +
                """"record":{"${'$'}type":"app.bsky.feed.post","text":"t","createdAt":"2026-04-15T19:51:12.861Z"},""" +
                """"indexedAt":"2026-04-15T19:51:13.000Z"}"""
    }
}
