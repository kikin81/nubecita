package net.kikin.nubecita.core.testing.android

import androidx.test.platform.app.InstrumentationRegistry

/**
 * Reads a JSON fixture from `<consumer-module>/src/androidTest/assets/fixtures/<name>`.
 *
 * Uses the instrumentation context (the test APK's assets), not the
 * target app's assets — fixtures live alongside the tests in the
 * consuming module, not bundled into the production APK.
 *
 * Each consuming module ships its own fixtures under
 * `src/androidTest/assets/fixtures/`; this loader is a thin convenience
 * wrapper that doesn't itself bundle any fixtures.
 */
fun loadFixture(name: String): String =
    InstrumentationRegistry
        .getInstrumentation()
        .context.assets
        .open("fixtures/$name")
        .bufferedReader()
        .use { it.readText() }
