package net.kikin.nubecita.core.auth.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Hilt qualifier for the `:core:auth` Singleton-scoped [CoroutineScope]
 * used to collect the session-state flow inside long-lived auth
 * components (e.g. `DefaultXrpcClientProvider`'s cache invalidator).
 *
 * Internal so cross-feature consumers don't reach in to launch background
 * work on the auth module's scope — they own their own scopes.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class AuthCoroutineScope

@Module
@InstallIn(SingletonComponent::class)
internal object AuthCoroutineScopeModule {
    @Provides
    @Singleton
    @AuthCoroutineScope
    fun provideAuthCoroutineScope(): CoroutineScope =
        // SupervisorJob: a failure in one collector (e.g. the session-state
        // observer) doesn't cancel sibling collectors. Dispatchers.Default:
        // the work is non-blocking flow collection + cache mutation; no UI
        // affinity required.
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
