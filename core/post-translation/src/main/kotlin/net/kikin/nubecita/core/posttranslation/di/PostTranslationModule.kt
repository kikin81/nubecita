package net.kikin.nubecita.core.posttranslation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posttranslation.RoomTranslationCache
import net.kikin.nubecita.core.posttranslation.TranslationCache
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class PostTranslationModule {
    @Binds
    @Singleton
    abstract fun bindTranslationCache(impl: RoomTranslationCache): TranslationCache
}
