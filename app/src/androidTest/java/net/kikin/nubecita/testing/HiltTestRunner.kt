package net.kikin.nubecita.testing

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner
import dagger.hilt.android.testing.HiltTestApplication

/**
 * Custom [AndroidJUnitRunner] that boots [HiltTestApplication] in place of the
 * production [net.kikin.nubecita.NubecitaApplication] so `@HiltAndroidTest`
 * tests can drive the Hilt component graph.
 *
 * Wired via `defaultConfig.testInstrumentationRunner` in `app/build.gradle.kts`.
 *
 * Firebase remains available because Firebase auto-initializes via its
 * manifest [com.google.firebase.provider.FirebaseInitProvider] before any
 * Application.onCreate runs — that's also why the existing Firebase smoke
 * tests don't need the production Application class.
 */
class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(
        cl: ClassLoader?,
        className: String?,
        context: Context?,
    ): Application = super.newApplication(cl, HiltTestApplication::class.java.name, context)
}
