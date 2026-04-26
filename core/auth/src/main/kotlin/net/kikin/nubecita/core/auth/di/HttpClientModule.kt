package net.kikin.nubecita.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import timber.log.Timber
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
    private const val TAG = "AtProtoHttp"

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient =
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
            // Debug-only request/response trace. HEADERS level surfaces the
            // status, URL, and DPoP-Nonce rotation needed to diagnose the
            // cold-start refresh path (nubecita-09o) without logging request
            // bodies. The Authorization/DPoP header values DO appear in
            // logcat at this level — release builds plant no Timber tree, so
            // Timber.d short-circuits to a no-op (Logging-plugin formatting
            // overhead aside, no logcat output and no PII leak). Filter via:
            //   adb logcat -s AtProtoHttp
            if (Timber.treeCount > 0) {
                install(Logging) {
                    level = LogLevel.HEADERS
                    logger =
                        object : Logger {
                            override fun log(message: String) {
                                Timber.tag(TAG).d(message)
                            }
                        }
                }
            }
        }
}
