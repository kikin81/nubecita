package net.kikin.nubecita.testing

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Reads a JSON fixture from `app/src/androidTest/assets/fixtures/<name>`.
 *
 * Uses the instrumentation context (the test APK's assets), not the
 * target app's assets — fixtures live alongside the tests, not bundled
 * into the production APK.
 */
fun loadFixture(name: String): String =
    InstrumentationRegistry
        .getInstrumentation()
        .context.assets
        .open("fixtures/$name")
        .bufferedReader()
        .use { it.readText() }
