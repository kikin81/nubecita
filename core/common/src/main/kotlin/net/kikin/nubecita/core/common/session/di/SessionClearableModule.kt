package net.kikin.nubecita.core.common.session.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import net.kikin.nubecita.core.common.session.SessionClearable

/**
 * Declares an empty [SessionClearable] multibinding set so that the binding
 * exists in the Hilt graph even when no module has contributed any element.
 * Feature modules contribute their own [SessionClearable] implementations via
 * `@Binds @IntoSet` in their own Hilt modules.
 */
@Module
@InstallIn(SingletonComponent::class)
internal object SessionClearableModule {
    @Provides
    @ElementsIntoSet
    fun provideEmptySessionClearableSet(): Set<SessionClearable> = emptySet()
}
