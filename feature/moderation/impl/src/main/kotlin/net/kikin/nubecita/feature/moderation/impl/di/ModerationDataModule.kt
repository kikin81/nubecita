package net.kikin.nubecita.feature.moderation.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.feature.moderation.impl.ModerationRepository
import net.kikin.nubecita.feature.moderation.impl.data.DefaultModerationRepository
import javax.inject.Singleton

/**
 * Hilt bindings for the moderation data layer.
 *
 * `@Binds` is `internal` to `:feature:moderation:impl` — the
 * [ModerationRepository] interface is the only public surface, and
 * future moderation children (oftc.4 Block, oftc.5 Mute) will land
 * their bindings in this same module rather than introducing new ones.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ModerationDataModule {
    @Binds
    @Singleton
    abstract fun bindModerationRepository(impl: DefaultModerationRepository): ModerationRepository
}
