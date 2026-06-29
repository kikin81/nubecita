package net.kikin.nubecita.core.postinteractions.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.session.SessionClearable
import net.kikin.nubecita.core.postinteractions.PostInteractionHandler
import net.kikin.nubecita.core.postinteractions.PostInteractionsCache
import net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionHandler
import net.kikin.nubecita.core.postinteractions.internal.DefaultPostInteractionsCache
import javax.inject.Singleton

/**
 * Hilt bindings for [PostInteractionsCache], [SessionClearable], and
 * [PostInteractionHandler]. These bindings are flavor-independent and live in
 * `src/main`.
 *
 * The [LikeRepostRepository] and [FollowRepository] write bindings have been
 * moved to the flavored `PostInteractionsWriteModule` (same FQN in
 * `src/production/` and `src/bench/`), so that the bench build can swap in
 * offline no-op fakes without touching this module. AGP source-set selection
 * picks exactly one write module per variant.
 *
 * The cache is `@Singleton`-scoped (matches the class-level annotation on
 * [DefaultPostInteractionsCache]) so all VMs across the app share one
 * canonical state map.
 *
 * The class is publicly addressable (not `internal`) so future test modules in
 * any feature module can replace it via
 * `@TestInstallIn(replaces = [PostInteractionsModule::class])`.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PostInteractionsModule {
    @Binds
    @Singleton
    internal abstract fun bindPostInteractionsCache(
        impl: DefaultPostInteractionsCache,
    ): PostInteractionsCache

    @Binds
    @IntoSet
    internal abstract fun bindPostInteractionsCacheAsSessionClearable(
        impl: DefaultPostInteractionsCache,
    ): SessionClearable

    /**
     * Binds [DefaultPostInteractionHandler] → [PostInteractionHandler].
     *
     * **Intentionally unscoped** (no `@Singleton`, no `@ActivityRetainedScoped`).
     * Each ViewModel that injects [PostInteractionHandler] receives its own
     * instance via `bind(surface, viewModelScope)`. Scoping the handler to the
     * singleton component would let one VM's surface bleed into another's
     * analytics attribution at runtime.
     */
    @Binds
    internal abstract fun bindPostInteractionHandler(
        impl: DefaultPostInteractionHandler,
    ): PostInteractionHandler
}
