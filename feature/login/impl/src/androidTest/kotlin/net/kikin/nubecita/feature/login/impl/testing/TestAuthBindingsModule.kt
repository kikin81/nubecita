package net.kikin.nubecita.feature.login.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.core.auth.AuthRepository
import net.kikin.nubecita.core.auth.OAuthRedirectBroker
import net.kikin.nubecita.core.auth.di.AuthBindingsModule

/**
 * Replaces the production [AuthBindingsModule] in `:feature:login:impl`'s
 * androidTest graph. Provides only the two interfaces the
 * `LoginViewModel` actually injects ([AuthRepository], [OAuthRedirectBroker]);
 * the other bindings the production module declares
 * (`OAuthSessionStore`, `SessionStateProvider`, `XrpcClientProvider`)
 * are intentionally NOT replaced because nothing in the login-test
 * graph requests them — Hilt validates only the transitive closure of
 * what each test class injects, so omitting them keeps the test surface
 * small without false-positive validation errors.
 *
 * If a future login test exercises a path that touches one of those
 * unbound interfaces, this module needs a fake binding for it (or the
 * test should fake at a narrower boundary).
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [AuthBindingsModule::class],
)
internal interface TestAuthBindingsModule {
    @Binds
    fun bindFakeAuthRepository(impl: FakeAuthRepository): AuthRepository

    @Binds
    fun bindFakeOAuthRedirectBroker(impl: FakeOAuthRedirectBroker): OAuthRedirectBroker
}
