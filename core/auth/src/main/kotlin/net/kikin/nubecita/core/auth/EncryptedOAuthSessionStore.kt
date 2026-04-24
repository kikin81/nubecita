package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject

internal class EncryptedOAuthSessionStore
    @Inject
    constructor(
        private val dataStore: DataStore<OAuthSession?>,
    ) : OAuthSessionStore {
        // Spec: any failure in the read path — IO, AEAD decrypt, JSON parse, Keystore
        // invalidation — degrades to `null` so callers see "no session" rather than
        // a propagated exception. `KeyPermanentlyInvalidatedException` extends
        // `GeneralSecurityException`, so it is covered by that clause.
        override suspend fun load(): OAuthSession? =
            dataStore.data
                .catch { cause ->
                    when (cause) {
                        is IOException,
                        is GeneralSecurityException,
                        is SerializationException,
                        -> emit(null)
                        else -> throw cause
                    }
                }.firstOrNull()

        override suspend fun save(session: OAuthSession) {
            dataStore.updateData { session }
        }

        override suspend fun clear() {
            dataStore.updateData { null }
        }
    }
