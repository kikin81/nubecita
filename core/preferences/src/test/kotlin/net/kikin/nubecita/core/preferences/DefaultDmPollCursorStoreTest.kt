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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
internal class DefaultDmPollCursorStoreTest {
    @Test
    fun `cursor starts null on a fresh store`() =
        runTest {
            val store = DefaultDmPollCursorStore(newDataStore(this))

            assertNull(store.cursor(DID_A).first())
        }

    @Test
    fun `setCursor round-trips the stored value for that DID`() =
        runTest {
            val store = DefaultDmPollCursorStore(newDataStore(this))

            store.cursor(DID_A).test {
                assertNull(awaitItem())
                store.setCursor(DID_A, "cursor-1")
                assertEquals("cursor-1", awaitItem())
            }
        }

    @Test
    fun `cursors are isolated per DID`() =
        runTest {
            val store = DefaultDmPollCursorStore(newDataStore(this))

            store.setCursor(DID_A, "cursor-a")
            store.setCursor(DID_B, "cursor-b")

            assertEquals("cursor-a", store.cursor(DID_A).first())
            assertEquals("cursor-b", store.cursor(DID_B).first())
        }

    @Test
    fun `setCursor overwrites the prior value for the same DID`() =
        runTest {
            val store = DefaultDmPollCursorStore(newDataStore(this))

            store.setCursor(DID_A, "old")
            store.setCursor(DID_A, "new")

            assertEquals("new", store.cursor(DID_A).first())
        }

    private companion object {
        const val DID_A = "did:plc:alice"
        const val DID_B = "did:plc:bob"
    }

    @JvmField
    @TempDir
    var tempDir: File = File("")

    private fun newDataStore(scope: TestScope): DataStore<Preferences> {
        val file = tempDir.resolve("dm_cursor_${System.nanoTime()}.preferences_pb")
        return PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { file },
        )
    }
}
