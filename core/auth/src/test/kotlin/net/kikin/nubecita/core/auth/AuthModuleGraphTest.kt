package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStoreFactory
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Pure-JVM round trip against a real DataStoreFactory file, without Tink or Keystore
 * (those require an Android runtime). Exercises the same spec scenarios that the
 * encrypted store will satisfy on-device; instrumented coverage lives under nubecita-16a.
 */
class AuthModuleGraphTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `round trip through a real file-backed DataStore`() =
        runTest {
            val file = tempFolder.newFile("oauth_session.pb")
            file.delete() // DataStore creates the file itself; ensure it starts missing.
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
