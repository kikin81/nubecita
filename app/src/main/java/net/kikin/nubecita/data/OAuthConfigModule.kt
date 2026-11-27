package net.kikin.nubecita.data

import com.google.firebase.appcheck.FirebaseAppCheck
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.tasks.await
import net.kikin.nubecita.BuildConfig
import net.kikin.nubecita.core.auth.AttestationTokenProvider
import net.kikin.nubecita.core.auth.attestationTokenProvider
import net.kikin.nubecita.core.auth.di.OAuthClientMetadataUrl
import net.kikin.nubecita.core.auth.di.OAuthMediatorUrl
import net.kikin.nubecita.core.auth.di.OAuthRedirectUri
import net.kikin.nubecita.core.auth.di.OAuthScope
import javax.inject.Singleton

/**
 * `:app`-side providers for the OAuth configuration values that
 * `:core:auth`'s `AtOAuthModule` injects. Read from `BuildConfig` fields
 * set in `app/build.gradle.kts` so dev / prod / future flavors can supply
 * different values without touching `:core:auth`.
 */
@Module
@InstallIn(SingletonComponent::class)
object OAuthConfigModule {
    @Provides
    @Singleton
    @OAuthClientMetadataUrl
    fun provideOAuthClientMetadataUrl(): String = BuildConfig.OAUTH_CLIENT_METADATA_URL

    @Provides
    @Singleton
    @OAuthRedirectUri
    fun provideOAuthRedirectUri(): String = BuildConfig.OAUTH_REDIRECT_URI

    @Provides
    @Singleton
    @OAuthScope
    fun provideOAuthScope(): String = BuildConfig.OAUTH_SCOPE

    @Provides
    @Singleton
    @OAuthMediatorUrl
    fun provideOAuthMediatorUrl(): String = BuildConfig.OAUTH_MEDIATOR_URL

    /**
     * Firebase App Check as the attestation source. Only ever called when a
     * mediator URL is configured, so a build without one makes no App Check
     * request on behalf of sign-in.
     */
    @Provides
    @Singleton
    fun provideAttestationTokenProvider(): AttestationTokenProvider =
        attestationTokenProvider {
            FirebaseAppCheck
                .getInstance()
                .getAppCheckToken(false)
                .await()
                .token
        }
}
