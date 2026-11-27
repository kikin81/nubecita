package net.kikin.nubecita.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import javax.inject.Singleton

/**
 * Provides the Ktor [HttpClientEngine] used by the singleton [io.ktor.client.HttpClient].
 *
 * Split out from [HttpClientModule] so instrumentation tests can replace the engine
 * (e.g. with `MockEngine`) via `@TestInstallIn(replaces = [NetworkEngineModule::class])`
 * without losing the production [HttpClientModule] configuration (timeouts, logging,
 * header sanitization).
 */
@Module
@InstallIn(SingletonComponent::class)
object NetworkEngineModule {
    @Provides
    @Singleton
    fun provideHttpClientEngine(): HttpClientEngine = OkHttp.create()

    @Provides
    @Singleton
    @TokenEndpoint
    fun provideTokenEndpointEngine(): HttpClientEngine = OkHttp.create { preconfigured = tokenEndpointOkHttpClient() }

    /**
     * A client that sends each request exactly once.
     *
     * A token POST spends a single-use refresh token, and the server deletes
     * the session when it sees a spent one again. OkHttp will re-send a
     * request by itself in three situations, and each is turned off here:
     *
     * - the connection dropped mid-flight (`retryOnConnectionFailure`);
     * - the server answered with a redirect (`followRedirects`);
     * - the server answered `503` with `Retry-After: 0`, which OkHttp
     *   retries whatever the setting above says. There is no switch for
     *   that, so the header is removed before OkHttp decides.
     *
     * A re-send the server receives is also what an `invalid_dpop_proof`
     * ("DPoP proof replayed") on a refresh means: the first copy was
     * processed and its answer lost (nubecita-75wm).
     */
    internal fun tokenEndpointOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .retryOnConnectionFailure(false)
            .followRedirects(false)
            .followSslRedirects(false)
            .addNetworkInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == HTTP_UNAVAILABLE) response.newBuilder().removeHeader("Retry-After").build() else response
            }.build()

    private const val HTTP_UNAVAILABLE = 503
}
