package net.kikin.nubecita.core.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreNotificationsPromptShownStoreTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `read returns false when nothing has been written`() =
        runTest {
            val store = newStore(this)

            assertFalse(store.read())
        }

    @Test
    fun `markShown flips the persisted flag to true`() =
        runTest {
            val store = newStore(this)

            store.markShown()

            assertTrue(store.read())
        }

    @Test
    fun `markShown is idempotent across repeated calls`() =
        runTest {
            val store = newStore(this)

            store.markShown()
            store.markShown()
            store.markShown()

            assertTrue(store.read())
        }

    private fun newStore(
        scope: TestScope,
        fileName: String = "test.preferences_pb",
    ): NotificationsPromptShownStore {
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(scope.coroutineContext),
                produceFile = { File(tempDir, fileName) },
            )
        return DataStoreNotificationsPromptShownStore(dataStore)
    }
}
