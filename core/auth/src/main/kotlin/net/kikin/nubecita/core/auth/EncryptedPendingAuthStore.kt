package net.kikin.nubecita.core.auth

import androidx.datastore.core.DataStore
import io.github.kikin81.atproto.oauth.PendingAuth
import io.github.kikin81.atproto.oauth.PendingAuthStore
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
         *
         * One `catch` per type rather than `catch (Exception)` plus a `when`:
         * this way the swallow list is the catch list, so it cannot drift out
         * of sync with itself. It also means [CancellationException] needs no
         * explicit rethrow — it extends `IllegalStateException` and so matches
         * none of these — instead of structured concurrency depending on a
         * guard clause that a later edit could quietly drop.
         */
        private suspend fun <T> runStorage(
            operation: String,
            block: suspend () -> T,
        ): T? =
            try {
                block()
            } catch (cause: IOException) {
                degradeToNoPendingLogin(operation, cause)
            } catch (cause: GeneralSecurityException) {
                degradeToNoPendingLogin(operation, cause)
            } catch (cause: SerializationException) {
                degradeToNoPendingLogin(operation, cause)
            }

        private fun degradeToNoPendingLogin(
            operation: String,
            cause: Exception,
        ): Nothing? {
            Timber.tag(TAG).w(cause, "pending-auth %s failed; treating as no pending login", operation)
            return null
        }

        private companion object {
            const val TAG = "PendingAuthStore"
        }
    }
