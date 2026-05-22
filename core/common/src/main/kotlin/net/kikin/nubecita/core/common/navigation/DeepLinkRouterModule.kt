package net.kikin.nubecita.core.common.navigation

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Hilt bindings for the [DeepLinkRouter] singleton and the (possibly
 * empty) `Set<NavKeyDeepLinkMatcher>` multibinding that feature `:impl`
 * modules contribute to.
 *
 * The `@Multibinds` declaration is required so Hilt can resolve an
 * empty set on builds where no `@IntoSet` providers exist yet — the
 * spike PR ships this plumbing first, and the profile / post matchers
 * land in kf6k.2 / kf6k.3 respectively. Without `@Multibinds`, Hilt
 * fails compilation with "Set ... cannot be provided without an
 * @Provides-annotated method".
 *
 * Publicly addressable (not `internal`) so downstream feature modules'
 * instrumentation tests can swap the binding via
 * `@TestInstallIn(replaces = [DeepLinkRouterModule::class])` — same
 * convention as `AuthBindingsModule`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DeepLinkRouterModule {
    @Binds
    @Singleton
    internal abstract fun bindDeepLinkRouter(impl: DefaultDeepLinkRouter): DeepLinkRouter

    @Multibinds
    internal abstract fun deepLinkMatchers(): Set<NavKeyDeepLinkMatcher>
}
