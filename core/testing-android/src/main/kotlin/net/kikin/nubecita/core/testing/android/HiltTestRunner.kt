package net.kikin.nubecita.core.testing.android

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom [AndroidJUnitRunner] that boots [HiltTestApplication] in place of
 * the production [net.kikin.nubecita.NubecitaApplication] so
 * `@HiltAndroidTest` tests can drive the Hilt component graph.
 *
 * Wire via `defaultConfig.testInstrumentationRunner` in each consuming
 * module's `build.gradle.kts`:
 * ```kotlin
 * testInstrumentationRunner = "net.kikin.nubecita.core.testing.android.HiltTestRunner"
 * ```
 *
 * **What this swap loses.** [net.kikin.nubecita.NubecitaApplication.onCreate]
 * does not run, so:
 * - The Firebase App Check provider factory (`installAppCheckProviderFactory`)
 *   is **not** installed. `FirebaseApp.getInstance()` and the per-SDK
 *   auto-init via [com.google.firebase.provider.FirebaseInitProvider] (a
 *   manifest content provider that runs before any Application.onCreate)
 *   still work — that's why the existing `FirebaseInitTest` smoke checks
 *   pass. But any instrumentation test that calls a Firebase service which
 *   enforces App Check (Firestore, Functions, etc.) will fail until a
 *   test-side App Check provider is installed.
 * - [timber.log.Timber.plant] is not called, so `Timber.d/e/...` calls
 *   from production code under test become no-ops in instrumentation
 *   tests. Code under test should not depend on a tree being planted; if
 *   it does, that's a bug in the production code.
 *
 * **Escape hatch.** If a future instrumentation test needs the production
 * Application's startup work, switch to Hilt's `@CustomTestApplication`
 * pattern: declare a base `Application` subclass that performs the needed
 * init (App Check, Timber, etc.), annotate a marker interface with
 * `@CustomTestApplication(Base::class)`, and have this runner instantiate
 * the generated `<Marker>_Application` class. Keep test-time providers
 * (e.g. `DebugAppCheckProviderFactory`) distinct from the production
 * factory so tests don't depend on Play Services availability.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
