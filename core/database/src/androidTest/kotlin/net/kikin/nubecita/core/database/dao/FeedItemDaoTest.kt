package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.FeedItemEntity
import net.kikin.nubecita.core.database.model.FeedItemMemberEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class FeedItemDaoTest : DatabaseTest() {
    private lateinit var dao: FeedItemDao
    private lateinit var cachedDao: CachedPostDao

    @Before
    fun resolveDaos() {
        dao = db.feedItemDao()
        cachedDao = db.cachedPostDao()
    }

    private fun item(
        position: Int,
        key: String = "single:at://post/$position",
        variant: String? = "single",
        leaf: String? = "at://post/$position",
        suppressed: Int = 0,
        root: String? = "at://post/$position",
        account: String = ACCOUNT,
        type: String = TYPE,
        feed: String = FEED_URI,
    ) = FeedItemEntity(
        accountDid = account,
        feedType = type,
        feedUri = feed,
        position = position,
        rowType = FeedItemEntity.ROW_TYPE_ITEM,
        variant = variant,
        itemKey = key,
        leafUri = leaf,
        suppressedReplyCount = suppressed,
        threadRootUri = root,
    )

    private fun seam(
        position: Int,
        exhausted: Boolean?,
    ) = FeedItemEntity(
        accountDid = ACCOUNT,
        feedType = TYPE,
        feedUri = FEED_URI,
        position = position,
        rowType = FeedItemEntity.ROW_TYPE_SEAM,
        itemKey = "seam:$position",
        seamExhausted = exhausted,
    )

    private fun member(
        position: Int,
        ordinal: Int,
        uri: String,
    ) = FeedItemMemberEntity(ACCOUNT, TYPE, FEED_URI, position, ordinal, uri)

    @Test
    fun itemsIn_ordersByPosition_acrossZero() =
        runTest {
            // A prepend writes above existing rows without renumbering, so the
            // projection carries negatives at its head just like the entry table.
            dao.upsert(listOf(item(position = 1), item(position = -2), item(position = 0), item(position = -1)))

            assertEquals(listOf(-2, -1, 0, 1), dao.itemsIn(ACCOUNT, TYPE, FEED_URI).map { it.position })
            assertEquals(-2, dao.minPosition(ACCOUNT, TYPE, FEED_URI))
            assertEquals(1, dao.maxPosition(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun itemsIn_isScopedToPartition() =
        runTest {
            dao.upsert(
                listOf(
                    item(position = 0),
                    item(position = 0, type = "DISCOVER", feed = "at://feed/x", key = "single:at://other/0"),
                ),
            )

            assertEquals(1, dao.itemsIn(ACCOUNT, TYPE, FEED_URI).size)
            assertEquals(1, dao.itemsIn(ACCOUNT, "DISCOVER", "at://feed/x").size)
        }

    @Test
    fun membersRoundTripInRenderOrder() =
        runTest {
            dao.upsert(listOf(item(position = 0, variant = "cluster", key = "cluster:at://post/root")))
            // Deliberately inserted out of order: ordinal is what defines render
            // order, not insertion order.
            dao.upsertMembers(
                listOf(
                    member(0, 2, "at://post/leaf"),
                    member(0, 0, "at://post/root"),
                    member(0, 1, "at://post/parent"),
                ),
            )

            assertEquals(
                listOf("at://post/root", "at://post/parent", "at://post/leaf"),
                dao.membersOf(ACCOUNT, TYPE, FEED_URI, position = 0).map { it.uri },
            )
        }

    @Test
    fun membersOfAll_readsAWholePageInRenderOrder() =
        runTest {
            // The batched read the paging source uses. Grouping downstream
            // relies on the ordering, so position-then-ordinal is part of the
            // contract, not an incidental.
            dao.upsert(listOf(item(position = 0, key = "single:at://post/a"), item(position = 1, key = "single:at://post/b")))
            dao.upsertMembers(
                listOf(
                    member(1, 1, "at://post/b-parent"),
                    member(0, 0, "at://post/a"),
                    member(1, 0, "at://post/b"),
                ),
            )

            val all = dao.membersOfAll(ACCOUNT, TYPE, FEED_URI, positions = listOf(0, 1))

            assertEquals(
                listOf("at://post/a", "at://post/b", "at://post/b-parent"),
                all.map { it.uri },
            )
            // Positions not asked for are left out.
            assertEquals(1, dao.membersOfAll(ACCOUNT, TYPE, FEED_URI, positions = listOf(0)).size)
        }

    @Test
    fun positionOf_resolvesAPersistedAnchorKey() =
        runTest {
            dao.upsert(listOf(item(position = 3, key = "chain:at://post/first")))

            assertEquals(3, dao.positionOf(ACCOUNT, TYPE, FEED_URI, "chain:at://post/first"))
            assertNull(dao.positionOf(ACCOUNT, TYPE, FEED_URI, "chain:at://post/gone"))
        }

    @Test
    fun firstAtOrBelow_resolvesADeadAnchorDownwards() =
        runTest {
            // The anchored item at position 2 is gone; the reader's unread
            // content is what sits below it, so restore must land on 4, not 0.
            dao.upsert(listOf(item(position = 0), item(position = 4), item(position = 7)))

            assertEquals(4, dao.firstAtOrBelow(ACCOUNT, TYPE, FEED_URI, fromPosition = 2)?.position)
            assertEquals(0, dao.firstAtOrBelow(ACCOUNT, TYPE, FEED_URI, fromPosition = -5)?.position)
            assertNull(dao.firstAtOrBelow(ACCOUNT, TYPE, FEED_URI, fromPosition = 99))
        }

    @Test
    fun itemsFrom_readsAKeysetWindow() =
        runTest {
            dao.upsert((0 until 6).map { item(position = it) })

            assertEquals(
                listOf(2, 3, 4),
                dao.itemsFrom(ACCOUNT, TYPE, FEED_URI, fromPosition = 2, limit = 3).map { it.position },
            )
        }

    @Test
    fun seam_roundTripsItsFillState_andClears() =
        runTest {
            dao.upsert(listOf(item(position = -3), seam(position = -1, exhausted = false), item(position = 0)))

            val found = dao.seam(ACCOUNT, TYPE, FEED_URI)
            assertNotNull(found)
            assertEquals(FeedItemEntity.ROW_TYPE_SEAM, found?.rowType)
            assertEquals(false, found?.seamExhausted)
            // The seam is a row in the ordered read, not an inferred gap.
            assertEquals(listOf(-3, -1, 0), dao.itemsIn(ACCOUNT, TYPE, FEED_URI).map { it.position })

            dao.clearSeam(ACCOUNT, TYPE, FEED_URI)
            assertNull(dao.seam(ACCOUNT, TYPE, FEED_URI))
            assertEquals(listOf(-3, 0), dao.itemsIn(ACCOUNT, TYPE, FEED_URI).map { it.position })
        }

    @Test
    fun suppressedReplyCountRoundTrips() =
        runTest {
            dao.upsert(listOf(item(position = 0, variant = "cluster", suppressed = 4)))

            assertEquals(4, dao.itemsIn(ACCOUNT, TYPE, FEED_URI).single().suppressedReplyCount)
        }

    @Test
    fun seenThreadRoots_returnsTheAppendSeed_distinctAndScoped() =
        runTest {
            dao.upsert(
                listOf(
                    item(position = 0, root = "at://thread/a"),
                    item(position = 1, root = "at://thread/a"),
                    item(position = 2, root = "at://thread/b"),
                    // A seam occupies no thread and must not pollute the seed.
                    seam(position = -1, exhausted = false),
                    item(position = 0, type = "DISCOVER", feed = "at://feed/x", root = "at://thread/elsewhere"),
                ),
            )

            val roots = dao.seenThreadRoots(ACCOUNT, TYPE, FEED_URI).sorted()

            assertEquals(listOf("at://thread/a", "at://thread/b"), roots)
        }

    @Test
    fun renderedPostUris_returnsEveryPostTheePartitionShows() =
        runTest {
            dao.upsert(listOf(item(position = 0, variant = "cluster")))
            dao.upsertMembers(
                listOf(
                    member(0, 0, "at://post/root"),
                    member(0, 1, "at://post/parent"),
                    member(0, 2, "at://post/leaf"),
                ),
            )

            assertEquals(
                listOf("at://post/leaf", "at://post/parent", "at://post/root"),
                dao.renderedPostUris(ACCOUNT, TYPE, FEED_URI).sorted(),
            )
        }

    @Test
    fun tailItem_isTheLastItem_ignoringSeams() =
        runTest {
            // The append merge prepends to this item, so picking the seam
            // instead would try to extend a chain into a marker row.
            dao.upsert(listOf(item(position = 0), item(position = 5), seam(position = 9, exhausted = true)))

            assertEquals(5, dao.tailItem(ACCOUNT, TYPE, FEED_URI)?.position)
        }

    @Test
    fun tailItem_isNullOnAnEmptyPartition() =
        runTest {
            assertNull(dao.tailItem(ACCOUNT, TYPE, FEED_URI))
        }

    @Test
    fun clearPartition_removesItemsAndMembersTogether() =
        runTest {
            dao.upsert(listOf(item(position = 0)))
            dao.upsertMembers(listOf(member(0, 0, "at://post/0")))

            dao.clearPartition(ACCOUNT, TYPE, FEED_URI)

            assertEquals(0, dao.itemsIn(ACCOUNT, TYPE, FEED_URI).size)
            // Orphaned members would pin posts against eviction forever.
            assertEquals(0, dao.membersOf(ACCOUNT, TYPE, FEED_URI, position = 0).size)
        }

    @Test
    fun clearAccount_removesItemsAndMembers_forThatAccountOnly() =
        runTest {
            dao.upsert(listOf(item(position = 0), item(position = 0, account = "did:other")))
            dao.upsertMembers(listOf(member(0, 0, "at://post/0")))

            dao.clearAccount(ACCOUNT)

            assertEquals(0, dao.itemsIn(ACCOUNT, TYPE, FEED_URI).size)
            assertEquals(0, dao.membersOf(ACCOUNT, TYPE, FEED_URI, position = 0).size)
            assertEquals(1, dao.itemsIn("did:other", TYPE, FEED_URI).size)
        }

    @Test
    fun aPostReferencedOnlyByAClustersMembershipSurvivesEviction() =
        runTest {
            // The reason feed_item_member exists. A cluster's root usually has no
            // entry row, so an eviction that checked only feed_post would delete
            // the post the rendered cluster is built from.
            cachedDao.upsert(
                listOf(
                    CachedPostEntity(accountDid = ACCOUNT, uri = "at://post/root", postBlob = "{}"),
                    CachedPostEntity(accountDid = ACCOUNT, uri = "at://post/orphan", postBlob = "{}"),
                ),
            )
            dao.upsert(listOf(item(position = 0, variant = "cluster", key = "cluster:at://post/root")))
            dao.upsertMembers(listOf(member(0, 0, "at://post/root")))

            cachedDao.deleteUnreferenced(ACCOUNT)

            assertNotNull(cachedDao.byUri(ACCOUNT, "at://post/root"))
            assertNull(cachedDao.byUri(ACCOUNT, "at://post/orphan"))
        }

    @Test
    fun itemsWithLeaf_findsTheItemInEveryPartitionOfTheAccountOnly() =
        runTest {
            dao.upsert(
                listOf(
                    item(position = 0, leaf = "at://post/gone"),
                    item(position = 1, leaf = "at://post/kept"),
                    item(position = 0, leaf = "at://post/gone", feed = "at://feed/other"),
                    item(position = 0, leaf = "at://post/gone", account = "did:other"),
                    seam(position = 2, exhausted = false),
                ),
            )

            val found = dao.itemsWithLeaf(ACCOUNT, "at://post/gone")

            assertEquals(setOf(FEED_URI, "at://feed/other"), found.map { it.feedUri }.toSet())
            assertEquals(2, found.size)
        }

    private companion object {
        const val ACCOUNT = "did:me"
        const val TYPE = "FOLLOWING"
        const val FEED_URI = ""
    }
}
