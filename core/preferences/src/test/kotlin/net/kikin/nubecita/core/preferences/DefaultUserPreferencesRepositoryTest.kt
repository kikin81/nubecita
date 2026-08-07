package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
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
    fun `themePreference defaults to DYNAMIC on a fresh store`() =
        runTest {
            val repo = DefaultUserPreferencesRepository(newDataStore(this))

            assertEquals(ThemePreference.DYNAMIC, repo.themePreference.first())
        }

    @Test
    fun `setThemePreference round-trips the stored value`() =
        runTest {
            val repo = DefaultUserPreferencesRepository(newDataStore(this))

            repo.themePreference.test {
                assertEquals(ThemePreference.DYNAMIC, awaitItem())
                repo.setThemePreference(ThemePreference.DARK)
                assertEquals(ThemePreference.DARK, awaitItem())
            }
        }

    // The picker (nubecita-wqb8) renamed the follow-the-OS constant SYSTEM ->
    // DYNAMIC. Nothing had ever written the preference, so no install actually
    // holds "SYSTEM" — but this fallback IS the migration for any that might,
    // and for any value a NEWER build (e.g. a future custom theme) writes and
    // this one can't parse. Pinned so a refactor can't quietly drop the
    // runCatching and start throwing on an unknown string.
    @Test
    fun `themePreference falls back to DYNAMIC for an unrecognized stored value`() =
        runTest {
            val dataStore = newDataStore(this)
            dataStore.edit { prefs -> prefs[stringPreferencesKey("theme_preference")] = "SYSTEM" }

            val repo = DefaultUserPreferencesRepository(dataStore)

            assertEquals(ThemePreference.DYNAMIC, repo.themePreference.first())
        }

    @Test
    fun `themePreference falls back to DYNAMIC for a value written by a newer build`() =
        runTest {
            val dataStore = newDataStore(this)
            dataStore.edit { prefs -> prefs[stringPreferencesKey("theme_preference")] = "CUSTOM_MIDNIGHT" }

            val repo = DefaultUserPreferencesRepository(dataStore)

            assertEquals(ThemePreference.DYNAMIC, repo.themePreference.first())
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
