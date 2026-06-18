package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultUserPreferencesRepositoryTest {
    @Test
    fun `hasSeenOnboarding starts as false on a fresh store`() =
        runTest {
            val repo = DefaultUserPreferencesRepository(newDataStore(this))

            assertFalse(repo.hasSeenOnboarding.first())
        }

    @Test
    fun `markOnboardingSeen flips the flag to true`() =
        runTest {
            val repo = DefaultUserPreferencesRepository(newDataStore(this))

            repo.hasSeenOnboarding.test {
                assertFalse(awaitItem())
                repo.markOnboardingSeen()
                assertTrue(awaitItem())
            }
        }

    @Test
    fun `markOnboardingSeen is idempotent`() =
        runTest {
            val repo = DefaultUserPreferencesRepository(newDataStore(this))

            repo.markOnboardingSeen()
            repo.markOnboardingSeen()

            assertTrue(repo.hasSeenOnboarding.first())
        }

    @Test
    fun `lastSelectedFeedUri starts as null on a fresh store`() =
        runTest {
            val repo = DefaultUserPreferencesRepository(newDataStore(this))

            assertNull(repo.lastSelectedFeedUri.first())
        }

    @Test
    fun `setLastSelectedFeedUri round-trips the stored value`() =
        runTest {
            val repo = DefaultUserPreferencesRepository(newDataStore(this))
            val uri = "at://did:plc:abc123/app.bsky.feed.generator/whats-hot"

            repo.lastSelectedFeedUri.test {
                assertNull(awaitItem())
                repo.setLastSelectedFeedUri(uri)
                assertEquals(uri, awaitItem())
            }
        }

    @Test
    fun `themePreference defaults to SYSTEM on a fresh store`() =
        runTest {
            val repo = DefaultUserPreferencesRepository(newDataStore(this))

            assertEquals(ThemePreference.SYSTEM, repo.themePreference.first())
        }

    @Test
    fun `setThemePreference round-trips the stored value`() =
        runTest {
            val repo = DefaultUserPreferencesRepository(newDataStore(this))

            repo.themePreference.test {
                assertEquals(ThemePreference.SYSTEM, awaitItem())
                repo.setThemePreference(ThemePreference.DARK)
                assertEquals(ThemePreference.DARK, awaitItem())
            }
        }

    @JvmField
    @TempDir
    var tempDir: File = File("")

    // DataStore needs its own coroutine scope for its writer actor. Use the
    // test's `backgroundScope` so work is auto-cancelled at the end of
    // `runTest` — a standalone `TestScope` would outlive the test and could
    // mask coroutine leaks across tests.
    private fun newDataStore(scope: TestScope): DataStore<Preferences> {
        val file = tempDir.resolve("user_prefs_${System.nanoTime()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file },
        )
    }
}
