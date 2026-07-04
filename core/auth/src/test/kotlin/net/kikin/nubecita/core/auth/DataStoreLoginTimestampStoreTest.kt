package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.io.IOException

class DataStoreLoginTimestampStoreTest {
    @TempDir
    lateinit var tempFolder: File

    private fun fileBackedStore(): DataStoreLoginTimestampStore =
        DataStoreLoginTimestampStore(
            PreferenceDataStoreFactory.create(
                produceFile = { File(tempFolder, "auth_telemetry.preferences_pb") },
            ),
        )

    @Test
    fun `reads null before any login is recorded`() =
        runTest {
            assertNull(fileBackedStore().lastLoginEpochMillis())
        }

    @Test
    fun `record then read round-trips, later logins overwrite`() =
        runTest {
            val store = fileBackedStore()

            store.record(1_000L)
            assertEquals(1_000L, store.lastLoginEpochMillis())

            store.record(2_000L)
            assertEquals(2_000L, store.lastLoginEpochMillis())
        }

    @Test
    fun `read failure degrades to null - telemetry must never break its caller`() =
        runTest {
            val store = DataStoreLoginTimestampStore(ThrowingPreferencesDataStore(IOException("simulated")))

            assertNull(store.lastLoginEpochMillis())
        }

    @Test
    fun `unexpected read failure types propagate`() =
        runTest {
            val store =
                DataStoreLoginTimestampStore(
                    ThrowingPreferencesDataStore(IllegalStateException("not a storage failure")),
                )

            val thrown = runCatching { store.lastLoginEpochMillis() }.exceptionOrNull()

            assertTrue(thrown is IllegalStateException, "expected IllegalStateException, got $thrown")
        }
}

private class ThrowingPreferencesDataStore(
    private val toThrow: Throwable,
) : DataStore<Preferences> {
    override val data: Flow<Preferences> = flow { throw toThrow }

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences = throw toThrow
}
