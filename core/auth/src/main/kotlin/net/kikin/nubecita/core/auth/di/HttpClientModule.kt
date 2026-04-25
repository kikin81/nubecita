package net.kikin.nubecita.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import javax.inject.Singleton

/**
 * Singleton Ktor [HttpClient] used by every AT Protocol call — both the
 * unauthenticated AppView calls (`:app`'s anonymous XrpcClient) and the
 * authenticated OAuth flow (`AtOAuth` in this module).
 *
 * Lives in `:core:auth` because `AtOAuth`'s constructor requires it and
 * `:core:auth` is the natural shared owner. Promote to a dedicated
 * `:core:network` module if a third consumer (e.g. analytics, telemetry)
 * needs its own configured HttpClient.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object HttpClientModule {
    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient =
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
        }
}
