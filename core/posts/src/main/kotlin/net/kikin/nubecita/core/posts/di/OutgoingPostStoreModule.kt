package net.kikin.nubecita.core.posts.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.posting.OutgoingPostStore
import net.kikin.nubecita.core.posts.internal.DefaultOutgoingPostStore
import javax.inject.Singleton

/**
 * Binds the app-scoped [OutgoingPostStore]. Flavor-agnostic: the bench build
 * gets its network-free behavior from the bench `PostingRepository` and
 * `PostRepository` the store depends on.
 */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class OutgoingPostStoreModule {
    @Binds
    @Singleton
    abstract fun bindOutgoingPostStore(impl: DefaultOutgoingPostStore): OutgoingPostStore
}
