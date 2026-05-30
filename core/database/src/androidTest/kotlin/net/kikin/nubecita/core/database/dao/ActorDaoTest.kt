package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.ActorEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class ActorDaoTest : DatabaseTest() {
    private lateinit var dao: ActorDao

    @Before
    fun resolveDao() {
        dao = db.actorDao()
    }

    private fun actor(
        did: String,
        handle: String,
        name: String?,
        seen: Long,
    ) = ActorEntity(did, handle, name, avatarUrl = null, lastSeenAt = Instant.fromEpochMilliseconds(seen))

    @Test
    fun getActor_emitsNull_whenAbsent() =
        runTest {
            dao.getActor("did:absent").test {
                assertNull(awaitItem())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun upsert_thenGetActor_emitsRow() =
        runTest {
            dao.upsert(listOf(actor("did:a", "alice.bsky.social", "Alice", 1_000)))
            dao.getActor("did:a").test {
                assertEquals("alice.bsky.social", awaitItem()?.handle)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun upsert_overwritesExistingDid() =
        runTest {
            dao.upsert(listOf(actor("did:a", "old.handle", "Old", 1_000)))
            dao.upsert(listOf(actor("did:a", "new.handle", "New", 2_000)))
            dao.getActor("did:a").test {
                val row = awaitItem()
                assertEquals("new.handle", row?.handle)
                assertEquals("New", row?.displayName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun upsert_batchOfTwo_bothRowsRetrievable() =
        runTest {
            dao.upsert(
                listOf(
                    actor("did:a", "alice.bsky.social", "Alice", 1_000),
                    actor("did:b", "bob.bsky.social", "Bob", 2_000),
                ),
            )
            assertNotNull(dao.getActor("did:a").first())
            assertNotNull(dao.getActor("did:b").first())
        }

    @Test
    fun upsert_nullDisplayName_roundTripsAsNull() =
        runTest {
            dao.upsert(listOf(actor("did:c", "carol.bsky.social", null, 3_000)))
            assertNull(dao.getActor("did:c").first()?.displayName)
        }
}
