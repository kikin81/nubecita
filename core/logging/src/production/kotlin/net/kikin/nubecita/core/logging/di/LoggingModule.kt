package net.kikin.nubecita.core.logging.di

import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.kikin.nubecita.core.logging.CrashReporter
import net.kikin.nubecita.core.logging.FirebaseCrashReporter
import javax.inject.Singleton

/**
 * Production-flavor Hilt wiring for `:core:logging`.
 *
 * Binds the real [FirebaseCrashReporter] and provides the underlying
 * [FirebaseCrashlytics] singleton. The bench-flavor parallel under `src/bench`
 * shares this FQN but binds `NoOpCrashReporter` instead — so downstream feature
 * instrumentation tests can swap either via
 * `@TestInstallIn(replaces = [LoggingModule::class])`. The module class is
 * public (the bound implementation stays `internal`) so that swap target
 * resolves from another Gradle module's `androidTest` source set.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LoggingModule {
    @Binds
    @Singleton
    internal abstract fun bindCrashReporter(impl: FirebaseCrashReporter): CrashReporter

    companion object {
        @Provides
        @Singleton
        fun provideFirebaseCrashlytics(): FirebaseCrashlytics = FirebaseCrashlytics.getInstance()
    }
}
