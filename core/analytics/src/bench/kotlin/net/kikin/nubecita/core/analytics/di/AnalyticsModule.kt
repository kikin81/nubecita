package net.kikin.nubecita.core.analytics.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.analytics.AnalyticsClient
import net.kikin.nubecita.core.analytics.NoOpAnalyticsClient

/**
 * Bench-flavor parallel of `:core:analytics`'s production [AnalyticsModule].
 *
 * AGP source-set selection includes this file in bench-flavored variants only —
 * the production-flavor variant picks up the `src/production/...` copy instead.
 * Both files share the FQN `net.kikin.nubecita.core.analytics.di.AnalyticsModule`
 * so existing `@TestInstallIn(replaces = [AnalyticsModule::class])` references in
 * downstream feature androidTests resolve identically regardless of which flavor
 * variant they run against.
 *
 * Binds [NoOpAnalyticsClient] so screenshot / baseline-profile / Macrobenchmark
 * runs emit zero analytics and never link Firebase. There is no
 * `provideFirebaseAnalytics` here — the bench flavor doesn't depend on Firebase.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {
    @Binds
    internal abstract fun bindAnalyticsClient(impl: NoOpAnalyticsClient): AnalyticsClient
}
