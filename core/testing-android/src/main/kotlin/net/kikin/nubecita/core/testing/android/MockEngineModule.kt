package net.kikin.nubecita.core.testing.android

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.engine.mock.MockEngine
import net.kikin.nubecita.core.auth.di.NetworkEngineModule
import javax.inject.Singleton

/**
 * Replaces the production [NetworkEngineModule] in instrumentation tests
 * with a [MockEngine] driven by [MockEngineHandlerHolder].
 *
 * Production [net.kikin.nubecita.core.auth.di.HttpClientModule] still
 * builds the singleton `HttpClient` from this `HttpClientEngine`, so the
 * timeouts, logging, and header sanitization configuration remain
 * exactly as in production — only the engine layer is swapped.
 *
 * The mock engine reads `holder.handler` on every request, so tests can
 * mutate the handler at any time (typically once in `@Before`) and the
 * change applies to subsequent requests immediately.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [NetworkEngineModule::class],
)
object MockEngineModule {
    @Provides
    @Singleton
    fun provideHttpClientEngine(holder: MockEngineHandlerHolder): HttpClientEngine =
        MockEngine { request ->
            holder.handler.invoke(this, request)
        }
}
