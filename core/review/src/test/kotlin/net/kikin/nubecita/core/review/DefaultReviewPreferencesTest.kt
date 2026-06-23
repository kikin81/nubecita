package net.kikin.nubecita.core.review

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultReviewPreferencesTest {
    private val now = Instant.parse("2026-06-23T12:00:00Z")
    private val later = Instant.parse("2026-07-01T12:00:00Z")

    @Test
    fun `fresh store returns default state`() =
        runTest {
            val prefs = DefaultReviewPreferences(newDataStore(this))

            val state = prefs.currentState()

            assertNull(state.firstLaunchAt)
            assertEquals(0, state.successfulPostCount)
            assertEquals(0, state.requestCount)
            assertNull(state.lastRequestedAt)
        }

    @Test
    fun `incrementPostCount accumulates`() =
        runTest {
            val prefs = DefaultReviewPreferences(newDataStore(this))

            prefs.incrementPostCount()
            prefs.incrementPostCount()

            assertEquals(2, prefs.currentState().successfulPostCount)
        }

    @Test
    fun `recordReviewRequested bumps count and stamps timestamp`() =
        runTest {
            val prefs = DefaultReviewPreferences(newDataStore(this))

            prefs.recordReviewRequested(now)

            val state = prefs.currentState()
            assertEquals(1, state.requestCount)
            assertEquals(now, state.lastRequestedAt)
        }

    @Test
    fun `stampFirstLaunchIfUnset sets once and never overwrites`() =
        runTest {
            val prefs = DefaultReviewPreferences(newDataStore(this))

            prefs.stampFirstLaunchIfUnset(now)
            prefs.stampFirstLaunchIfUnset(later)

            assertEquals(now, prefs.currentState().firstLaunchAt)
        }

    @JvmField
    @TempDir
    var tempDir: File = File("")

    private fun newDataStore(scope: TestScope): DataStore<Preferences> {
        val file = tempDir.resolve("review_prefs_${System.nanoTime()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file },
        )
    }
}
