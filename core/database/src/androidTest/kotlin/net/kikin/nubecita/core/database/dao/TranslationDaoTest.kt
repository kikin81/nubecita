package net.kikin.nubecita.core.database.dao

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.database.DatabaseTest
import net.kikin.nubecita.core.database.model.TranslationEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.time.Instant

@RunWith(AndroidJUnit4::class)
internal class TranslationDaoTest : DatabaseTest() {
    private lateinit var dao: TranslationDao

    @Before
    fun resolveDao() {
        dao = db.translationDao()
    }

    @Test
    fun find_missingRowIsNull() =
        runTest {
            assertNull(dao.find("at://post/1", "en"))
        }

    @Test
    fun find_roundTripsEverySpanField() =
        runTest {
            dao.upsert(entity(postUri = "at://post/1", target = "en", text = "Good morning"))

            val row = dao.find("at://post/1", "en")

            assertNotNull(row)
            assertEquals("Good morning", row!!.translatedText)
            assertEquals("ja", row.sourceLanguage)
        }

    @Test
    fun targetLanguageIsPartOfTheKey() =
        runTest {
            // The whole reason the key is composite: a reader who switches
            // device language must not be served the previous language's
            // translation. Keyed on the URI alone they would be, and the
            // staleness is invisible — the text is in *a* language and reads
            // fine.
            dao.upsert(entity(postUri = "at://post/1", target = "en", text = "Good morning"))
            dao.upsert(entity(postUri = "at://post/1", target = "es", text = "Buenos días"))

            assertEquals("Good morning", dao.find("at://post/1", "en")?.translatedText)
            assertEquals("Buenos días", dao.find("at://post/1", "es")?.translatedText)
            assertEquals(2, dao.count())
        }

    @Test
    fun upsertReplacesTheSameKey() =
        runTest {
            dao.upsert(entity(postUri = "at://post/1", target = "en", text = "first"))
            dao.upsert(entity(postUri = "at://post/1", target = "en", text = "second"))

            assertEquals(1, dao.count())
            assertEquals("second", dao.find("at://post/1", "en")?.translatedText)
        }

    @Test
    fun upsertAndTrim_keepsTheNewestUpToCapacity() =
        runTest {
            repeat(5) { index ->
                dao.upsertAndTrim(
                    entity(
                        postUri = "at://post/$index",
                        target = "en",
                        text = "t$index",
                        createdAt = Instant.fromEpochSeconds(1_000L + index),
                    ),
                    capacity = 3,
                )
            }

            assertEquals(3, dao.count())
            // The three most recent survive; the two oldest are gone.
            assertNull(dao.find("at://post/0", "en"))
            assertNull(dao.find("at://post/1", "en"))
            assertNotNull(dao.find("at://post/4", "en"))
        }

    @Test
    fun upsertAndTrim_neverLeavesTheTableOverCapacity() =
        runTest {
            // Trimming inside the transaction is what makes this true at every
            // observable point, not just at the end.
            repeat(20) { index ->
                dao.upsertAndTrim(
                    entity(
                        postUri = "at://post/$index",
                        target = "en",
                        createdAt = Instant.fromEpochSeconds(1_000L + index),
                    ),
                    capacity = 4,
                )
                // assertTrue, not Kotlin's `assert`: that intrinsic is gated
                // on desiredAssertionStatus(), which ART reports false unless
                // the runtime is started with -ea. The check never ran, so the
                // one assertion proving the cache stays bounded was dead.
                assertTrue("cache exceeded capacity after insert $index", dao.count() <= 4)
            }
        }

    @Test
    fun deleteForLanguage_leavesOtherLanguagesAlone() =
        runTest {
            dao.upsert(entity(postUri = "at://post/1", target = "en"))
            dao.upsert(entity(postUri = "at://post/1", target = "es"))
            dao.upsert(entity(postUri = "at://post/2", target = "en"))

            dao.deleteForLanguage("en")

            assertEquals(1, dao.count())
            assertNotNull(dao.find("at://post/1", "es"))
        }

    @Test
    fun clear_emptiesTheCache() =
        runTest {
            dao.upsert(entity(postUri = "at://post/1", target = "en"))
            dao.upsert(entity(postUri = "at://post/2", target = "en"))

            dao.clear()

            assertEquals(0, dao.count())
        }

    private fun entity(
        postUri: String,
        target: String,
        text: String = "translated",
        createdAt: Instant = Instant.fromEpochSeconds(1_000),
    ) = TranslationEntity(
        postUri = postUri,
        targetLanguage = target,
        sourceLanguage = "ja",
        translatedText = text,
        facetsJson = "[]",
        createdAt = createdAt,
    )
}
