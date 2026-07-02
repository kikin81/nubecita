package net.kikin.nubecita.core.klipy.internal.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import kotlinx.serialization.json.Json
import net.kikin.nubecita.core.klipy.BuildConfig
import net.kikin.nubecita.core.klipy.internal.KlipyKeyRedactingLogger
import javax.inject.Singleton

/**
 * Provides KLIPY's own Ktor [HttpClient] — separate from `:core:auth`'s
 * AT-Protocol client (both are in `SingletonComponent`, hence the [KlipyClient]
 * qualifier). The key is a path segment of the base URL, so:
 *
 * - the base URL (with key) is baked into [DefaultRequest] and never passed at
 *   call sites, and
 * - request logging goes through [KlipyKeyRedactingLogger], which masks the key
 *   before anything reaches logcat, and is DEBUG-gated on top of that.
 *
 * A blank key (keyless/bench builds) yields a client that will 4xx; the bench
 * flavour uses the fake repository instead (nubecita-srx0) and never issues a
 * real request.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object KlipyNetworkModule {
    @Provides
    @Singleton
    fun provideKlipyJson(): Json = Json { ignoreUnknownKeys = true }

    @Provides
    @Singleton
    @KlipyClient
    fun provideKlipyHttpClient(): HttpClient {
        val apiKey = BuildConfig.KLIPY_API_KEY
        return HttpClient(OkHttp.create()) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
            install(DefaultRequest) {
                url("https://api.klipy.com/api/v1/$apiKey/")
            }
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    level = LogLevel.INFO
                    logger = KlipyKeyRedactingLogger(apiKey)
                }
            }
        }
    }
}
