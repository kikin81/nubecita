package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

// @Singleton on the CLASS, not just the bindings: this impl is bound to two
// interfaces (OAuthSessionStore + SessionReader). @Binds @Singleton scopes
// each binding separately, so without class-level scoping Dagger would build
// one instance per interface.
@Singleton
internal class EncryptedOAuthSessionStore
    @Inject
    constructor(
        private val dataStore: DataStore<OAuthSession?>,
        private val telemetry: SessionTelemetry,
    ) : OAuthSessionStore,
        SessionReader,
        SessionResultStream {
        /**
         * Continuous projection of the session DataStore. Deliberately does NOT
         * catch storage failures the way [loadResult] does: the collector owns
         * the bounded retry, and swallowing here would collapse a transient
         * read error into an indistinguishable [SessionLoadResult.Absent] —
         * the exact conflation epic nubecita-09xt removed.
         */
        override fun results(): Flow<SessionLoadResult> =
            dataStore.data.map { session ->
                when (session) {
                    null -> SessionLoadResult.Absent
                    else -> SessionLoadResult.Loaded(session)
                }
            }

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
            } catch (cause: CancellationException) {
                throw cause // cancellation is flow control, never a ReadError
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
            // bucketing reads. The full non-fatal still fires only after the
            // wipe succeeds; only the bucketed reason is persisted ahead of it.
            val marker = SessionClearedException()
            // Durable breadcrumb first: NonCancellable below saves the report
            // from a cancelled scope, but not from the process being killed
            // outright, and the marker lives only on this stack.
            telemetry.onSessionClearing(marker)
            // NonCancellable from here: once the marker is down, the wipe and its
            // report must reach a definite outcome together. onSessionCleared
            // suspends (login-timestamp read, refresh forensics) and rethrows
            // CancellationException, so on a plain scope a background clear whose
            // worker is stopped destroyed the session and reported nothing — the
            // marker lives only on this stack, so nothing reconstructed it later
            // (nubecita-yv4p). Covering the write too removes the other half of
            // that ambiguity: a cancelled-mid-write clear can no longer leave a
            // marker behind for a session that is still on disk.
            withContext(NonCancellable) {
                try {
                    dataStore.updateData { null }
                } catch (e: Exception) {
                    // The session survived, so the marker would report a logout
                    // that never happened. A blanket catch is right here: inside
                    // NonCancellable there is no cancellation to honour, so any
                    // throw means the write genuinely did not commit.
                    telemetry.onSessionClearAborted()
                    throw e
                }
                telemetry.onSessionCleared(marker)
            }
        }
    }
