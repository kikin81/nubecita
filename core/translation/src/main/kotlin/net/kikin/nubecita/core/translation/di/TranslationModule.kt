package net.kikin.nubecita.core.translation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.translation.LanguageIdentifier
import net.kikin.nubecita.core.translation.TranslationEngine
import net.kikin.nubecita.core.translation.TranslationModelRepository
import net.kikin.nubecita.core.translation.internal.MlKitLanguageIdentifier
import net.kikin.nubecita.core.translation.internal.MlKitTranslationEngine
import net.kikin.nubecita.core.translation.internal.MlKitTranslationModelRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class TranslationModule {
    @Binds
    @Singleton
    internal abstract fun bindTranslationEngine(impl: MlKitTranslationEngine): TranslationEngine

    @Binds
    @Singleton
    internal abstract fun bindLanguageIdentifier(impl: MlKitLanguageIdentifier): LanguageIdentifier

    @Binds
    @Singleton
    internal abstract fun bindTranslationModelRepository(impl: MlKitTranslationModelRepository): TranslationModelRepository
}
