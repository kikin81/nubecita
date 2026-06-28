package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.SavedFeedEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
internal class SavedFeedDaoTest : DatabaseTest() {
    private lateinit var dao: SavedFeedDao

    @Before
    fun resolveDao() {
        dao = db.savedFeedDao()
    }

    private fun feed(
        uri: String,
        displayName: String = "Feed $uri",
        creatorHandle: String? = null,
        avatarUrl: String? = null,
        pinned: Boolean = false,
        position: Int = 0,
    ) = SavedFeedEntity(uri, displayName, creatorHandle, avatarUrl, pinned, position)

    @Test
    fun observeSavedFeeds_emitsEmptyList_whenNoRows() =
        runTest {
            dao.observeSavedFeeds().test {
                assertTrue(awaitItem().isEmpty())
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun upsert_thenObserve_emitsBothFeedsOrderedByPosition() =
        runTest {
            dao.upsert(
                listOf(
                    feed(uri = "at://feed/2", position = 2),
                    feed(uri = "at://feed/1", position = 1),
                ),
            )
            dao.observeSavedFeeds().test {
                val items = awaitItem()
                assertEquals(2, items.size)
                assertEquals("at://feed/1", items[0].uri)
                assertEquals("at://feed/2", items[1].uri)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun upsert_overwritesExistingUri_noduplicates() =
        runTest {
            dao.upsert(listOf(feed(uri = "at://feed/a", displayName = "Old", position = 0)))
            dao.upsert(listOf(feed(uri = "at://feed/a", displayName = "New", position = 0)))
            dao.observeSavedFeeds().test {
                val items = awaitItem()
                assertEquals(1, items.size)
                assertEquals("New", items[0].displayName)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun setPinned_false_flipsPin() =
        runTest {
            dao.upsert(listOf(feed(uri = "at://feed/a", pinned = true, position = 0)))
            dao.setPinned("at://feed/a", false)
            dao.observeSavedFeeds().test {
                val items = awaitItem()
                assertEquals(false, items[0].pinned)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun setPinned_true_flipsPin() =
        runTest {
            dao.upsert(listOf(feed(uri = "at://feed/a", pinned = false, position = 0)))
            dao.setPinned("at://feed/a", true)
            dao.observeSavedFeeds().test {
                assertEquals(true, awaitItem()[0].pinned)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun deleteUrisNotIn_removesAbsentUris_keepsListed() =
        runTest {
            dao.upsert(
                listOf(
                    feed(uri = "at://feed/keep", position = 0),
                    feed(uri = "at://feed/drop", position = 1),
                ),
            )
            dao.deleteUrisNotIn(listOf("at://feed/keep"))
            dao.observeSavedFeeds().test {
                val items = awaitItem()
                assertEquals(1, items.size)
                assertEquals("at://feed/keep", items[0].uri)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun nullableFields_roundTripAsNull() =
        runTest {
            dao.upsert(
                listOf(feed(uri = "at://feed/bare", creatorHandle = null, avatarUrl = null, position = 0)),
            )
            dao.observeSavedFeeds().test {
                val item = awaitItem()[0]
                assertEquals(null, item.creatorHandle)
                assertEquals(null, item.avatarUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }

    @Test
    fun nullableFields_roundTripWithValues() =
        runTest {
            dao.upsert(
                listOf(
                    feed(
                        uri = "at://feed/full",
                        creatorHandle = "alice.bsky.social",
                        avatarUrl = "https://cdn.bsky.app/img/avatar",
                        position = 0,
                    ),
                ),
            )
            dao.observeSavedFeeds().test {
                val item = awaitItem()[0]
                assertEquals("alice.bsky.social", item.creatorHandle)
                assertEquals("https://cdn.bsky.app/img/avatar", item.avatarUrl)
                cancelAndIgnoreRemainingEvents()
            }
        }
}
