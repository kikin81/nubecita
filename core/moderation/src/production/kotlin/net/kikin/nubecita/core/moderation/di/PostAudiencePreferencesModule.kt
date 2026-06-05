package net.kikin.nubecita.core.moderation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.moderation.DefaultPostAudienceDefaultRepository
import net.kikin.nubecita.core.moderation.PostAudienceDefaultRepository
import javax.inject.Singleton

// Public class (downstream `androidTest` may `@TestInstallIn(replaces = …)`);
// only the @Binds method is `internal`. Mirrors `ModerationModule`.
//
// The sign-in `refresh()` / sign-out `resetToDefault()` lifecycle wiring (a
// coordinator registered in the production `ProductionBootstrapModule`) lands in
// layer nubecita-33bw.5, where the composer consumes the default and the refresh
// becomes observable. Until then the repository seeds at PostAudience.DEFAULT.
@Module
@InstallIn(SingletonComponent::class)
abstract class PostAudiencePreferencesModule {
    @Binds
    @Singleton
    internal abstract fun bindPostAudienceDefaultRepository(
        impl: DefaultPostAudienceDefaultRepository,
    ): PostAudienceDefaultRepository
}
