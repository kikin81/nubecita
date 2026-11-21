package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.CachedProfileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
internal class CachedProfileDaoTest : DatabaseTest() {
    private lateinit var dao: CachedProfileDao

    @Before
    fun resolveDao() {
        dao = db.cachedProfileDao()
    }

    private fun row(
        did: String,
        handle: String = "$did.test",
        account: String = ACCOUNT,
        openedAt: Long = 0,
    ) = CachedProfileEntity(
        accountDid = account,
        did = did,
        handle = handle,
        profileBlob = "{\"did\":\"$did\"}",
        fetchedAt = Instant.fromEpochMilliseconds(openedAt),
        lastOpenedAt = Instant.fromEpochMilliseconds(openedAt),
    )

    @Test
    fun byActor_matchesDidOrHandle() =
        runTest {
            dao.upsert(row(did = "did:plc:a", handle = "a.test"))

            assertNotNull(dao.byActor(ACCOUNT, "did:plc:a"))
            assertNotNull(dao.byActor(ACCOUNT, "a.test"))
            assertNull(dao.byActor("did:plc:other", "did:plc:a"))
        }

    @Test
    fun upsert_overwritesTheRow() =
        runTest {
            dao.upsert(row(did = "did:plc:a", handle = "old.test"))
            dao.upsert(row(did = "did:plc:a", handle = "new.test"))

            assertEquals("new.test", dao.byActor(ACCOUNT, "did:plc:a")?.handle)
            assertNull(dao.byActor(ACCOUNT, "old.test"))
        }

    @Test
    fun touch_updatesLastOpenedAt() =
        runTest {
            dao.upsert(row(did = "did:plc:a", openedAt = 1))

            dao.touch(ACCOUNT, "did:plc:a", Instant.fromEpochMilliseconds(99))

            assertEquals(Instant.fromEpochMilliseconds(99), dao.byActor(ACCOUNT, "did:plc:a")?.lastOpenedAt)
        }

    @Test
    fun deleteByActor_dropsByHandleOrDid() =
        runTest {
            dao.upsert(row(did = "did:plc:a", handle = "a.test"))
            dao.upsert(row(did = "did:plc:b", handle = "b.test"))

            dao.deleteByActor(ACCOUNT, "a.test")
            dao.deleteByActor(ACCOUNT, "did:plc:b")

            assertNull(dao.byActor(ACCOUNT, "did:plc:a"))
            assertNull(dao.byActor(ACCOUNT, "did:plc:b"))
        }

    @Test
    fun trimToCap_keepsOwnAndMostRecent() =
        runTest {
            (1..5).forEach { dao.upsert(row(did = "did:plc:$it", openedAt = it.toLong())) }

            dao.trimToCap(ACCOUNT, cap = 2, keepDid = "did:plc:1")

            assertNotNull(dao.byActor(ACCOUNT, "did:plc:1"))
            assertNotNull(dao.byActor(ACCOUNT, "did:plc:5"))
            assertNull(dao.byActor(ACCOUNT, "did:plc:2"))
            assertNull(dao.byActor(ACCOUNT, "did:plc:3"))
            assertNull(dao.byActor(ACCOUNT, "did:plc:4"))
        }

    @Test
    fun trimToCap_isScopedToOneAccount() =
        runTest {
            dao.upsert(row(did = "did:plc:a", openedAt = 1))
            dao.upsert(row(did = "did:plc:b", openedAt = 2))
            dao.upsert(row(did = "did:plc:a", account = "did:plc:other", openedAt = 1))

            dao.trimToCap(ACCOUNT, cap = 1, keepDid = ACCOUNT)

            assertNull(dao.byActor(ACCOUNT, "did:plc:a"))
            assertNotNull(dao.byActor("did:plc:other", "did:plc:a"))
        }

    @Test
    fun clearAccount_dropsOnlyThatAccount() =
        runTest {
            dao.upsert(row(did = "did:plc:a"))
            dao.upsert(row(did = "did:plc:a", account = "did:plc:other"))

            dao.clearAccount(ACCOUNT)

            assertNull(dao.byActor(ACCOUNT, "did:plc:a"))
            assertNotNull(dao.byActor("did:plc:other", "did:plc:a"))
        }

    private companion object {
        const val ACCOUNT = "did:plc:me"
    }
}
