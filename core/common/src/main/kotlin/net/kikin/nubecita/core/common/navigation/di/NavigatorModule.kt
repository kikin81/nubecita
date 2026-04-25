package net.kikin.nubecita.core.common.navigation.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.common.navigation.DefaultNavigator
import net.kikin.nubecita.core.common.navigation.Navigator
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal abstract class NavigatorModule {
    @Binds
    @Singleton
    internal abstract fun bindNavigator(impl: DefaultNavigator): Navigator
}
