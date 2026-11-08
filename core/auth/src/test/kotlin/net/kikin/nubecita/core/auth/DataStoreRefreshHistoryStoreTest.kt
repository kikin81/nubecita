package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.TokenRefreshCaller
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class DataStoreRefreshHistoryStoreTest {
    @JvmField
    @TempDir
    var tempDir: File = File("")

    private val marker =
        InFlightRefresh(epochMillis = 1_000, caller = TokenRefreshCaller.DmPoll, foreground = false, processId = "p1")

    @Test
    fun `a marker with no outcome after it is unfinished, and round-trips`() =
        runTest {
            val store = DataStoreRefreshHistoryStore(newDataStore(this))
            store.recordCompleted(epochMillis = 500)

            store.markInFlight(marker)

            assertEquals(marker, store.unfinished())
        }

    @Test
    fun `an outcome after the marker means the refresh finished`() =
        runTest {
            val store = DataStoreRefreshHistoryStore(newDataStore(this))
            store.markInFlight(marker)

            store.recordCompleted(epochMillis = 1_300)

            assertNull(store.unfinished())
        }

    /** Completions are written off the refresh path, so they can land out of order. */
    @Test
    fun `a late write from an older refresh doesn't move completion backwards`() =
        runTest {
            val store = DataStoreRefreshHistoryStore(newDataStore(this))
            store.markInFlight(marker)
            store.recordCompleted(epochMillis = 1_300)

            store.recordCompleted(epochMillis = 900)

            assertNull(store.unfinished())
        }

    @Test
    fun `a cleared marker is gone`() =
        runTest {
            val store = DataStoreRefreshHistoryStore(newDataStore(this))
            store.markInFlight(marker)

            store.clearInFlight()

            assertNull(store.unfinished())
        }

    @Test
    fun `a refresh that died in flight is kept for later clears`() =
        runTest {
            val store = DataStoreRefreshHistoryStore(newDataStore(this))

            store.recordDiedInFlight(RefreshRecord(epochMillis = 1_000, caller = TokenRefreshCaller.Widget))

            assertEquals(RefreshRecord(1_000, TokenRefreshCaller.Widget), store.history().lastDiedInFlight)
        }

    private fun newDataStore(scope: TestScope): DataStore<Preferences> {
        val file = tempDir.resolve("refresh_history_${System.nanoTime()}.preferences_pb")
        return PreferenceDataStoreFactory.create(scope = scope.backgroundScope, produceFile = { file })
    }
}
