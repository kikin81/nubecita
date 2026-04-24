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
import net.kikin.nubecita.core.auth.OAuthSessionSerializer
import java.security.GeneralSecurityException
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object AuthDataStoreModule {
    private const val SESSION_FILE_NAME = "oauth_session.pb"
    private const val SESSION_KEYSET_PREF_FILE = "nubecita_core_auth_keyset"
    private const val SESSION_KEYSET_NAME = "nubecita_oauth_session_keyset"
    private const val SESSION_MASTER_KEY_URI = "android-keystore://nubecita_oauth_session_master_key"
    private val SESSION_ASSOCIATED_DATA = "nubecita.oauth.session.v1".encodeToByteArray()

    @Provides
    @Singleton
    fun provideOAuthSessionDataStore(
        @ApplicationContext context: Context,
    ): DataStore<OAuthSession?> {
        AeadConfig.register()
        // Recoverable crypto failures at construction time — most commonly
        // KeyPermanentlyInvalidatedException (biometric reset / factory-wipe of user data),
        // but also any GeneralSecurityException from a corrupted keyset payload — are
        // treated as "discard the old keyset and regenerate." The retry is bounded: a
        // second failure means the Keystore environment is unrecoverable in a way we
        // cannot paper over, and propagating the exception is preferable to silently
        // losing future writes.
        val keysetHandle =
            try {
                buildKeysetHandle(context)
            } catch (_: GeneralSecurityException) {
                context.deleteSharedPreferences(SESSION_KEYSET_PREF_FILE)
                buildKeysetHandle(context)
            }
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

    private fun buildKeysetHandle(context: Context) =
        AndroidKeysetManager
            .Builder()
            .withSharedPref(context, SESSION_KEYSET_NAME, SESSION_KEYSET_PREF_FILE)
            .withKeyTemplate(KeyTemplate.createFrom(PredefinedAeadParameters.AES256_GCM))
            .withMasterKeyUri(SESSION_MASTER_KEY_URI)
            .build()
            .keysetHandle
}
