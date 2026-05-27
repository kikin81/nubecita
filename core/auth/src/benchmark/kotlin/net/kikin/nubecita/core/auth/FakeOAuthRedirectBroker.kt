package net.kikin.nubecita.core.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benchmark-flavor [OAuthRedirectBroker]. Exposes an empty redirect
 * stream and accepts publish calls as no-ops.
 *
 * `MainActivity` injects this to publish redirect URIs captured from
 * deep-link intents. Under the benchmark flavor we never receive such
 * an intent — `applicationIdSuffix = ".benchmark"` means the
 * `nubecita.app://oauth` redirect filter resolves to the production
 * install if both are present, and there's no OAuth consent step
 * during a bench journey anyway.
 *
 * Scoped `@Singleton` to match the production binding.
 */
@Singleton
internal class FakeOAuthRedirectBroker
    @Inject
    constructor() : OAuthRedirectBroker {
        override val redirects: Flow<String> = emptyFlow()

        override suspend fun publish(redirectUri: String) = Unit
    }
