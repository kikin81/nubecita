package net.kikin.nubecita.core.profile.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.profile.ActorProfileRepository
import net.kikin.nubecita.core.profile.DefaultActorProfileRepository
import javax.inject.Singleton

// NOT `internal`: downstream instrumentation tests reach this class
// via `@TestInstallIn(replaces = [ActorProfileModule::class])`, which
// requires the module type to be addressable from outside `:core:profile`.
// The `@Binds` method itself stays `internal` because Hilt's generated
// factory lives in the same module — no caller needs the bind function
// directly. Mirrors `core/auth/.../AuthBindingsModule` and
// `core/preferences/.../UserPreferencesBindingsModule`.
@Module
@InstallIn(SingletonComponent::class)
abstract class ActorProfileModule {
    @Binds
    @Singleton
    internal abstract fun bindActorProfileRepository(impl: DefaultActorProfileRepository): ActorProfileRepository
}
