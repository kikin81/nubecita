package net.kikin.nubecita.feature.onboarding.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.OuterShell
import net.kikin.nubecita.feature.onboarding.api.Onboarding
import net.kikin.nubecita.feature.onboarding.impl.OnboardingScreen

@Module
@InstallIn(SingletonComponent::class)
internal object OnboardingNavigationModule {
    @Provides
    @IntoSet
    @OuterShell
    fun provideOnboardingEntries(): EntryProviderInstaller =
        {
            entry<Onboarding> { OnboardingScreen() }
        }
}
