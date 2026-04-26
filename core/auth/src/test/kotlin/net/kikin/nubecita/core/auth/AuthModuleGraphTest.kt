package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Pure-JVM round trip against a real DataStoreFactory file, without Tink or Keystore
 * (those require an Android runtime). Exercises the same spec scenarios that the
 * encrypted store will satisfy on-device; instrumented coverage lives under nubecita-16a.
 */
class AuthModuleGraphTest {
    @TempDir
    lateinit var tempFolder: File

    @Test
    fun `round trip through a real file-backed DataStore`() =
        runTest {
            val file = File(tempFolder, "oauth_session.pb")
            // DataStore creates the file itself; ensure it starts missing.
            file.delete()
            val dataStore =
                DataStoreFactory.create(
                    serializer = OAuthSessionSerializer,
                    produceFile = { file },
                )
            val store = EncryptedOAuthSessionStore(dataStore)

            assertNull(store.load())

            val session = sampleSession()
            store.save(session)
            assertEquals(session, store.load())

            store.clear()
            assertNull(store.load())

            val replacement = sampleSession(accessToken = "second")
            store.save(replacement)
            assertEquals(replacement, store.load())
        }
}
