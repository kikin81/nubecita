package net.kikin.nubecita.core.common.coroutines.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import net.kikin.nubecita.core.common.coroutines.ApplicationScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
internal object ApplicationScopeModule {
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        // SupervisorJob: a failure in one child coroutine (e.g. a cache
        // write) does not cancel the scope or other sibling jobs.
        // Dispatchers.Default: work is non-blocking in-memory mutation
        // (StateFlow updates, PersistentMap copy-on-write); no IO affinity.
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
