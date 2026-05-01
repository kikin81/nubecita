package net.kikin.nubecita.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import net.kikin.nubecita.core.auth.BuildConfig
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
    fun provideHttpClient(engine: HttpClientEngine): HttpClient =
        HttpClient(engine) {
            install(HttpTimeout) {
                requestTimeoutMillis = 30_000
                connectTimeoutMillis = 10_000
                socketTimeoutMillis = 30_000
            }
            // Debug-only request/response trace. HEADERS level surfaces the
            // status, URL, and DPoP-Nonce rotation needed to diagnose the
            // cold-start refresh path (nubecita-09o) without logging request
            // bodies. Gated on BuildConfig.DEBUG (NOT Timber.treeCount) so a
            // future release Timber tree (Crashlytics/Sentry) cannot retroactively
            // enable header logging and leak access tokens. Defense in depth:
            // sanitizeHeader also redacts Authorization + DPoP values even in
            // debug, so the diagnostically-useful DPoP-Nonce stays visible while
            // the bearer token + signed proof JWT do not. Filter via:
            //   adb logcat -s AtProtoHttp
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    level = LogLevel.HEADERS
                    logger =
                        object : Logger {
                            override fun log(message: String) {
                                Timber.tag(TAG).d(message)
                            }
                        }
                    sanitizeHeader { header ->
                        header.equals("Authorization", ignoreCase = true) ||
                            header.equals("DPoP", ignoreCase = true) ||
                            header.equals("Cookie", ignoreCase = true) ||
                            header.equals("Set-Cookie", ignoreCase = true)
                    }
                }
            }
        }
}
