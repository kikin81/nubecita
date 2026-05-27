package net.kikin.nubecita.core.auth

import io.github.kikin81.atproto.runtime.XrpcClient
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Benchmark-flavor [XrpcClientProvider]. Throws [NoSessionException]
 * on every [authenticated] call.
 *
 * Section A of nubecita-crmi.6 is "Auth + Preferences fakes only" —
 * the other 12 repositories (Feed, Notifications, Profile, etc.) are
 * still backed by their real implementations under the benchmark
 * flavor. Those repositories inject [XrpcClientProvider] and call
 * [authenticated] on first fetch. Throwing here surfaces a clean
 * "no session" error state in each feature's view-model rather than
 * a network IO crash — the bench journey lands on MainShell with the
 * Feed/Notifications tabs showing their `InitialError` branches.
 *
 * Once Section A2+ lands repository-layer fakes for those features
 * (per the 14-repo enumeration in [bd show nubecita-crmi.6]), no code
 * path will reach this method and the throw becomes defensive only.
 *
 * Scoped `@Singleton` to match the production binding.
 */
@Singleton
internal class FakeXrpcClientProvider
    @Inject
    constructor() : XrpcClientProvider {
        override suspend fun authenticated(): XrpcClient =
            throw NoSessionException(
                "FakeXrpcClientProvider: the benchmark flavor does not provide an authenticated " +
                    "XrpcClient. Repository fakes should be installed instead of routing through " +
                    "the real XRPC layer.",
            )
    }
