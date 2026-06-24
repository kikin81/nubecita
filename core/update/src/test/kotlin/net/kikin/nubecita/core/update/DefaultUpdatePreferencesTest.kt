package net.kikin.nubecita.core.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultUpdatePreferencesTest {
    @Test
    fun `defaults to null when never written`() =
        runTest {
            val prefs = DefaultUpdatePreferences(newDataStore(this))

            assertNull(prefs.lastPromptedVersionCode())
        }

    @Test
    fun `round-trips the last prompted version code`() =
        runTest {
            val prefs = DefaultUpdatePreferences(newDataStore(this))

            prefs.setLastPromptedVersionCode(142)

            assertEquals(142, prefs.lastPromptedVersionCode())
        }

    @Test
    fun `overwrites with the newer version code`() =
        runTest {
            val prefs = DefaultUpdatePreferences(newDataStore(this))

            prefs.setLastPromptedVersionCode(142)
            prefs.setLastPromptedVersionCode(143)

            assertEquals(143, prefs.lastPromptedVersionCode())
        }

    @JvmField
    @TempDir
    var tempDir: File = File("")

    private fun newDataStore(scope: TestScope): DataStore<Preferences> {
        val file = tempDir.resolve("update_prefs_${System.nanoTime()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file },
        )
    }
}
