package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
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
    ) : OAuthSessionStore,
        SessionReader {
        /**
         * App-internal read that distinguishes "no session" from "read failed"
         * (epic nubecita-09xt). Storage-layer failures — IO, AEAD decrypt, JSON
         * parse, Keystore invalidation (`KeyPermanentlyInvalidatedException`
         * extends `GeneralSecurityException`) — become
         * [SessionLoadResult.ReadError] and are recorded; anything else
         * propagates.
         */
        override suspend fun loadResult(): SessionLoadResult =
            try {
                when (val session = dataStore.data.firstOrNull()) {
                    null -> SessionLoadResult.Absent
                    else -> SessionLoadResult.Loaded(session)
                }
            } catch (cause: Exception) {
                when (cause) {
                    is IOException,
                    is GeneralSecurityException,
                    is SerializationException,
                    -> {
                        telemetry.onSessionReadError(cause)
                        SessionLoadResult.ReadError(cause)
                    }
                    else -> throw cause
                }
            }

        // SDK-facing contract: any read failure degrades to `null` so the SDK
        // sees "no session" rather than a propagated exception. App-side
        // callers that must not conflate "read failed" with "signed out" use
        // [loadResult] instead.
        override suspend fun load(): OAuthSession? =
            when (val result = loadResult()) {
                is SessionLoadResult.Loaded -> result.session
                SessionLoadResult.Absent -> null
                is SessionLoadResult.ReadError -> null
            }

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
