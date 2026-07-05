package net.kikin.nubecita.core.testing.android

import dagger.Binds
import dagger.Module
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import net.kikin.nubecita.core.logging.CrashReporter
import net.kikin.nubecita.core.logging.NoOpCrashReporter
import net.kikin.nubecita.core.logging.di.LoggingModule
import javax.inject.Singleton

/**
 * Replaces the production [LoggingModule] in instrumentation tests with a
 * [NoOpCrashReporter], for every module whose androidTest graph pulls in
 * `:core:testing-android`.
 *
 * The production `LoggingModule` binds the real `FirebaseCrashReporter` and
 * provides `FirebaseCrashlytics.getInstance()`, which calls
 * `FirebaseApp.getInstance()`. Firebase's default app is only initialized in
 * `:app` (where the `google-services` plugin generates the init resources
 * consumed by [com.google.firebase.provider.FirebaseInitProvider]); a
 * library module's instrumentation-test APK has no default `FirebaseApp`, so
 * the moment its Hilt graph instantiates the crash reporter it throws
 * `IllegalStateException: Default FirebaseApp is not initialized`. Swapping in
 * the NoOp reporter removes that dependency for the whole androidTest fleet —
 * the same centralized approach [MockEngineModule] takes for the production
 * `NetworkEngineModule`.
 *
 * No instrumentation test asserts on crash-reporting behaviour, so forcing the
 * NoOp everywhere is safe; a test that ever needs the real reporter would
 * install its own `@TestInstallIn` and be responsible for initializing Firebase.
 */
@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [LoggingModule::class],
)
abstract class TestLoggingModule {
    // @Singleton to match the production LoggingModule's scoping it replaces.
    @Binds
    @Singleton
    abstract fun bindCrashReporter(impl: NoOpCrashReporter): CrashReporter
}
