package net.kikin.nubecita.core.actors.internal

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import net.kikin.nubecita.core.actors.AnonymousXrpcClient
import javax.inject.Singleton

/**
 * The public AppView gateway: AT Protocol lexicons that serve unauthenticated
 * callers, without the per-user PDS discovery an authenticated client needs.
 *
 * This exists because some surfaces run *before* there is a session at all —
 * the login screen's account typeahead being the first. `XrpcClientProvider`
 * cannot serve them: it throws `NoSessionException` by design.
 *
 * Sharing the singleton `HttpClient` with the authenticated path is safe and
 * deliberate. In this SDK auth is an `AuthProvider` on the `XrpcClient`, never a
 * plugin on the transport, so a client built without one cannot pick up the
 * DPoP-bound token — `XrpcClient`'s `authProvider` already defaults to `NoAuth`.
 * `HttpClientModule` documents the same expectation from the other side.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object AnonymousXrpcModule {
    @Provides
    @Singleton
    @AnonymousXrpcClient
    fun provideAnonymousXrpcClient(httpClient: HttpClient): XrpcClient = XrpcClient(baseUrl = APPVIEW_URL, httpClient = httpClient)
}

private const val APPVIEW_URL = "https://public.api.bsky.app"
