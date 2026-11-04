package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import net.kikin.nubecita.core.analytics.SessionClearReason
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class DataStoreSessionClearPendingStoreTest {
    @TempDir
    lateinit var tempFolder: File

    private fun fileBackedStore(): DataStoreSessionClearPendingStore =
        DataStoreSessionClearPendingStore(
            PreferenceDataStoreFactory.create(
                produceFile = { File(tempFolder, "auth_telemetry.preferences_pb") },
            ),
        )

    @Test
    fun `reads null before any clear is marked`() =
        runTest {
            assertNull(fileBackedStore().pendingReason())
        }

    @Test
    fun `a marked clear survives being read back - this is what outlives process death`() =
        runTest {
            val store = fileBackedStore()

            store.markPending(SessionClearReason.InvalidGrant)

            assertEquals(SessionClearReason.InvalidGrant, store.pendingReason())
        }

    @Test
    fun `marking the pending reason again overwrites the previous one`() =
        runTest {
            val store = fileBackedStore()

            store.markPending(SessionClearReason.InvalidGrant)
            store.markPending(SessionClearReason.UserSignOut)

            assertEquals(SessionClearReason.UserSignOut, store.pendingReason())
        }

    @Test
    fun `clearPending removes the marker so the next launch reports nothing`() =
        runTest {
            val store = fileBackedStore()
            store.markPending(SessionClearReason.InvalidGrant)

            store.clearPending()

            assertNull(store.pendingReason())
        }

    @Test
    fun `an unrecognised persisted reason reads back as Unknown, never a crash`() =
        runTest {
            // A wire value written by a newer build, or a corrupted preference,
            // must not take down the launch-time recovery pass.
            val prefs =
                PreferenceDataStoreFactory.create(
                    produceFile = { File(tempFolder, "auth_telemetry.preferences_pb") },
                )
            prefs.edit { it[stringPreferencesKey("pending_session_clear_reason")] = "some_reason_from_the_future" }

            assertEquals(SessionClearReason.Unknown, DataStoreSessionClearPendingStore(prefs).pendingReason())
        }

    @Test
    fun `read failure degrades to null - recovery must never break app start`() =
        runTest {
            val store = DataStoreSessionClearPendingStore(ThrowingClearPendingPrefsDataStore(IOException("simulated")))

            assertNull(store.pendingReason())
        }

    @Test
    fun `unexpected read failure types propagate`() =
        runTest {
            val store =
                DataStoreSessionClearPendingStore(
                    ThrowingClearPendingPrefsDataStore(IllegalStateException("not a storage failure")),
                )

            val thrown = runCatching { store.pendingReason() }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException, "expected IllegalStateException, got $thrown")
        }
}

private class ThrowingClearPendingPrefsDataStore(
    private val toThrow: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw toThrow }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw toThrow
}
