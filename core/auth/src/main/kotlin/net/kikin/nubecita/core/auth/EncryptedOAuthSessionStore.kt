package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import java.io.IOException
import javax.inject.Inject

internal class EncryptedOAuthSessionStore
    @Inject
    constructor(
        private val dataStore: DataStore<OAuthSession?>,
    ) : OAuthSessionStore {
        override suspend fun load(): OAuthSession? =
            dataStore.data
                .catch { cause -> if (cause is IOException) emit(null) else throw cause }
                .firstOrNull()

        override suspend fun save(session: OAuthSession) {
            dataStore.updateData { session }
        }

        override suspend fun clear() {
            dataStore.updateData { null }
        }
    }
