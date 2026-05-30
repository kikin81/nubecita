package net.kikin.nubecita.core.database

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import net.kikin.nubecita.core.database.dao.ActorDao
import net.kikin.nubecita.core.database.model.ActorEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActorDaoTest {
    private lateinit var db: NubecitaDatabase
    private lateinit var dao: ActorDao

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            NubecitaDatabase::class.java,
        ).build()
        dao = db.actorDao()
    }

    @After
    fun tearDown() = db.close()

    private fun actor(did: String, handle: String, name: String?, seen: Long) =
        ActorEntity(did, handle, name, avatarUrl = null, lastSeenAt = Instant.fromEpochMilliseconds(seen))

    @Test
    fun getActor_emitsNull_whenAbsent() = runTest {
        dao.getActor("did:absent").test {
            assertNull(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upsert_thenGetActor_emitsRow() = runTest {
        dao.upsert(listOf(actor("did:a", "alice.bsky.social", "Alice", 1_000)))
        dao.getActor("did:a").test {
            assertEquals("alice.bsky.social", awaitItem()?.handle)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun upsert_overwritesExistingDid() = runTest {
        dao.upsert(listOf(actor("did:a", "old.handle", "Old", 1_000)))
        dao.upsert(listOf(actor("did:a", "new.handle", "New", 2_000)))
        dao.getActor("did:a").test {
            val row = awaitItem()
            assertEquals("new.handle", row?.handle)
            assertEquals("New", row?.displayName)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
