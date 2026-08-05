package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.PendingAuth
import io.github.kikin81.atproto.oauth.PendingAuthStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.SerializationException
import timber.log.Timber
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Durable, Tink-encrypted [PendingAuthStore].
 *
 * Exists so a sign-in survives the app being killed while the Custom Tab is
 * foregrounded. Android reaps the cached app under memory pressure — confirmed
 * on a 2GB device with the app at `oom_adj 700` — and with the SDK's default
 * in-memory store the returning callback then fails with "No pending login",
 * which no amount of retrying clears (nubecita-nzec).
 *
 * ## Failure posture: never throw
 *
 * Unlike [EncryptedOAuthSessionStore], a storage failure here must NOT
 * propagate. The pending record is a 15-minute-lived convenience; if it cannot
 * be read or written the correct degradation is "no pending login", which is
 * exactly the behaviour we had before this class existed. Throwing instead
 * would convert a recoverable sign-in into a crash on the callback path, and
 * these calls sit directly in `beginLogin` / `completeLogin`.
 *
 * Cancellation is flow control and is always rethrown.
 */
@Singleton
internal class EncryptedPendingAuthStore
    @Inject
    constructor(
        private val dataStore: DataStore<PendingAuth?>,
    ) : PendingAuthStore {
        override suspend fun load(): PendingAuth? = runStorage("load") { dataStore.data.firstOrNull() }

        override suspend fun save(pending: PendingAuth) {
            runStorage("save") { dataStore.updateData { pending } }
        }

        override suspend fun clear() {
            runStorage("clear") { dataStore.updateData { null } }
        }

        /**
         * Runs a DataStore call, swallowing the storage-layer failures that
         * [EncryptedOAuthSessionStore] classifies as read errors — IO, AEAD
         * decrypt / Keystore invalidation, and JSON decode. Anything else is a
         * programming error and propagates.
         */
        private suspend fun <T> runStorage(
            operation: String,
            block: suspend () -> T,
        ): T? =
            try {
                block()
            } catch (cause: CancellationException) {
                throw cause
            } catch (cause: Exception) {
                when (cause) {
                    is IOException,
                    is GeneralSecurityException,
                    is SerializationException,
                    -> {
                        Timber.tag(TAG).w(cause, "pending-auth %s failed; treating as no pending login", operation)
                        null
                    }
                    else -> throw cause
                }
            }

        private companion object {
            const val TAG = "PendingAuthStore"
        }
    }
