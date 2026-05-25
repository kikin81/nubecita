package net.kikin.nubecita.core.push

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class PushRegistrationStateStoreTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun `read returns the default state when nothing has been written`() =
        runTest {
            val store = newStore(this)

            assertEquals(PushRegistrationState.Default, store.read())
        }

    @Test
    fun `write then read roundtrips every field`() =
        runTest {
            val store = newStore(this)
            val written =
                PushRegistrationState(
                    accountDid = "did:plc:alice",
                    fcmToken = "fcm-token-abc",
                    status = PushRegistrationState.Status.Succeeded,
                )

            store.write(written)

            assertEquals(written, store.read())
        }

    @Test
    fun `clear resets the store to the default state`() =
        runTest {
            val store = newStore(this)
            store.write(
                PushRegistrationState(
                    accountDid = "did:plc:alice",
                    fcmToken = "fcm-token-abc",
                    status = PushRegistrationState.Status.Succeeded,
                ),
            )

            store.clear()

            assertEquals(PushRegistrationState.Default, store.read())
        }

    @Test
    fun `write supports each Status variant`() {
        runTest {
            for (status in PushRegistrationState.Status.entries) {
                val store = newStore(this, fileName = "store-${status.name}.preferences_pb")
                val written =
                    PushRegistrationState(
                        accountDid = "did:plc:alice",
                        fcmToken = "fcm-token-abc",
                        status = status,
                    )

                store.write(written)

                assertEquals(written, store.read())
            }
        }
    }

    private fun newStore(
        scope: TestScope,
        fileName: String = "test.preferences_pb",
    ): PushRegistrationStateStore {
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(
                scope = CoroutineScope(scope.coroutineContext),
                produceFile = { File(tempDir, fileName) },
            )
        return PushRegistrationStateStore(dataStore)
    }
}
