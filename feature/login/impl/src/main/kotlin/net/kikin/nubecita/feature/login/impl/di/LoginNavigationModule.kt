package net.kikin.nubecita.feature.login.impl.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.navigation.EntryProviderInstaller
import net.kikin.nubecita.core.common.navigation.OuterShell
import net.kikin.nubecita.feature.login.api.Login
import net.kikin.nubecita.feature.login.impl.LoginScreen

@Module
@InstallIn(SingletonComponent::class)
internal object LoginNavigationModule {
    @Provides
    @IntoSet
    @OuterShell
    fun provideLoginEntries(): EntryProviderInstaller =
        {
            entry<Login> { LoginScreen() }
        }
}
