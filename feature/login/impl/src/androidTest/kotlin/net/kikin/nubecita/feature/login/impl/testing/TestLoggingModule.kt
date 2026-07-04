package net.kikin.nubecita.feature.login.impl.testing

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.core.logging.CrashReporter
import net.kikin.nubecita.core.logging.NoOpCrashReporter
import net.kikin.nubecita.core.logging.di.LoggingModule

/**
 * Swaps `:core:logging`'s production [LoggingModule] (which binds the real
 * `FirebaseCrashReporter`) for a [NoOpCrashReporter] in `:feature:login:impl`'s
 * androidTest graph.
 *
 * `:feature:login:impl` is unflavored, so it resolves the `production` variant of
 * `:core:logging` via `missingDimensionStrategy` — i.e. the real
 * `FirebaseCrashReporter`, which calls `FirebaseCrashlytics.getInstance()` →
 * `FirebaseApp.getInstance()` at construction and throws because Firebase is never
 * initialized in an instrumentation test. The bench flavor already binds the NoOp
 * for the same reason; this mirrors that swap for the test graph.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LoggingModule::class],
)
internal abstract class TestLoggingModule {
    @Binds
    abstract fun bindCrashReporter(impl: NoOpCrashReporter): CrashReporter
}
