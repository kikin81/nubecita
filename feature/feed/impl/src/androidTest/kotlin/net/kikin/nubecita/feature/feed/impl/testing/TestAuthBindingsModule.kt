package net.kikin.nubecita.feature.feed.impl.testing

import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import io.github.kikin81.atproto.runtime.XrpcClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.MutableStateFlow
import net.kikin.nubecita.core.auth.SessionState
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import net.kikin.nubecita.core.auth.di.AuthBindingsModule
import javax.inject.Singleton

/**
 * A signed-in viewer for the feed's UI tests.
 *
 * The feed reads a cache partition keyed by the viewer's DID, so a
 * signed-out graph has no partition to read and the screen renders nothing —
 * these tests would assert against an empty list. There is no session on a
 * test device, so the session is faked here instead.
 *
 * [XrpcClientProvider] is faked for the same reason it was before: the real
 * one calls `authenticated()` and throws without a session. It still hands
 * back a client over the Hilt-provided [HttpClient], so the production
 * timeout / logging / header-sanitization configuration and
 * `MockEngineModule`'s engine swap both stay in play.
 *
 * Only the two bindings this graph actually reaches are provided. Hilt
 * validates the transitive closure of what each test injects, so the
 * module's other bindings (`OAuthSessionStore`, `AuthRepository`, …) are
 * deliberately left out — the same approach `:feature:login:impl`'s test
 * graph takes. A future test that touches one of them needs a fake for it
 * here.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AuthBindingsModule::class],
)
object TestAuthBindingsModule {
    @Provides
    @Singleton
    fun provideSessionStateProvider(): SessionStateProvider =
        object : SessionStateProvider {
            override val state =
                MutableStateFlow<SessionState>(
                    SessionState.SignedIn(handle = "viewer.bsky.social", did = VIEWER_DID),
                )

            override suspend fun refresh() = Unit
        }

    @Provides
    @Singleton
    fun provideXrpcClientProvider(httpClient: HttpClient): XrpcClientProvider =
        object : XrpcClientProvider {
            override suspend fun authenticated(): XrpcClient = XrpcClient(baseUrl = "https://example.test", httpClient = httpClient)
        }

    const val VIEWER_DID = "did:plc:viewer"
}
