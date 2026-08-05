package net.kikin.nubecita.core.auth.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.datastore.tink.AeadSerializer
import com.google.crypto.tink.KeyTemplate
import com.google.crypto.tink.RegistryConfiguration
import com.google.crypto.tink.aead.AeadConfig
import com.google.crypto.tink.aead.PredefinedAeadParameters
import com.google.crypto.tink.integration.android.AndroidKeysetManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.oauth.OAuthSession
import io.github.kikin81.atproto.oauth.PendingAuth
import net.kikin.nubecita.core.auth.OAuthSessionSerializer
import net.kikin.nubecita.core.auth.PendingAuthSerializer
import net.kikin.nubecita.core.auth.SessionTelemetry
import timber.log.Timber
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AuthDataStoreModule {
    private const val SESSION_FILE_NAME = "oauth_session.pb"
    private const val SESSION_KEYSET_PREF_FILE = "nubecita_core_auth_keyset"
    private const val SESSION_KEYSET_NAME = "nubecita_oauth_session_keyset"
    private const val SESSION_MASTER_KEY_URI = "android-keystore://nubecita_oauth_session_master_key"
    private val SESSION_ASSOCIATED_DATA = "nubecita.oauth.session.v1".encodeToByteArray()

    private const val PENDING_FILE_NAME = "oauth_pending_auth.pb"
    private const val PENDING_KEYSET_PREF_FILE = "nubecita_core_auth_pending_keyset"
    private const val PENDING_KEYSET_NAME = "nubecita_oauth_pending_keyset"
    private const val PENDING_MASTER_KEY_URI = "android-keystore://nubecita_oauth_pending_master_key"
    private val PENDING_ASSOCIATED_DATA = "nubecita.oauth.pending.v1".encodeToByteArray()

    @Provides
    @Singleton
    fun provideOAuthSessionDataStore(
        @ApplicationContext context: Context,
        telemetry: SessionTelemetry,
    ): DataStore<OAuthSession?> {
        AeadConfig.register()
        // Crypto failures at construction time get one non-destructive delayed
        // retry (a transiently unavailable Keystore presents exactly like a
        // corrupted keyset — see KeysetRecovery); only a persistent failure —
        // most commonly KeyPermanentlyInvalidatedException (biometric reset /
        // factory-wipe of user data) or a corrupted keyset payload — is treated
        // as "discard the old keyset and regenerate," and every fire is
        // reported (epic nubecita-09xt).
        //
        // Regeneration makes the previously persisted session ciphertext
        // permanently undecryptable, so the reset also deletes the session
        // file: the state is then honestly "signed out" (clean re-login)
        // instead of a session that fails decryption on every cold start —
        // which under SessionLoadResult would burn the full ReadError retry
        // schedule behind the splash on every launch, forever.
        val keysetHandle =
            KeysetRecovery.buildWithRecovery(
                build = { buildKeysetHandle(context) },
                reset = {
                    context.deleteSharedPreferences(SESSION_KEYSET_PREF_FILE)
                    context.dataStoreFile(SESSION_FILE_NAME).delete()
                },
                onRegenerated = telemetry::onKeysetRegenerated,
            )
        val aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), com.google.crypto.tink.Aead::class.java)
        val encryptedSerializer =
            AeadSerializer(
                aead = aead,
                wrappedSerializer = OAuthSessionSerializer,
                associatedData = SESSION_ASSOCIATED_DATA,
            )
        return DataStoreFactory.create(
            serializer = encryptedSerializer,
            produceFile = { context.dataStoreFile(SESSION_FILE_NAME) },
        )
    }

    /**
     * Encrypted store for the in-flight login ([PendingAuth]) so a sign-in
     * survives the app being killed while the Custom Tab is foregrounded
     * (nubecita-nzec). Holds a PKCE verifier and a DPoP private key, so it gets
     * the same Tink AEAD treatment as the session.
     *
     * Deliberately a SEPARATE keyset and file from the session, not a reuse of
     * [provideOAuthSessionDataStore]'s: that keyset's recovery path is
     * load-bearing for spurious-logout (epic nubecita-09xt), and a 15-minute
     * scratch record has no business sharing its blast radius. Losing this
     * keyset costs at most one in-flight login — which is exactly the behaviour
     * we had before it existed — so its reset is unconditional and quiet, with
     * no session telemetry attached.
     */
    @Provides
    @Singleton
    fun providePendingAuthDataStore(
        @ApplicationContext context: Context,
    ): DataStore<PendingAuth?> {
        AeadConfig.register()
        val keysetHandle =
            KeysetRecovery.buildWithRecovery(
                build = { buildPendingKeysetHandle(context) },
                reset = {
                    context.deleteSharedPreferences(PENDING_KEYSET_PREF_FILE)
                    context.dataStoreFile(PENDING_FILE_NAME).delete()
                },
                onRegenerated = { cause ->
                    Timber.tag("PendingAuthStore").w(cause, "pending-auth keyset regenerated; in-flight login discarded")
                },
            )
        val aead = keysetHandle.getPrimitive(RegistryConfiguration.get(), com.google.crypto.tink.Aead::class.java)
        val encryptedSerializer =
            AeadSerializer(
                aead = aead,
                wrappedSerializer = PendingAuthSerializer,
                associatedData = PENDING_ASSOCIATED_DATA,
            )
        return DataStoreFactory.create(
            serializer = encryptedSerializer,
            produceFile = { context.dataStoreFile(PENDING_FILE_NAME) },
        )
    }

    private fun buildKeysetHandle(context: Context) =
        AndroidKeysetManager
            .Builder()
            .withSharedPref(context, SESSION_KEYSET_NAME, SESSION_KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri(SESSION_MASTER_KEY_URI)
            .build()
            .keysetHandle

    private fun buildPendingKeysetHandle(context: Context) =
        AndroidKeysetManager
            .Builder()
            .withSharedPref(context, PENDING_KEYSET_NAME, PENDING_KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri(PENDING_MASTER_KEY_URI)
            .build()
            .keysetHandle
}
