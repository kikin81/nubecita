package net.kikin.nubecita.core.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bench-flavor [OAuthRedirectBroker]. Exposes an empty redirect stream
 * and accepts publish calls as no-ops.
 *
 * `MainActivity` injects this to publish redirect URIs captured from
 * deep-link intents. Under the bench flavor we never receive such an
 * intent — [FakeSessionStateProvider] reports `SignedIn` at boot, so
 * the OAuth flow that would surface a redirect is never composed.
 * The no-op `publish` is defensive only.
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
