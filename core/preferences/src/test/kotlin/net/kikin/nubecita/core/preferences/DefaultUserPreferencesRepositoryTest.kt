package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
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

    @JvmField
    @TempDir
    var tempDir: File = File("")

    private fun newDataStore(scope: TestScope): DataStore<Preferences> {
        val file = tempDir.resolve("user_prefs_${System.nanoTime()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = TestScope(StandardTestDispatcher(scope.testScheduler)),
            produceFile = { file },
        )
    }
}
