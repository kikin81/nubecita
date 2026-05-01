package net.kikin.nubecita.feature.login.impl.testing

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import javax.inject.Inject
import javax.inject.Singleton

/**
 * No-op [OAuthRedirectBroker] for instrumentation tests. Injected via
 * [TestAuthBindingsModule]'s `@TestInstallIn(replaces = [AuthBindingsModule::class])`.
 *
 * [LoginViewModel] subscribes to [redirects] in its `init` block; an
 * empty flow keeps the collector subscribed but never delivers a value,
 * so the post-callback `completeLogin` path doesn't fire — exactly what
 * the login-intent test wants. [publish] is a no-op since the test
 * never simulates a redirect callback.
 */
@Singleton
internal class FakeOAuthRedirectBroker
    @Inject
    constructor() : OAuthRedirectBroker {
        override val redirects: Flow<String> = emptyFlow()

        override suspend fun publish(redirectUri: String) {
            // No-op — the login-intent test asserts only that the Custom
            // Tab intent fires; the callback path is covered separately.
        }
    }
