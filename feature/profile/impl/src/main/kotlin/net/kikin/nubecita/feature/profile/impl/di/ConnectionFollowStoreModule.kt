package net.kikin.nubecita.feature.profile.impl.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import net.kikin.nubecita.core.common.session.SessionClearable
import net.kikin.nubecita.feature.profile.impl.data.ConnectionFollowStore

/** Clears one account's follow changes when the session ends. */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class ConnectionFollowStoreModule {
    @Binds
    @IntoSet
    abstract fun bindSessionClearable(impl: ConnectionFollowStore): SessionClearable
}
