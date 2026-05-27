package net.kikin.nubecita.core.auth.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.kikin81.atproto.oauth.OAuthSessionStore
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.FakeAuthRepository
import net.kikin.nubecita.core.auth.FakeOAuthRedirectBroker
import net.kikin.nubecita.core.auth.FakeOAuthSessionStore
import net.kikin.nubecita.core.auth.FakeSessionStateProvider
import net.kikin.nubecita.core.auth.FakeXrpcClientProvider
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.auth.SessionStateProvider
import net.kikin.nubecita.core.auth.XrpcClientProvider
import javax.inject.Singleton

/**
 * Benchmark-flavor parallel of `:core:auth`'s production [AuthBindingsModule].
 *
 * AGP source-set selection includes this file in benchmark-flavored
 * variants only — the production-flavor variant picks up the
 * `src/production/...` copy instead. The two files have the same FQN
 * (`net.kikin.nubecita.core.auth.di.AuthBindingsModule`) so existing
 * `@TestInstallIn(replaces = [AuthBindingsModule::class])` references in
 * downstream feature androidTests resolve identically regardless of
 * which flavor variant they run against.
 *
 * Each `@Binds` swaps the production implementation for its
 * benchmark-flavor fake. See:
 * - [FakeOAuthSessionStore] — always reports "no session"
 * - [FakeAuthRepository] — deterministic happy-path results
 * - [FakeOAuthRedirectBroker] — empty redirect stream, no-op publish
 * - [FakeSessionStateProvider] — hardcoded [SessionState.SignedIn] at boot
 * - [FakeXrpcClientProvider] — throws on `authenticated()` (no real XRPC)
 *
 * See `bd show nubecita-crmi.6` for the broader Section A scope.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthBindingsModule {
    @Binds
    @Singleton
    internal abstract fun bindOAuthSessionStore(impl: FakeOAuthSessionStore): OAuthSessionStore

    @Binds
    @Singleton
    internal abstract fun bindAuthRepository(impl: FakeAuthRepository): AuthRepository

    @Binds
    @Singleton
    internal abstract fun bindOAuthRedirectBroker(impl: FakeOAuthRedirectBroker): OAuthRedirectBroker

    @Binds
    @Singleton
    internal abstract fun bindSessionStateProvider(impl: FakeSessionStateProvider): SessionStateProvider

    @Binds
    @Singleton
    internal abstract fun bindXrpcClientProvider(impl: FakeXrpcClientProvider): XrpcClientProvider
}
