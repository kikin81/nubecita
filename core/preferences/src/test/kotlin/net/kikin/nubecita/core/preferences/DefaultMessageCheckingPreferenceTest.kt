package net.kikin.nubecita.core.preferences

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultMessageCheckingPreferenceTest {
    @Test
    fun `defaults to enabled on a fresh store`() =
        runTest {
            val pref = DefaultMessageCheckingPreference(newDataStore(this))

            assertTrue(pref.enabled.first())
        }

    @Test
    fun `setEnabled false then true round-trips`() =
        runTest {
            val pref = DefaultMessageCheckingPreference(newDataStore(this))

            pref.enabled.test {
                assertTrue(awaitItem())
                pref.setEnabled(false)
                assertFalse(awaitItem())
                pref.setEnabled(true)
                assertTrue(awaitItem())
            }
        }

    @JvmField
    @TempDir
    var tempDir: File = File("")

    private fun newDataStore(scope: TestScope): DataStore<Preferences> {
        val file = tempDir.resolve("msg_checking_${System.nanoTime()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file },
        )
    }
}
