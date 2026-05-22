package net.kikin.nubecita.buildlogic

import com.android.build.api.dsl.TestExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

/**
 * Convention plugin for `:benchmark` — the AndroidX Macrobenchmark module
 * that exercises the real `:app` APK and reports startup + frame-timing
 * metrics. Applies:
 *
 * - `com.android.test` — Android test-only module shape (no production
 *   APK output; the module ships an APK that runs against a separately-
 *   installed target). Under AGP 9 this brings Kotlin support inline, so
 *   `org.jetbrains.kotlin.android` is intentionally NOT applied.
 * - `androidx.baselineprofile` — registers `:benchmark` as a baseline-
 *   profile *producer*. `:app` applies the same plugin as a consumer and
 *   wires `baselineProfile(project(":benchmark"))` to pull generated
 *   profiles. The plugin auto-generates `benchmarkRelease` and
 *   `nonMinifiedRelease` variants off `:app`'s `release` build type, so
 *   this convention does NOT declare its own build types — doing so
 *   collides with the plugin's naming. The actual
 *   `BaselineProfileGenerator` test that produces a profile is a
 *   follow-up ticket (`nubecita-crmi.2`); landing the producer wiring
 *   here keeps that change scoped to "add the generator test" rather
 *   than "add the generator AND rewire build-logic".
 * - `com.squareup.sort-dependencies` — consistency with every other
 *   Android module in the repo.
 *
 * Configures compileSdk = 37 / minSdk = 28 (matching the rest of the
 * Android conventions), Java 17 source/target, JVM 17 toolchain,
 * `targetProjectPath = ":app"`, and the
 * `android.experimental.self-instrumenting` flag Macrobenchmark requires
 * under AGP 9. Pulls in `benchmark-macro-junit4` and
 * `androidx.test.uiautomator` as `implementation` deps so test sources
 * under `src/main/` can `@RunWith(AndroidJUnit4)` directly (the
 * `com.android.test` module type's main sources ARE the test sources —
 * there's no separate `androidTest` source set).
 */
class AndroidBenchmarkConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // AGP 9 ships built-in Kotlin support for `com.android.*` modules,
            // so `org.jetbrains.kotlin.android` is intentionally NOT applied
            // here — applying it raises a "plugin no longer required" error.
            pluginManager.apply("com.android.test")
            pluginManager.apply("androidx.baselineprofile")
            pluginManager.apply("com.squareup.sort-dependencies")

            extensions.configure<TestExtension> {
                compileSdk = 37
                defaultConfig {
                    minSdk = 28
                    // Macrobenchmark requires API 23+ at runtime; matching
                    // :app's compileSdk for `targetSdk` keeps the variant
                    // resolver happy when targeting :app:benchmark.
                    targetSdk = 37
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions.sourceCompatibility = JavaVersion.VERSION_17
                compileOptions.targetCompatibility = JavaVersion.VERSION_17

                @Suppress("UnstableApiUsage")
                experimentalProperties["android.experimental.self-instrumenting"] = true

                targetProjectPath = ":app"

                // The androidx.baselineprofile plugin auto-generates the
                // matching variants (`benchmark`-qualified plus a
                // `nonMinified`-qualified twin used for profile generation)
                // off :app's build-type matrix. We intentionally don't
                // declare our own build types here — doing so collides
                // with the plugin's naming and produces awkward task
                // names like `assembleBenchmarkBenchmark`.
            }

            dependencies {
                "implementation"(libs.findLibrary("androidx-test-runner").get())
                "implementation"(libs.findLibrary("androidx-test-ext-junit").get())
                "implementation"(libs.findLibrary("androidx-test-uiautomator").get())
                "implementation"(libs.findLibrary("androidx-benchmark-macro-junit4").get())
            }
        }
    }
}
