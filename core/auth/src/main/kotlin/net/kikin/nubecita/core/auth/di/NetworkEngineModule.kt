package net.kikin.nubecita.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.okhttp.OkHttp
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
}
