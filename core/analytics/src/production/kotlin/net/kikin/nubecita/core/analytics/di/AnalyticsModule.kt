package net.kikin.nubecita.core.analytics.di

import android.content.Context
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.AnalyticsInstanceIdProvider
import net.kikin.nubecita.core.analytics.FirebaseAnalyticsClient
import net.kikin.nubecita.core.analytics.FirebaseAnalyticsInstanceIdProvider
import javax.inject.Singleton

/**
 * Production-flavor Hilt wiring for `:core:analytics`.
 *
 * Binds the real [FirebaseAnalyticsClient] and provides the underlying
 * [FirebaseAnalytics] instance. The bench-flavor parallel under `src/bench`
 * shares this FQN but binds `NoOpAnalyticsClient` instead — so downstream
 * feature instrumentation tests can swap either via
 * `@TestInstallIn(replaces = [AnalyticsModule::class])`. The module class is
 * public (the bound implementation stays `internal`) so that swap target
 * resolves from another Gradle module's `androidTest` source set.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    @Singleton
    internal abstract fun bindAnalyticsClient(impl: FirebaseAnalyticsClient): AnalyticsClient

    @Binds
    @Singleton
    internal abstract fun bindAnalyticsInstanceIdProvider(
        impl: FirebaseAnalyticsInstanceIdProvider,
    ): AnalyticsInstanceIdProvider

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseAnalytics(
            @ApplicationContext context: Context,
        ): FirebaseAnalytics = FirebaseAnalytics.getInstance(context)
    }
}
