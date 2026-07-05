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
 * NoOp everywhere is safe. Because Hilt rejects two modules replacing the same
 * one, a test that genuinely needed the real reporter could not simply add
 * another `@TestInstallIn` for [LoggingModule] alongside this one — it would
 * first have to opt out of this shared replacement and initialize Firebase itself.
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
