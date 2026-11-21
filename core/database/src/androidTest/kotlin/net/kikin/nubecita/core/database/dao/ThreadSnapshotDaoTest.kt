package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.CachedPostEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotItemEntity
import net.kikin.nubecita.core.database.model.ThreadSnapshotKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
internal class ThreadSnapshotDaoTest : DatabaseTest() {
    private lateinit var dao: ThreadSnapshotDao
    private lateinit var postDao: CachedPostDao

    @Before
    fun resolveDaos() {
        dao = db.threadSnapshotDao()
        postDao = db.cachedPostDao()
    }

    private fun snapshot(
        focus: String,
        openedAt: Long = 0,
        account: String = ACCOUNT,
    ) = ThreadSnapshotEntity(
        accountDid = account,
        focusUri = focus,
        fetchedAt = Instant.fromEpochMilliseconds(openedAt),
        lastOpenedAt = Instant.fromEpochMilliseconds(openedAt),
    )

    private fun item(
        focus: String,
        position: Int,
        postUri: String = "$focus/$position",
        kind: String = ThreadSnapshotKind.REPLY,
        account: String = ACCOUNT,
    ) = ThreadSnapshotItemEntity(
        accountDid = account,
        focusUri = focus,
        position = position,
        kind = kind,
        postUri = postUri,
        depth = 1,
        authorDid = null,
    )

    @Test
    fun replace_overwritesItems() =
        runTest {
            dao.replace(snapshot("at://f"), listOf(item("at://f", 0), item("at://f", 1), item("at://f", 2)))
            dao.replace(snapshot("at://f"), listOf(item("at://f", 0)))

            assertEquals(1, dao.items(ACCOUNT, "at://f").size)
            assertNotNull(dao.snapshot(ACCOUNT, "at://f"))
        }

    @Test
    fun items_areOrderedByPosition() =
        runTest {
            dao.replace(snapshot("at://f"), listOf(item("at://f", 2), item("at://f", 0), item("at://f", 1)))

            assertEquals(listOf(0, 1, 2), dao.items(ACCOUNT, "at://f").map { it.position })
        }

    @Test
    fun touch_updatesLastOpenedAt() =
        runTest {
            dao.replace(snapshot("at://f", openedAt = 1), emptyList())

            dao.touch(ACCOUNT, "at://f", Instant.fromEpochMilliseconds(42))

            assertEquals(Instant.fromEpochMilliseconds(42), dao.snapshot(ACCOUNT, "at://f")?.lastOpenedAt)
        }

    @Test
    fun delete_removesSnapshotAndItems() =
        runTest {
            dao.replace(snapshot("at://f"), listOf(item("at://f", 0)))

            dao.delete(ACCOUNT, "at://f")

            assertNull(dao.snapshot(ACCOUNT, "at://f"))
            assertTrue(dao.items(ACCOUNT, "at://f").isEmpty())
        }

    @Test
    fun evictBeyond_dropsLeastRecentlyOpened() =
        runTest {
            dao.replace(snapshot("at://old", openedAt = 1), listOf(item("at://old", 0)))
            dao.replace(snapshot("at://mid", openedAt = 2), listOf(item("at://mid", 0)))
            dao.replace(snapshot("at://new", openedAt = 3), listOf(item("at://new", 0)))

            dao.evictBeyond(ACCOUNT, cap = 2)

            assertNull(dao.snapshot(ACCOUNT, "at://old"))
            assertTrue(dao.items(ACCOUNT, "at://old").isEmpty())
            assertNotNull(dao.snapshot(ACCOUNT, "at://mid"))
            assertNotNull(dao.snapshot(ACCOUNT, "at://new"))
        }

    @Test
    fun clearAccount_dropsOnlyThatAccount() =
        runTest {
            dao.replace(snapshot("at://f"), listOf(item("at://f", 0)))
            dao.replace(snapshot("at://f", account = OTHER), listOf(item("at://f", 0, account = OTHER)))

            dao.clearAccount(ACCOUNT)

            assertNull(dao.snapshot(ACCOUNT, "at://f"))
            assertTrue(dao.items(ACCOUNT, "at://f").isEmpty())
            assertNotNull(dao.snapshot(OTHER, "at://f"))
            assertEquals(1, dao.items(OTHER, "at://f").size)
        }

    @Test
    fun deleteUnreferenced_keepsPostsReferencedBySnapshot() =
        runTest {
            postDao.upsert(listOf(post("at://p/kept"), post("at://p/orphan")))
            dao.replace(snapshot("at://f"), listOf(item("at://f", 0, postUri = "at://p/kept")))

            postDao.deleteUnreferenced(ACCOUNT)

            assertNotNull(postDao.byUri(ACCOUNT, "at://p/kept"))
            assertNull(postDao.byUri(ACCOUNT, "at://p/orphan"))

            dao.delete(ACCOUNT, "at://f")
            postDao.deleteUnreferenced(ACCOUNT)

            assertNull(postDao.byUri(ACCOUNT, "at://p/kept"))
        }

    private fun post(uri: String) =
        CachedPostEntity(
            accountDid = ACCOUNT,
            uri = uri,
            cid = "cid",
            authorDid = "did:author",
            indexedAt = Instant.fromEpochMilliseconds(1),
            text = "t",
            postBlob = "{}",
        )

    private companion object {
        const val ACCOUNT = "did:plc:me"
        const val OTHER = "did:plc:other"
    }
}
