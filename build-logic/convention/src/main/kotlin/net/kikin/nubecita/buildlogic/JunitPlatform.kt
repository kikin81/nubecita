package net.kikin.nubecita.buildlogic

import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * Wires the JUnit 5 (Jupiter) Platform runner into the module's unit-test
 * source set: `useJUnitPlatform()` plus `testImplementation` on the API +
 * engine artifacts so tests under `src/test/` can `@Test`-annotate with
 * `org.junit.jupiter.api.Test`.
 *
 * Only the unit-test source set is affected. The AGP-managed Compose
 * screenshot runner stays on JUnit 4 internally and is not touched here —
 * `screenshotTest` source sets continue to consume the screenshot plugin's
 * own deps without JUnit 5 wiring.
 */
internal fun Project.configureJunitPlatform() {
    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
    dependencies {
        "testImplementation"(libs.findLibrary("junit-jupiter-api").get())
        "testRuntimeOnly"(libs.findLibrary("junit-jupiter-engine").get())
        // Gradle 9 no longer auto-loads junit-platform-launcher off the test
        // worker classpath — it must be on testRuntimeClasspath explicitly,
        // otherwise the worker fails with "Failed to load JUnit Platform".
        "testRuntimeOnly"(libs.findLibrary("junit-platform-launcher").get())
    }
}
