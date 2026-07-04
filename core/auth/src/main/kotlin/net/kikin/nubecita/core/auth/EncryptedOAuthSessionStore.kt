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
        private val telemetry: SessionTelemetry,
    ) : OAuthSessionStore {
        // Spec: any failure in the read path — IO, AEAD decrypt, JSON parse, Keystore
        // invalidation — degrades to `null` so callers see "no session" rather than
        // a propagated exception. `KeyPermanentlyInvalidatedException` extends
        // `GeneralSecurityException`, so it is covered by that clause.
        //
        // Every degradation is recorded (epic nubecita-09xt): at the routing layer
        // "read failed" is indistinguishable from "signed out", so these swallows
        // are the top spurious-logout suspect and must be measurable.
        override suspend fun load(): OAuthSession? =
            dataStore.data
                .catch { cause ->
                    when (cause) {
                        is IOException,
                        is GeneralSecurityException,
                        is SerializationException,
                        -> {
                            telemetry.onSessionReadError(cause)
                            emit(null)
                        }
                        else -> throw cause
                    }
                }.firstOrNull()

        override suspend fun save(session: OAuthSession) {
            dataStore.updateData { session }
        }

        override suspend fun clear() {
            // The marker must be constructed before the suspending write: after a
            // suspension point the resumed JVM stack no longer carries the caller
            // frames (SDK failRefresh vs AtOAuth.logout) that clear-reason
            // bucketing reads. Telemetry fires only after the wipe succeeds.
            val marker = SessionClearedException()
            dataStore.updateData { null }
            telemetry.onSessionCleared(marker)
        }
    }
