package net.kikin.nubecita.core.logging.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.logging.CrashReporter
import net.kikin.nubecita.core.logging.NoOpCrashReporter
import javax.inject.Singleton

/**
 * Bench-flavor parallel of `:core:logging`'s production [LoggingModule].
 *
 * AGP source-set selection includes this file in bench-flavored variants only —
 * the production-flavor variant picks up the `src/production/...` copy instead.
 * Both files share the FQN `net.kikin.nubecita.core.logging.di.LoggingModule` so
 * `@TestInstallIn(replaces = [LoggingModule::class])` references resolve
 * identically regardless of which flavor variant they run against.
 *
 * Binds [NoOpCrashReporter] so screenshot / baseline-profile / Macrobenchmark
 * runs emit zero crash reports and never link Firebase. There is no
 * `provideFirebaseCrashlytics` here — the bench flavor doesn't depend on Firebase.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {
    @Binds
    @Singleton
    internal abstract fun bindCrashReporter(impl: NoOpCrashReporter): CrashReporter
}
