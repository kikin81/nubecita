package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.ActorEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

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
        canMessage: Boolean = true,
    ) = ActorEntity(did, handle, name, avatarUrl = null, lastSeenAt = Instant.fromEpochMilliseconds(seen), canMessage = canMessage)

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

    @Test
    fun recentActors_ordersByLastSeenDesc_andLimits() =
        runTest {
            dao.upsert(
                listOf(
                    actor("did:a", "a.bsky.social", "A", 1_000),
                    actor("did:b", "b.bsky.social", "B", 3_000),
                    actor("did:c", "c.bsky.social", "C", 2_000),
                ),
            )
            dao.recentActors(selfDid = null, limit = 2).test {
                assertEquals(listOf("did:b", "did:c"), awaitItem().map { it.did })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun recentActors_excludesSelfDid() =
        runTest {
            dao.upsert(
                listOf(
                    actor("did:self", "me.bsky.social", "Me", 3_000),
                    actor("did:other", "other.bsky.social", "Other", 1_000),
                ),
            )
            dao.recentActors(selfDid = "did:self", limit = 10).test {
                assertEquals(listOf("did:other"), awaitItem().map { it.did })
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun upsert_canMessageFalse_roundTrips() =
        runTest {
            dao.upsert(listOf(actor("did:n", "n.bsky.social", "N", 1_000, canMessage = false)))
            assertEquals(false, dao.getActor("did:n").first()?.canMessage)
        }

    @Test
    fun upsert_canMessageDefaultsTrue() =
        runTest {
            dao.upsert(listOf(actor("did:y", "y.bsky.social", "Y", 1_000)))
            assertEquals(true, dao.getActor("did:y").first()?.canMessage)
        }

    @Test
    fun recentActors_nullSelfDid_returnsAllRows() =
        runTest {
            dao.upsert(listOf(actor("did:a", "a.bsky.social", "A", 1_000)))
            dao.recentActors(selfDid = null, limit = 10).test {
                assertEquals(listOf("did:a"), awaitItem().map { it.did })
                cancelAndIgnoreRemainingEvents()
            }
        }
}
